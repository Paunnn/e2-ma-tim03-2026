package com.tim03.slagalica.ui.region

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import com.tim03.slagalica.data.repository.MapPlayerDot
import com.tim03.slagalica.data.repository.RegionLeaderboardEntry
import com.tim03.slagalica.data.repository.RegionStats
import com.tim03.slagalica.ui.components.MozaikBottomNav
import com.tim03.slagalica.ui.izazov.CreateIzazovDialog
import com.tim03.slagalica.ui.izazov.IzazovCard
import com.tim03.slagalica.ui.izazov.IzazovGameScreen
import com.tim03.slagalica.ui.izazov.IzazovResultsScreen
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.IzazovPhase
import com.tim03.slagalica.viewmodel.IzazovViewModel
import com.tim03.slagalica.viewmodel.RegionViewModel
import kotlin.math.abs

private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

// Approximate lat/lng bounding boxes for each Serbian region
private data class RegionLatLngBounds(
    val latMin: Double, val latMax: Double,
    val lngMin: Double, val lngMax: Double
)

// Bounds are deliberately inset well away from national borders (Croatia, Hungary,
// Romania, Bulgaria, North Macedonia, Bosnia) so hashed dots never render outside
// Serbia — a plain rectangle can't follow the actual (non-rectangular) borders,
// so we trade a bit of edge-territory coverage for staying inside the country.
private val REGION_LATLNG_BOUNDS = mapOf(
    "Vojvodina"          to RegionLatLngBounds(44.90, 45.95, 19.20, 21.10),
    "Beograd"            to RegionLatLngBounds(44.62, 44.92, 20.25, 20.65),
    "Centralna Srbija"   to RegionLatLngBounds(43.90, 44.55, 20.30, 21.20),
    "Zapadna Srbija"     to RegionLatLngBounds(43.35, 44.20, 19.55, 20.10),
    "Istočna Srbija"     to RegionLatLngBounds(43.65, 44.55, 21.65, 22.30),
    "Jugoistočna Srbija" to RegionLatLngBounds(42.45, 43.25, 20.70, 22.00)
)

private val REGION_MAP_COLORS = mapOf(
    "Vojvodina"          to Color(0xFF4A7C59),
    "Beograd"            to Color(0xFF7B4FA0),
    "Centralna Srbija"   to Color(0xFF2E6FA3),
    "Zapadna Srbija"     to Color(0xFF4D8BA6),
    "Istočna Srbija"     to Color(0xFF8B5E52),
    "Jugoistočna Srbija" to Color(0xFF5A7A4A)
)

private fun playerGeoPoint(uid: String, region: String): GeoPoint? {
    val bounds = REGION_LATLNG_BOUNDS[region] ?: return null
    val hash = uid.hashCode()
    val lat = bounds.latMin + (abs(hash % 1000) / 1000.0) * (bounds.latMax - bounds.latMin)
    val lng = bounds.lngMin + (abs(hash / 1000 % 1000) / 1000.0) * (bounds.lngMax - bounds.lngMin)
    return GeoPoint(lat, lng)
}

private class PlayerDotsOverlay(
    private val players: List<MapPlayerDot>,
    private val myUid: String
) : Overlay() {
    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val fillPaint = android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.FILL }
        val strokePaint = android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f; color = android.graphics.Color.WHITE }
        players.forEach { player ->
            val gp = playerGeoPoint(player.uid, player.region) ?: return@forEach
            val pt = projection.toPixels(gp, null)
            val isMe = player.uid == myUid
            fillPaint.color = if (isMe) android.graphics.Color.parseColor("#FFD700")
            else android.graphics.Color.parseColor("#2196F3")
            val r = if (isMe) 18f else 12f
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, fillPaint)
            if (isMe) canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, strokePaint)
        }
    }
}

@Composable
private fun SerbiaMapView(players: List<MapPlayerDot>, myUid: String) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.5)
            controller.setCenter(GeoPoint(44.0, 21.0))
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    LaunchedEffect(players, myUid) {
        mapView.overlays.clear()
        mapView.overlays.add(PlayerDotsOverlay(players, myUid))
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(14.dp))
    )
}

private fun rankColor(rank: Int) = when (rank) {
    1 -> Gold
    2 -> Silver
    3 -> Bronze
    else -> MediumGray
}

