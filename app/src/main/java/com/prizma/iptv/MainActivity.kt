package com.prizma.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

val PrizmaBg = Color(0xFF101014)
val PrizmaSurface = Color(0xFF1A1A21)
val PrizmaAccent = Color(0xFF4F8DF7)

object Refresh {
    var tick by mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrizmaApp() }
    }

    override fun onResume() {
        super.onResume()
        Refresh.tick++
    }
}

@Composable
fun PrizmaApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PrizmaAccent,
            background = PrizmaBg,
            surface = PrizmaSurface
        )
    ) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()

        var host by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var account by remember { mutableStateOf<Account?>(null) }
        var autoTried by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var forceLogin by remember { mutableStateOf(false) }
        var cacheEpoch by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            val saved = Prefs.load(ctx)
            if (saved == null) {
                autoTried = true
                return@LaunchedEffect
            }
            host = saved.first; user = saved.second; pass = saved.third

            // Kaydedilmis profil varsa ana ekrani HEMEN goster.
            //
            // Katalog zaten diskte duruyor, ama onceden giris yaniti gelene
            // kadar hicbir sey cizilmiyordu: acilis, sunucunun cevap hizina
            // bagli olarak okuma zaman asimina (60 sn) kadar bos ekranda
            // bekleyebiliyordu. Disk onbelleginin acilis hizina hic katkisi
            // olmuyordu, cunku ona ulasilan ekran hic acilmiyordu.
            //
            // Gecici hesap nesnesiyle ana ekran aciliyor; dogrulama arkadan
            // gelip gercek bilgileri yerine koyuyor.
            account = Account(user, "-", "-", "-", "-")
            autoTried = true
            try {
                account = XtreamApi.login(host, user, pass)
            } catch (e: AuthRejected) {
                // Kimlik gercekten reddedildi: giris ekranina don.
                account = null
            } catch (e: Exception) {
                // Ag ya da sunucu hatasi. Onbellekteki katalogla devam et;
                // kullaniciyi girise atmanin bir faydasi yok.
            }
        }

        val acc = account

        when {
            acc == null || forceLogin -> LoginScreen(
                initialHost = if (forceLogin) "" else host,
                initialUser = if (forceLogin) "" else user,
                initialPass = if (forceLogin) "" else pass,
                ready = autoTried
            ) { h, u, p, a ->
                host = h; user = u; pass = p; account = a
                Prefs.save(ctx, h, u, p)
                forceLogin = false
                showSettings = false
                cacheEpoch++
            }

            showSettings -> SettingsScreen(
                account = acc,
                onBack = { showSettings = false },
                onSwitchProfile = { prof ->
                    Prefs.setActive(ctx, prof)
                    scope.launch {
                        try {
                            val a = XtreamApi.login(prof.host, prof.user, prof.pass)
                            host = prof.host; user = prof.user; pass = prof.pass
                            account = a
                            cacheEpoch++
                            showSettings = false
                        } catch (e: Exception) {
                            forceLogin = true
                        }
                    }
                },
                onAddProfile = { forceLogin = true },
                onClearCache = {
                    Catalog.clear(ctx)
                    cacheEpoch++
                }
            )

            else -> HomeScreen(
                host = host,
                user = user,
                pass = pass,
                account = acc,
                cacheEpoch = cacheEpoch,
                onSettings = { showSettings = true },
                onLogout = {
                    Prefs.clear(ctx)
                    account = null
                }
            )
        }
    }
}
