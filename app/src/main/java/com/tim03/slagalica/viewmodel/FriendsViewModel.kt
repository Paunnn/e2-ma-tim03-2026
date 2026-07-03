package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.FriendInfo
import com.tim03.slagalica.data.model.Friendship
import com.tim03.slagalica.data.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FriendsTab { LIST, SEARCH, REQUESTS }

data class FriendsUiState(
    val tab: FriendsTab = FriendsTab.LIST,
    val friends: List<FriendInfo> = emptyList(),
    val pendingRequests: List<Friendship> = emptyList(),
    val searchQuery: String = "",
    val searchResult: FriendInfo? = null,
    val searchDone: Boolean = false,
    val requestSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMsg: String? = null
)

class FriendsViewModel(
    private val repo: FriendRepository = FriendRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private var friendsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null

    init {
        startListeners()
    }

    private fun startListeners() {
        friendsListener?.remove()
        requestsListener?.remove()

        friendsListener = repo.listenToFriendsList { list ->
            _uiState.value = _uiState.value.copy(friends = list)
            // Enrich with the current monthly leaderboard position (spec 7c).
            viewModelScope.launch {
                runCatching {
                    val ranks = repo.getMonthlyRanks()
                    _uiState.value = _uiState.value.copy(
                        friends = _uiState.value.friends.map { f ->
                            f.copy(monthlyRank = ranks[f.uid] ?: 0)
                        }
                    )
                }
            }
        }
        requestsListener = repo.listenToPendingRequests { list ->
            _uiState.value = _uiState.value.copy(pendingRequests = list)
        }
    }

    fun setTab(tab: FriendsTab) {
        _uiState.value = _uiState.value.copy(
            tab = tab,
            searchQuery = "",
            searchResult = null,
            searchDone = false,
            requestSent = false,
            errorMsg = null
        )
    }

    fun setSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q, searchResult = null, searchDone = false, requestSent = false, errorMsg = null)
    }

    fun searchUser() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, searchResult = null, searchDone = false, errorMsg = null)
        viewModelScope.launch {
            runCatching {
                val found = repo.findUserByUsername(q)
                val myUid = auth.currentUser?.uid
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResult = if (found?.uid == myUid) null else found,
                    searchDone = true,
                    errorMsg = if (found == null || found.uid == myUid) "Korisnik nije pronađen." else null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, searchDone = true, errorMsg = "Greška pri pretrazi.")
            }
        }
    }

    fun sendRequest(receiverId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = null)
        viewModelScope.launch {
            runCatching {
                val ok = repo.sendFriendRequest(receiverId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    requestSent = ok,
                    errorMsg = if (!ok) "Zahtev je već poslat ili ste već prijatelji." else null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMsg = "Greška pri slanju zahteva.")
            }
        }
    }

    fun acceptRequest(request: Friendship) {
        viewModelScope.launch { runCatching { repo.acceptRequest(request) } }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch { runCatching { repo.rejectRequest(requestId) } }
    }

    fun removeFriend(friendUid: String) {
        viewModelScope.launch { runCatching { repo.removeFriend(friendUid) } }
    }

    fun handleQrScanResult(scannedUid: String) {
        if (scannedUid.isBlank()) return
        val myUid = auth.currentUser?.uid
        if (scannedUid == myUid) {
            _uiState.value = _uiState.value.copy(errorMsg = "Ne možete dodati sebe.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMsg = null)
        viewModelScope.launch {
            runCatching {
                val found = repo.findUserByUid(scannedUid)
                if (found == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMsg = "Korisnik nije pronađen.")
                    return@runCatching
                }
                // Already a friend: just show the result card (labeled "Već ste
                // prijatelji"), don't fire a request that would report an error.
                if (_uiState.value.friends.any { it.uid == scannedUid }) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        searchResult = found,
                        searchDone = true,
                        requestSent = false,
                        tab = FriendsTab.SEARCH
                    )
                    return@runCatching
                }
                val ok = repo.sendFriendRequest(scannedUid)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResult = found,
                    searchDone = true,
                    requestSent = ok,
                    tab = FriendsTab.SEARCH,
                    errorMsg = if (!ok) "Zahtev je već poslat ili ste već prijatelji." else null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMsg = "Greška pri QR skeniranju.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        friendsListener?.remove()
        requestsListener?.remove()
    }
}
