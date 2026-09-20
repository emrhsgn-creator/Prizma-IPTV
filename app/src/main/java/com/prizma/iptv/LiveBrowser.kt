package com.prizma.iptv

import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PaneBg = Color(0xFF0C0F1D)
private val ListBg = Color(0xFF10142A)
private val CardBg = Color(0xFF141A2E)
private val TrackBg = Color(0xFF1E2440)

/**
 * Önizleme başlamadan önce beklenen süre.
 *
 * Bu gecikme yalnizca YENI akisi baslatmayi erteler. Onceden eski akisin
 * yikilmasi gecikmenin disindaydi: odak bir satir kaydigi anda player.stop()
 * calisiyor, calisan cozucu kapaniyor ve goruntu karariyordu. Amlogic S905
 * sinifinda tek bir donanim AVC hatti var; onu her odak adiminda kapatip
 * yeniden acmak cihazda olculen %58 jank'in ana kaynagi (boşta %5,6).
 *
 * Artik yikim da gecikmenin icinde: odak yerlesene kadar onceki onizleme
 * oynamaya devam eder, odak degisirse coroutine delay'de iptal olur ve
 * cozucuye hic dokunulmaz.
 */
private const val PREVIEW_DELAY_MS = 900L

/**
 * Onizlemede kabul edilen en yuksek cozunurluk.
 *
 * Onizleme kutusu ~300 piksel genisliginde; FHD bir akisi tam cozunurlukte
 * cozmenin gorsel karsiligi yok. Olcumde iki turda da %90+ jank veren dort
 * kanalin (ATV FHD, CNN TÜRK FHD, HABER GLOBAL FHD, ULUSAL KANAL HD) dordu
 * de yuksek cozunurluklu.
 *
 * Cok varyantli kaynakta (HLS) dusuk varyant secilir. Tek varyantli TS
 * akisinda media3 zaten tek izi secmek zorunda oldugu icin ayar sessizce
 * etkisiz kalir; exceedVideoConstraintsIfNecessary varsayilan olarak acik
 * oldugundan goruntu kaybolmaz.
 */
private const val PREVIEW_MAX_W = 1280
private const val PREVIEW_MAX_H = 720

/**
 * Onizleme baglantisi birakildiktan sonra tam ekran oynaticinin acilmasi
 * icin beklenen sure.
 *
 * stop() soketi oynatma is parcaciginda kapatir; cagrildigi anda is bitmis
 * olmuyor. Bu kisa bekleme olmadan tam ekran oynatici, onizlemenin soketi
 * hala acikken baglaniyor ve saglayici iki es zamanli baglanti goruyor.
 */
private const val PREVIEW_RELEASE_MS = 400L

/**
 * Onizlemede kalici hata veren kanallar.
 *
 * Xtream'de canli yayinda 403 genellikle "es zamanli baglanti sinirin doldu"
 * demektir ve olu kanallarda kalicidir. Olcumde TELE 1 HD ile beIN GURME HD
 * iki turda da 403 verdi. Odak her dustugunde yeniden baglanmak hesabin
 * baglanti hakkini saniyelerce tutuyor; bu, saglam kanallarin da 403
 * almasina yol acabilir. Surec boyunca hatirlayip bir daha denemiyoruz.
 */
private val deadPreviewIds: MutableSet<String> = java.util.Collections.newSetFromMap(
    java.util.concurrent.ConcurrentHashMap<String, Boolean>()
)

/**
 * Onizleme icin yukleme hatasi politikasi.
 *
 * Onizleme oynaticisi daha once hic politika vermiyordu, yani media3
 * varsayilani gecerliydi: 6 deneme, 5 saniyeye kadar artan bekleme. Olu bir
 * kanalda bu, ~20 saniye boyunca hesabin baglanti hakkini tutmak demek.
 * Kalici hatalarda (403/401/404) hic denemiyoruz.
 */
@OptIn(UnstableApi::class)
private fun previewErrorPolicy(): LoadErrorHandlingPolicy =
    object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(
            loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
        ): Long {
            val code = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)
                ?.responseCode
            if (code == 403 || code == 401 || code == 404) return C.TIME_UNSET
            return super.getRetryDelayMsFor(loadErrorInfo)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 2
    }

