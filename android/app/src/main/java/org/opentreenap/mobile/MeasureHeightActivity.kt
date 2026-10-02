package org.opentreenap.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

class MeasureHeightActivity :
    ComponentActivity(),
    SensorEventListener {

    companion object {
        const val EXTRA_HEIGHT_M =
            "org.opentreenap.mobile.extra.HEIGHT_M"
        const val EXTRA_METHOD =
            "org.opentreenap.mobile.extra.METHOD"
        const val EXTRA_REPEATABILITY_M =
            "org.opentreenap.mobile.extra.REPEATABILITY_M"

        private const val GUIDE_URL =
            "https://opentreenap.altervista.org/come-misurare-un-albero/"
    }

    private lateinit var previewView: PreviewView
    private lateinit var distanceLayout: TextInputLayout
    private lateinit var distanceInput: TextInputEditText
    private lateinit var liveAngle: TextView
    private lateinit var arStatus: TextView
    private lateinit var captured: TextView
    private lateinit var result: TextView
    private lateinit var baseButton: MaterialButton
    private lateinit var topButton: MaterialButton
    private lateinit var saveButton: MaterialButton

    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null

    private var elevationDeg = 0.0
    private var smoothedElevationDeg: Double? = null
    private var baseAngleDeg: Double? = null
    private var topAngleDeg: Double? = null
    private val measurements = mutableListOf<Double>()

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            } else {
                arStatus.text =
                    getString(R.string.measure_camera_required)
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        window.statusBarColor =
            ContextCompat.getColor(
                this,
                R.color.otn_navy
            )
        window.navigationBarColor =
            ContextCompat.getColor(
                this,
                R.color.otn_navy
            )

        setContentView(
            R.layout.activity_measure_height
        )

        previewView =
            findViewById(R.id.measurePreview)
        distanceLayout =
            findViewById(R.id.measureDistanceLayout)
        distanceInput =
            findViewById(R.id.measureDistance)
        liveAngle =
            findViewById(R.id.measureLiveAngle)
        arStatus =
            findViewById(R.id.measureArStatus)
        captured =
            findViewById(R.id.measureCaptured)
        result =
            findViewById(R.id.measureResult)
        baseButton =
            findViewById(R.id.measureBase)
        topButton =
            findViewById(R.id.measureTop)
        saveButton =
            findViewById(R.id.measureSave)

        sensorManager =
            getSystemService(SENSOR_SERVICE)
                as SensorManager
        rotationVector =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_ROTATION_VECTOR
            )

        updateResultUi()
        checkArSupport()

        findViewById<MaterialButton>(
            R.id.measureCancel
        ).setOnClickListener {
            finish()
        }

        findViewById<TextView>(
            R.id.measureGuide
        ).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(GUIDE_URL)
                )
            )
        }

        baseButton.setOnClickListener {
            captureBase()
        }

        topButton.setOnClickListener {
            captureTop()
        }

        saveButton.setOnClickListener {
            finishWithMeasurement()
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    override fun onResume() {
        super.onResume()

        rotationVector?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(
        event: SensorEvent
    ) {
        if (
            event.sensor.type !=
            Sensor.TYPE_ROTATION_VECTOR
        ) {
            return
        }

        val matrix = FloatArray(9)

        SensorManager.getRotationMatrixFromVector(
            matrix,
            event.values
        )

        // Android device Z points out of the screen; the rear camera looks
        // approximately along -Z. The world Z component therefore gives the
        // elevation of the camera optical axis relative to the horizon.
        val cameraUpComponent =
            (-matrix[8]).coerceIn(
                -1f,
                1f
            )

        val rawDegrees =
            asin(cameraUpComponent.toDouble()) *
                180.0 /
                PI

        smoothedElevationDeg =
            smoothedElevationDeg
                ?.let { previous ->
                    previous * 0.82 +
                        rawDegrees * 0.18
                }
                ?: rawDegrees

        elevationDeg =
            smoothedElevationDeg ?: rawDegrees

        liveAngle.text =
            getString(
                R.string.measure_live_angle,
                elevationDeg
            )
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) = Unit

    private fun startCamera() {
        val future =
            ProcessCameraProvider
                .getInstance(this)

        future.addListener(
            {
                val provider =
                    runCatching {
                        future.get()
                    }.getOrNull()
                        ?: return@addListener

                val preview =
                    Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(
                                previewView.surfaceProvider
                            )
                        }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        CameraSelector
                            .DEFAULT_BACK_CAMERA,
                        preview
                    )
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun checkArSupport() {
        arStatus.text =
            getString(
                R.string.measure_ar_checking
            )

        ArCoreApk.getInstance()
            .checkAvailabilityAsync(
                this
            ) { availability ->
                runOnUiThread {
                    arStatus.text =
                        if (
                            availability.isSupported
                        ) {
                            getString(
                                R.string.measure_ar_available
                            )
                        } else {
                            getString(
                                R.string.measure_ar_unavailable
                            )
                        }
                }
            }
    }

    private fun captureBase() {
        if (rotationVector == null) {
            result.text =
                getString(
                    R.string.measure_sensor_missing
                )
            return
        }

        val distance =
            parseDistance()
                ?: return

        if (distance < 1.0) {
            distanceLayout.error =
                getString(
                    R.string.measure_distance_too_short
                )
            return
        }

        distanceLayout.error = null
        baseAngleDeg = elevationDeg
        topAngleDeg = null
        topButton.isEnabled = true

        captured.text =
            getString(
                R.string.measure_base_captured,
                baseAngleDeg ?: 0.0
            )

        result.text =
            getString(
                R.string.measure_point_top
            )
    }

    private fun captureTop() {
        val base =
            baseAngleDeg
                ?: return

        val distance =
            parseDistance()
                ?: return

        topAngleDeg = elevationDeg

        val top =
            topAngleDeg
                ?: return

        val height =
            distance * (
                tan(Math.toRadians(top)) -
                    tan(Math.toRadians(base))
                )

        if (
            !height.isFinite() ||
            height <= 0.5 ||
            height > 100.0
        ) {
            result.text =
                getString(
                    R.string.measure_invalid_geometry
                )
            return
        }

        measurements += height

        baseAngleDeg = null
        topAngleDeg = null
        topButton.isEnabled = false

        captured.text =
            getString(
                R.string.measure_completed_count,
                measurements.size,
                3
            )

        updateResultUi()
    }

    private fun updateResultUi() {
        if (measurements.isEmpty()) {
            result.text =
                getString(
                    R.string.measure_height_empty
                )
            saveButton.isEnabled = false
            return
        }

        val median =
            median(measurements)

        val spread =
            (
                measurements.maxOrNull()
                    ?: median
                ) -
                (
                    measurements.minOrNull()
                        ?: median
                    )

        val lines =
            measurements.mapIndexed {
                    index,
                    value ->
                getString(
                    R.string.measure_result_item,
                    index + 1,
                    value
                )
            }.toMutableList()

        if (measurements.size >= 3) {
            lines += getString(
                R.string.measure_result_median,
                median,
                spread
            )

            if (spread <= 1.5) {
                lines += getString(
                    R.string.measure_repeatability_ok
                )
                saveButton.isEnabled = true
            } else {
                lines += getString(
                    R.string.measure_repeatability_bad
                )
                saveButton.isEnabled = false
            }
        } else {
            lines += getString(
                R.string.measure_need_more,
                3 - measurements.size
            )
            saveButton.isEnabled = false
        }

        result.text =
            lines.joinToString("\n")
    }

    private fun finishWithMeasurement() {
        if (
            measurements.size < 3
        ) {
            return
        }

        val median =
            median(measurements)

        val spread =
            (
                measurements.maxOrNull()
                    ?: median
                ) -
                (
                    measurements.minOrNull()
                        ?: median
                    )

        if (spread > 1.5) {
            return
        }

        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(
                    EXTRA_HEIGHT_M,
                    median
                )
                .putExtra(
                    EXTRA_METHOD,
                    "smartphone clinometro"
                )
                .putExtra(
                    EXTRA_REPEATABILITY_M,
                    spread
                )
        )

        finish()
    }

    private fun parseDistance():
        Double? {
        val raw =
            distanceInput.text
                ?.toString()
                ?.trim()
                .orEmpty()
                .replace(',', '.')

        val value =
            raw.toDoubleOrNull()

        if (value == null) {
            distanceLayout.error =
                getString(
                    R.string.measure_distance_required
                )
            return null
        }

        return value
    }

    private fun median(
        values: List<Double>
    ): Double {
        val sorted =
            values.sorted()

        val middle =
            sorted.size / 2

        return if (
            sorted.size % 2 == 0
        ) {
            (
                sorted[middle - 1] +
                    sorted[middle]
                ) / 2.0
        } else {
            sorted[middle]
        }
    }
}
