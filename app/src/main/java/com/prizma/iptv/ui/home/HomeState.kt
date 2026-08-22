package com.prizma.iptv.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prizma.iptv.R
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.Category
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.StreamItem
import com.prizma.iptv.data.repo.CatalogRepository
import com.prizma.iptv.data.repo.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sol menüdeki / alt çubuktaki sekmeler. */
enum class Route(@StringRes val titleRes: Int, val glyph: String) {
    HOME(R.string.nav_home, "⌂"),
    LIVE(R.string.nav_live, "📡"),
    MOVIES(R.string.nav_movies, "🎬"),
    SERIES(R.string.nav_series, "📺"),
    GUIDE(R.string.nav_guide, "🗓"),
    SEARCH(R.string.nav_search, "🔍"),
    RECENT(R.string.nav_recent, "🕘"),
    SETTINGS(R.string.nav_settings, "⚙");

    val section: Section?
        get() = when (this) {
            LIVE -> Section.LIVE
            MOVIES -> Section.VOD
            SERIES -> Section.SERIES
            else -> null
        }
}

enum class SortMode(@StringRes val labelRes: Int) {
    DEFAULT(R.string.sort_default),
    NAME(R.string.sort_name),
    NAME_DESC(R.string.sort_name_desc),
    RATING(R.string.sort_rating),
    ADDED(R.string.sort_added),
    NUMBER(R.string.sort_number)
}

/** Etkinlikten ana ekrana kumanda tuslarini tasiyan kopru. */
object HomeBus {
    @Volatile
    var onKey: ((Int) -> Boolean)? = null
}

const val CATEGORY_ALL = ""
const val CATEGORY_FAVORITES = "__FAV__"
const val CATEGORY_RECENT = "__NEW__"

/**
 * Ana ekranın gezinme ve filtre durumu. Oturuma bağlıdır: profil değişince
 * yeniden oluşturulur, böylece seçili kategori önceki hesaptan sızmaz.
 */
class HomeState(
    val session: Session,
    private val scope: CoroutineScope
) {
    var route by mutableStateOf(Route.HOME)
        private set

    /** Bölüm başına seçili kategori hatırlanır (Compose tarafından izlenir). */
    private val categoryBySection = mutableStateMapOf<Section, String>()

    var sortMode by mutableStateOf(SortMode.DEFAULT)
    var query by mutableStateOf("")
    var searchActive by mutableStateOf(false)

    /** PIN ile bu oturumda açılmış kategoriler. */
    var unlockedCategories by mutableStateOf(emptySet<String>())
        private set

    var pendingPinCategory by mutableStateOf<Category?>(null)

    var contextItem by mutableStateOf<Pair<Section, StreamItem>?>(null)

    /**
     * Kumandada o an odakta olan kutucuk. MENU tusuna basildiginda bunun
     * baglam menusu acilir; uzun basma kumandalarda guvenilir calismiyor.
     */
    var focusedItem by mutableStateOf<Pair<Section, StreamItem>?>(null)

    var epgDialogItem by mutableStateOf<StreamItem?>(null)

    fun selectedCategory(section: Section): String =
        categoryBySection[section] ?: CATEGORY_ALL

    fun selectCategory(section: Section, categoryId: String) {
        categoryBySection[section] = categoryId
    }

    fun navigate(target: Route) {
        if (route == target) return
        route = target
        query = ""
        searchActive = target == Route.SEARCH
        target.section?.let { ensure(it) }
        if (target == Route.GUIDE) ensure(Section.LIVE)
    }

    /** Geri tuşu davranışı; true dönerse olay tüketilmiştir. */
    fun handleBack(): Boolean {
        val section = route.section
        return when {
            searchActive && route != Route.SEARCH -> {
                searchActive = false
                query = ""
                true
            }
            section != null && selectedCategory(section) != CATEGORY_ALL -> {
                selectCategory(section, CATEGORY_ALL)
                true
            }
            route != Route.HOME -> {
                navigate(Route.HOME)
                true
            }
            else -> false
        }
    }

    fun ensure(section: Section, force: Boolean = false) {
        scope.launch {
            runCatching { session.catalog.ensure(section, force) }
        }
    }

    /** Ana sayfa rafları için üç bölümü de arka planda hazırlar. */
    fun warmUp() {
        scope.launch {
            runCatching { session.catalog.ensure(Section.LIVE) }
            runCatching { session.catalog.ensure(Section.VOD) }
            runCatching { session.catalog.ensure(Section.SERIES) }
            loadEpg()
        }
    }

    fun refreshAll() {
        scope.launch {
            Section.entries.forEach { runCatching { session.catalog.ensure(it, force = true) } }
            loadEpg(force = true)
        }
    }

    private suspend fun loadEpg(force: Boolean = false) {
        runCatching { session.epg.loadFromDisk() }
        if (!Settings.epgAutoRefresh && !force) return

        val liveItems = session.catalog.get(Section.LIVE)?.items.orEmpty()
        val ids = liveItems
            .mapNotNull { item -> item.epgChannelId.takeIf { it.isNotBlank() } }
            .toSet()

        // Panel çoğu kanal için epg_channel_id vermiyorsa süzme yapılamaz:
        // ad eşleştirmesi XMLTV içindeki tüm kanallara ihtiyaç duyar.
        val filter = if (ids.isNotEmpty() && ids.size * 2 >= liveItems.size) ids else emptySet()

        runCatching { session.epg.refresh(filter, force) }
    }

    fun refreshEpg() {
        scope.launch { loadEpg(force = true) }
    }

    // ------------------------------------------------------------ ebeveyn kilidi

    fun isUnlocked(category: Category): Boolean =
        !session.needsPin(category) || unlockedCategories.contains(category.id)

    fun requestCategory(section: Section, category: Category) {
        if (isUnlocked(category)) {
            selectCategory(section, category.id)
        } else {
            pendingPinCategory = category
        }
    }

    fun unlockPending() {
        val category = pendingPinCategory ?: return
        unlockedCategories = unlockedCategories + category.id
        pendingPinCategory = null
        Section.entries.forEach { section ->
            val exists = session.catalog.get(section)?.categories?.any { it.id == category.id }
            if (exists == true) selectCategory(section, category.id)
        }
    }

    fun dismissPin() {
        pendingPinCategory = null
    }

    // Aşağıdaki iki yardımcı, veriyi parametre olarak alır: katalog akışı
    // ekran tarafında toplanır, böylece Compose değişiklikleri görebilir.

    /** Yetişkin kategorilerin gizlenmesi ayarı uygulanmış kategori listesi. */
    fun visibleCategories(data: CatalogRepository.SectionData?): List<Category> =
        data?.categories.orEmpty().filterNot { session.isHidden(it) }

    /** Gizlenmiş kategorilerin içerikleri de listelerden çıkarılır. */
    fun visibleItems(data: CatalogRepository.SectionData?): List<StreamItem> {
        if (data == null) return emptyList()
        if (!Settings.hideAdult) return data.items
        val hidden = data.categories.filter { it.adult }.map { it.id }.toSet()
        if (hidden.isEmpty()) return data.items
        return data.items.filterNot { hidden.contains(it.categoryId) }
    }
}
