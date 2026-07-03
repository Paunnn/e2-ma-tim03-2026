package com.tim03.slagalica.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val region: String = "",
    val tokens: Int = 5,
    val stars: Int = 0,
    val league: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val avatarIndex: Int = 0,
    val weeklyStars: Int = 0,
    val monthlyStars: Int = 0,
    // Non-friendly partije played in the current weekly/monthly cycle - a player is
    // ranked as soon as this is > 0 (spec 4a), even if their star balance is 0 or negative.
    val weeklyGamesPlayed: Int = 0,
    val monthlyGamesPlayed: Int = 0,
    val fcmToken: String = "",
    // True from successful login until logout - "currently active" players are the
    // logged-in ones. (Named "loggedIn", not "isLoggedIn": Firestore's bean mapper
    // would strip the "is" prefix and read/write a mismatched field name.)
    val loggedIn: Boolean = false,
    // Presence heartbeat (ms epoch) - refreshed every minute while the app is foregrounded.
    val lastActive: Long = 0L,
    val lastDailyTokenDate: String = "",
    val lastMonthlyCheck: String = "",
    val koZnaZnaCorrect: Int = 0,
    val koZnaZnaIncorrect: Int = 0,
    val koZnaZnaRounds: Int = 0,
    val spojniceConnected: Int = 0,
    val spojniceTotalPairs: Int = 0,
    val spojniceRounds: Int = 0,
    val mojBrojHits: Int = 0,
    val mojBrojRounds: Int = 0,
    val korakStep1: Int = 0,
    val korakStep2: Int = 0,
    val korakStep3: Int = 0,
    val korakStep4: Int = 0,
    val korakStep5: Int = 0,
    val korakStep6: Int = 0,
    val korakStep7: Int = 0,
    val korakRounds: Int = 0,
    val asocijacijeSolved: Int = 0,
    val asocijacijeTotal: Int = 0,
    val skockoAttempt1: Int = 0,
    val skockoAttempt2: Int = 0,
    val skockoAttempt3: Int = 0,
    val skockoAttempt4: Int = 0,
    val skockoAttempt5: Int = 0,
    val skockoAttempt6: Int = 0,
    val skockoRounds: Int = 0,
    val gamesWon: Int = 0,
    val gamesLost: Int = 0,
    val partijaWon: Int = 0,
    val partijaLost: Int = 0,
    val partijaDrawn: Int = 0
)
