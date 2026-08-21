package com.prizma.iptv.data.repo

import com.prizma.iptv.data.local.Paths
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.local.Tsv
import com.prizma.iptv.data.model.Category
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.SourceType
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.remote.M3uParser
import com.prizma.iptv.data.remote.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Katalog erişiminin tek kapısı.
 *
 * Akış şu sırayla ilerler: bellek → disk → ağ. Diskte bir kopya varsa
 * bayat olsa bile hemen gösterilir, tazeleme arka planda yapılır; böylece
 * on binlerce kanallı paneller bile anında açılır.
 */
class CatalogRepository(private val profile: Profile) {

    data class SectionData(
        val categories: List<Category>,
        val items: List<StreamItem>,
        val savedAt: Long
    ) {
        fun isStale(ttlHours: Int): Boolean =
            System.currentTimeMillis() - savedAt > ttlHours * 3_600_000L
    }

    private companion object {
        const val ITEM_VERSION = 2
        const val ITEM_COLUMNS = 11
        const val CAT_VERSION = 2
        const val CAT_COLUMNS = 4
    }

    private val mutex = Mutex()

    private val _sections = MutableStateFlow<Map<Section, SectionData>>(emptyMap())
    val sections: StateFlow<Map<Section, SectionData>> = _sections

    private val _loading = MutableStateFlow<Set<Section>>(emptySet())
    val loading: StateFlow<Set<Section>> = _loading

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error

    fun get(section: Section): SectionData? = _sections.value[section]

    fun allItems(): List<StreamItem> = _sections.value.values.flatMap { it.items }

    // ------------------------------------------------------------------ yükleme

    /**
     * Bölümü kullanılabilir hâle getirir. [force] verilirse disk atlanır ve
     * doğrudan ağdan çekilir.
     */
    suspend fun ensure(section: Section, force: Boolean = false) {
        if (!force && _sections.value.containsKey(section)) {
            val current = _sections.value.getValue(section)
            if (!current.isStale(Settings.catalogTtlHours)) return
        }

        if (!force) {
            val fromDisk = readDisk(section)
            if (fromDisk != null) {
                publish(section, fromDisk)
                if (!fromDisk.isStale(Settings.catalogTtlHours)) return
            }
        }

        fetch(section, force)
    }

    private suspend fun fetch(section: Section, force: Boolean) {
        mutex.withLock {
            // Beklerken başka bir çağrı tazelemiş olabilir.
            val current = _sections.value[section]
            if (!force && current != null && !current.isStale(Settings.catalogTtlHours)) return

            _loading.value = _loading.value + section
            try {
                when (profile.type) {
                    SourceType.XTREAM -> fetchXtream(section)
                    SourceType.M3U -> fetchM3u()
                }
                _error.value = null
            } catch (e: Exception) {
                // Diskte eski bir kopya varsa kullanıcıyı boş ekranla baş başa bırakma;
                // elde hiçbir şey yoksa hatayı yukarı taşı.
                if (_sections.value[section] == null) {
                    _error.value = e
                    throw e
                }
            } finally {
                _loading.value = _loading.value - section
            }
        }
    }

    private suspend fun fetchXtream(section: Section) {
        val categories = XtreamApi.categories(profile, section)
        val items = XtreamApi.streams(profile, section)
        publishAndPersist(section, categories, items)
    }

    private var lastM3uFetch = 0L

    /**
     * M3U tek indirmede üç bölümü birden doldurur. Üç bölüm için art arda
     * çağrıldığında listeyi tekrar tekrar indirmemek adına kısa süreli
     * bir koruma var.
     */
    private suspend fun fetchM3u() {
        val now = System.currentTimeMillis()
        if (now - lastM3uFetch < 30_000L && _sections.value.isNotEmpty()) return
        val all = M3uParser.load(profile)
        lastM3uFetch = System.currentTimeMillis()
        all.forEach { (section, pair) ->
            publishAndPersist(section, pair.first, pair.second)
        }
    }

    private suspend fun publishAndPersist(
        section: Section,
        categories: List<Category>,
        items: List<StreamItem>
    ) {
        val counts = items.groupingBy { it.categoryId }.eachCount()
        val withCounts = categories
            .map { it.copy(count = counts[it.id] ?: it.count) }
            .filter { it.count > 0 || items.isEmpty() }
        val data = SectionData(withCounts, items, System.currentTimeMillis())
        publish(section, data)
        writeDisk(section, data)
    }

    private fun publish(section: Section, data: SectionData) {
        _sections.value = _sections.value + (section to data)
    }

    // ------------------------------------------------------------------ disk

    private fun itemFile(section: Section) =
        Paths.file(profile.id, "catalog_" + section.name.lowercase() + ".tsv")

    private fun catFile(section: Section) =
        Paths.file(profile.id, "cats_" + section.name.lowercase() + ".tsv")

    private suspend fun readDisk(section: Section): SectionData? = withContext(Dispatchers.IO) {
        val itemData = Tsv.read(itemFile(section), ITEM_VERSION, ITEM_COLUMNS)
            ?: return@withContext null
        val catData = Tsv.read(catFile(section), CAT_VERSION, CAT_COLUMNS)

        val items = itemData.rows.mapNotNull { r ->
            val id = Tsv.str(r, 0)
            if (id.isBlank()) return@mapNotNull null
            StreamItem(
                id = id,
                name = Tsv.str(r, 1),
                icon = Tsv.str(r, 2),
                extension = Tsv.str(r, 3),
                categoryId = Tsv.str(r, 4),
                rating = Tsv.dbl(r, 5),
                added = Tsv.long(r, 6),
                number = Tsv.int(r, 7),
                epgChannelId = Tsv.str(r, 8),
                archiveDays = Tsv.int(r, 9),
                url = Tsv.str(r, 10)
            )
        }
        if (items.isEmpty()) return@withContext null

        val categories = catData?.rows?.mapNotNull { r ->
            val name = Tsv.str(r, 1)
            if (name.isBlank()) return@mapNotNull null
            Category(
                id = Tsv.str(r, 0),
                name = name,
                count = Tsv.int(r, 2),
                adult = Tsv.bool(r, 3)
            )
        }.orEmpty()

        SectionData(categories, items, itemData.savedAt)
    }

    private suspend fun writeDisk(section: Section, data: SectionData) =
        withContext(Dispatchers.IO) {
            Tsv.write(
                itemFile(section), ITEM_VERSION,
                data.items.map {
                    Tsv.row(
                        it.id, it.name, it.icon, it.extension, it.categoryId,
                        it.rating, it.added, it.number, it.epgChannelId,
                        it.archiveDays, it.url
                    )
                }
            )
            Tsv.write(
                catFile(section), CAT_VERSION,
                data.categories.map {
                    Tsv.row(it.id, it.name, it.count, if (it.adult) "1" else "0")
                }
            )
        }

    // ------------------------------------------------------------------ bakım

    /** Belleği ve diski boşaltır; sonraki [ensure] çağrısı ağdan çeker. */
    suspend fun invalidate() = withContext(Dispatchers.IO) {
        _sections.value = emptyMap()
        Section.entries.forEach {
            runCatching { itemFile(it).delete() }
            runCatching { catFile(it).delete() }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
