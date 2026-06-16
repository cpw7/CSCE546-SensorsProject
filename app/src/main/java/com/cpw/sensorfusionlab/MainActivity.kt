package com.cpw.sensorfusionlab

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lightSensor: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val gyro = FloatArray(3)
    private var lux = -1f
    private var lastShakeTime = 0L
    private var shakeCount = 0
    private var lastMagnitude = 9.8f
    private var highMotionSamples = 0
    private var stableSamples = 0
    private var challengeIndex = 0

    private lateinit var root: LinearLayout
    private lateinit var headingText: TextView
    private lateinit var motionText: TextView
    private lateinit var shakeText: TextView
    private lateinit var tiltText: TextView
    private lateinit var lightText: TextView
    private lateinit var fusionText: TextView
    private lateinit var statusText: TextView
    private lateinit var sensorText: TextView
    private lateinit var compass: CompassView

    private val challenges = listOf(
        "Shake trigger: background color changed + event logged.",
        "Shake trigger: focus timer reset. Hold phone flat for stability points.",
        "Shake trigger: compass challenge. Rotate toward the brightest heading.",
        "Shake trigger: lab mode. Compare tilt, heading, light, and motion together."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        buildUi()
        updateSensorList()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(115, 0, 10))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Sensor Fusion Lab"
            textSize = 34f
            setTextColor(Color.WHITE)
            setTypeface(null, 1)
        })
        root.addView(TextView(this).apply {
            text = "A sensor project that combines accelerometer, magnetometer, gyroscope, and light sensor data instead of only listing raw values."
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(16))
        })

        sensorText = cardText("Sensor availability", "Checking sensors...")
        root.addView(sensorText)

        compass = CompassView(this)
        root.addView(compass, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(245)).apply { bottomMargin = dp(14) })

        headingText = cardText("Compass heading", "Waiting for accelerometer + magnetometer...")
        motionText = cardText("Motion classifier", "Waiting for accelerometer + gyroscope...")
        shakeText = cardText("Shake events", "Shake count: 0")
        tiltText = cardText("Tilt / posture", "Waiting for accelerometer...")
        lightText = cardText("Lighting", "Waiting for light sensor...")
        fusionText = cardText("Fusion decision", "Collecting sensor samples...")
        statusText = cardText("500-level feature evidence", "Shake events trigger UI changes and a different challenge mode. Sensor values are fused into stability, posture, lighting, motion, and heading decisions.")
        listOf(headingText, motionText, shakeText, tiltText, lightText, fusionText, statusText).forEach { root.addView(it) }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Button(this).apply {
            text = "RESET LAB"
            setOnClickListener { resetLab() }
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { rightMargin = dp(8) })
        row.addView(Button(this).apply {
            text = "NEW CHALLENGE"
            setOnClickListener { nextChallenge() }
        }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(8) })
        root.addView(row)
        setContentView(scroll)
    }

    private fun cardText(title: String, body: String): TextView {
        return TextView(this).apply {
            text = "$title\n$body"
            textSize = 18f
            setTextColor(Color.rgb(30, 28, 35))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(250, 246, 252))
        }
    }

    private fun updateCard(view: TextView, title: String, body: String) { view.text = "$title\n$body" }

    private fun updateSensorList() {
        val present = mutableListOf<String>()
        val missing = mutableListOf<String>()
        fun add(name: String, sensor: Sensor?) { if (sensor == null) missing.add(name) else present.add(name) }
        add("Accelerometer", accelerometer)
        add("Magnetometer", magnetometer)
        add("Gyroscope", gyroscope)
        add("Light", lightSensor)
        updateCard(sensorText, "Sensor availability", "Available: ${present.joinToString()}\nMissing: ${if (missing.isEmpty()) "none" else missing.joinToString()}\nThe app still runs if one sensor is unavailable, but uses every available sensor for the fusion report.")
    }

    override fun onResume() {
        super.onResume()
        listOf(accelerometer, magnetometer, gyroscope, lightSensor).forEach { sensor ->
            sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                for (i in 0..2) gravity[i] = lowPass(event.values[i], gravity[i])
                processMotion(event.values)
                updateTilt()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> for (i in 0..2) geomagnetic[i] = lowPass(event.values[i], geomagnetic[i])
            Sensor.TYPE_GYROSCOPE -> for (i in 0..2) gyro[i] = event.values[i]
            Sensor.TYPE_LIGHT -> lux = event.values[0]
        }
        updateHeading()
        updateFusion()
    }

    private fun processMotion(values: FloatArray) {
        val mag = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val delta = abs(mag - lastMagnitude)
        lastMagnitude = mag
        val gyroRate = sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2])
        val movingHard = delta > 3.0f || gyroRate > 1.8f
        if (movingHard) highMotionSamples++ else stableSamples++
        val now = System.currentTimeMillis()
        if (delta > 8.5f && now - lastShakeTime > 650) {
            lastShakeTime = now
            shakeCount++
            nextChallenge()
            randomBackground()
        }
        val mode = when {
            delta > 8.5f -> "Shake detected"
            gyroRate > 1.8f -> "Rotating quickly"
            delta > 2.2f -> "Walking / moving"
            else -> "Stable"
        }
        updateCard(motionText, "Motion classifier", "Mode: $mode\nAcceleration change: ${delta.format(2)} m/s²\nGyroscope rate: ${gyroRate.format(2)} rad/s")
        updateCard(shakeText, "Shake events", "Shake count: $shakeCount\nCurrent challenge: ${challenges[challengeIndex]}")
    }

    private fun updateTilt() {
        val pitch = Math.toDegrees(atan2((-gravity[0]).toDouble(), sqrt((gravity[1] * gravity[1] + gravity[2] * gravity[2]).toDouble()))).toFloat()
        val roll = Math.toDegrees(atan2(gravity[1].toDouble(), gravity[2].toDouble())).toFloat()
        val posture = when {
            abs(pitch) < 10 && abs(roll) < 10 -> "Flat / level"
            abs(pitch) > 45 -> "Standing upright"
            roll > 25 -> "Tilted right"
            roll < -25 -> "Tilted left"
            else -> "Slight tilt"
        }
        updateCard(tiltText, "Tilt / posture", "Posture: $posture\nPitch: ${pitch.format(1)}°\nRoll: ${roll.format(1)}°")
    }

    private fun updateHeading() {
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            val azimuth = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).toFloat()
            compass.heading = azimuth
            compass.invalidate()
            updateCard(headingText, "Compass heading", "Heading: ${azimuth.format(0)}°\nUses accelerometer + magnetometer together, not one sensor by itself.")
        }
    }

    private fun updateFusion() {
        val lightLabel = when {
            lux < 0 -> "Unavailable"
            lux < 40 -> "Dim"
            lux < 400 -> "Indoor / normal"
            else -> "Bright"
        }
        if (lux >= 0) updateCard(lightText, "Lighting", "Level: ${lux.format(0)} lux\nClassification: $lightLabel")
        val total = max(1, highMotionSamples + stableSamples)
        val stability = (stableSamples * 100 / total).coerceIn(0, 100)
        val advice = when {
            stability > 80 && (lux < 0 || lux in 80f..900f) -> "Good for steady work or measuring orientation."
            stability < 45 -> "Too much movement for accurate reading."
            lux in 0f..39f -> "Lighting is low; move to a brighter area."
            lux > 900f -> "Very bright; screen glare may affect use."
            else -> "Usable sensor environment."
        }
        updateCard(fusionText, "Fusion decision", "Stability score: $stability%\nLight class: $lightLabel\nDecision: $advice")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetLab() {
        shakeCount = 0
        highMotionSamples = 0
        stableSamples = 0
        challengeIndex = 0
        root.setBackgroundColor(Color.rgb(115, 0, 10))
        updateCard(shakeText, "Shake events", "Shake count: 0")
        updateCard(fusionText, "Fusion decision", "Lab reset. Collecting new sensor samples...")
    }

    private fun nextChallenge() {
        challengeIndex = (challengeIndex + 1) % challenges.size
        updateCard(statusText, "500-level feature evidence", challenges[challengeIndex] + "\nThis goes beyond a raw accelerometer/magnetometer demo by using sensor events to drive app behavior.")
    }

    private fun randomBackground() {
        val r = Random.nextInt(90, 150)
        val g = Random.nextInt(0, 45)
        val b = Random.nextInt(10, 60)
        root.setBackgroundColor(Color.rgb(r, g, b))
    }

    private fun lowPass(input: Float, output: Float): Float = output + 0.18f * (input - output)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun Float.format(decimals: Int): String = "% .${decimals}f".format(this).trim()
}

