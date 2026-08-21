# Prizma IPTV

Android telefon, tablet ve **Android TV / Fire TV** için Xtream Codes ve M3U
uyumlu IPTV oynatıcı. Tek APK hem dokunmatik hem kumanda ile çalışır; arayüz
cihazı algılayıp kendini ona göre kurar.

---

## Öne çıkanlar

### Oynatma
- **FFmpeg yazılım kod çözücüleri** — AC3 / E-AC3 / DTS / TrueHD sesli kanallarda
  "görüntü var, ses yok" sorunu ortadan kalkar. Donanım önce denenir, yetmezse
  yazılıma düşülür; ayarlardan zorlanabilir.
- **Otomatik yeniden bağlanma** — canlı yayın koptuğunda artan bekleme ile
  yeniden bağlanır, kanal listesi bozulmaz.
- **Format yedeği** — panel `.ts` vermiyorsa aynı kanal `.m3u8` olarak denenir
  (yalnızca ilgili öğe değiştirilir, sıra korunur).
- HLS, DASH, RTSP, MPEG-TS, progressive MP4/MKV.
- Arka planda ses + bildirim kontrolleri + kulaklık/Bluetooth tuşları
  (MediaSession).
- **Küçük ekran (PiP)**, Android 12+ üzerinde otomatik geçiş.
- Ses / altyazı / görüntü kalitesi seçimi, harici altyazı dosyası yükleme
  (SRT / ASS / VTT / TTML), altyazı boyutu ve arka planı.
- Oynatma hızı, en-boy oranı (Sığdır / Kırp / Ger / 16:9 / 4:3), kademesiz
  yakınlaştırma.
- Uyku zamanlayıcı, teknik bilgi katmanı (çözünürlük, kodek, bit hızı, tampon,
  atlanan kare).
- Ayarlanabilir tampon (10 / 30 / 60 / 120 sn) ve tünelli oynatma seçeneği.

### Yayın akışı (EPG)
- Tüm kanalların akışı **tek XMLTV indirmesiyle** alınır — kanal başına istek
  atılmaz, binlerce kanallı panellerde de çalışır.
- Klasik **rehber ızgarası**: solda kanal sütunu, sağda zaman ekseni, gün seçimi,
  "şimdiye git".
- Kanal kutucuklarında ve kanal listesinde "şu an yayında" bilgisi.
- Panel `epg_channel_id` vermiyorsa kanal adları normalize edilerek eşleştirilir.
- XMLTV yoksa tek kanal için panelin kendi uç noktasına düşülür.
- **Catch-up / timeshift**: arşivi olan kanallarda geçmiş bir programa
  dokununca kaldığı yerden oynatılır.

### Katalog
- **Bellek → disk → ağ** sırası. Diskteki kopya bayat olsa bile hemen gösterilir,
  tazeleme arka planda yapılır; uygulama anında açılır.
- Katalog JSON yerine satır tabanlı biçimde saklanır — on binlerce kanalın
  okunması saniyeler yerine milisaniyeler sürer.
- Üç bölümde birden **global arama**, adın başında geçen eşleşmeler önce.
- Favoriler (elle sıralanabilir), izleme geçmişi, "devam et", izlendi işareti.
- Kategori / A-Z / Z-A / puan / eklenme / kanal no sıralaması.

### Hesap ve gizlilik
- **Çoklu profil**: birden fazla abonelik, aralarında tek dokunuşla geçiş.
  Favori, geçmiş ve önbellek profil başına ayrı tutulur.
- Xtream Codes **ve** M3U listesi desteği (isteğe bağlı XMLTV adresi, özel
  User-Agent).
- **Ebeveyn kontrolü**: yetişkin kategorilerini gizleme ve/veya 4 haneli PIN ile
  kilitleme. PIN tuzlanıp SHA-256 ile saklanır, düz metin tutulmaz.
- Şifreler cihaz yedeklemesinin dışında bırakılır.

### Arayüz
- Kumanda için sol menü + odak çerçevesi; telefon için alt gezinme çubuğu.
- Jestler: tek dokunuş kontroller, çift dokunuş ±10/30 sn, sol dikey parlaklık,
  sağ dikey ses, yatay sürükleyerek sarma, uzun basılı tutunca 2x.
