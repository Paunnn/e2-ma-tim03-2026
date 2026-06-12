package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val navigateTo: AuthNavEvent? = null
)

enum class AuthNavEvent {
    HOME, LOGIN, EMAIL_VERIFICATION_SENT
}

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(email: String, username: String, region: String, password: String, confirmPassword: String) {
        if (email.isBlank() || username.isBlank() || region.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Sva polja su obavezna")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = AuthUiState(error = "Lozinke se ne poklapaju")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState(error = "Lozinka mora imati najmanje 6 karaktera")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.register(email, username, region, password)
                .onSuccess {
                    _uiState.value = AuthUiState(navigateTo = AuthNavEvent.EMAIL_VERIFICATION_SENT)
                }
                .onFailure { e ->
                    _uiState.value = AuthUiState(error = friendlyError(e.message))
                }
        }
    }

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Unesite email/korisničko ime i lozinku")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.login(identifier, password)
                .onSuccess {
                    _uiState.value = AuthUiState(navigateTo = AuthNavEvent.HOME)
                }
                .onFailure { e ->
                    if (e.message == "EMAIL_NOT_VERIFIED") {
                        _uiState.value = AuthUiState(
                            error = "Email nije potvrđen. Proverite Vaš inbox.",
                            navigateTo = null
                        )
                    } else {
                        _uiState.value = AuthUiState(error = friendlyError(e.message))
                    }
                }
        }
    }

    fun resendVerificationEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            // Need to sign in temporarily to resend
            runCatching { repo.login(email, password) }
            repo.sendVerificationEmail()
                .onSuccess {
                    _uiState.value = AuthUiState(successMessage = "Verifikacioni email je poslat ponovo")
                    repo.logout()
                }
                .onFailure {
                    _uiState.value = AuthUiState(error = "Nije moguće poslati email ponovo")
                }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            _uiState.value = AuthUiState(error = "Sva polja su obavezna")
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.value = AuthUiState(error = "Nove lozinke se ne poklapaju")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = AuthUiState(error = "Nova lozinka mora imati najmanje 6 karaktera")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.changePassword(oldPassword, newPassword)
                .onSuccess {
                    _uiState.value = AuthUiState(successMessage = "Lozinka je uspešno promenjena")
                }
                .onFailure { e ->
                    _uiState.value = AuthUiState(error = friendlyError(e.message))
                }
        }
    }

    fun loginAsGuest() {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.loginAsGuest()
                .onSuccess { _uiState.value = AuthUiState(navigateTo = AuthNavEvent.HOME) }
                .onFailure { _uiState.value = AuthUiState(error = "Gost prijava nije uspela") }
        }
    }

    fun logout() {
        repo.logout()
        _uiState.value = AuthUiState(navigateTo = AuthNavEvent.LOGIN)
    }

    fun clearEvent() {
        _uiState.value = _uiState.value.copy(navigateTo = null, error = null, successMessage = null)
    }

    private fun friendlyError(msg: String?): String = when {
        msg == null -> "Greška. Pokušajte ponovo."
        msg.contains("email address is already in use", ignoreCase = true) -> "Email je već registrovan"
        msg.contains("badly formatted", ignoreCase = true) -> "Neispravan email format"
        msg.contains("password is invalid", ignoreCase = true) || msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) -> "Pogrešan email ili lozinka"
        msg.contains("no user record", ignoreCase = true) -> "Korisnik nije pronađen"
        msg.contains("network", ignoreCase = true) -> "Greška mreže. Proverite internet konekciju."
        msg.contains("INVALID_EMAIL", ignoreCase = true) -> "Neispravan email"
        msg.contains("WRONG_PASSWORD", ignoreCase = true) -> "Pogrešna lozinka"
        else -> msg
    }
}
