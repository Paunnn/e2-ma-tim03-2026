package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "",
    val tokens: Int = 0,
    val stars: Int = 0,
    val league: Int = 0,
    val avatarIndex: Int = 0,
    val isGuest: Boolean = false
)

class HomeViewModel(
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    fun loadUser() {
        if (auth.currentUser?.isAnonymous == true) {
            _uiState.value = HomeUiState(
                username = auth.currentUser?.displayName ?: "Guest",
                isGuest = true
            )
            return
        }
        viewModelScope.launch {
            runCatching {
                val user = userRepo.getCurrentUser() ?: return@runCatching
                _uiState.value = HomeUiState(
                    username = user.username,
                    tokens = user.tokens,
                    stars = user.stars,
                    league = user.league,
                    avatarIndex = user.avatarIndex
                )
            }
        }
    }

    fun logout() {
        val user = auth.currentUser
        if (user?.isAnonymous == true) {
            user.delete().addOnCompleteListener { auth.signOut() }
        } else {
            auth.signOut()
        }
    }
}