/**
 * Üç panelli Canlı TV gezintisi: solda kategoriler, ortada kanal listesi,
 * sağda seçili kanalın canlı önizlemesi ve yayın akışı.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LiveBrowser(
    host: String,
    user: String,
    pass: String,
    categories: List<Category>,
    totalCount: Int,
    favCount: Int,
    histCount: Int,
    selectedCat: String,
    onSelectCat: (String) -> Unit,
    channels: List<Tile>,
    favIds: Set<String>,
    onToggleFav: (Tile) -> Unit,
    showMove: Boolean,
    onMove: (Tile, Int) -> Unit,
    account: Account,
    autoFocus: Boolean
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(channels) { index = 0 }

    val current = channels.getOrNull(index)
    val preview = rememberPreviewPlayer()

    // Xtream hesaplarının çoğunda aynı anda tek bağlantıya izin var. Tam ekran
    // oynatıcı açılmadan önce önizlemenin bağlantısını bırakması şart; yoksa
    // sunucu ikinci isteği reddediyor ve hiçbir kanal açılmıyor.
    //
    // stop() soketi oynatma is parcaciginda kapatir, yani cagrildigi anda
    // is bitmis olmuyor. Activity'yi ayni karede baslatinca tam ekran
    // oynatici, onizlemenin soketi hala acikken baglaniyordu. Cihazda
    // olculen desen buydu: tam ekrana girerken saglayiciya ayni anda IKI
    // baglanti (ikisi de :25869), ~6 saniye sonra biri CLOSE_WAIT'e
    // dusuyor. CLOSE_WAIT sunucunun kapattigi demektir, yani saglayici iki
    // baglanti gorup birini kesiyor.
    //
    // Once birakip sonra aciyoruz. Gecikme kisa; kanal acilisi zaten
    // saniyeler suruyor, ama sunucunun iki baglanti gormesi butun yayini
    // bozabiliyor.
    val openChannel: (Tile) -> Unit = { t ->
        scope.launch {
            preview.stop()
            preview.clearMediaItems()
            delay(PREVIEW_RELEASE_MS)
            playLiveList(ctx, host, user, pass, channels, t)
        }
    }

    Row(Modifier.fillMaxSize()) {
        CategoryPane(
            categories = categories,
            totalCount = totalCount,
            favCount = favCount,
            histCount = histCount,
            selectedCat = selectedCat,
            onSelectCat = onSelectCat,
            account = account
        )
        ChannelPane(
            channels = channels,
            favIds = favIds,
            index = index,
            autoFocus = autoFocus,
            onSelect = { index = it },
            onOpen = openChannel,
            onToggleFav = onToggleFav,
            showMove = showMove,
            onMove = onMove
        )
        PreviewPane(
            host = host,
            user = user,
            pass = pass,
            channel = current,
            player = preview,
            onOpen = openChannel
        )
    }
}

@Composable
private fun CategoryPane(
    categories: List<Category>,
    totalCount: Int,
    favCount: Int,
    histCount: Int,
    selectedCat: String,
    onSelectCat: (String) -> Unit,
    account: Account
) {
    Column(
        Modifier
            .width(212.dp)
            .fillMaxHeight()
            .background(PaneBg)
    ) {
        Text(
            "Kategoriler",
            color = PrizmaAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            item {
                SideRow("FAVORİLER", favCount, selectedCat == FAV_CAT) { onSelectCat(FAV_CAT) }
            }
            item {
                SideRow("SON İZLENENLER", histCount, selectedCat == HIST_CAT) {
                    onSelectCat(HIST_CAT)
                }
            }
            item {
                SideRow("TÜMÜ", totalCount, selectedCat.isEmpty()) { onSelectCat("") }
            }
            items(categories) { c ->
                SideRow(c.name, c.count, selectedCat == c.id) { onSelectCat(c.id) }
            }
        }
        Text(
            "${account.username} · ${account.expiry}",
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun ChannelPane(
    channels: List<Tile>,
    favIds: Set<String>,
    index: Int,
    autoFocus: Boolean,
    onSelect: (Int) -> Unit,
    onOpen: (Tile) -> Unit,
    onToggleFav: (Tile) -> Unit,
    showMove: Boolean,
    onMove: (Tile, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val firstRow = remember { FocusRequester() }
    var focusDone by remember { mutableStateOf(false) }

    LaunchedEffect(channels) {
        listState.scrollToItem(0)
        if (autoFocus && !focusDone && channels.isNotEmpty()) {
            focusDone = true
            runCatching { firstRow.requestFocus() }
        }
    }

    Column(
        Modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(ListBg)
    ) {
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Kanal yok", color = Muted, fontSize = 12.sp)
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            itemsIndexed(
                channels,
                // Anahtar indeksi de iceriyor: favoriler/gecmis birlesiminde
                // ayni id iki kez gorunebiliyor ve Compose yinelenen anahtarda
                // cokuyor. Bu yuzden sade t.id'ye indirgenmedi.
                key = { i, t -> "${t.id}#$i" },
                // Butun satirlar ayni yapida. contentType verilince Compose
                // kaydirirken kompozisyonlari yeniden kullanabiliyor; 2151
                // kanallik listede fark belirgin.
                contentType = { _, _ -> "channel" }
            ) { i, t ->
                ChannelRow(
                    tile = t,
                    selected = i == index,
                    fav = t.id in favIds,
                    modifier = if (i == 0) Modifier.focusRequester(firstRow) else Modifier,
                    onFocused = { onSelect(i) },
                    onClick = { if (i == index) onOpen(t) else onSelect(i) },
                    onFav = { onToggleFav(t) },
                    showMove = showMove,
                    onMoveUp = { onMove(t, -1) },
                    onMoveDown = { onMove(t, 1) }
                )
            }
        }
    }
}

// androidx.annotation.OptIn dosyada media3 için import edili; Compose'un
// deneysel işareti Kotlin'inkini gerektirdiğinden burada niteliyoruz.
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    tile: Tile,
    selected: Boolean,
    fav: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onFav: () -> Unit,
    showMove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = modifier
                .weight(1f)
                .clip(shape)
                .background(
                    if (selected) PrizmaAccent.copy(alpha = 0.20f) else Color.Transparent
                )
                .border(
                    width = if (selected) 1.5.dp else 0.dp,
                    color = if (selected) PrizmaAccent else Color.Transparent,
                    shape = shape
                )
                .onFocusChanged { if (it.isFocused) onFocused() }
                .combinedClickable(onClick = onClick, onLongClick = onFav)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(tile, 40.dp, 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                tile.name,
                color = if (selected) Color.White else Color(0xFFC3C8D4),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Favoriler listesinde elle sıralama. Dikey bir liste olduğu için
        // yukarı/aşağı; poster ızgarasındaki sol/sağ karşılığı.
        if (showMove) {
            MoveButton("▲", onMoveUp)
            MoveButton("▼", onMoveDown)
        }
        // Kalp, satırın çocuğu değil kardeşi: kumandada sağ ok ile üstüne
        // gidilebiliyor. İçine gömülü olduğunda odak hiç ona geçmiyordu.
        Box(
            modifier = Modifier
                .tvFocus(RoundedCornerShape(15.dp), 1.0f)
                .clip(RoundedCornerShape(15.dp))
                .clickable(onClick = onFav)
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (fav) "Favorilerden çıkar" else "Favorilere ekle",
                tint = if (fav) PrizmaAccent else Color(0xFF6B7286),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MoveButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = PrizmaAccent,
        fontSize = 13.sp,
        modifier = Modifier
            .tvFocus(RoundedCornerShape(6.dp), 1.0f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun ChannelLogo(tile: Tile, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(w, h)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1B2038)),
        contentAlignment = Alignment.Center
    ) {
        if (tile.icon.isNotBlank()) {
            AsyncImage(
                model = tile.icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
        } else {
            Text(
                tile.name.take(2).uppercase(),
                color = Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Önizleme oynatıcısını kurar ve ekrandan çıkılırken serbest bırakır. */
