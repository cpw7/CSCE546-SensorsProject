package com.cpw.sensorsproject

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var running = false
    private var shakeCount = 0
    private var lastShakeTime = 0L

    private val accelValues = FloatArray(3)
    private val magValues = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private lateinit var statusText: TextView
    private lateinit var accelText: TextView
    private lateinit var magnetText: TextView
    private lateinit var tiltText: TextView
    private lateinit var directionText: TextView
    private lateinit var shakeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        buildUi()
        updateStatus("Ready. Press Start Sensors to begin.")
    }

    private fun buildUi() {
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(242, 248, 247))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        scrollView.addView(root)

        val title = TextView(this).apply {
            text = "Sensors Project"
            textSize = 30f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(0, 77, 64))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Uses two non-GPS sensors: accelerometer and magnetometer."
            textSize = 16f
            setTextColor(Color.rgb(70, 80, 80))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(14))
        }
        root.addView(subtitle)

        statusText = cardText("Status: --", 18f, true)
        root.addView(statusText)

        accelText = cardText("Accelerometer: --", 17f, false)
        root.addView(accelText)

        magnetText = cardText("Magnetometer: --", 17f, false)
        root.addView(magnetText)

        tiltText = cardText("Tilt: --", 17f, false)
        root.addView(tiltText)

        directionText = cardText("Compass direction: --", 17f, false)
        root.addView(directionText)

        shakeText = cardText("Shake count: 0", 17f, true)
        root.addView(shakeText)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startButton = button("START SENSORS") { startSensors() }
        val stopButton = button("STOP SENSORS") { stopSensors() }
        row1.addView(startButton, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(0, dp(8), dp(5), 0) })
        row1.addView(stopButton, LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(5), dp(8), 0, 0) })
        root.addView(row1)

        val resetButton = button("RESET SHAKE COUNT") {
            shakeCount = 0
            shakeText.text = "Shake count: 0"
            updateStatus("Shake count reset.")
        }
        root.addView(resetButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { setMargins(0, dp(10), 0, 0) })

        val instructions = TextView(this).apply {
            text = "Testing tip: in the emulator, open the three-dot menu, choose Virtual sensors, then change the device position. The accelerometer and magnetometer values should update."
            textSize = 15f
            setTextColor(Color.rgb(40, 50, 50))
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(instructions)

        setContentView(scrollView)
    }

    private fun startSensors() {
        val accel = accelerometer
        val mag = magnetometer
        if (accel == null || mag == null) {
            updateStatus("Missing required sensor. Accelerometer available: ${accel != null}; Magnetometer available: ${mag != null}.")
            return
        }
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        running = true
        updateStatus("Receiving accelerometer and magnetometer data.")
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
        running = false
        updateStatus("Sensor updates stopped.")
    }

    override fun onResume() {
        super.onResume()
        if (running) startSensors()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelValues, 0, 3)
                hasAccel = true
                checkShake(accelValues[0], accelValues[1], accelValues[2])
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magValues, 0, 3)
                hasMag = true
            }
        }
        updateSensorDisplay()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy changes are handled by the system. The app displays live values each update.
    }

    private fun updateSensorDisplay() {
        if (hasAccel) {
            accelText.text = "Accelerometer (m/s²)\nX: ${fmt(accelValues[0])}\nY: ${fmt(accelValues[1])}\nZ: ${fmt(accelValues[2])}"
            val roll = atan2(accelValues[1], accelValues[2]) * 57.2958
            val pitch = atan2(-accelValues[0], sqrt(accelValues[1] * accelValues[1] + accelValues[2] * accelValues[2])) * 57.2958
            tiltText.text = "Tilt estimate\nPitch: ${fmt(pitch.toFloat())} degrees\nRoll: ${fmt(roll.toFloat())} degrees"
        }
        if (hasMag) {
            magnetText.text = "Magnetometer (µT)\nX: ${fmt(magValues[0])}\nY: ${fmt(magValues[1])}\nZ: ${fmt(magValues[2])}"
            val heading = ((atan2(magValues[1], magValues[0]) * 57.2958 + 360.0) % 360.0).roundToInt()
            directionText.text = "Compass direction estimate\nHeading: $heading degrees\nDirection: ${directionName(heading)}"
        }
    }

    private fun checkShake(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()
        if (abs(magnitude - SensorManager.GRAVITY_EARTH) > 10.0f && now - lastShakeTime > 800) {
            shakeCount++
            lastShakeTime = now
            shakeText.text = "Shake count: $shakeCount\nLast acceleration magnitude: ${fmt(magnitude)} m/s²"
        } else if (shakeCount == 0) {
            shakeText.text = "Shake count: 0\nMove or tilt the emulator to change sensor values."
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = "Status: $message"
    }

    private fun cardText(value: String, size: Float, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            if (bold) setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.rgb(20, 35, 35))
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            setOnClickListener { action() }
        }
    }

    private fun directionName(degrees: Int): String {
        return when (degrees) {
            in 338..360, in 0..22 -> "North"
            in 23..67 -> "North-East"
            in 68..112 -> "East"
            in 113..157 -> "South-East"
            in 158..202 -> "South"
            in 203..247 -> "South-West"
            in 248..292 -> "West"
            else -> "North-West"
        }
    }

    private fun fmt(value: Float): String = "%.2f".format(value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
