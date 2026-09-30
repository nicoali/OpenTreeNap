package org.opentreenap.mobile

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
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
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        statusView = findViewById(R.id.status)
        refreshButton = findViewById(R.id.refresh)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        refreshButton.setOnClickListener {
            loadTrees()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        googleMap.uiSettings.apply {
            isCompassEnabled = true
            isMapToolbarEnabled = false
            isZoomControlsEnabled = false
        }

        val naples = LatLng(40.8518, 14.2681)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(naples, 12.3f))

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
                    renderTrees(googleMap, trees)
                }.onFailure { error ->
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

        statusView.text =
            trees.size.toString() + " alberi caricati · " + BuildConfig.OTM_INSTANCE
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
}