@Composable
fun RegionScreen(
    onHomeClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    vm: RegionViewModel = viewModel(),
    izazovVm: IzazovViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val izazovState by izazovVm.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateIzazovDialog(
            bidStars = izazovState.createBidStars,
            bidTokens = izazovState.createBidTokens,
            myStars = izazovState.myStars,
            myTokens = izazovState.myTokens,
            onBidStarsChange = izazovVm::setBidStars,
            onBidTokensChange = izazovVm::setBidTokens,
            onConfirm = { showCreateDialog = false; izazovVm.createIzazov() },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Game / results take over the full screen
    when (izazovState.phase) {
        IzazovPhase.PLAYING -> {
            IzazovGameScreen(
                questions = izazovState.gameQuestions,
                currentIndex = izazovState.currentQuestionIndex,
                selectedAnswer = izazovState.selectedAnswer,
                score = izazovState.gameScore,
                gameComplete = izazovState.gameComplete,
                onSelectAnswer = izazovVm::selectAnswer,
                onNext = izazovVm::nextQuestion
            )
            return
        }
        IzazovPhase.RESULTS -> {
            IzazovResultsScreen(
                session = izazovState.activeSession,
                myUid = izazovState.myUid,
                onBack = { izazovVm.backToList() }
            )
            return
        }
        else -> {}
    }

    Scaffold(
        containerColor = Navy,
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = Gold,
                    contentColor = Navy
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Kreiraj izazov")
                }
            }
        },
        bottomBar = {
            MozaikBottomNav(
                selectedIndex = 2,
                onHomeClick = onHomeClick,
                onLeaderboardClick = onLeaderboardClick,
                onMapClick = {},
                onFriendsClick = onFriendsClick,
                onProfileClick = onProfileClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = PrimaryBlueBright, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("Regioni", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.loadLeaderboard() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Osvezi", tint = MediumGray)
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyCard),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Rang Lista", "Izazovi").forEachIndexed { i, label ->
                    val sel = selectedTab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) PrimaryBlueBright else Color.Transparent)
                            .clickable { selectedTab = i }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sel) White else LightGray, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedTab == 0) {
                // --- Rang Lista tab ---
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlueBright)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Map, contentDescription = null, tint = PrimaryBlueBright, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Igrači na mapi", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text("${state.mapPlayers.size} igrača", color = MediumGray, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    SerbiaMapView(players = state.mapPlayers, myUid = state.myUid)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        itemsIndexed(state.leaderboard) { _, region ->
                            RegionCard(
                                stats = region,
                                isMyRegion = region.region == state.myRegion,
                                onClick = { vm.selectRegion(region) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            } else {
                // --- Izazovi tab ---
                if (izazovState.phase == IzazovPhase.CREATING) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                } else if (izazovState.openSessions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚔", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Nema aktivnih izazova", color = MediumGray, fontWeight = FontWeight.SemiBold)
                            Text("Klikni + da kreirate novi", color = MediumGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(izazovState.openSessions, key = { it.id }) { session ->
                            IzazovCard(
                                session = session,
                                myUid = izazovState.myUid,
                                onJoin = { izazovVm.joinIzazov(session) },
                                onPlay = { izazovVm.playExisting(session) },
                                onViewResults = { izazovVm.viewResults(session) }
                            )
                        }
                    }
                }
            }
        }

        // Region detail dialog
        val selected = state.selectedRegion
        if (selected != null) {
            RegionDetailDialog(
                stats = selected,
                players = state.regionPlayers,
                isLoadingPlayers = state.isLoadingPlayers,
                onDismiss = { vm.clearSelectedRegion() }
            )
        }
    }
}

@Composable
private fun RegionCard(stats: RegionStats, isMyRegion: Boolean, onClick: () -> Unit) {
    val borderColor = if (isMyRegion) PrimaryBlueBright else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rankColor(stats.rank).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#${stats.rank}",
                    color = rankColor(stats.rank),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            // Region icon + name
            Text(stats.icon, fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stats.region, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (isMyRegion) {
                        Spacer(Modifier.width(6.dp))
                        Text("(moj)", color = PrimaryBlueBright, fontSize = 11.sp)
                    }
                }
                Text(
                    "${stats.activePlayers} aktivnih / ${stats.totalPlayers} ukupno",
                    color = MediumGray,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("${stats.totalMonthlyStars}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("mesečno", color = MediumGray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RegionDetailDialog(
    stats: RegionStats,
    players: List<RegionLeaderboardEntry>,
    isLoadingPlayers: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stats.icon, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stats.region, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text("Rang #${stats.rank} • ${stats.totalMonthlyStars}★ mesečno", color = LightGray, fontSize = 12.sp)
                }
            }
        },
        text = {
            if (isLoadingPlayers) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlueBright)
                }
            } else if (players.isEmpty()) {
                Text("Nema aktivnih igrača u ovom regionu.", color = MediumGray, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    players.take(10).forEach { player ->
                        PlayerRow(player)
                    }
                    if (players.size > 10) {
                        Text("...i još ${players.size - 10} igrača", color = MediumGray, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zatvori", color = PrimaryBlueBright)
            }
        }
    )
}

@Composable
private fun PlayerRow(player: RegionLeaderboardEntry) {
    val frameColor = when (player.rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#${player.rank}",
            color = rankColor(player.rank),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(32.dp)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(NavyCardLight)
                .then(
                    if (frameColor != null)
                        Modifier.border(2.dp, frameColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = LightGray, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(player.username, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
            Text("${player.monthlyStars}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
