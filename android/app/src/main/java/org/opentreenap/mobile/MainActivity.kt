package org.opentreenap.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import coil.load
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.opentreenap.mobile.api.OtmApiClient
import org.opentreenap.mobile.data.BotanicalImageRepository
import org.opentreenap.mobile.data.TreeCache
import org.opentreenap.mobile.map.OtnClusterRenderer
import org.opentreenap.mobile.map.TreeClusterItem
import org.opentreenap.mobile.model.ApiUser
import org.opentreenap.mobile.model.InstancePermissions
import org.opentreenap.mobile.model.SpeciesItem
import org.opentreenap.mobile.model.TreeMarker
import java.text.Normalizer
import java.util.Locale
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
    private lateinit var accountButton: ImageButton
    private lateinit var addTreeButton: ImageButton
    private lateinit var filterAll: TextView
    private lateinit var filterMonumental: TextView
    private lateinit var mapControls: View
    private lateinit var treeCard: View
    private lateinit var treeSheetBehavior: BottomSheetBehavior<View>
    private lateinit var treeBadge: TextView
    private lateinit var treeTitle: TextView
    private lateinit var treeScientific: TextView
    private lateinit var treeDetails: TextView
    private lateinit var treeImageContainer: View
    private lateinit var treeHeroImage: ImageView
    private lateinit var treeHeroPlaceholder: ImageView
    private lateinit var treeImageSource: TextView
    private lateinit var treeMeta: TextView
    private lateinit var treeStats: View
    private lateinit var treeCustomId: TextView
    private lateinit var treeDbh: TextView
    private lateinit var treeHeight: TextView
    private lateinit var botanicalCardButton: Button
    private lateinit var editTreeButton: Button

    private var map: GoogleMap? = null
    private var clusterManager: ClusterManager<TreeClusterItem>? = null
    private var clusterRenderer: OtnClusterRenderer? = null
    private lateinit var treeCache: TreeCache
    private lateinit var botanicalImages: BotanicalImageRepository
    private var systemTopInset = 0
    private var systemBottomInset = 0
    private var allTrees: List<TreeMarker> = emptyList()
    private var speciesCache: List<SpeciesItem> = emptyList()
    private var monumentalOnly = false
    private var addingTree = false
    private var selectedMarker: Marker? = null
    private var selectedTree: TreeMarker? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val assetExecutor = Executors.newSingleThreadExecutor()

    private var sessionUser: ApiUser? = null
    private var sessionPermissions: InstancePermissions? = null
    private var sessionUsername: String? = null
    private var sessionPassword: String? = null

    private var normalTreeIcon: BitmapDescriptor? = null
    private var monumentalTreeIcon: BitmapDescriptor? = null
    private var selectedTreeIcon: BitmapDescriptor? = null
    private var selectedMonumentalIcon: BitmapDescriptor? = null
    private val clusterIconCache = mutableMapOf<String, BitmapDescriptor>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logMapsDiagnostics()
        runCatching { MapsInitializer.initialize(applicationContext) }
            .onFailure { Log.e(MAPS_LOG_TAG, "Maps init failed", it) }

        configureSystemBars()
        setContentView(R.layout.activity_main)
        treeCache = TreeCache(applicationContext)
        botanicalImages =
            BotanicalImageRepository(
                applicationContext,
                BuildConfig.OTM_BASE_URL
            )

        rootView = findViewById(R.id.root)
        statusBarScrim = findViewById(R.id.statusBarScrim)
        topPanel = findViewById(R.id.topPanel)
        filterBar = findViewById(R.id.filterBar)
        mapView = findViewById(R.id.map)
        statusView = findViewById(R.id.status)
        refreshButton = findViewById(R.id.refresh)
        accountButton = findViewById(R.id.account)
        addTreeButton = findViewById(R.id.addTree)
        filterAll = findViewById(R.id.filterAll)
        filterMonumental = findViewById(R.id.filterMonumental)
        mapControls = findViewById(R.id.mapControls)
        treeCard = findViewById(R.id.treeCard)
        treeBadge = findViewById(R.id.treeBadge)
        treeTitle = findViewById(R.id.treeTitle)
        treeScientific = findViewById(R.id.treeScientific)
        treeDetails = findViewById(R.id.treeDetails)
        treeImageContainer = findViewById(R.id.treeImageContainer)
        treeHeroImage = findViewById(R.id.treeHeroImage)
        treeHeroPlaceholder = findViewById(R.id.treeHeroPlaceholder)
        treeImageSource = findViewById(R.id.treeImageSource)
        treeMeta = findViewById(R.id.treeMeta)
        treeStats = findViewById(R.id.treeStats)
        treeCustomId = findViewById(R.id.treeCustomId)
        treeDbh = findViewById(R.id.treeDbh)
        treeHeight = findViewById(R.id.treeHeight)
        botanicalCardButton = findViewById(R.id.botanicalCard)
        editTreeButton = findViewById(R.id.editTree)

        setupTreeSheet()
        applySystemInsets()
        updateSessionUi()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        refreshButton.setOnClickListener { loadTrees() }
        accountButton.setOnClickListener { showAccountDialog() }
        addTreeButton.setOnClickListener {
            if (addingTree) {
                cancelAddMode()
            } else {
                requireLoginThen {
                    if (sessionPermissions?.canAddTree == true) {
                        enterAddMode()
                    } else {
                        showOtnMessage(
                            getString(R.string.permission_add_denied),
                            isError = true
                        )
                    }
                }
            }
        }

        findViewById<ImageButton>(R.id.closeTreeCard).setOnClickListener {
            hideTreeCard()
        }
        findViewById<ImageButton>(R.id.location).setOnClickListener {
            centerOnMyLocation()
        }
        findViewById<ImageButton>(R.id.layers).setOnClickListener {
            toggleMapType()
        }

        botanicalCardButton.setOnClickListener {
            selectedTree?.let { openBotanicalCard(it) }
        }

        editTreeButton.setOnClickListener {
            val tree = selectedTree ?: return@setOnClickListener
            requireLoginThen {
                openTreeEditor(
                    tree = tree,
                    point = LatLng(tree.latitude, tree.longitude)
                )
            }
        }

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
                Toast.makeText(
                    this,
                    R.string.no_monumental_loaded,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        refreshBotanicalManifest()
    }

    private fun setupTreeSheet() {
        treeSheetBehavior =
            BottomSheetBehavior.from(treeCard).apply {
                isHideable = true
                skipCollapsed = true
                peekHeight = 0
                state = BottomSheetBehavior.STATE_HIDDEN
            }

        treeSheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(
                    bottomSheet: View,
                    newState: Int
                ) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            restoreSelectedMarker()
                            mapControls.visibility = View.VISIBLE
                            updateSessionUi()
                            updateMapPadding()
                        }

                        BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                            mapControls.visibility = View.GONE
                            updateMapPadding()
                        }
                    }
                }

                override fun onSlide(
                    bottomSheet: View,
                    slideOffset: Float
                ) = Unit
            }
        )
    }

    private fun isTreeSheetVisible(): Boolean =
        ::treeSheetBehavior.isInitialized &&
            treeSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN

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
            googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
        }.onFailure {
            Log.w(MAPS_LOG_TAG, "Custom map style unavailable", it)
        }

        setupClusterManager(googleMap)
        updateMapPadding()

        googleMap.setOnMapClickListener { point ->
            if (addingTree) {
                addingTree = false
                updateAddButtonUi()
                openTreeEditor(tree = null, point = point)
            } else {
                hideTreeCard()
            }
        }

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(40.8518, 14.2681),
                12.3f
            )
        )
        loadCachedTreesThenRefresh()
    }

    private fun setupClusterManager(
        googleMap: GoogleMap
    ) {
        val manager =
            ClusterManager<TreeClusterItem>(
                this,
                googleMap
            )

        val renderer =
            OtnClusterRenderer(
                context = this,
                map = googleMap,
                clusterManager = manager,
                treeIconProvider = { tree ->
                    treeIcon(tree)
                },
                clusterIconProvider = { count, hasMonumental ->
                    clusterIcon(
                        count = count,
                        hasMonumental = hasMonumental
                    )
                }
            )

        manager.renderer = renderer

        manager.setOnClusterItemClickListener { item ->
            if (addingTree) {
                false
            } else {
                val marker =
                    renderer.getMarker(item)

                if (marker != null) {
                    showTreeCard(item.tree, marker)
                } else {
                    showTreeCardContent(item.tree)
                }

                loadTreeDetails(item.tree)
                true
            }
        }

        manager.setOnClusterClickListener { cluster ->
            val bounds = LatLngBounds.builder()
            cluster.items.forEach {
                bounds.include(it.getPosition())
            }

            runCatching {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds.build(),
                        dp(64)
                    )
                )
            }.onFailure {
                val first = cluster.items.firstOrNull()
                if (first != null) {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            first.getPosition(),
                            (
                                googleMap.cameraPosition.zoom +
                                    2f
                                ).coerceAtMost(19f)
                        )
                    )
                }
            }
            true
        }

        googleMap.setOnCameraIdleListener(manager)
        googleMap.setOnMarkerClickListener(manager)

        clusterManager = manager
        clusterRenderer = renderer
    }

    private fun loadCachedTreesThenRefresh() {
        executor.execute {
            val snapshot = treeCache.load()

            runOnUiThread {
                if (
                    snapshot != null &&
                    snapshot.trees.isNotEmpty()
                ) {
                    allTrees = snapshot.trees
                    updateFilterUi()
                    renderClusters()
                    statusView.text =
                        getString(
                            R.string.status_cached,
                            allTrees.size
                        )
                }

                loadTrees()
            }
        }
    }

    private fun apiClient(): OtmApiClient =
        OtmApiClient(
            baseUrl = BuildConfig.OTM_BASE_URL,
            instance = BuildConfig.OTM_INSTANCE,
            accessKey = BuildConfig.OTM_ACCESS_KEY,
            secretKey = BuildConfig.OTM_SECRET_KEY
        )

    private fun loadTrees() {
        if (map == null) return

        if (!isConfigured()) {
            statusView.setText(R.string.config_missing)
            return
        }

        val hadData = allTrees.isNotEmpty()

        statusView.setText(
            if (hadData) {
                R.string.status_refreshing
            } else {
                R.string.status_loading
            }
        )

        refreshButton.isEnabled = false
        refreshButton.alpha = 0.45f

        executor.execute {
            val result =
                runCatching {
                    apiClient().fetchAllTrees(
                        pageSize = 1000
                    ) { partial ->
                        if (!hadData) {
                            runOnUiThread {
                                allTrees = partial
                                updateFilterUi()
                                renderClusters()
                                statusView.text =
                                    getString(
                                        R.string.status_loading_count,
                                        partial.size
                                    )
                            }
                        }
                    }
                }

            result.onSuccess { trees ->
                treeCache.save(trees)
            }

            runOnUiThread {
                refreshButton.isEnabled = true
                refreshButton.alpha = 1f

                result.onSuccess { trees ->
                    allTrees = trees
                    updateFilterUi()
                    renderClusters()

                    Log.i(
                        MAPS_LOG_TAG,
                        "OTM trees loaded count=${trees.size}"
                    )
                }.onFailure { error ->
                    Log.e(
                        MAPS_LOG_TAG,
                        "OTM API failure",
                        error
                    )

                    if (allTrees.isNotEmpty()) {
                        restoreMapStatus()
                        Toast.makeText(
                            this,
                            getString(
                                R.string.refresh_failed_cached
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val fullMessage =
                            error.message
                                ?: error.javaClass.simpleName

                        statusView.text =
                            getString(
                                R.string.status_error_short
                            )

                        showMessageSheet(
                            title = getString(R.string.api_error_title),
                            message = fullMessage
                        )
                    }
                }
            }
        }
    }

    private fun renderClusters() {
        val manager = clusterManager ?: return

        val visibleTrees =
            if (monumentalOnly) {
                allTrees.filter { it.isMonumental }
            } else {
                allTrees
            }

        restoreSelectedMarker()
        manager.clearItems()

        if (visibleTrees.isEmpty()) {
            manager.cluster()
            statusView.setText(R.string.status_no_results)
            return
        }

        manager.addItems(
            visibleTrees.map {
                TreeClusterItem(it)
            }
        )
        manager.cluster()

        restoreMapStatus()
    }

    private fun showTreeCard(
        tree: TreeMarker,
        marker: Marker
    ) {
        restoreSelectedMarker()

        selectedMarker = marker
        selectedTree = tree

        marker.setIcon(
            treeIcon(
                tree,
                selected = true
            )
        )
        marker.zIndex = 10f

        showTreeCardContent(tree)
    }

    private fun showTreeCardContent(
        tree: TreeMarker
    ) {
        selectedTree = tree

        treeBadge.text =
            getString(
                if (tree.isMonumental) {
                    R.string.badge_monumental
                } else {
                    R.string.badge_tree
                }
            )

        treeBadge.setBackgroundResource(
            if (tree.isMonumental) {
                R.drawable.chip_monumental_active
            } else {
                R.drawable.chip_active
            }
        )

        treeTitle.text =
            tree.commonName
                ?.takeIf { it.isNotBlank() }
                ?: tree.title

        val scientific =
            tree.scientificName.orEmpty()

        treeScientific.text = scientific
        treeScientific.visibility =
            if (scientific.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        val address =
            tree.address?.takeIf { it.isNotBlank() }

        treeDetails.text =
            address ?: getString(R.string.details_missing)
        treeDetails.visibility =
            if (address != null) View.VISIBLE else View.GONE

        bindTreeImage(tree)
        bindTreeStats(tree)
        treeMeta.text = buildTreeMeta(tree)

        botanicalCardButton.isEnabled =
            scientific.isNotBlank()
        botanicalCardButton.alpha =
            if (scientific.isNotBlank()) 1f else 0.45f

        editTreeButton.visibility =
            if (
                sessionUser != null &&
                sessionPermissions?.canEditTree == true
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        mapControls.visibility = View.GONE
        treeSheetBehavior.state =
            BottomSheetBehavior.STATE_EXPANDED
        treeCard.post { updateMapPadding() }
    }

    private fun loadTreeDetails(
        tree: TreeMarker
    ) {
        if (tree.plotId < 0) return

        executor.execute {
            val result =
                runCatching {
                    apiClient().fetchPlot(tree.plotId)
                }

            result.onSuccess { detailed ->
                val updatedTrees =
                    allTrees.map {
                        if (it.plotId == detailed.plotId) {
                            detailed
                        } else {
                            it
                        }
                    }

                treeCache.save(updatedTrees)

                runOnUiThread {
                    allTrees = updatedTrees

                    if (
                        selectedTree?.plotId ==
                        detailed.plotId
                    ) {
                        showTreeCardContent(detailed)
                    }
                }
            }
        }
    }

    private fun refreshBotanicalManifest() {
        assetExecutor.execute {
            val result =
                runCatching {
                    botanicalImages.refresh()
                }

            if (result.isSuccess) {
                runOnUiThread {
                    selectedTree?.let {
                        bindTreeImage(it)
                    }
                }
            }
        }
    }

    private fun bindTreeImage(
        tree: TreeMarker
    ) {
        val realPhoto =
            tree.photoUrl
                ?.takeIf { it.isNotBlank() }

        val botanicalPhoto =
            botanicalImages.imageUrl(
                tree.scientificName
            )

        val imageUrl =
            realPhoto ?: botanicalPhoto

        treeHeroImage.setImageDrawable(null)
        treeHeroPlaceholder.visibility = View.VISIBLE
        treeImageSource.visibility = View.GONE

        if (imageUrl == null) {
            treeImageContainer.visibility = View.VISIBLE
            return
        }

        treeImageContainer.visibility = View.VISIBLE
        treeImageSource.text =
            getString(
                if (realPhoto != null) {
                    R.string.tree_image_real
                } else {
                    R.string.tree_image_species
                }
            )
        treeImageSource.visibility = View.VISIBLE

        treeHeroImage.load(imageUrl) {
            crossfade(true)
            listener(
                onSuccess = { _, _ ->
                    treeHeroPlaceholder.visibility = View.GONE
                },
                onError = { _, _ ->
                    treeHeroPlaceholder.visibility = View.VISIBLE
                    treeImageSource.visibility = View.GONE
                }
            )
        }
    }

    private fun bindTreeStats(
        tree: TreeMarker
    ) {
        val customId =
            tree.customId?.takeIf { it.isNotBlank() }

        treeCustomId.visibility =
            if (customId != null) View.VISIBLE else View.GONE
        treeCustomId.text =
            customId?.let { "ID $it" }.orEmpty()

        treeDbh.visibility =
            if (tree.diameter != null) View.VISIBLE else View.GONE
        treeDbh.text =
            tree.diameter
                ?.let { "DBH ${formatNumber(it)} cm" }
                .orEmpty()

        treeHeight.visibility =
            if (tree.height != null) View.VISIBLE else View.GONE
        treeHeight.text =
            tree.height
                ?.let { "H ${formatNumber(it)} m" }
                .orEmpty()

        treeStats.visibility =
            if (
                customId != null ||
                tree.diameter != null ||
                tree.height != null
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun buildTreeMeta(tree: TreeMarker): String {
        val parts = mutableListOf<String>()

        if (tree.plotId >= 0) {
            parts += "Sito #${tree.plotId}"
        }

        tree.treeId?.let {
            parts += "Albero #$it"
        }

        return parts.joinToString("  ·  ")
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.ITALY, "%.1f", value)
        }

    private fun hideTreeCard() {
        restoreSelectedMarker()

        if (::treeSheetBehavior.isInitialized) {
            treeSheetBehavior.state =
                BottomSheetBehavior.STATE_HIDDEN
        }

        mapControls.visibility = View.VISIBLE
        updateSessionUi()
        updateMapPadding()
    }

    private fun restoreSelectedMarker() {
        val marker = selectedMarker
        val tree = selectedTree

        if (marker != null && tree != null) {
            runCatching {
                marker.setIcon(treeIcon(tree))
                marker.zIndex =
                    if (tree.isMonumental) 3f else 2f
            }
        }

        selectedMarker = null
        selectedTree = null
    }

    private fun updateFilterUi() {
        filterAll.setBackgroundResource(
            if (monumentalOnly) {
                R.drawable.chip_inactive
            } else {
                R.drawable.chip_active
            }
        )
        filterAll.setTextColor(
            getColor(
                if (monumentalOnly) {
                    R.color.otn_navy
                } else {
                    R.color.white
                }
            )
        )

        filterMonumental.setBackgroundResource(
            if (monumentalOnly) {
                R.drawable.chip_monumental_active
            } else {
                R.drawable.chip_inactive
            }
        )
        filterMonumental.setTextColor(
            getColor(
                if (monumentalOnly) {
                    R.color.white
                } else {
                    R.color.otn_navy
                }
            )
        )

        val monumentalCount =
            allTrees.count { it.isMonumental }

        filterMonumental.text =
            if (monumentalCount > 0) {
                getString(
                    R.string.filter_monumental_count,
                    monumentalCount
                )
            } else {
                getString(R.string.filter_monumental)
            }
    }

    private fun showAccountDialog() {
        val user = sessionUser

        if (user == null) {
            showLoginDialog()
            return
        }

        val displayName =
            listOfNotNull(
                user.firstName?.takeIf { it.isNotBlank() },
                user.lastName?.takeIf { it.isNotBlank() }
            ).joinToString(" ")
                .ifBlank { user.username }

        val content =
            layoutInflater.inflate(
                R.layout.sheet_account,
                null
            )

        content.findViewById<TextView>(
            R.id.accountDisplayName
        ).text = displayName

        content.findViewById<TextView>(
            R.id.accountUsername
        ).text = "@${user.username}"

        content.findViewById<TextView>(
            R.id.accountEmail
        ).text =
            user.email?.takeIf { it.isNotBlank() }
                ?: getString(R.string.account_email_missing)

        val permissionLabels =
            buildList {
                val perms = sessionPermissions

                if (perms?.canAddTree == true) {
                    add(getString(R.string.account_permission_add))
                }

                if (perms?.canEditTree == true) {
                    add(getString(R.string.account_permission_edit))
                }

                if (perms?.canEditTreePhoto == true) {
                    add(getString(R.string.account_permission_photo))
                }
            }

        content.findViewById<TextView>(
            R.id.accountPermissions
        ).text =
            permissionLabels
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  ·  ")
                ?: getString(R.string.account_permissions_view)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(content)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state =
                BottomSheetBehavior.STATE_EXPANDED
        }

        content.findViewById<MaterialButton>(
            R.id.accountClose
        ).setOnClickListener {
            dialog.dismiss()
        }

        content.findViewById<MaterialButton>(
            R.id.accountLogout
        ).setOnClickListener {
            sessionUser = null
            sessionPermissions = null
            sessionUsername = null
            sessionPassword = null
            updateSessionUi()
            restoreMapStatus()
            dialog.dismiss()
            showOtnMessage(getString(R.string.logout))
        }

        dialog.show()
    }

    private fun showLoginDialog(
        onSuccess: (() -> Unit)? = null
    ) {
        val content =
            layoutInflater.inflate(
                R.layout.sheet_login,
                null
            )

        val usernameLayout =
            content.findViewById<TextInputLayout>(
                R.id.loginUsernameLayout
            )
        val passwordLayout =
            content.findViewById<TextInputLayout>(
                R.id.loginPasswordLayout
            )
        val usernameInput =
            content.findViewById<TextInputEditText>(
                R.id.loginUsername
            )
        val passwordInput =
            content.findViewById<TextInputEditText>(
                R.id.loginPassword
            )
        val errorView =
            content.findViewById<TextView>(
                R.id.loginError
            )
        val submit =
            content.findViewById<MaterialButton>(
                R.id.loginSubmit
            )
        val cancel =
            content.findViewById<MaterialButton>(
                R.id.loginCancel
            )

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(content)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state =
                BottomSheetBehavior.STATE_EXPANDED
        }

        cancel.setOnClickListener {
            dialog.dismiss()
            restoreMapStatus()
        }

        fun attemptLogin() {
            val username =
                usernameInput.text?.toString()?.trim().orEmpty()
            val password =
                passwordInput.text?.toString().orEmpty()

            usernameLayout.error = null
            passwordLayout.error = null
            errorView.visibility = View.GONE

            if (username.isBlank() || password.isBlank()) {
                if (username.isBlank()) {
                    usernameLayout.error =
                        getString(R.string.username)
                }

                if (password.isBlank()) {
                    passwordLayout.error =
                        getString(R.string.password)
                }

                errorView.text =
                    getString(R.string.login_required_fields)
                errorView.visibility = View.VISIBLE
                return
            }

            submit.isEnabled = false
            submit.text = getString(R.string.login_in_progress)
            statusView.setText(R.string.status_loading)

            executor.execute {
                val result =
                    runCatching {
                        val client = apiClient()
                        val user =
                            client.login(
                                username,
                                password
                            )
                        val permissions =
                            client.fetchInstancePermissions(
                                username,
                                password
                            )

                        user to permissions
                    }

                runOnUiThread {
                    submit.isEnabled = true
                    submit.text = getString(R.string.login)

                    result.onSuccess { loginResult ->
                        val user = loginResult.first
                        val permissions = loginResult.second

                        sessionUser = user
                        sessionPermissions = permissions
                        sessionUsername = username
                        sessionPassword = password
                        updateSessionUi()

                        restoreMapStatus()

                        dialog.dismiss()
                        showOtnMessage(
                            getString(R.string.login_success)
                        )
                        onSuccess?.invoke()
                    }.onFailure { error ->
                        errorView.text =
                            getString(
                                R.string.login_failed,
                                error.message
                                    ?: error.javaClass.simpleName
                            )
                        errorView.visibility = View.VISIBLE
                        restoreMapStatus()
                    }
                }
            }
        }

        submit.setOnClickListener {
            attemptLogin()
        }

        dialog.show()
    }

    private fun requireLoginThen(
        action: () -> Unit
    ) {
        if (
            sessionUser != null &&
            sessionUsername != null &&
            sessionPassword != null
        ) {
            action()
        } else {
            showLoginDialog(action)
        }
    }

    private fun updateSessionUi() {
        val loggedIn = sessionUser != null
        val canAdd = sessionPermissions?.canAddTree == true

        accountButton.imageTintList =
            ColorStateList.valueOf(
                getColor(
                    if (loggedIn) {
                        R.color.otn_gold
                    } else {
                        R.color.white
                    }
                )
            )

        addTreeButton.alpha =
            if (!loggedIn || canAdd) 1f else 0.45f

        if (::editTreeButton.isInitialized) {
            editTreeButton.visibility =
                if (
                    loggedIn &&
                    sessionPermissions?.canEditTree == true &&
                    isTreeSheetVisible()
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
    }

    private fun enterAddMode() {
        hideTreeCard()
        addingTree = true
        updateAddButtonUi()
        statusView.setText(R.string.add_tree_map_step)

        showOtnMessage(
            getString(R.string.add_tree_map_step)
        )
    }

    private fun cancelAddMode() {
        addingTree = false
        updateAddButtonUi()
        restoreMapStatus()
    }

    private fun updateAddButtonUi() {
        addTreeButton.imageTintList =
            ColorStateList.valueOf(
                getColor(
                    if (addingTree) {
                        R.color.otn_gold
                    } else {
                        R.color.otn_green
                    }
                )
            )
    }

    private fun ensureSpecies(
        onReady: (List<SpeciesItem>) -> Unit
    ) {
        if (speciesCache.isNotEmpty()) {
            onReady(speciesCache)
            return
        }

        statusView.setText(R.string.loading_species)

        executor.execute {
            val result =
                runCatching {
                    apiClient().fetchSpecies()
                }

            runOnUiThread {
                result.onSuccess { species ->
                    speciesCache = species
                    restoreMapStatus()
                    onReady(species)
                }.onFailure { error ->
                    restoreMapStatus()
                    showOtnMessage(
                        error.message
                            ?: error.javaClass.simpleName,
                        isError = true
                    )
                }
            }
        }
    }

    private fun openTreeEditor(
        tree: TreeMarker?,
        point: LatLng
    ) {
        ensureSpecies { species ->
            showTreeEditorDialog(
                tree = tree,
                point = point,
                species = species
            )
        }
    }

    private fun showTreeEditorDialog(
        tree: TreeMarker?,
        point: LatLng,
        species: List<SpeciesItem>
    ) {
        var selectedSpecies =
            tree?.speciesId?.let { id ->
                species.firstOrNull { it.id == id }
            }

        val content =
            layoutInflater.inflate(
                R.layout.sheet_tree_editor,
                null
            )

        val eyebrow =
            content.findViewById<TextView>(
                R.id.editorEyebrow
            )
        val title =
            content.findViewById<TextView>(
                R.id.editorTitle
            )
        val position =
            content.findViewById<TextView>(
                R.id.editorPosition
            )
        val speciesLayout =
            content.findViewById<TextInputLayout>(
                R.id.editorSpeciesLayout
            )
        val speciesInput =
            content.findViewById<AutoCompleteTextView>(
                R.id.editorSpecies
            )
        val speciesScientific =
            content.findViewById<TextView>(
                R.id.editorSpeciesScientific
            )
        val diameterInput =
            content.findViewById<TextInputEditText>(
                R.id.editorDiameter
            )
        val heightInput =
            content.findViewById<TextInputEditText>(
                R.id.editorHeight
            )
        val errorView =
            content.findViewById<TextView>(
                R.id.editorError
            )
        val cancel =
            content.findViewById<MaterialButton>(
                R.id.editorCancel
            )
        val save =
            content.findViewById<MaterialButton>(
                R.id.editorSave
            )

        eyebrow.text =
            getString(
                if (tree == null) {
                    R.string.add_tree_step
                } else {
                    R.string.edit_tree_step
                }
            )

        title.text =
            getString(
                if (tree == null) {
                    R.string.add_tree_complete_title
                } else {
                    R.string.edit_tree_title
                }
            )

        position.text =
            getString(
                R.string.selected_position,
                point.latitude,
                point.longitude
            )

        tree?.diameter?.let {
            diameterInput.setText(formatNumber(it))
        }

        tree?.height?.let {
            heightInput.setText(formatNumber(it))
        }

        val labels = species.map { it.value }

        speciesInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )
        speciesInput.threshold = 0

        fun bindSelectedSpecies(
            selected: SpeciesItem?
        ) {
            if (selected == null) {
                speciesScientific.visibility = View.GONE
                speciesScientific.text = ""
                return
            }

            val primary =
                selected.commonName
                    .takeIf { it.isNotBlank() }
                    ?: selected.scientificName
                        .takeIf { it.isNotBlank() }
                    ?: selected.value

            speciesInput.setText(primary, false)

            val scientific =
                selected.scientificName
                    .takeIf {
                        it.isNotBlank() &&
                            !it.equals(
                                primary,
                                ignoreCase = true
                            )
                    }

            speciesScientific.text = scientific.orEmpty()
            speciesScientific.visibility =
                if (scientific != null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        bindSelectedSpecies(selectedSpecies)

        speciesInput.setOnItemClickListener {
                parent,
                _,
                itemPosition,
                _ ->
            val label =
                parent.getItemAtPosition(itemPosition)
                    ?.toString()
                    .orEmpty()

            selectedSpecies =
                species.firstOrNull {
                    it.value == label
                }

            bindSelectedSpecies(selectedSpecies)
            speciesLayout.error = null
        }

        speciesInput.setOnClickListener {
            speciesInput.showDropDown()
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(content)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state =
                BottomSheetBehavior.STATE_EXPANDED
        }

        cancel.setOnClickListener {
            dialog.dismiss()
            restoreMapStatus()
        }

        save.setOnClickListener {
            val typedSpecies =
                speciesInput.text?.toString()?.trim().orEmpty()

            val selected =
                selectedSpecies
                    ?: species.firstOrNull {
                        it.value.equals(
                            typedSpecies,
                            ignoreCase = true
                        ) ||
                            it.commonName.equals(
                                typedSpecies,
                                ignoreCase = true
                            ) ||
                            it.scientificName.equals(
                                typedSpecies,
                                ignoreCase = true
                            )
                    }

            speciesLayout.error = null
            errorView.visibility = View.GONE

            if (selected == null) {
                speciesLayout.error =
                    getString(R.string.species_required)
                return@setOnClickListener
            }

            val username = sessionUsername
            val password = sessionPassword

            if (
                username == null ||
                password == null
            ) {
                dialog.dismiss()
                showLoginDialog {
                    openTreeEditor(tree, point)
                }
                return@setOnClickListener
            }

            val diameter =
                parseDecimal(
                    diameterInput.text?.toString().orEmpty()
                )
            val height =
                parseDecimal(
                    heightInput.text?.toString().orEmpty()
                )

            save.isEnabled = false
            save.text = getString(R.string.save_in_progress)
            statusView.setText(R.string.status_saving)

            executor.execute {
                val result =
                    runCatching {
                        if (tree == null) {
                            apiClient().createTree(
                                latitude = point.latitude,
                                longitude = point.longitude,
                                speciesId = selected.id,
                                diameter = diameter,
                                height = height,
                                username = username,
                                password = password
                            )
                        } else {
                            apiClient().updateTree(
                                plotId = tree.plotId,
                                speciesId = selected.id,
                                diameter = diameter,
                                height = height,
                                username = username,
                                password = password
                            )
                        }
                    }

                runOnUiThread {
                    save.isEnabled = true
                    save.text = getString(R.string.save)

                    result.onSuccess { saved ->
                        dialog.dismiss()

                        showOtnMessage(
                            getString(R.string.saved_successfully)
                        )

                        map?.animateCamera(
                            CameraUpdateFactory
                                .newLatLngZoom(
                                    LatLng(
                                        saved.latitude,
                                        saved.longitude
                                    ),
                                    17f
                                )
                        )

                        loadTrees()
                    }.onFailure { error ->
                        errorView.text =
                            getString(
                                R.string.tree_save_failed,
                                error.message
                                    ?: error.javaClass.simpleName
                            )
                        errorView.visibility = View.VISIBLE
                        restoreMapStatus()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun parseDecimal(
        raw: String
    ): Double? =
        raw.trim()
            .replace(',', '.')
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()

    private fun openBotanicalCard(
        tree: TreeMarker
    ) {
        val scientificName =
            tree.scientificName
                ?.takeIf { it.isNotBlank() }

        if (scientificName == null) {
            showOtnMessage(
                getString(R.string.botanical_card_missing),
                isError = true
            )
            return
        }

        val slug = slugify(scientificName)
        val url =
            botanicalImages.pageUrl(scientificName)
                ?: "$BOTANICAL_BASE_URL/specie/$slug/"

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }

    private fun showOtnMessage(
        message: String,
        isError: Boolean = false
    ) {
        if (!::rootView.isInitialized) return

        Snackbar.make(
            rootView,
            message,
            Snackbar.LENGTH_LONG
        )
            .setBackgroundTint(
                getColor(
                    if (isError) {
                        R.color.otn_error
                    } else {
                        R.color.otn_green_dark
                    }
                )
            )
            .setTextColor(Color.WHITE)
            .show()
    }

    private fun showMessageSheet(
        title: String,
        message: String
    ) {
        val content =
            layoutInflater.inflate(
                R.layout.sheet_message,
                null
            )

        content.findViewById<TextView>(
            R.id.messageTitle
        ).text = title

        content.findViewById<TextView>(
            R.id.messageBody
        ).text = message

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(content)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state =
                BottomSheetBehavior.STATE_EXPANDED
        }

        content.findViewById<MaterialButton>(
            R.id.messageClose
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun slugify(value: String): String {
        val normalized =
            Normalizer.normalize(
                value,
                Normalizer.Form.NFD
            )
                .replace(
                    "\\p{M}+".toRegex(),
                    ""
                )

        return normalized
            .lowercase(Locale.ROOT)
            .replace(
                "[^a-z0-9]+".toRegex(),
                "-"
            )
            .trim('-')
    }

    private fun restoreMapStatus() {
        if (allTrees.isEmpty()) {
            statusView.setText(R.string.status_ready)
            return
        }

        val username =
            sessionUser?.username
                ?.takeIf { it.isNotBlank() }

        if (monumentalOnly) {
            val count =
                allTrees.count { it.isMonumental }

            statusView.text =
                if (username != null) {
                    getString(
                        R.string.status_monumental_user,
                        count,
                        username
                    )
                } else {
                    getString(
                        R.string.status_monumental,
                        count
                    )
                }
        } else {
            statusView.text =
                if (username != null) {
                    getString(
                        R.string.status_loaded_user,
                        allTrees.size,
                        "Napoli",
                        username
                    )
                } else {
                    getString(
                        R.string.status_loaded,
                        allTrees.size,
                        "Napoli"
                    )
                }
        }
    }

    private fun treeIcon(
        tree: TreeMarker,
        selected: Boolean = false
    ): BitmapDescriptor =
        when {
            tree.isMonumental && selected ->
                selectedMonumentalIcon
                    ?: createTreeMarker(
                        monumental = true,
                        selected = true
                    ).also {
                        selectedMonumentalIcon = it
                    }

            tree.isMonumental ->
                monumentalTreeIcon
                    ?: createTreeMarker(
                        monumental = true,
                        selected = false
                    ).also {
                        monumentalTreeIcon = it
                    }

            selected ->
                selectedTreeIcon
                    ?: createTreeMarker(
                        monumental = false,
                        selected = true
                    ).also {
                        selectedTreeIcon = it
                    }

            else ->
                normalTreeIcon
                    ?: createTreeMarker(
                        monumental = false,
                        selected = false
                    ).also {
                        normalTreeIcon = it
                    }
        }

    private fun createTreeMarker(
        monumental: Boolean,
        selected: Boolean
    ): BitmapDescriptor {
        val size =
            dp(if (selected) 38 else 32)

        val bitmap =
            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val center = size / 2f
        val radius = size * 0.38f

        val fill =
            Color.parseColor(
                if (monumental) {
                    "#B88A2B"
                } else {
                    "#8BAA3D"
                }
            )

        val outline =
            Color.parseColor(
                if (selected) {
                    "#56ABB2"
                } else {
                    "#246B4B"
                }
            )

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(45, 0, 0, 0)
        canvas.drawCircle(
            center + dp(1),
            center + dp(2),
            radius + dp(2),
            paint
        )

        paint.color = outline
        canvas.drawCircle(
            center,
            center,
            radius + dp(if (selected) 3 else 2),
            paint
        )

        paint.color = fill
        canvas.drawCircle(
            center,
            center,
            radius,
            paint
        )

        paint.color =
            Color.parseColor("#FFF9ED")

        val canopyY =
            center - size * 0.08f
        val crown =
            size * 0.10f

        canvas.drawCircle(
            center,
            canopyY - crown * 0.65f,
            crown,
            paint
        )
        canvas.drawCircle(
            center - crown * 0.75f,
            canopyY,
            crown,
            paint
        )
        canvas.drawCircle(
            center + crown * 0.75f,
            canopyY,
            crown,
            paint
        )
        canvas.drawRect(
            center - size * 0.025f,
            canopyY + crown * 0.55f,
            center + size * 0.025f,
            center + size * 0.19f,
            paint
        )

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clusterIcon(
        count: Int,
        hasMonumental: Boolean = false
    ): BitmapDescriptor {
        val cacheKey =
            "$count:$hasMonumental"

        clusterIconCache[cacheKey]?.let {
            return it
        }

        val size = dp(42)

        val bitmap =
            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f

        paint.color = Color.argb(42, 0, 0, 0)
        canvas.drawCircle(
            center + dp(1),
            center + dp(2),
            size * 0.41f,
            paint
        )

        paint.color =
            Color.parseColor(
                if (hasMonumental) {
                    "#B88A2B"
                } else {
                    "#FFF9ED"
                }
            )
        canvas.drawCircle(
            center,
            center,
            size * 0.41f,
            paint
        )

        paint.color =
            Color.parseColor(
                when {
                    hasMonumental -> "#174B35"
                    count < 10 -> "#8BAA3D"
                    count < 40 -> "#246B4B"
                    else -> "#174B35"
                }
            )

        canvas.drawCircle(
            center,
            center,
            size * 0.34f,
            paint
        )

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize =
            when {
                count < 10 -> size * 0.35f
                count < 100 -> size * 0.30f
                else -> size * 0.25f
            }

        val baseline =
            center -
                (paint.ascent() + paint.descent()) /
                2f

        canvas.drawText(
            count.toString(),
            center,
            baseline,
            paint
        )

        return BitmapDescriptorFactory
            .fromBitmap(bitmap)
            .also {
                clusterIconCache[cacheKey] = it
            }
    }

    private fun toggleMapType() {
        val googleMap = map ?: return

        googleMap.mapType =
            if (
                googleMap.mapType ==
                GoogleMap.MAP_TYPE_NORMAL
            ) {
                Toast.makeText(
                    this,
                    R.string.map_satellite,
                    Toast.LENGTH_SHORT
                ).show()
                GoogleMap.MAP_TYPE_HYBRID
            } else {
                Toast.makeText(
                    this,
                    R.string.map_standard,
                    Toast.LENGTH_SHORT
                ).show()
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
                        LatLng(
                            location.latitude,
                            location.longitude
                        ),
                        16f
                    )
                )

                googleMap.setOnMyLocationChangeListener(null)
            }
        }

        Toast.makeText(
            this,
            R.string.location_searching,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            LOCATION_PERMISSION_REQUEST
        ) {
            if (hasLocationPermission()) {
                enableLocationAndCenter()
            } else {
                Toast.makeText(
                    this,
                    R.string.location_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
    }

    private fun applySystemInsets() {
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            if (
                android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.R
            ) {
                val bars =
                    insets.getInsets(
                        WindowInsets.Type.systemBars()
                    )
                systemTopInset = bars.top
                systemBottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                systemTopInset =
                    insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                systemBottomInset =
                    insets.systemWindowInsetBottom
            }

            statusBarScrim.layoutParams =
                statusBarScrim.layoutParams.also { params ->
                    params.height = systemTopInset
                }

            (topPanel.layoutParams as
                ViewGroup.MarginLayoutParams).also { params ->
                params.topMargin = systemTopInset
                topPanel.layoutParams = params
            }

            (filterBar.layoutParams as
                ViewGroup.MarginLayoutParams).also { params ->
                params.topMargin =
                    systemTopInset + dp(64)
                filterBar.layoutParams = params
            }

            (mapControls.layoutParams as
                ViewGroup.MarginLayoutParams).also { params ->
                params.bottomMargin =
                    systemBottomInset + dp(18)
                mapControls.layoutParams = params
            }

            (treeCard.layoutParams as
                ViewGroup.MarginLayoutParams).also { params ->
                params.bottomMargin = 0
                treeCard.layoutParams = params
            }

            treeCard.setPadding(
                treeCard.paddingLeft,
                treeCard.paddingTop,
                treeCard.paddingRight,
                systemBottomInset + dp(18)
            )

            updateMapPadding()
            insets
        }

        rootView.requestApplyInsets()
    }

    private fun updateMapPadding() {
        val topPadding =
            systemTopInset + dp(108)

        val sheetHeight =
            if (
                ::treeCard.isInitialized &&
                isTreeSheetVisible()
            ) {
                treeCard.height
                    .takeIf { it > 0 }
                    ?: dp(310)
            } else {
                0
            }

        val bottomPadding =
            if (sheetHeight > 0) {
                systemBottomInset +
                    sheetHeight +
                    dp(16)
            } else {
                systemBottomInset + dp(24)
            }

        map?.setPadding(
            0,
            topPadding,
            0,
            bottomPadding
        )
    }

    private fun logMapsDiagnostics() {
        val mapsKey =
            runCatching {
                val appInfo =
                    packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA
                    )
                appInfo.metaData?.getString(
                    MAPS_API_KEY_METADATA
                )
            }.getOrNull()

        Log.i(
            MAPS_LOG_TAG,
            "Maps key present=${!mapsKey.isNullOrBlank()} " +
                "length=${mapsKey?.length ?: 0}"
        )
    }

    private fun isConfigured(): Boolean =
        BuildConfig.OTM_BASE_URL
            .startsWith("https://") &&
            BuildConfig.OTM_ACCESS_KEY
                .isNotBlank() &&
            BuildConfig.OTM_SECRET_KEY
                .isNotBlank() &&
            BuildConfig.OTM_INSTANCE
                .isNotBlank()

    private fun dp(value: Int): Int =
        (
            value *
                resources.displayMetrics.density
            ).roundToInt()

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        assetExecutor.shutdownNow()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }


    companion object {
        private const val MAPS_LOG_TAG =
            "OTN-MAPS"
        private const val MAPS_API_KEY_METADATA =
            "com.google.android.geo.API_KEY"
        private const val LOCATION_PERMISSION_REQUEST =
            210
        private const val BOTANICAL_BASE_URL =
            "https://opentreenap.altervista.org"
    }
}
