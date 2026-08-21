package com.prizma.iptv.data.repo

import com.prizma.iptv.data.local.Paths
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.local.Tsv
import com.prizma.iptv.data.model.EpgProgram
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.SourceType
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.remote.XmltvParser
import com.prizma.iptv.data.remote.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Yayın akışı (EPG) deposu.
 *
 * Tüm kanalların akışı tek bir XMLTV indirmesiyle alınır; kanal başına
 * ayrı istek atmak binlerce kanallı paneller için uygulanabilir değil.
 * Panel XMLTV vermiyorsa tek tek kanal için Xtream uç noktasına düşülür.
 */
class EpgRepository(private val profile: Profile) {

    private companion object {
        const val VERSION = 1
        const val COLUMNS = 5
        const val NAME_VERSION = 1
        const val NAME_COLUMNS = 2
        /** Bellekte tutulacak azami program sayısı. */
        const val MAX_PROGRAMS = 400_000
        const val PAST_WINDOW_MS = 12 * 3_600_000L
    }

    private val mutex = Mutex()

    // Bu haritalar arayüz iş parçacığından okunurken arka planda tazeleniyor.
    // Yerinde değiştirmek yerine her seferinde bütün olarak değiştirilirler,
    // böylece kilit gerekmeden tutarlı bir görüntü okunur.

    /** kanal kimliği -> zamana göre sıralı programlar */
    @Volatile
    private var byChannel: Map<String, List<EpgProgram>> = emptyMap()

    /** XMLTV kanal kimliği -> görünen ad (isim eşleştirmesi için) */
    @Volatile
    private var channelNames: Map<String, String> = emptyMap()

    /** normalize edilmiş ad -> XMLTV kanal kimliği */
    @Volatile
    private var nameIndex: Map<String, String> = emptyMap()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    val isEmpty: Boolean get() = byChannel.isEmpty()

    val programCount: Int get() = byChannel.values.sumOf { it.size }

    // ------------------------------------------------------------------ sorgular

    /**
     * Bir katalog öğesinin EPG kimliğini bulur. Panel epg_channel_id vermiyorsa
     * kanal adı normalize edilerek XMLTV kanal adlarıyla eşleştirilir.
     */
    fun resolveChannelId(item: StreamItem): String {
        val direct = item.epgChannelId
        if (direct.isNotBlank() && byChannel.containsKey(direct)) return direct
        val byName = nameIndex[normalize(item.name)]
        if (byName != null) return byName
        return direct
    }

    fun programs(channelId: String): List<EpgProgram> =
        if (channelId.isBlank()) emptyList() else byChannel[channelId].orEmpty()

    fun programsFor(item: StreamItem): List<EpgProgram> = programs(resolveChannelId(item))

    /** Şu an yayında olan ve ondan sonraki program. */
    fun nowNext(channelId: String, nowMs: Long = System.currentTimeMillis()):
        Pair<EpgProgram?, EpgProgram?> {
        val list = programs(channelId)
        if (list.isEmpty()) return null to null
        val idx = list.indexOfFirst { nowMs < it.stop }
        if (idx < 0) return null to null
        val candidate = list[idx]
        return if (candidate.start <= nowMs) {
            candidate to list.getOrNull(idx + 1)
        } else {
            null to candidate
        }
    }

    fun nowNextFor(item: StreamItem, nowMs: Long = System.currentTimeMillis()):
        Pair<EpgProgram?, EpgProgram?> = nowNext(resolveChannelId(item), nowMs)

    /** Rehber ızgarası için belirli bir zaman aralığındaki programlar. */
    fun inWindow(channelId: String, fromMs: Long, toMs: Long): List<EpgProgram> =
        programs(channelId).filter { it.stop > fromMs && it.start < toMs }

    // ------------------------------------------------------------------ yükleme

    suspend fun loadFromDisk() = mutex.withLock {
        if (byChannel.isNotEmpty()) return@withLock
        withContext(Dispatchers.IO) {
            val names = HashMap<String, String>()
            Tsv.read(nameFile(), NAME_VERSION, NAME_COLUMNS)?.rows?.forEach { r ->
                val id = Tsv.str(r, 0)
                val label = Tsv.str(r, 1)
                if (id.isNotBlank() && label.isNotBlank()) names[id] = label
            }

            val grouped = HashMap<String, ArrayList<EpgProgram>>()
            val data = Tsv.read(programFile(), VERSION, COLUMNS)
            if (data != null) {
                val cutoff = System.currentTimeMillis() - PAST_WINDOW_MS
                data.rows.forEach { r ->
                    val channel = Tsv.str(r, 0)
                    if (channel.isBlank()) return@forEach
                    val stop = Tsv.long(r, 2)
                    if (stop < cutoff) return@forEach
                    grouped.getOrPut(channel) { ArrayList() }.add(
                        EpgProgram(
                            channelId = channel,
                            start = Tsv.long(r, 1),
                            stop = stop,
                            title = Tsv.str(r, 3),
                            description = Tsv.str(r, 4)
                        )
                    )
                }
            }
            channelNames = names
            byChannel = grouped.mapValues { (_, v) -> v.sortedBy { it.start } }
            nameIndex = buildNameIndex(byChannel, names)
        }
        _revision.value = _revision.value + 1
    }

