package com.prizma.iptv.data.local

import com.prizma.iptv.data.model.SavedItem
import com.prizma.iptv.data.model.WatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val userDataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Profile bağlı favori listesi. Sıralama elle değiştirilebildiği için
 * liste sırası anlamlıdır ve olduğu gibi saklanır.
 */
class FavoritesStore(private val profileId: String) {

    private companion object {
        const val VERSION = 1
        const val COLUMNS = 8
        const val NAME = "favorites.tsv"
    }

    private val file get() = Paths.file(profileId, NAME)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedItem>> = _items

    private fun load(): List<SavedItem> {
        val data = Tsv.read(file, VERSION, COLUMNS) ?: return emptyList()
        return data.rows.mapNotNull { r ->
            val id = Tsv.str(r, 1)
            if (id.isBlank()) return@mapNotNull null
            SavedItem(
                section = Tsv.str(r, 0),
                id = id,
                name = Tsv.str(r, 2),
                icon = Tsv.str(r, 3),
                extension = Tsv.str(r, 4),
                rating = Tsv.dbl(r, 5),
                number = Tsv.int(r, 6),
                savedAt = Tsv.long(r, 7)
            )
        }
    }

    private fun persist(list: List<SavedItem>) {
        _items.value = list
        val snapshot = list.map {
            Tsv.row(
                it.section, it.id, it.name, it.icon, it.extension,
                it.rating, it.number, it.savedAt
            )
        }
        userDataScope.launch { Tsv.write(file, VERSION, snapshot) }
    }

    fun ofSection(section: String): List<SavedItem> = _items.value.filter { it.section == section }

    fun isFavorite(section: String, id: String): Boolean =
        _items.value.any { it.section == section && it.id == id }

    /** Ekledi ise true, çıkardı ise false döner. */
    fun toggle(item: SavedItem): Boolean {
        val list = _items.value.toMutableList()
        val idx = list.indexOfFirst { it.section == item.section && it.id == item.id }
        return if (idx >= 0) {
            list.removeAt(idx)
            persist(list)
            false
        } else {
            list.add(item.copy(savedAt = System.currentTimeMillis()))
            persist(list)
            true
        }
    }

    /** Aynı bölümdeki komşu favori ile yer değiştirir. */
    fun move(section: String, id: String, delta: Int) {
        val all = _items.value.toMutableList()
        val indices = all.indices.filter { all[it].section == section }
        val pos = indices.indexOfFirst { all[it].id == id }
        if (pos < 0) return
        val target = pos + delta
        if (target !in indices.indices) return
        val a = indices[pos]
        val b = indices[target]
        val tmp = all[a]
        all[a] = all[b]
        all[b] = tmp
        persist(all)
    }

    fun clear() = persist(emptyList())
}

/**
 * İzleme geçmişi ve "kaldığın yerden devam" konumları.
 * Canlı kanallar da kaydedilir (süre 0 ile) — son kanal özelliği buradan beslenir.
 */
class HistoryStore(private val profileId: String) {

    private companion object {
        const val VERSION = 1
        const val COLUMNS = 9
        const val NAME = "history.tsv"
        const val LIMIT = 400
        const val MIN_RESUME_MS = 20_000L
    }

    private val file get() = Paths.file(profileId, NAME)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<WatchState>> = _items

    private fun load(): List<WatchState> {
        val data = Tsv.read(file, VERSION, COLUMNS) ?: return emptyList()
        return data.rows.mapNotNull { r ->
            val id = Tsv.str(r, 1)
            if (id.isBlank()) return@mapNotNull null
            WatchState(
                section = Tsv.str(r, 0),
                id = id,
                name = Tsv.str(r, 2),
                icon = Tsv.str(r, 3),
                extension = Tsv.str(r, 4),
                position = Tsv.long(r, 5),
                duration = Tsv.long(r, 6),
                lastSeen = Tsv.long(r, 7),
                parentId = Tsv.str(r, 8)
            )
        }.sortedByDescending { it.lastSeen }
    }

    private fun persist(list: List<WatchState>) {
        val trimmed = list.sortedByDescending { it.lastSeen }.take(LIMIT)
        _items.value = trimmed
        val snapshot = trimmed.map {
            Tsv.row(
                it.section, it.id, it.name, it.icon, it.extension,
                it.position, it.duration, it.lastSeen, it.parentId
            )
        }
        userDataScope.launch { Tsv.write(file, VERSION, snapshot) }
    }

    fun record(state: WatchState) {
        if (state.id.isBlank()) return
        val list = _items.value.toMutableList()
        val idx = list.indexOfFirst { it.section == state.section && it.id == state.id }
        val entry = state.copy(lastSeen = System.currentTimeMillis())
        if (idx >= 0) list[idx] = entry else list.add(entry)
        persist(list)
    }

    fun find(section: String, id: String): WatchState? =
        _items.value.firstOrNull { it.section == section && it.id == id }

    /** Devam edilecek konum; baş tarafta ya da sona çok yakınsa 0 döner. */
    fun resumePosition(section: String, id: String): Long {
        if (!Settings.resumeEnabled) return 0L
        val w = find(section, id) ?: return 0L
        if (w.duration <= 0L) return 0L
        if (w.position < MIN_RESUME_MS) return 0L
        if (w.finished) return 0L
        return w.position
    }

    fun markWatched(state: WatchState) {
        val duration = if (state.duration > 0L) state.duration else 1L
        record(state.copy(position = duration, duration = duration))
    }

    fun markUnwatched(section: String, id: String) {
        val existing = find(section, id) ?: return
        record(existing.copy(position = 0L))
    }

    fun remove(section: String, id: String) =
        persist(_items.value.filterNot { it.section == section && it.id == id })

    fun clear() = persist(emptyList())

    /** Ana sayfadaki "Devam Et" rafı. */
    fun continueWatching(): List<WatchState> = _items.value.filter {
        it.duration > 0L && it.position > MIN_RESUME_MS && !it.finished
    }
}
