package org.opentreenap.mobile

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.opentreenap.mobile.api.OtmApiClient
import org.opentreenap.mobile.model.TreeMarker
import java.util.concurrent.Executors

class MainActivity : Activity(), OnMapReadyCallback {
    private lateinit var mapView: MapView
    private lateinit var statusView: TextView
    private lateinit var refreshButton: Button

    private var map: GoogleMap? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logMapsDiagnostics()

        val mapsInitResult = runCatching {
            MapsInitializer.initialize(applicationContext)
        }
        mapsInitResult
            .onSuccess { result ->
                Log.i(MAPS_LOG_TAG, "MapsInitializer.initialize result=$result")
            }
            .onFailure { error ->
                Log.e(MAPS_LOG_TAG, "MapsInitializer.initialize failed", error)
            }

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        statusView = findViewById(R.id.status)
        refreshButton = findViewById(R.id.refresh)

        mapView.onCreate(savedInstanceState)
        Log.i(MAPS_LOG_TAG, "MapView.onCreate completed; requesting map")
        mapView.getMapAsync(this)

        refreshButton.setOnClickListener {
            Log.i(MAPS_LOG_TAG, "Refresh pressed")
            loadTrees()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.i(MAPS_LOG_TAG, "onMapReady received")
        map = googleMap

        googleMap.uiSettings.apply {
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }

        googleMap.setOnMapLoadedCallback {
            Log.i(MAPS_LOG_TAG, "onMapLoaded fired: Google base map finished loading")
        }

        googleMap.setOnCameraMoveStartedListener { reason ->
            Log.i(MAPS_LOG_TAG, "cameraMoveStarted reason=$reason")
        }

        googleMap.setOnCameraIdleListener {
            val position = googleMap.cameraPosition
            Log.i(
                MAPS_LOG_TAG,
                "cameraIdle lat=${position.target.latitude} lon=${position.target.longitude} zoom=${position.zoom}"
            )
        }

        googleMap.setOnMapClickListener { point ->
            Log.i(MAPS_LOG_TAG, "mapClick lat=${point.latitude} lon=${point.longitude}")
        }

        val naples = LatLng(40.8518, 14.2681)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(naples, 12.3f))
        Log.i(MAPS_LOG_TAG, "Initial camera moved to Naples")

        loadTrees()
    }

    private fun loadTrees() {
        val googleMap = map
        if (googleMap == null) {
            Log.w(MAPS_LOG_TAG, "loadTrees skipped: GoogleMap is null")
            return
        }

        if (!isConfigured()) {
            Log.e(MAPS_LOG_TAG, "OTM configuration missing")
            statusView.setText(R.string.config_missing)
            return
        }

        statusView.setText(R.string.status_loading)
        refreshButton.isEnabled = false

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

                result.onSuccess { trees ->
                    Log.i(MAPS_LOG_TAG, "OTM trees loaded count=${trees.size}")
                    renderTrees(googleMap, trees)
                }.onFailure { error ->
                    Log.e(MAPS_LOG_TAG, "OTM API failure", error)
                    statusView.text =
                        "Errore API: " + (error.message ?: error.javaClass.simpleName)
                }
            }
        }
    }

    private fun renderTrees(
        googleMap: GoogleMap,
        trees: List<TreeMarker>
    ) {
        googleMap.clear()

        trees.forEach { tree ->
            googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(tree.latitude, tree.longitude))
                    .title(tree.title)
                    .snippet(tree.snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(120f))
            )
        }

        Log.i(MAPS_LOG_TAG, "Markers rendered count=${trees.size}")

        statusView.text =
            trees.size.toString() + " alberi caricati · " + BuildConfig.OTM_INSTANCE
    }

    private fun logMapsDiagnostics() {
        val mapsKey = runCatching {
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(MAPS_API_KEY_METADATA)
        }.getOrElse { error ->
            Log.e(MAPS_LOG_TAG, "Unable to read Maps API key metadata", error)
            null
        }

        Log.i(
            MAPS_LOG_TAG,
            "Maps API key metadata present=${!mapsKey.isNullOrBlank()} length=${mapsKey?.length ?: 0}"
        )
        Log.i(
            MAPS_LOG_TAG,
            "package=$packageName sdk=${android.os.Build.VERSION.SDK_INT}"
        )
    }

    private fun isConfigured(): Boolean =
        BuildConfig.OTM_BASE_URL.startsWith("https://") &&
            BuildConfig.OTM_ACCESS_KEY.isNotBlank() &&
            BuildConfig.OTM_SECRET_KEY.isNotBlank() &&
            BuildConfig.OTM_INSTANCE.isNotBlank()

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

    companion object {
        private const val MAPS_LOG_TAG = "OTN-MAPS"
        private const val MAPS_API_KEY_METADATA = "com.google.android.geo.API_KEY"
    }
}