    /**
     * XMLTV kaynağını indirir. [wantedChannels] boş değilse yalnızca o kanallar
     * saklanır — 20 bin kanallı bir XMLTV dosyasında bellek farkı ciddi oluyor.
     */
    suspend fun refresh(wantedChannels: Set<String>, force: Boolean = false): Int {
        if (_refreshing.value) return 0
        if (!force && !isStale()) return programCount

        return mutex.withLock {
            _refreshing.value = true
            _progress.value = 0
            try {
                val now = System.currentTimeMillis()
                val from = now - PAST_WINDOW_MS
                val to = now + Settings.epgDays * 86_400_000L

                val collected = HashMap<String, ArrayList<EpgProgram>>()
                var total = 0
                val accept: (String) -> Boolean = { id ->
                    total < MAX_PROGRAMS && (wantedChannels.isEmpty() || wantedChannels.contains(id))
                }

                val result = XmltvParser.download(profile, accept, from, to) { program ->
                    if (total < MAX_PROGRAMS) {
                        collected.getOrPut(program.channelId) { ArrayList() }.add(program)
                        total++
                        if ((total and 0x7FF) == 0) _progress.value = total
                    }
                }

                if (total > 0) {
                    byChannel = collected.mapValues { (_, v) -> v.sortedBy { it.start } }
                    channelNames = result.channelNames
                    nameIndex = buildNameIndex(byChannel, channelNames)
                    persist()
                    Settings.setEpgUpdatedAt(profile.id, System.currentTimeMillis())
                    _revision.value = _revision.value + 1
                }
                _progress.value = total
                total
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * XMLTV yoksa ya da bu kanal orada bulunmuyorsa tek kanal için panelin
     * kendi uç noktasından akış çeker.
     */
    suspend fun fetchSingle(item: StreamItem): List<EpgProgram> {
        if (profile.type != SourceType.XTREAM) return emptyList()
        val existing = programsFor(item)
        if (existing.isNotEmpty()) return existing
        val fetched = runCatching { XtreamApi.fullEpg(profile, item.id) }
            .getOrElse { runCatching { XtreamApi.shortEpg(profile, item.id) }.getOrDefault(emptyList()) }
        if (fetched.isEmpty()) return emptyList()
        val key = item.epgChannelId.ifBlank { "stream:" + item.id }
        val normalized = fetched.map { it.copy(channelId = key) }
        mutex.withLock {
            byChannel = byChannel + (key to normalized)
            if (item.epgChannelId.isBlank()) {
                nameIndex = nameIndex + (normalize(item.name) to key)
            }
        }
        _revision.value = _revision.value + 1
        return normalized
    }

    fun isStale(): Boolean {
        val last = Settings.epgUpdatedAt(profile.id)
        if (last <= 0L) return true
        // Akış verisi hızlı eskir; altı saatte bir tazelemek yeterli.
        return System.currentTimeMillis() - last > 6 * 3_600_000L
    }

    fun lastUpdated(): Long = Settings.epgUpdatedAt(profile.id)

    suspend fun clear() = mutex.withLock {
        byChannel = emptyMap()
        channelNames = emptyMap()
        nameIndex = emptyMap()
        withContext(Dispatchers.IO) {
            runCatching { programFile().delete() }
            runCatching { nameFile().delete() }
        }
        Settings.setEpgUpdatedAt(profile.id, 0L)
        _revision.value = _revision.value + 1
    }

    // ------------------------------------------------------------------ iç işler

    private fun programFile() = Paths.file(profile.id, "epg.tsv")
    private fun nameFile() = Paths.file(profile.id, "epg_channels.tsv")

    private suspend fun persist() = withContext(Dispatchers.IO) {
        val rows = ArrayList<String>(programCount)
        byChannel.forEach { (channel, list) ->
            list.forEach { p ->
                rows.add(Tsv.row(channel, p.start, p.stop, p.title, p.description))
            }
        }
        Tsv.write(programFile(), VERSION, rows)
        Tsv.write(
            nameFile(), NAME_VERSION,
            channelNames.map { (id, label) -> Tsv.row(id, label) }
        )
    }

    private fun buildNameIndex(
        programs: Map<String, List<EpgProgram>>,
        names: Map<String, String>
    ): Map<String, String> {
        val index = HashMap<String, String>(names.size * 2)
        names.forEach { (id, label) ->
            if (programs.containsKey(id)) {
                val key = normalize(label)
                if (key.isNotEmpty() && !index.containsKey(key)) index[key] = id
            }
        }
        // XMLTV kanal listesi eksikse kimliğin kendisini de ad gibi dene.
        programs.keys.forEach { id ->
            val key = normalize(id)
            if (key.isNotEmpty() && !index.containsKey(key)) index[key] = id
        }
        return index
    }

    /**
     * "TRT 1 HD" ve "trt1" aynı kanalı gösterir; eşleştirme için ad
     * küçük harfe indirilir, kalite ekleri ve noktalama atılır.
     */
    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        var s = sb.toString()
        for (suffix in qualitySuffixes) {
            if (s.endsWith(suffix) && s.length > suffix.length + 1) {
                s = s.dropLast(suffix.length)
                break
            }
        }
        return s
    }

    private val qualitySuffixes = listOf("fhd", "uhd", "hd", "sd", "4k", "1080p", "720p", "hevc")
}
