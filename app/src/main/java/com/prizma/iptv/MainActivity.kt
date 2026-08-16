package com.prizma.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val PrizmaBg = Color(0xFF101014)
val PrizmaSurface = Color(0xFF1A1A21)
val PrizmaAccent = Color(0xFF4F8DF7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrizmaApp() }
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
        var host by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var account by remember { mutableStateOf<Account?>(null) }
        var autoTried by remember { mutableStateOf(false) }

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
        if (acc == null) {
            LoginScreen(
                initialHost = host,
                initialUser = user,
                initialPass = pass,
                ready = autoTried
            ) { h, u, p, a ->
                host = h; user = u; pass = p; account = a
                Prefs.save(ctx, h, u, p)
            }
        } else {
            HomeScreen(host, user, pass, acc) {
                Prefs.clear(ctx)
                account = null
            }
        }
    }
}
