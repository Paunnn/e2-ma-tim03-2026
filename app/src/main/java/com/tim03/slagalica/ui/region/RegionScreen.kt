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
import com.tim03.slagalica.ui.izazov.IzazovResultsScreen
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.IzazovPhase
import com.tim03.slagalica.viewmodel.IzazovViewModel
import com.tim03.slagalica.viewmodel.RegionViewModel

private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

// Approximate polygons (lat, lng) for each Serbian region. Every vertex is deliberately
// placed a safe margin INSIDE the national border (Hungary, Romania, Bulgaria, North
// Macedonia, Bosnia, Croatia), so points sampled inside the polygon can never land
// abroad — unlike the old bounding boxes, whose corners reached into Romania.
private val REGION_POLYGONS: Map<String, List<Pair<Double, Double>>> = mapOf(
    "Vojvodina" to listOf(
        45.90 to 19.25, 45.95 to 19.70, 45.80 to 20.05, 45.70 to 20.30,
        45.50 to 20.55, 45.25 to 20.90, 45.10 to 21.15, 44.93 to 21.15,
        44.88 to 20.85, 44.92 to 20.45, 44.95 to 20.10, 45.03 to 19.70,
        45.12 to 19.30, 45.40 to 19.20, 45.60 to 19.10, 45.80 to 19.10
    ),
    "Beograd" to listOf(
        44.90 to 20.25, 44.92 to 20.45, 44.88 to 20.62, 44.75 to 20.72,
        44.62 to 20.65, 44.55 to 20.45, 44.62 to 20.25, 44.78 to 20.18
    ),
    "Zapadna Srbija" to listOf(
        44.55 to 19.50, 44.60 to 19.90, 44.50 to 20.20, 44.20 to 20.30,
        43.90 to 20.35, 43.60 to 20.30, 43.40 to 20.15, 43.35 to 19.95,
        43.45 to 19.70, 43.70 to 19.55, 44.00 to 19.45, 44.30 to 19.40
    ),
    "Centralna Srbija" to listOf(
        44.60 to 20.40, 44.65 to 20.80, 44.65 to 21.20, 44.50 to 21.45,
        44.20 to 21.50, 43.90 to 21.45, 43.65 to 21.30, 43.60 to 21.00,
        43.70 to 20.60, 43.90 to 20.40, 44.20 to 20.35, 44.45 to 20.30
    ),
    "Istočna Srbija" to listOf(
        44.55 to 21.60, 44.55 to 21.85, 44.35 to 22.05, 44.30 to 22.40,
        44.10 to 22.50, 43.95 to 22.30, 43.75 to 22.15, 43.70 to 21.90,
        43.80 to 21.60, 44.10 to 21.55, 44.35 to 21.55
    ),
    "Jugoistočna Srbija" to listOf(
        43.40 to 21.40, 43.45 to 21.75, 43.35 to 22.10, 43.20 to 22.45,
        43.05 to 22.55, 42.85 to 22.35, 42.65 to 22.25, 42.50 to 22.05,
        42.45 to 21.85, 42.60 to 21.70, 42.85 to 21.60, 43.10 to 21.45
    )
)

private val REGION_MAP_COLORS = mapOf(
    "Vojvodina"          to Color(0xFF4A7C59),
    "Beograd"            to Color(0xFF7B4FA0),
    "Centralna Srbija"   to Color(0xFF2E6FA3),
    "Zapadna Srbija"     to Color(0xFF4D8BA6),
    "Istočna Srbija"     to Color(0xFF8B5E52),
    "Jugoistočna Srbija" to Color(0xFF5A7A4A)
)

// Standard ray-casting point-in-polygon test.
private fun pointInPolygon(lat: Double, lng: Double, poly: List<Pair<Double, Double>>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val (latI, lngI) = poly[i]
        val (latJ, lngJ) = poly[j]
        if ((lngI > lng) != (lngJ > lng) &&
            lat < (latJ - latI) * (lng - lngI) / (lngJ - lngI) + latI
        ) inside = !inside
        j = i
    }
    return inside
}

// Deterministic per-player point inside the region polygon: a uid-seeded RNG samples
// the polygon's bounding box and rejects points outside the polygon, so the same
// player always gets the same dot and the dot is always inside the region.
private fun playerGeoPoint(uid: String, region: String): GeoPoint? {
    val poly = REGION_POLYGONS[region] ?: return null
    val latMin = poly.minOf { it.first }; val latMax = poly.maxOf { it.first }
    val lngMin = poly.minOf { it.second }; val lngMax = poly.maxOf { it.second }
    val rnd = java.util.Random(uid.hashCode().toLong())
    repeat(100) {
        val lat = latMin + rnd.nextDouble() * (latMax - latMin)
        val lng = lngMin + rnd.nextDouble() * (lngMax - lngMin)
        if (pointInPolygon(lat, lng, poly)) return GeoPoint(lat, lng)
    }
    // Practically unreachable; polygon centroid is a safe interior fallback for our shapes.
    return GeoPoint(poly.map { it.first }.average(), poly.map { it.second }.average())
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
        // Region polygons first (below the dots) - the visible "map of Serbian regions".
        REGION_POLYGONS.forEach { (region, poly) ->
            val color = REGION_MAP_COLORS[region] ?: Color(0xFF2E6FA3)
            val argb = android.graphics.Color.argb(
                60,
                (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
            )
            val outline = android.graphics.Color.argb(
                180,
                (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
            )
            mapView.overlays.add(org.osmdroid.views.overlay.Polygon(mapView).apply {
                points = poly.map { (lat, lng) -> GeoPoint(lat, lng) }
                fillPaint.color = argb
                outlinePaint.color = outline
                outlinePaint.strokeWidth = 3f
                title = region
            })
        }
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
    onPlayIzazov: (String) -> Unit = {},
    vm: RegionViewModel = viewModel(),
    izazovVm: IzazovViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val izazovState by izazovVm.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Refresh balance/list when (re-)entering, e.g. coming back from the izazov partija.
    LaunchedEffect(Unit) { izazovVm.refresh() }

    // One-shot navigation into the solo partija for this izazov.
    LaunchedEffect(izazovState.playIzazovId) {
        izazovState.playIzazovId?.let { id ->
            izazovVm.consumePlayEvent()
            onPlayIzazov(id)
        }
    }

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

    // Results take over the full screen
    when (izazovState.phase) {
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
                                onViewResults = { izazovVm.viewResults(session) },
                                onFinalizeEarly = { izazovVm.finalizeEarly(session) }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Spec 5d: podium finishes across closed monthly cycles + player counts.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RegionStatChip("🥇", "${stats.firstPlaces}", "prvih")
                    RegionStatChip("🥈", "${stats.secondPlaces}", "drugih")
                    RegionStatChip("🥉", "${stats.thirdPlaces}", "trećih")
                }
                HorizontalDivider(color = LineColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RegionStatChip("🟢", "${stats.activePlayers}", "aktivnih")
                    RegionStatChip("👥", "${stats.totalPlayers}", "registrovanih")
                }
                HorizontalDivider(color = LineColor)

                if (isLoadingPlayers) {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryBlueBright)
                    }
                } else if (players.isEmpty()) {
                    Text("Nema igrača u ovom regionu.", color = MediumGray, fontSize = 13.sp)
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
private fun RegionStatChip(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(value, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = MediumGray, fontSize = 10.sp)
    }
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
