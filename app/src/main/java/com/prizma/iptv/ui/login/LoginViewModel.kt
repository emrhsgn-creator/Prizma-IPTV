package com.prizma.iptv.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prizma.iptv.R
import com.prizma.iptv.core.appError
import com.prizma.iptv.core.userMessage
import com.prizma.iptv.data.local.ProfileStore
import com.prizma.iptv.data.local.Settings
import com.prizma.iptv.data.model.Profile
import com.prizma.iptv.data.model.Section
import com.prizma.iptv.data.model.SourceType
import com.prizma.iptv.data.remote.M3uParser
import com.prizma.iptv.data.remote.XtreamApi
import com.prizma.iptv.data.repo.App
import com.prizma.iptv.data.repo.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Giriş formundaki ham girdiler. */
data class ProfileDraft(
    val type: SourceType = SourceType.XTREAM,
    val host: String = "",
    val user: String = "",
    val pass: String = "",
    val label: String = "",
    val epgUrl: String = "",
    val userAgent: String = ""
)

/** Uygulamanın en üst seviyedeki durumu. */
sealed interface Stage {
    data object Booting : Stage
    data class SignIn(val draft: ProfileDraft, val busy: Boolean, val error: String?) : Stage
    data class Ready(val session: Session) : Stage
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _stage = MutableStateFlow<Stage>(Stage.Booting)
    val stage: StateFlow<Stage> = _stage

    private val _accountWarning = MutableStateFlow<String?>(null)
    val accountWarning: StateFlow<String?> = _accountWarning

    init {
        bootstrap()
    }

    /**
     * Açılış. Kayıtlı profil varsa ağ beklenmeden oturum açılır: katalog
     * diskteki kopyadan anında gelir, hesap doğrulaması arka planda yapılır.
     */
    private fun bootstrap() {
        val profile = if (Settings.signedOut) null else ProfileStore.active()
        if (profile == null) {
            _stage.value = Stage.SignIn(ProfileDraft(), busy = false, error = null)
            return
        }
        val session = App.open(profile)
        _stage.value = Stage.Ready(session)
        verifyInBackground(session)
    }

    private fun verifyInBackground(session: Session) {
        if (session.profile.type != SourceType.XTREAM) return
        viewModelScope.launch {
            try {
                val (account, server) = XtreamApi.login(session.profile)
                session.updateAccount(account, server)
                _accountWarning.value = null
            } catch (e: Exception) {
                val message = e.userMessage(getApplication())
                // Kimlik hatasıysa kullanıcıyı girişe düşür; ağ hatasıysa
                // çevrimdışı kullanıma devam edilir.
                if (isAuthProblem(e)) {
                    _stage.value = Stage.SignIn(
                        draft = session.profile.toDraft(),
                        busy = false,
                        error = message
                    )
                } else {
                    _accountWarning.value = message
                }
            }
        }
    }

    private fun isAuthProblem(e: Throwable): Boolean {
        val err = e as? com.prizma.iptv.core.AppError ?: return false
        return err.resId == R.string.error_auth ||
            err.resId == R.string.error_account_expired ||
            err.resId == R.string.error_account_inactive
    }

    fun updateDraft(draft: ProfileDraft) {
        val current = _stage.value
        if (current is Stage.SignIn) {
            _stage.value = current.copy(draft = draft, error = null)
        }
    }

    fun signIn(draft: ProfileDraft) {
        val current = _stage.value as? Stage.SignIn ?: return
        if (current.busy) return

        val normalizedHost = when (draft.type) {
            SourceType.XTREAM -> XtreamApi.normalizeHost(draft.host)
            SourceType.M3U -> draft.host.trim()
        }
        if (normalizedHost.isBlank()) {
            _stage.value = current.copy(
                error = getApplication<Application>().getString(R.string.login_host_required)
            )
            return
        }
        if (candidateNeedsCredentials(draft) && draft.user.isBlank()) {
            _stage.value = current.copy(
                error = getApplication<Application>().getString(R.string.login_user_required)
            )
            return
        }

        val candidate = Profile(
            id = "",
            label = draft.label.trim(),
            type = draft.type,
            host = normalizedHost,
            user = draft.user.trim(),
            pass = draft.pass.trim(),
            epgUrl = draft.epgUrl.trim(),
            userAgent = draft.userAgent.trim()
        )

        _stage.value = current.copy(busy = true, error = null)

        viewModelScope.launch {
            try {
                Settings.signedOut = false
                when (candidate.type) {
                    SourceType.XTREAM -> {
                        val (account, server) = XtreamApi.login(candidate)
                        val saved = ProfileStore.upsert(candidate)
                        val session = App.open(saved)
                        session.updateAccount(account, server)
                        _stage.value = Stage.Ready(session)
                    }

                    SourceType.M3U -> {
                        // M3U listesinde doğrulanacak hesap yok; listenin
                        // gerçekten indirilebildiğini kontrol etmek yeterli.
                        val entries = M3uParser.download(candidate)
                        if (entries.isEmpty()) throw appError(R.string.error_empty_playlist)
                        val saved = ProfileStore.upsert(candidate)
                        val session = App.open(saved)
                        _stage.value = Stage.Ready(session)
                        session.catalog.ensure(Section.LIVE, force = true)
                    }
                }
                _accountWarning.value = null
            } catch (e: Exception) {
                _stage.value = Stage.SignIn(
                    draft = draft.copy(host = normalizedHost),
                    busy = false,
                    error = e.userMessage(getApplication())
                )
            }
        }
    }

    fun switchProfile(profile: Profile) {
        Settings.signedOut = false
        val session = App.open(profile)
        _stage.value = Stage.Ready(session)
        verifyInBackground(session)
    }

    /** Ayarlardan yeni profil eklerken boş formla girişe döner. */
    fun addProfile() {
        _stage.value = Stage.SignIn(ProfileDraft(), busy = false, error = null)
    }

    fun cancelAddProfile() {
        val profile = ProfileStore.active() ?: return
        switchProfile(profile)
    }

    fun removeProfile(profile: Profile) {
        ProfileStore.remove(profile.id)
        val remaining = ProfileStore.active()
        if (remaining == null) {
            App.close()
            _stage.value = Stage.SignIn(ProfileDraft(), busy = false, error = null)
        } else if ((_stage.value as? Stage.Ready)?.session?.profile?.id == profile.id) {
            switchProfile(remaining)
        }
    }

    fun signOut() {
        Settings.signedOut = true
        App.close()
        _stage.value = Stage.SignIn(ProfileDraft(), busy = false, error = null)
    }

    fun dismissWarning() {
        _accountWarning.value = null
    }
}

private fun candidateNeedsCredentials(draft: ProfileDraft) = draft.type == SourceType.XTREAM

fun Profile.toDraft() = ProfileDraft(
    type = type,
    host = host,
    user = user,
    pass = pass,
    label = label,
    epgUrl = epgUrl,
    userAgent = userAgent
)
