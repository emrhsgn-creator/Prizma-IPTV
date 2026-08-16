package com.prizma.iptv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    initialHost: String,
    initialUser: String,
    initialPass: String,
    ready: Boolean,
    onSuccess: (String, String, String, Account) -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Surface(color = PrizmaBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PRIZMA IPTV", color = PrizmaAccent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Xtream Codes hesabınla giriş yap", color = Color(0xFF9AA0A6), fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))

            if (!ready) {
                CircularProgressIndicator(color = PrizmaAccent)
            } else {
                val fieldMod = Modifier.fillMaxWidth().widthIn(max = 460.dp)

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Sunucu adresi (örn: http://ornek.com:8080)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = fieldMod
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Kullanıcı adı") },
                    singleLine = true,
                    modifier = fieldMod
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Şifre") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = fieldMod
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    enabled = !busy && host.isNotBlank() && user.isNotBlank(),
                    onClick = {
                        busy = true
                        error = ""
                        val h = XtreamApi.normalizeHost(host)
                        scope.launch {
                            try {
                                val acc = XtreamApi.login(h, user.trim(), pass.trim())
                                onSuccess(h, user.trim(), pass.trim(), acc)
                            } catch (e: Exception) {
                                error = e.message ?: "Bağlanılamadı"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp).height(50.dp)
                ) {
                    Text(if (busy) "Bağlanıyor..." else "Giriş yap")
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }
            }
        }
    }
}