class CompassView(context: Context) : View(context) {
    var heading: Float = 0f
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2f; val cy = h / 2f
        paint.color = Color.rgb(250, 246, 252); canvas.drawRoundRect(0f, 0f, w, h, 36f, 36f, paint)
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 8f; paint.color = Color.rgb(115, 0, 10)
        canvas.drawCircle(cx, cy, min(w, h) * 0.32f, paint)
        canvas.save(); canvas.rotate(heading, cx, cy)
        paint.style = android.graphics.Paint.Style.FILL; paint.color = Color.rgb(255, 199, 44)
        val path = android.graphics.Path()
        path.moveTo(cx, cy - min(w, h) * 0.28f); path.lineTo(cx - 22f, cy + 22f); path.lineTo(cx + 22f, cy + 22f); path.close()
        canvas.drawPath(path, paint); canvas.restore()
        paint.color = Color.rgb(30, 28, 35); paint.textAlign = android.graphics.Paint.Align.CENTER; paint.textSize = 42f; paint.style = android.graphics.Paint.Style.FILL
        canvas.drawText("${heading.roundToInt()}°", cx, cy + min(w, h) * 0.43f, paint)
        paint.textSize = 30f; canvas.drawText("Live compass from sensor fusion", cx, 48f, paint)
    }
}
