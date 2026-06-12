package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.model.NotificationModel
import com.tim03.slagalica.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotifFilter { ALL, UNREAD, READ, CHAT, RANKING, REWARD, OTHER }

class NotificationsViewModel(
    private val repo: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _filter = MutableStateFlow(NotifFilter.ALL)
    val filter: StateFlow<NotifFilter> = _filter.asStateFlow()

    val allNotifications: StateFlow<List<NotificationModel>> = repo.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotifications: StateFlow<List<NotificationModel>> = combine(
        allNotifications, filter
    ) { notifs, f ->
        when (f) {
            NotifFilter.ALL -> notifs
            NotifFilter.UNREAD -> notifs.filter { !it.isRead }
            NotifFilter.READ -> notifs.filter { it.isRead }
            NotifFilter.CHAT -> notifs.filter { it.channel == "CHAT" }
            NotifFilter.RANKING -> notifs.filter { it.channel == "RANKING" }
            NotifFilter.REWARD -> notifs.filter { it.channel == "REWARD" }
            NotifFilter.OTHER -> notifs.filter { it.channel == "OTHER" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = allNotifications.map { it.count { n -> !n.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch { repo.seedSampleNotificationsIfEmpty() }
    }

    fun setFilter(f: NotifFilter) { _filter.value = f }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch { repo.markAsRead(notificationId) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { repo.markAllAsRead() }
    }

    fun addTestNotifications() {
        viewModelScope.launch {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            android.util.Log.d("NOTIF_TEST", "UID = $uid")
            if (uid == null) { android.util.Log.e("NOTIF_TEST", "UID je null!"); return@launch }
            try {
                repo.addNotification(uid, "CHAT", "Nova poruka", "Petar: Hej, idemo na partiju?")
                repo.addNotification(uid, "RANKING", "Nedeljni rezultati", "Zauzeli ste 2. mesto na nedeljnoj rang listi!")
                repo.addNotification(uid, "REWARD", "Nagrada!", "Dobili ste 3 tokena za plasman na rang listi.")
                repo.addNotification(uid, "OTHER", "Poziv za partiju", "Marko vas poziva na prijateljsku partiju.")
                android.util.Log.d("NOTIF_TEST", "Sve notifikacije dodate!")
            } catch (e: Exception) {
                android.util.Log.e("NOTIF_TEST", "Greška: ${e.message}", e)
            }
        }
    }
}
