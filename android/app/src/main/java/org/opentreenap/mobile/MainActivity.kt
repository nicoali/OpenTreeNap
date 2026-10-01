package org.opentreenap.mobile

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.opentreenap.mobile.api.OtmApiClient
import org.opentreenap.mobile.model.TreeMarker
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity(), OnMapReadyCallback {
    private lateinit var rootView: View
    private lateinit var statusBarScrim: View
    private lateinit var topPanel: View
    private lateinit var filterBar: View
    private lateinit var mapView: MapView
    private lateinit var statusView: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var filterAll: TextView
    private lateinit var filterMonumental: TextView
    private lateinit var mapControls: View
    private lateinit var treeCard: View
    private lateinit var treeBadge: TextView
    private lateinit var treeTitle: TextView
    private lateinit var treeScientific: TextView
    private lateinit var treeDetails: TextView
    private lateinit var treeMeta: TextView

    private var map: GoogleMap? = null
    private var systemTopInset = 0
    private var systemBottomInset = 0
    private var allTrees: List<TreeMarker> = emptyList()
    private var monumentalOnly = false
    private var selectedMarker: Marker? = null
    private var selectedTree: TreeMarker? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var normalTreeIcon: BitmapDescriptor? = null
    private var monumentalTreeIcon: BitmapDescriptor? = null
    private var selectedTreeIcon: BitmapDescriptor? = null
    private var selectedMonumentalIcon: BitmapDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logMapsDiagnostics()
        runCatching { MapsInitializer.initialize(applicationContext) }
            .onFailure { Log.e(MAPS_LOG_TAG, "Maps init failed", it) }

        configureSystemBars()
        setContentView(R.layout.activity_main)
        rootView = findViewById(R.id.root)
        statusBarScrim = findViewById(R.id.statusBarScrim)
        topPanel = findViewById(R.id.topPanel)
        filterBar = findViewById(R.id.filterBar)
        mapView = findViewById(R.id.map)
        statusView = findViewById(R.id.status)
        refreshButton = findViewById(R.id.refresh)
        filterAll = findViewById(R.id.filterAll)
        filterMonumental = findViewById(R.id.filterMonumental)
        mapControls = findViewById(R.id.mapControls)
        treeCard = findViewById(R.id.treeCard)
        treeBadge = findViewById(R.id.treeBadge)
        treeTitle = findViewById(R.id.treeTitle)
        treeScientific = findViewById(R.id.treeScientific)
        treeDetails = findViewById(R.id.treeDetails)
        treeMeta = findViewById(R.id.treeMeta)

        applySystemInsets()
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        refreshButton.setOnClickListener { loadTrees() }
        findViewById<ImageButton>(R.id.closeTreeCard).setOnClickListener { hideTreeCard() }
        findViewById<ImageButton>(R.id.location).setOnClickListener { centerOnMyLocation() }
        findViewById<ImageButton>(R.id.layers).setOnClickListener { toggleMapType() }

        filterAll.setOnClickListener {
            monumentalOnly = false
            updateFilterUi()
            hideTreeCard()
            renderClusters()
        }
        filterMonumental.setOnClickListener {
            monumentalOnly = true
            updateFilterUi()
            hideTreeCard()
            renderClusters()
            if (allTrees.none { it.isMonumental }) {
                Toast.makeText(this, R.string.no_monumental_loaded, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.apply {
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
            isMyLocationButtonEnabled = false
        }

        runCatching {
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        }.onFailure { Log.w(MAPS_LOG_TAG, "Custom map style unavailable", it) }

        updateMapPadding()
        googleMap.setOnCameraIdleListener { renderClusters() }
        googleMap.setOnMapClickListener { hideTreeCard() }
        googleMap.setOnMarkerClickListener { marker ->
            when (val tag = marker.tag) {
                is TreeMarker -> { showTreeCard(tag, marker); true }
                is ClusterTag -> { zoomIntoCluster(tag); true }
                else -> false
            }
        }

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(40.8518, 14.2681), 12.3f)
        )
        loadTrees()
    }

    private fun loadTrees() {
        val googleMap = map ?: return
        if (!isConfigured()) {
            statusView.setText(R.string.config_missing)
            return
        }

        statusView.setText(R.string.status_loading)
        refreshButton.isEnabled = false
        refreshButton.alpha = 0.45f

        executor.execute {
            val result = runCatching {
                OtmApiClient(
                    baseUrl = BuildConfig.OTM_BASE_URL,
                    instance = BuildConfig.OTM_INSTANCE,
                    accessKey = BuildConfig.OTM_ACCESS_KEY,
                    secretKey = BuildConfig.OTM_SECRET_KEY
                ).fetchTrees()
            }

            runOnUiThread {
                refreshButton.isEnabled = true
                refreshButton.alpha = 1f
                result.onSuccess { trees ->
                    allTrees = trees
                    updateFilterUi()
                    renderClusters()
                    Log.i(MAPS_LOG_TAG, "OTM trees loaded count=${trees.size}")
                }.onFailure { error ->
                    Log.e(MAPS_LOG_TAG, "OTM API failure", error)
                    statusView.text = getString(
                        R.string.status_error,
                        error.message ?: error.javaClass.simpleName
                    )
                    googleMap.clear()
                }
            }
        }
    }

    private fun renderClusters() {
        val googleMap = map ?: return
        if (allTrees.isEmpty()) return

        val visibleTrees = if (monumentalOnly) allTrees.filter { it.isMonumental } else allTrees
        googleMap.clear()
        selectedMarker = null
        selectedTree = null

        if (visibleTrees.isEmpty()) {
            statusView.setText(R.string.status_no_results)
            return
        }

        val zoom = googleMap.cameraPosition.zoom
        val cellSize = when {
            zoom < 11f -> dp(112)
            zoom < 13f -> dp(88)
            zoom < 15f -> dp(68)
            zoom < 17f -> dp(52)
            else -> dp(34)
        }

        val groups = linkedMapOf<Long, MutableList<TreeMarker>>()
        val projection = googleMap.projection
        visibleTrees.forEach { tree ->
            val p = projection.toScreenLocation(LatLng(tree.latitude, tree.longitude))
            val cellX = p.x / cellSize
            val cellY = p.y / cellSize
            val key = (cellX.toLong() shl 32) xor (cellY.toLong() and 0xffffffffL)
            groups.getOrPut(key) { mutableListOf() }.add(tree)
        }

        groups.values.forEach { trees ->
            if (trees.size == 1) {
                val tree = trees.first()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(tree.latitude, tree.longitude))
                        .icon(treeIcon(tree))
                        .anchor(0.5f, 0.5f)
                        .zIndex(if (tree.isMonumental) 3f else 2f)
                )?.tag = tree
            } else {
                val lat = trees.sumOf { it.latitude } / trees.size
                val lon = trees.sumOf { it.longitude } / trees.size
                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(lat, lon))
                        .icon(clusterIcon(trees.size))
                        .anchor(0.5f, 0.5f)
                        .zIndex(1f)
                )?.tag = ClusterTag(trees)
            }
        }

        statusView.text = if (monumentalOnly) {
            getString(R.string.status_monumental, visibleTrees.size)
        } else {
            getString(R.string.status_loaded, allTrees.size, "Napoli")
        }
    }

    private fun showTreeCard(tree: TreeMarker, marker: Marker) {
        restoreSelectedMarker()
        selectedMarker = marker
        selectedTree = tree
        marker.setIcon(treeIcon(tree, true))
        marker.zIndex = 10f

        treeBadge.text = getString(
            if (tree.isMonumental) R.string.badge_monumental else R.string.badge_tree
        )
        treeTitle.text = tree.commonName?.takeIf { it.isNotBlank() } ?: tree.title

        val scientific = tree.scientificName.orEmpty()
        treeScientific.text = scientific
        treeScientific.visibility = if (scientific.isBlank()) View.GONE else View.VISIBLE

        treeDetails.text = tree.address?.takeIf { it.isNotBlank() }
            ?: tree.snippet?.takeIf { it.isNotBlank() }
            ?: getString(R.string.details_missing)

        treeMeta.text = buildString {
            if (tree.plotId >= 0) append("Sito #${tree.plotId}")
            tree.treeId?.let {
                if (isNotEmpty()) append("  ·  ")
                append("Albero #$it")
            }
        }

        treeCard.visibility = View.VISIBLE
        mapControls.visibility = View.GONE
        updateMapPadding()
    }

    private fun hideTreeCard() {
        restoreSelectedMarker()
        treeCard.visibility = View.GONE
        mapControls.visibility = View.VISIBLE
        updateMapPadding()
    }

    private fun restoreSelectedMarker() {
        val marker = selectedMarker
        val tree = selectedTree
        if (marker != null && tree != null) {
            runCatching {
                marker.setIcon(treeIcon(tree))
                marker.zIndex = if (tree.isMonumental) 3f else 2f
            }
        }
        selectedMarker = null
        selectedTree = null
    }

    private fun zoomIntoCluster(cluster: ClusterTag) {
        if (cluster.trees.isEmpty()) return
        val lat = cluster.trees.sumOf { it.latitude } / cluster.trees.size
        val lon = cluster.trees.sumOf { it.longitude } / cluster.trees.size
        val z = ((map?.cameraPosition?.zoom ?: 12f) + 2f).coerceAtMost(19f)
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), z))
    }

    private fun updateFilterUi() {
        filterAll.setBackgroundResource(
            if (monumentalOnly) R.drawable.chip_inactive else R.drawable.chip_active
        )
        filterAll.setTextColor(getColor(if (monumentalOnly) R.color.otn_navy else R.color.white))
        filterMonumental.setBackgroundResource(
            if (monumentalOnly) R.drawable.chip_monumental_active else R.drawable.chip_inactive
        )
        filterMonumental.setTextColor(getColor(if (monumentalOnly) R.color.white else R.color.otn_navy))
        val n = allTrees.count { it.isMonumental }
        filterMonumental.text = if (n > 0) {
            getString(R.string.filter_monumental_count, n)
        } else {
            getString(R.string.filter_monumental)
        }
    }

    private fun treeIcon(tree: TreeMarker, selected: Boolean = false): BitmapDescriptor =
        when {
            tree.isMonumental && selected ->
                selectedMonumentalIcon ?: createTreeMarker(true, true).also { selectedMonumentalIcon = it }
            tree.isMonumental ->
                monumentalTreeIcon ?: createTreeMarker(true, false).also { monumentalTreeIcon = it }
            selected ->
                selectedTreeIcon ?: createTreeMarker(false, true).also { selectedTreeIcon = it }
            else ->
                normalTreeIcon ?: createTreeMarker(false, false).also { normalTreeIcon = it }
        }

    private fun createTreeMarker(monumental: Boolean, selected: Boolean): BitmapDescriptor {
        val size = dp(if (selected) 38 else 32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        val radius = size * 0.38f
        val fill = Color.parseColor(if (monumental) "#B88A2B" else "#8BAA3D")
        val outline = Color.parseColor(if (selected) "#56ABB2" else "#246B4B")

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(45, 0, 0, 0)
        canvas.drawCircle(center + dp(1), center + dp(2), radius + dp(2), paint)
        paint.color = outline
        canvas.drawCircle(center, center, radius + dp(if (selected) 3 else 2), paint)
        paint.color = fill
        canvas.drawCircle(center, center, radius, paint)

        paint.color = Color.parseColor("#FFF9ED")
        val canopyY = center - size * 0.08f
        val crown = size * 0.10f
        canvas.drawCircle(center, canopyY - crown * 0.65f, crown, paint)
        canvas.drawCircle(center - crown * 0.75f, canopyY, crown, paint)
        canvas.drawCircle(center + crown * 0.75f, canopyY, crown, paint)
        canvas.drawRect(
            center - size * 0.025f,
            canopyY + crown * 0.55f,
            center + size * 0.025f,
            center + size * 0.19f,
            paint
        )
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clusterIcon(count: Int): BitmapDescriptor {
        val size = dp(42)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f

        paint.color = Color.argb(42, 0, 0, 0)
        canvas.drawCircle(center + dp(1), center + dp(2), size * 0.41f, paint)
        paint.color = Color.parseColor("#FFF9ED")
        canvas.drawCircle(center, center, size * 0.41f, paint)
        paint.color = Color.parseColor(
            when {
                count < 10 -> "#8BAA3D"
                count < 40 -> "#246B4B"
                else -> "#174B35"
            }
        )
        canvas.drawCircle(center, center, size * 0.34f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = when {
            count < 10 -> size * 0.35f
            count < 100 -> size * 0.30f
            else -> size * 0.25f
        }
        val y = center - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(count.toString(), center, y, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun toggleMapType() {
        val googleMap = map ?: return
        googleMap.mapType = if (googleMap.mapType == GoogleMap.MAP_TYPE_NORMAL) {
            Toast.makeText(this, R.string.map_satellite, Toast.LENGTH_SHORT).show()
            GoogleMap.MAP_TYPE_HYBRID
        } else {
            Toast.makeText(this, R.string.map_standard, Toast.LENGTH_SHORT).show()
            GoogleMap.MAP_TYPE_NORMAL
        }
    }

    private fun centerOnMyLocation() {
        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }
        enableLocationAndCenter()
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun enableLocationAndCenter() {
        val googleMap = map ?: return
        googleMap.isMyLocationEnabled = true
        var centered = false
        googleMap.setOnMyLocationChangeListener { location ->
            if (!centered) {
                centered = true
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        16f
                    )
                )
                googleMap.setOnMyLocationChangeListener(null)
            }
        }
        Toast.makeText(this, R.string.location_searching, Toast.LENGTH_SHORT).show()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (hasLocationPermission()) enableLocationAndCenter()
            else Toast.makeText(this, R.string.location_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureSystemBars() {
        window.statusBarColor = getColor(R.color.otn_green_dark)
        window.navigationBarColor = getColor(R.color.otn_cream)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun applySystemInsets() {
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                systemTopInset = bars.top
                systemBottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                systemTopInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                systemBottomInset = insets.systemWindowInsetBottom
            }

            statusBarScrim.layoutParams = statusBarScrim.layoutParams.also { params ->
                params.height = systemTopInset
            }

            (topPanel.layoutParams as FrameLayout.LayoutParams).also { params ->
                params.topMargin = systemTopInset
                topPanel.layoutParams = params
            }

            (filterBar.layoutParams as FrameLayout.LayoutParams).also { params ->
                params.topMargin = systemTopInset + dp(64)
                filterBar.layoutParams = params
            }

            (mapControls.layoutParams as FrameLayout.LayoutParams).also { params ->
                params.bottomMargin = systemBottomInset + dp(18)
                mapControls.layoutParams = params
            }

            (treeCard.layoutParams as FrameLayout.LayoutParams).also { params ->
                params.bottomMargin = systemBottomInset + dp(10)
                treeCard.layoutParams = params
            }

            updateMapPadding()
            insets
        }
        rootView.requestApplyInsets()
    }

    private fun updateMapPadding() {
        val topPadding = systemTopInset + dp(108)
        val bottomPadding =
            if (::treeCard.isInitialized && treeCard.visibility == View.VISIBLE) {
                systemBottomInset + dp(220)
            } else {
                systemBottomInset + dp(24)
            }

        map?.setPadding(0, topPadding, 0, bottomPadding)
    }

    private fun logMapsDiagnostics() {
        val mapsKey = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            appInfo.metaData?.getString(MAPS_API_KEY_METADATA)
        }.getOrNull()
        Log.i(
            MAPS_LOG_TAG,
            "Maps key present=${!mapsKey.isNullOrBlank()} length=${mapsKey?.length ?: 0}"
        )
    }

    private fun isConfigured(): Boolean =
        BuildConfig.OTM_BASE_URL.startsWith("https://") &&
            BuildConfig.OTM_ACCESS_KEY.isNotBlank() &&
            BuildConfig.OTM_SECRET_KEY.isNotBlank() &&
            BuildConfig.OTM_INSTANCE.isNotBlank()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { executor.shutdownNow(); mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }

    private data class ClusterTag(val trees: List<TreeMarker>)

    companion object {
        private const val MAPS_LOG_TAG = "OTN-MAPS"
        private const val MAPS_API_KEY_METADATA = "com.google.android.geo.API_KEY"
        private const val LOCATION_PERMISSION_REQUEST = 210
    }
}
