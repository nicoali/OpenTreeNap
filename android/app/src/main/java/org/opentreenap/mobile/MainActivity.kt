package org.opentreenap.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
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
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.opentreenap.mobile.api.OtmApiClient
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
    private lateinit var treeBadge: TextView
    private lateinit var treeTitle: TextView
    private lateinit var treeScientific: TextView
    private lateinit var treeDetails: TextView
    private lateinit var treeMeta: TextView
    private lateinit var botanicalCardButton: Button
    private lateinit var editTreeButton: Button

    private var map: GoogleMap? = null
    private var systemTopInset = 0
    private var systemBottomInset = 0
    private var allTrees: List<TreeMarker> = emptyList()
    private var speciesCache: List<SpeciesItem> = emptyList()
    private var monumentalOnly = false
    private var addingTree = false
    private var selectedMarker: Marker? = null
    private var selectedTree: TreeMarker? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var sessionUser: ApiUser? = null
    private var sessionPermissions: InstancePermissions? = null
    private var sessionUsername: String? = null
    private var sessionPassword: String? = null

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
        treeMeta = findViewById(R.id.treeMeta)
        botanicalCardButton = findViewById(R.id.botanicalCard)
        editTreeButton = findViewById(R.id.editTree)

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
                        Toast.makeText(
                            this,
                            R.string.permission_add_denied,
                            Toast.LENGTH_LONG
                        ).show()
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
            googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
        }.onFailure {
            Log.w(MAPS_LOG_TAG, "Custom map style unavailable", it)
        }

        updateMapPadding()
        googleMap.setOnCameraIdleListener {
            if (!addingTree) {
                renderClusters()
            }
        }

        googleMap.setOnMapClickListener { point ->
            if (addingTree) {
                addingTree = false
                updateAddButtonUi()
                openTreeEditor(tree = null, point = point)
            } else {
                hideTreeCard()
            }
        }

        googleMap.setOnMarkerClickListener { marker ->
            when (val tag = marker.tag) {
                is TreeMarker -> {
                    if (addingTree) {
                        false
                    } else {
                        showTreeCard(tag, marker)
                        true
                    }
                }
                is ClusterTag -> {
                    if (addingTree) {
                        false
                    } else {
                        zoomIntoCluster(tag)
                        true
                    }
                }
                else -> false
            }
        }

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(40.8518, 14.2681),
                12.3f
            )
        )
        loadTrees()
    }

    private fun apiClient(): OtmApiClient =
        OtmApiClient(
            baseUrl = BuildConfig.OTM_BASE_URL,
            instance = BuildConfig.OTM_INSTANCE,
            accessKey = BuildConfig.OTM_ACCESS_KEY,
            secretKey = BuildConfig.OTM_SECRET_KEY
        )

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
                apiClient().fetchAllTrees()
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
                    Log.e(MAPS_LOG_TAG, "OTM API failure", error)
                    statusView.text =
                        getString(
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

        val visibleTrees =
            if (monumentalOnly) {
                allTrees.filter { it.isMonumental }
            } else {
                allTrees
            }

        googleMap.clear()
        selectedMarker = null
        selectedTree = null

        if (visibleTrees.isEmpty()) {
            statusView.setText(R.string.status_no_results)
            return
        }

        val zoom = googleMap.cameraPosition.zoom
        val cellSize =
            when {
                zoom < 11f -> dp(112)
                zoom < 13f -> dp(88)
                zoom < 15f -> dp(68)
                zoom < 17f -> dp(52)
                else -> dp(34)
            }

        val groups = linkedMapOf<Long, MutableList<TreeMarker>>()
        val projection = googleMap.projection

        visibleTrees.forEach { tree ->
            val point =
                projection.toScreenLocation(
                    LatLng(tree.latitude, tree.longitude)
                )
            val cellX = point.x / cellSize
            val cellY = point.y / cellSize
            val key =
                (cellX.toLong() shl 32) xor
                    (cellY.toLong() and 0xffffffffL)

            groups.getOrPut(key) { mutableListOf() }.add(tree)
        }

        groups.values.forEach { trees ->
            if (trees.size == 1) {
                val tree = trees.first()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                tree.latitude,
                                tree.longitude
                            )
                        )
                        .icon(treeIcon(tree))
                        .anchor(0.5f, 0.5f)
                        .zIndex(if (tree.isMonumental) 3f else 2f)
                )?.tag = tree
            } else {
                val latitude =
                    trees.sumOf { it.latitude } / trees.size
                val longitude =
                    trees.sumOf { it.longitude } / trees.size

                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(latitude, longitude))
                        .icon(clusterIcon(trees.size))
                        .anchor(0.5f, 0.5f)
                        .zIndex(1f)
                )?.tag = ClusterTag(trees)
            }
        }

        restoreMapStatus()
    }

    private fun showTreeCard(
        tree: TreeMarker,
        marker: Marker
    ) {
        restoreSelectedMarker()

        selectedMarker = marker
        selectedTree = tree

        marker.setIcon(treeIcon(tree, selected = true))
        marker.zIndex = 10f

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
            tree.commonName?.takeIf { it.isNotBlank() }
                ?: tree.title

        val scientific = tree.scientificName.orEmpty()
        treeScientific.text = scientific
        treeScientific.visibility =
            if (scientific.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        treeDetails.text =
            tree.address?.takeIf { it.isNotBlank() }
                ?: tree.snippet?.takeIf { it.isNotBlank() }
                ?: getString(R.string.details_missing)

        treeMeta.text = buildTreeMeta(tree)

        botanicalCardButton.isEnabled = scientific.isNotBlank()
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

        treeCard.visibility = View.VISIBLE
        mapControls.visibility = View.GONE
        updateMapPadding()
    }

    private fun buildTreeMeta(tree: TreeMarker): String {
        val parts = mutableListOf<String>()

        tree.customId?.takeIf { it.isNotBlank() }?.let {
            parts += "ID $it"
        }

        tree.diameter?.let {
            parts += "DBH ${formatNumber(it)} cm"
        }

        tree.height?.let {
            parts += "H ${formatNumber(it)} m"
        }

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
                marker.zIndex =
                    if (tree.isMonumental) 3f else 2f
            }
        }

        selectedMarker = null
        selectedTree = null
    }

    private fun zoomIntoCluster(cluster: ClusterTag) {
        if (cluster.trees.isEmpty()) return

        val latitude =
            cluster.trees.sumOf { it.latitude } /
                cluster.trees.size
        val longitude =
            cluster.trees.sumOf { it.longitude } /
                cluster.trees.size

        val nextZoom =
            ((map?.cameraPosition?.zoom ?: 12f) + 2f)
                .coerceAtMost(19f)

        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(latitude, longitude),
                nextZoom
            )
        )
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

        AlertDialog.Builder(this)
            .setTitle(R.string.account_title)
            .setMessage(
                "$displayName\n@${user.username}" +
                    (user.email?.let { "\n$it" } ?: "")
            )
            .setPositiveButton(R.string.logout) { _, _ ->
                sessionUser = null
                sessionPermissions = null
                sessionUsername = null
                sessionPassword = null
                updateSessionUi()
                restoreMapStatus()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showLoginDialog(
        onSuccess: (() -> Unit)? = null
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(22),
                dp(6),
                dp(22),
                0
            )
        }

        val usernameInput = EditText(this).apply {
            hint = getString(R.string.username)
            isSingleLine = true
            inputType =
                InputType.TYPE_CLASS_TEXT
        }

        val passwordInput = EditText(this).apply {
            hint = getString(R.string.password)
            isSingleLine = true
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        content.addView(usernameInput)
        content.addView(passwordInput)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.login_title)
                .setView(content)
                .setPositiveButton(R.string.login, null)
                .setNegativeButton(R.string.cancel, null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val username =
                        usernameInput.text.toString().trim()
                    val password =
                        passwordInput.text.toString()

                    if (username.isBlank() || password.isBlank()) {
                        return@setOnClickListener
                    }

                    dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                    ).isEnabled = false
                    statusView.setText(R.string.status_loading)

                    executor.execute {
                        val result =
                            runCatching {
                                val client = apiClient()
                                val user = client.login(
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
                            dialog.getButton(
                                AlertDialog.BUTTON_POSITIVE
                            ).isEnabled = true

                            result.onSuccess { loginResult ->
                                val user = loginResult.first
                                val permissions = loginResult.second
                                sessionUser = user
                                sessionPermissions = permissions
                                sessionUsername = username
                                sessionPassword = password
                                updateSessionUi()

                                Toast.makeText(
                                    this,
                                    R.string.login_success,
                                    Toast.LENGTH_SHORT
                                ).show()

                                statusView.text =
                                    getString(
                                        R.string.status_logged_in,
                                        user.username
                                    )

                                dialog.dismiss()
                                onSuccess?.invoke()
                            }.onFailure { error ->
                                Toast.makeText(
                                    this,
                                    getString(
                                        R.string.login_failed,
                                        error.message
                                            ?: error.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                                restoreMapStatus()
                            }
                        }
                    }
                }
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
                    treeCard.visibility == View.VISIBLE
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
        statusView.setText(R.string.status_add_mode)

        Toast.makeText(
            this,
            R.string.status_add_mode,
            Toast.LENGTH_LONG
        ).show()
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
                    Toast.makeText(
                        this,
                        error.message
                            ?: error.javaClass.simpleName,
                        Toast.LENGTH_LONG
                    ).show()
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(22),
                dp(4),
                dp(22),
                0
            )
        }

        val speciesButton = Button(this).apply {
            text =
                selectedSpecies?.value
                    ?: getString(R.string.select_species)
            isAllCaps = false
        }

        val diameterInput = EditText(this).apply {
            hint = getString(R.string.diameter_cm)
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            tree?.diameter?.let {
                setText(formatNumber(it))
            }
        }

        val heightInput = EditText(this).apply {
            hint = getString(R.string.height_m)
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            tree?.height?.let {
                setText(formatNumber(it))
            }
        }

        content.addView(speciesButton)
        content.addView(diameterInput)
        content.addView(heightInput)

        speciesButton.setOnClickListener {
            val labels =
                species.map { it.value }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle(R.string.select_species)
                .setItems(labels) { _, index ->
                    selectedSpecies = species[index]
                    speciesButton.text =
                        selectedSpecies?.value
                }
                .show()
        }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    if (tree == null) {
                        R.string.add_tree_title
                    } else {
                        R.string.edit_tree_title
                    }
                )
                .setView(content)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel) { _, _ ->
                    restoreMapStatus()
                }
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val selected = selectedSpecies

                    if (selected == null) {
                        Toast.makeText(
                            this,
                            R.string.species_required,
                            Toast.LENGTH_SHORT
                        ).show()
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
                            diameterInput.text.toString()
                        )
                    val height =
                        parseDecimal(
                            heightInput.text.toString()
                        )

                    dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                    ).isEnabled = false
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
                            dialog.getButton(
                                AlertDialog.BUTTON_POSITIVE
                            ).isEnabled = true

                            result.onSuccess { saved ->
                                dialog.dismiss()

                                Toast.makeText(
                                    this,
                                    R.string.tree_saved,
                                    Toast.LENGTH_SHORT
                                ).show()

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
                                Toast.makeText(
                                    this,
                                    getString(
                                        R.string.tree_save_failed,
                                        error.message
                                            ?: error.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                                restoreMapStatus()
                            }
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
            Toast.makeText(
                this,
                R.string.botanical_card_missing,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val slug = slugify(scientificName)
        val url =
            "$BOTANICAL_BASE_URL/specie/$slug/"

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
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

        if (monumentalOnly) {
            statusView.text =
                getString(
                    R.string.status_monumental,
                    allTrees.count { it.isMonumental }
                )
        } else {
            statusView.text =
                getString(
                    R.string.status_loaded,
                    allTrees.size,
                    "Napoli"
                )
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
        count: Int
    ): BitmapDescriptor {
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
            Color.parseColor("#FFF9ED")
        canvas.drawCircle(
            center,
            center,
            size * 0.41f,
            paint
        )

        paint.color =
            Color.parseColor(
                when {
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

        return BitmapDescriptorFactory.fromBitmap(bitmap)
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
        window.statusBarColor =
            getColor(R.color.otn_green_dark)
        window.navigationBarColor =
            getColor(R.color.otn_cream)

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
                FrameLayout.LayoutParams).also { params ->
                params.topMargin = systemTopInset
                topPanel.layoutParams = params
            }

            (filterBar.layoutParams as
                FrameLayout.LayoutParams).also { params ->
                params.topMargin =
                    systemTopInset + dp(64)
                filterBar.layoutParams = params
            }

            (mapControls.layoutParams as
                FrameLayout.LayoutParams).also { params ->
                params.bottomMargin =
                    systemBottomInset + dp(18)
                mapControls.layoutParams = params
            }

            (treeCard.layoutParams as
                FrameLayout.LayoutParams).also { params ->
                params.bottomMargin =
                    systemBottomInset + dp(10)
                treeCard.layoutParams = params
            }

            updateMapPadding()
            insets
        }

        rootView.requestApplyInsets()
    }

    private fun updateMapPadding() {
        val topPadding =
            systemTopInset + dp(108)

        val bottomPadding =
            if (
                ::treeCard.isInitialized &&
                treeCard.visibility == View.VISIBLE
            ) {
                systemBottomInset + dp(310)
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
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private data class ClusterTag(
        val trees: List<TreeMarker>
    )

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
