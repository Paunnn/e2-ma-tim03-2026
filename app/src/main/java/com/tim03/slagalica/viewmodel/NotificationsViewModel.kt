package com.tim03.slagalica.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.tim03.slagalica.data.model.NotificationModel
import com.tim03.slagalica.data.repository.NotificationRepository
import com.tim03.slagalica.util.LocalNotificationHelper
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
    application: Application
) : AndroidViewModel(application) {

    private val repo: NotificationRepository = NotificationRepository()

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

    private var previousIds: Set<String> = emptySet()

    init {
        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                viewModelScope.launch { repo.seedSampleNotificationsIfEmpty() }
            }
        }

        viewModelScope.launch {
            allNotifications.collect { notifications ->
                val currentIds = notifications.map { it.id }.toSet()
                val newUnread = notifications.filter { it.id !in previousIds && !it.isRead }
                if (previousIds.isNotEmpty()) {
                    newUnread.forEach { notif ->
                        LocalNotificationHelper.show(
                            context = getApplication(),
                            title = notif.title,
                            body = notif.message,
                            channel = LocalNotificationHelper.channelForType(notif.channel)
                        )
                    }
                }
                previousIds = currentIds
            }
        }
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
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            repo.addNotification(uid, "CHAT", "Nova poruka", "Petar: Hej, idemo na partiju?")
            repo.addNotification(uid, "RANKING", "Nedeljni rezultati", "Zauzeli ste 2. mesto!")
            repo.addNotification(uid, "REWARD", "Nagrada!", "Dobili ste 3 tokena za plasman.")
        }
    }
}