@OptIn(UnstableApi::class)
@Composable
private fun rememberPreviewPlayer(): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("PrizmaIPTV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)

        ExoPlayer.Builder(ctx)
            // Önizlemede de aynı çözücü havuzu kullanılsın; yoksa aynı kanal
            // önizlemede sessiz, tam ekranda sesli olurdu.
            .setRenderersFactory(
                DefaultRenderersFactory(ctx).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                )
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2000, 8000, 800, 1500)
                    .build()
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(http, extractors)
                    .setLoadErrorHandlingPolicy(previewErrorPolicy())
            )
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(PREVIEW_MAX_W, PREVIEW_MAX_H)
                    .build()
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPane(
    host: String,
    user: String,
    pass: String,
    channel: Tile?,
    player: ExoPlayer,
    onOpen: (Tile) -> Unit
) {
    val ctx = LocalContext.current

    var buffering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // Oynaticiya gercekten yuklenmis olan kanal. Odak degisince etiket hemen
    // guncellenir, ama goruntu debounce suresince onceki kanala aittir; bu
    // farki kullaniciya gosterebilmek icin ayri tutuluyor.
    val loadedId = remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) failed = false
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                failed = true
                // Kalici reddi hatirla; bu kanalda bir daha onizleme
                // baglantisi acip hesabin baglanti hakkini tutmayalim.
                val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)
                    ?.responseCode
                if (code == 403 || code == 401 || code == 404) {
                    loadedId.value?.let { deadPreviewIds.add(it) }
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    val owner = remember(ctx) {
        var c: android.content.Context? = ctx
        while (c != null && c !is LifecycleOwner) {
            c = (c as? android.content.ContextWrapper)?.baseContext
        }
        c as? LifecycleOwner
    }
    var resumeTick by remember { mutableIntStateOf(0) }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                // Yalnızca duraklatmak yetmez: ExoPlayer kaynağı açık tutar ve
                // hesabın tek bağlantı hakkı önizlemede kilitli kalır.
                Lifecycle.Event.ON_PAUSE -> {
                    player.stop()
                    player.clearMediaItems()
                }
                Lifecycle.Event.ON_RESUME -> resumeTick++
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    var epg by remember { mutableStateOf<List<EpgItem>?>(null) }

    LaunchedEffect(channel?.id, resumeTick) {
        val c = channel
        // Aninda yalnizca ucuz durum sifirlamasi. Oynaticiya burada
        // dokunmuyoruz: kaydirirken onceki onizleme oynamaya devam etsin.
        epg = null
        failed = false

        if (c == null) {
            player.stop()
            player.clearMediaItems()
            loadedId.value = null
            return@LaunchedEffect
        }

        if (c.id in deadPreviewIds) {
            // Daha once kalici hata vermisti; baglanti acmadan dogrudan
            // bildiriyoruz. Yayin akisi yine de gosterilebilir.
            player.stop()
            player.clearMediaItems()
            loadedId.value = null
            failed = true
            epg = runCatching { XtreamApi.shortEpg(host, user, pass, c.id) }
                .getOrDefault(emptyList())
            return@LaunchedEffect
        }

        // Odak yerlesene kadar bekle. Bu noktaya kadar cozucu hala onceki
        // kanali oynatiyor; odak degisirse coroutine tam burada iptal olur ve
        // cozucu hic yikilmaz.
        delay(PREVIEW_DELAY_MS)

        val ext = if (c.ext.isNotEmpty()) c.ext else "ts"
        player.stop()
        player.clearMediaItems()
        loadedId.value = c.id
        player.setMediaItem(MediaItem.fromUri("$host/live/$user/$pass/${c.id}.$ext"))
        player.playWhenReady = true
        player.prepare()
        epg = runCatching { XtreamApi.shortEpg(host, user, pass, c.id) }.getOrDefault(emptyList())
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .clickable(enabled = channel != null) { channel?.let(onOpen) },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).also { v ->
                        v.useController = false
                        v.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        v.setShutterBackgroundColor(android.graphics.Color.BLACK)
                        v.player = player
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            // Odak yeni kanalda ama goruntu henuz onceki kanala ait.
            val settling = channel != null && channel.id != loadedId.value
            when {
                failed -> Text(
                    if (channel != null && channel.id in deadPreviewIds) {
                        "Bu kanal yayında değil"
                    } else {
                        "Önizleme açılamadı"
                    },
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp
                )
                // Material3'un CircularProgressIndicator'u sonsuz animasyondur:
                // ekranda oldugu her karede yeniden cizim tetikler. Cihazda
                // hicbir sey degismezken 30 saniyede 1595 kare (~53 fps) cizim
                // olculdu, kaynagi buydu. Statik yazi ayni bilgiyi veriyor.
                settling || buffering -> Text("Yükleniyor…", color = Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (channel == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Kanal seçin", color = Muted, fontSize = 12.sp)
            }
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
                .clickable { onOpen(channel) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(channel, 46.dp, 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                channel.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
        ) {
            EpgList(epg)
        }
    }
}

@Composable
private fun EpgList(epg: List<EpgItem>?) {
    val now = System.currentTimeMillis() / 1000
    when {
        // Sonsuz animasyonlu gosterge yerine statik yazi: bu panel odak her
        // degistiginde yeniden yukleniyor, yani gosterge neredeyse surekli
        // ekranda kaliyor ve bos cizimi tek basina ayakta tutuyordu.
        epg == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Yayın akışı yükleniyor…", color = Muted, fontSize = 12.sp)
        }

        epg.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Yayın akışı yok", color = Muted, fontSize = 12.sp)
        }

        else -> LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(epg) { e ->
                val live = now in e.start..e.stop
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (live) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFF4D4D))
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            e.title.ifBlank { "Program" },
                            color = if (live) Color.White else Color(0xFFC3C8D4),
                            fontSize = 13.sp,
                            fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${epgClock(e.start)} - ${epgClock(e.stop)}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                    if (live) {
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TrackBg)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(epgProgress(now, e.start, e.stop))
                                    .height(3.dp)
                                    .background(PrizmaAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}
