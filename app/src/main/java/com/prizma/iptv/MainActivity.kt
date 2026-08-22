package com.prizma.iptv

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prizma.iptv.core.LocaleHelper
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.ui.common.LoadingBox
import com.prizma.iptv.ui.home.HomeBus
import com.prizma.iptv.ui.home.HomeScreen
import com.prizma.iptv.ui.home.Launch
import com.prizma.iptv.ui.login.LoginScreen
import com.prizma.iptv.ui.login.MainViewModel
import com.prizma.iptv.ui.login.Stage
import com.prizma.iptv.ui.login.rememberSavedProfiles
import com.prizma.iptv.ui.theme.Ink
import com.prizma.iptv.ui.theme.PrizmaTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            HomeBus.onKey?.invoke(event.keyCode) == true
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrizmaTheme {
                Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
                    PrizmaRoot()
                }
            }
        }
    }
}

@Composable
fun PrizmaRoot() {
    val viewModel: MainViewModel = viewModel()
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val warning by viewModel.accountWarning.collectAsStateWithLifecycle()
    val profiles = rememberSavedProfiles()

    when (val current = stage) {
        Stage.Booting -> LoadingBox(Modifier.fillMaxSize())

        is Stage.SignIn -> LoginScreen(
            state = current,
            profiles = profiles,
            canCancel = profiles.isNotEmpty(),
            onDraftChange = viewModel::updateDraft,
            onSubmit = viewModel::signIn,
            onPickProfile = viewModel::switchProfile,
            onDeleteProfile = viewModel::removeProfile,
            onCancel = viewModel::cancelAddProfile
        )

        is Stage.Ready -> {
            val ctx = LocalContext.current
            val session = current.session
            val sections by session.catalog.sections.collectAsStateWithLifecycle()

            // "Açılışta son kanalı aç" ayarı: canlı liste hazır olur olmaz
            // bir kereye mahsus tetiklenir.
            LaunchedEffect(sections.containsKey(Section.LIVE)) {
                if (sections.containsKey(Section.LIVE)) {
                    Launch.resumeLastChannelOnce(ctx, session)
                }
            }

            HomeScreen(
                session = session,
                warning = warning,
                onDismissWarning = viewModel::dismissWarning,
                onAddProfile = viewModel::addProfile,
                onSwitchProfile = viewModel::switchProfile,
                onRemoveProfile = viewModel::removeProfile,
                onSignOut = viewModel::signOut
            )
        }
    }
}
