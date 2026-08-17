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
            if (saved != null) {
                host = saved.first; user = saved.second; pass = saved.third
                try {
                    account = XtreamApi.login(host, user, pass)
                } catch (e: Exception) {
                    account = null
                }
            }
            autoTried = true
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
                onClearCache = { cacheEpoch++ }
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