- Kumanda: yukarı/aşağı kanal, sağ kanal listesi, menü ayarlar, **0-9 ile kanal
  numarası**, önceki kanal tuşu.
- Türkçe ve İngilizce; vurgu rengi ve ızgara yoğunluğu seçilebilir.

---

## Kurulum

APK'yı GitHub Actions çıktılarından indirebilirsin:
**Actions → Build APK → prizma-iptv-release**.

Tek bir `app-release.apk` üretilir; dört mimariyi de içerir ve telefon,
tablet, TV kutusu fark etmeksizin her cihaza kurulur.

İlk açılışta bağlantı türünü seç:

| Alan | Xtream Codes | M3U |
|---|---|---|
| Sunucu adresi | `http://ornek.com:8080` | listenin tam adresi |
| Kullanıcı adı / şifre | gerekli | gerekmez |
| XMLTV EPG (gelişmiş) | boşsa `xmltv.php` kullanılır | elle vermelisin |

---

## Derleme

```bash
gradle assembleRelease
```

Gereken ortam: JDK 17, Android SDK 35.

### İmzalama

İmza bilgileri **repoda tutulmaz**. Şu sırayla aranır:

1. Ortam değişkenleri: `PRIZMA_KEYSTORE`, `PRIZMA_KEYSTORE_PASSWORD`,
   `PRIZMA_KEY_ALIAS`, `PRIZMA_KEY_PASSWORD`
2. Kök dizindeki `keystore.properties` (`.gitignore` içinde):
   ```properties
   storeFile=prizma.jks
   storePassword=...
   keyAlias=prizma
   keyPassword=...
   ```
3. `gradle -PstorePassword=...` parametreleri

Hiçbiri yoksa release imzasız derlenir.

CI için gereken secret'lar: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Yeni anahtar üretmek için **Actions → Imza anahtari uret**
iş akışını çalıştır.

> **Uyarı:** Bu deponun geçmişinde eski imza şifresi düz metin olarak yer alıyor.
> Aynı anahtarla yayın yapmaya devam edecekseniz anahtarı yenileyin.

---

## Mimari

```
core/     ortak altyapı — HTTP havuzu, hata tipleri, biçimlendirme, cihaz tespiti
data/
  model/  saf veri sınıfları
  remote/ Xtream API, M3U ayrıştırıcı, XMLTV akış ayrıştırıcı
  local/  ayarlar, profiller, favori/geçmiş, satır tabanlı disk deposu
  repo/   katalog ve EPG depoları, oturum (Session) ve servis konumlandırıcı
ui/
  theme/  renkler, ölçüler, TV/telefon profili
  common/ paylaşılan bileşenler
  login/ home/ guide/ detail/ settings/
player/   ExoPlayer kurulumu, denetleyici, arayüz, MediaSession servisi
```

Kurallar:
- Ağ katmanı ham istisna sızdırmaz; her hata çevrilmiş `AppError` olarak çıkar.
- Depolar `StateFlow` yayar, arayüz yalnızca toplar.
- Oturuma bağlı her şey profil kimliğiyle ayrılır.
- Oynatma sırası Intent yerine bellek üzerinden aktarılır (Intent 1 MB sınırı).

---

## Lisans notu

Uygulama, FFmpeg tabanlı kod çözücüler için
[`nextlib`](https://github.com/anilbeesetti/nextlib) kütüphanesini kullanır ve bu
kütüphane **GPL-3.0** ile lisanslıdır. Uygulamayı bu haliyle dağıtırsanız
GPL-3.0 koşulları geçerli olur. AC3/DTS desteğinden vazgeçip bu bağımlılığı
kaldırmak isterseniz `app/build.gradle.kts` içindeki `nextlib-media3ext`
satırını silip `PlayerEngine.renderersFactory` fonksiyonunu
`DefaultRenderersFactory` kullanacak şekilde değiştirmeniz yeterli.

Kişisel kullanım içindir; içerik sağlayıcı ya da abonelik içermez.
