package com.prizma.iptv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    account: Account,
    onBack: () -> Unit,
    onSwitchProfile: (Profile) -> Unit,
    onAddProfile: () -> Unit,
    onClearCache: () -> Unit
) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var buffer by remember { mutableIntStateOf(Prefs.bufferSeconds(ctx)) }
    var autoNext by remember { mutableStateOf(Prefs.autoNext(ctx)) }
    var fastZap by remember { mutableStateOf(Prefs.fastZap(ctx)) }

    val profiles = remember(rev) { Prefs.profiles(ctx) }
    val activeKey = remember(rev) { Prefs.activeKey(ctx) }
    val favCount = remember(rev) { Store.favorites(ctx).size }
    val histCount = remember(rev) { Store.history(ctx).size }

    Surface(color = PrizmaBg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp)
        ) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B2350))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onBack)
                        .size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White, fontSize = 20.sp)
                }
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text("Ayarlar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Section("Hesap")
            Card {
                InfoRow("Kullanıcı", account.username)
                InfoRow("Durum", account.status)
                InfoRow("Bitiş tarihi", account.expiry)
                InfoRow("Bağlantı", "${account.activeConnections} / ${account.maxConnections}")
            }

            Section("Kayıtlı hesaplar")
            Card {
                profiles.forEach { prof ->
                    val active = (prof.host + "|" + prof.user) == activeKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0x264F8DF7) else Color.Transparent)
                            .clickable {
                                if (!active) onSwitchProfile(prof)
                            }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                prof.user,
                                color = if (active) Color.White else Color(0xFFC3C8D4),
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                prof.label,
                                color = Color(0xFF6E7686),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (active) {
                            Text("aktif", color = PrizmaAccent, fontSize = 10.sp)
                        } else {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = Color(0xFF6E7686),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        Prefs.removeProfile(ctx, prof)
                                        rev++
                                    }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "+ Yeni hesap ekle",
                    color = PrizmaAccent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAddProfile)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }

            Section("Oynatma")
            Card {
                Text("Tampon süresi", color = Color(0xFFC3C8D4), fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Yüksek değer donmayı azaltır, kanal açılışını yavaşlatır.",
                    color = Color(0xFF6E7686),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "Düşük", 30 to "Normal", 60 to "Yüksek").forEach { (v, label) ->
                        Pill("$label (${v}s)", buffer == v) {
                            buffer = v
                            Prefs.setBufferSeconds(ctx, v)
                            Toast.makeText(
                                ctx,
                                "Sonraki oynatmada geçerli olacak",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sonraki bölüm otomatik", color = Color(0xFFC3C8D4), fontSize = 12.sp)
                        Text(
                            "Dizilerde bölüm bitince devam eder.",
                            color = Color(0xFF6E7686),
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = autoNext,
                        onCheckedChange = {
                            autoNext = it
                            Prefs.setAutoNext(ctx, it)
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hızlı kanal açılışı", color = Color(0xFFC3C8D4), fontSize = 12.sp)
                        Text(
                            "Canlı yayınlarda gereksiz ön taramayı atlar. " +
                                "Bir kanal açılmazsa kapatmayı deneyin.",
                            color = Color(0xFF6E7686),
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = fastZap,
                        onCheckedChange = {
                            fastZap = it
                            Prefs.setFastZap(ctx, it)
                            Toast.makeText(
                                ctx,
                                "Sonraki kanal açılışında geçerli olacak",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            Section("Veri")
            Card {
                InfoRow("Favoriler", "$favCount kayıt")
                InfoRow("İzleme geçmişi", "$histCount kayıt")
                Spacer(Modifier.height(10.dp))
                DangerRow("İzleme geçmişini temizle") {
                    Store.clearHistory(ctx)
                    rev++
                    Toast.makeText(ctx, "Geçmiş temizlendi", Toast.LENGTH_SHORT).show()
                }
                DangerRow("Kanal listesi önbelleğini yenile") {
                    onClearCache()
                    Toast.makeText(ctx, "Liste yeniden yüklenecek", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Prizma IPTV · kişisel kullanım",
                color = Color(0xFF4A505C),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 22.dp)
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        color = PrizmaAccent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Card(content: @Composable ColumnScopeAlias.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16171F))
            .padding(12.dp)
    ) {
        content(ColumnScopeAlias)
    }
}

object ColumnScopeAlias

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF8A90A0), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Color(0xFFE6E8EB),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DangerRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color(0xFFFF8080),
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp)
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.White else Color(0xFFC3C8D4),
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PrizmaAccent.copy(alpha = 0.4f) else Color(0xFF23242E))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
