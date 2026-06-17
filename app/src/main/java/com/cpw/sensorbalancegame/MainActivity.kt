package com.cpw.sensorbalancegame

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.hardware.*
import android.view.*
import android.widget.FrameLayout
import kotlin.math.*
import java.util.Locale
import java.util.Random

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lightSensor: Sensor? = null
    private lateinit var gameView: TiltGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        gameView = TiltGameView(this)
        setContentView(gameView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        lightSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gameView.running = true
        gameView.postInvalidateOnAnimation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        gameView.running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gameView.updateAccelerometer(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_GYROSCOPE -> gameView.updateGyroscope(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_LIGHT -> gameView.updateLight(event.values[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

class TiltGameView(context: android.content.Context) : View(context) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var running = false
    private var ax = 0f; private var ay = 0f; private var az = 9.8f
    private var gx = 0f; private var gy = 0f; private var gz = 0f
    private var lux = 0f
    private var ballX = 0f; private var ballY = 0f; private var velX = 0f; private var velY = 0f
    private var targetX = 0f; private var targetY = 0f
    private val targetRadius = 88f; private val ballRadius = 28f
    private var score = 0; private var highScore = 0; private var streakFrames = 0
    private var stabilityScore = 100f
    private var lastTime = System.currentTimeMillis()
    private var flatMs = 0L; private var verticalMs = 0L; private var angledMs = 0L; private var activeMs = 0L
    private var lastPostureUpdate = System.currentTimeMillis()
    private var posture = "calibrating"; private var environment = "unknown"
    private var gameMessage = "Keep the blue ball inside the green bullseye."
    private val rand = Random(7)

    init {
        cardPaint.color = Color.rgb(31, 41, 55)
        textPaint.color = Color.WHITE; textPaint.textSize = 42f; textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        smallTextPaint.color = Color.rgb(220, 230, 240); smallTextPaint.textSize = 27f
        targetPaint.color = Color.rgb(34, 197, 94); targetPaint.style = Paint.Style.STROKE; targetPaint.strokeWidth = 12f
        targetInnerPaint.color = Color.argb(45, 34, 197, 94); targetInnerPaint.style = Paint.Style.FILL
        ballPaint.color = Color.rgb(59, 130, 246)
        linePaint.color = Color.argb(90, 255, 255, 255); linePaint.strokeWidth = 3f
        buttonPaint.color = Color.rgb(148, 30, 50)
        isFocusable = true
    }

    fun updateAccelerometer(x: Float, y: Float, z: Float) {
        ax = ax * 0.82f + x * 0.18f
        ay = ay * 0.82f + y * 0.18f
        az = az * 0.82f + z * 0.18f
        updatePosture()
    }

    fun updateGyroscope(x: Float, y: Float, z: Float) {
        gx = gx * 0.75f + x * 0.25f
        gy = gy * 0.75f + y * 0.25f
        gz = gz * 0.75f + z * 0.25f
    }

    fun updateLight(v: Float) {
        lux = v
        environment = when {
            lux < 10f -> "dark room"
            lux < 150f -> "indoor"
            lux < 1000f -> "bright indoor"
            else -> "outdoor/very bright"
        }
    }

    private fun updatePosture() {
        val now = System.currentTimeMillis()
        val dt = (now - lastPostureUpdate).coerceIn(0L, 1000L)
        lastPostureUpdate = now
        val totalG = sqrt(ax * ax + ay * ay + az * az)
        val gyroTotal = sqrt(gx * gx + gy * gy + gz * gz)
        posture = when {
            gyroTotal > 1.4f || abs(totalG - 9.8f) > 2.2f -> "active movement"
            abs(az) > 8.2f -> "flat / desk"
            abs(ax) > 7.0f || abs(ay) > 7.0f -> "vertical / pocket"
            else -> "angled / in hand"
        }
        when (posture) {
            "flat / desk" -> flatMs += dt
            "vertical / pocket" -> verticalMs += dt
            "angled / in hand" -> angledMs += dt
            else -> activeMs += dt
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ballX == 0f && ballY == 0f) {
            ballX = width / 2f; ballY = height / 2f + 160f
            targetX = width / 2f; targetY = height / 2f + 160f
        }
        updateGamePhysics()
        drawBackground(canvas); drawHeader(canvas); drawGameArena(canvas); drawStats(canvas); drawReport(canvas); drawButtons(canvas)
        if (running) postInvalidateOnAnimation()
    }

    private fun updateGamePhysics() {
        val now = System.currentTimeMillis()
        val dt = ((now - lastTime) / 1000f).coerceIn(0.001f, 0.035f)
        lastTime = now
        val tiltForceX = -ax * 80f
        val tiltForceY = ay * 80f
        velX = (velX + tiltForceX * dt) * 0.985f
        velY = (velY + tiltForceY * dt) * 0.985f
        ballX += velX * dt; ballY += velY * dt
        if (ballX < ballRadius) { ballX = ballRadius; velX *= -0.45f }
        if (ballX > width - ballRadius) { ballX = width - ballRadius; velX *= -0.45f }
        if (ballY < 300f + ballRadius) { ballY = 300f + ballRadius; velY *= -0.45f }
        if (ballY > height - 360f - ballRadius) { ballY = height - 360f - ballRadius; velY *= -0.45f }
        val dist = hypot(ballX - targetX, ballY - targetY)
        val gyroJitter = sqrt(gx * gx + gy * gy + gz * gz)
        stabilityScore = (100f - min(100f, (dist / targetRadius) * 55f + gyroJitter * 18f)).coerceIn(0f, 100f)
        if (dist < targetRadius - ballRadius) {
            streakFrames++
            if (streakFrames % 18 == 0) score += 1
            gameMessage = "Balanced: score increases while ball stays in the target."
        } else {
            streakFrames = 0
            gameMessage = "Move carefully: tilt back toward the bullseye."
        }
        if (score > highScore) highScore = score
    }

    private fun drawBackground(canvas: Canvas) {
        bgPaint.color = when {
            stabilityScore > 80 -> Color.rgb(15, 46, 38)
            stabilityScore > 45 -> Color.rgb(17, 24, 39)
            else -> Color.rgb(52, 20, 28)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    }

    private fun drawHeader(canvas: Canvas) {
        textPaint.textSize = 44f
        canvas.drawText("Tilt Balance Lab", 34f, 72f, textPaint)
        smallTextPaint.color = Color.rgb(220, 230, 240); smallTextPaint.textSize = 25f
        canvas.drawText("Tilt-control game powered by phone sensors.", 34f, 110f, smallTextPaint)
        drawChip(canvas, "Score $score", 34f, 135f, Color.rgb(34, 197, 94))
        drawChip(canvas, "High $highScore", 190f, 135f, Color.rgb(59, 130, 246))
        drawChip(canvas, "Stability ${stabilityScore.toInt()}%", 345f, 135f, Color.rgb(245, 158, 11))
    }

    private fun drawChip(canvas: Canvas, text: String, x: Float, y: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
        val r = RectF(x, y, x + 140f + text.length * 4f, y + 48f)
        canvas.drawRoundRect(r, 22f, 22f, p)
        smallTextPaint.color = Color.WHITE; smallTextPaint.textSize = 24f
        canvas.drawText(text, x + 16f, y + 32f, smallTextPaint)
    }

    private fun drawGameArena(canvas: Canvas) {
        val top = 210f; val bottom = height - 335f
        cardPaint.color = Color.rgb(24, 32, 46)
        canvas.drawRoundRect(RectF(22f, top, width - 22f, bottom), 28f, 28f, cardPaint)
        for (i in 1..5) {
            val y = top + i * ((bottom - top) / 6f)
            canvas.drawLine(45f, y, width - 45f, y, linePaint)
        }
        canvas.drawCircle(targetX, targetY, targetRadius, targetInnerPaint)
        canvas.drawCircle(targetX, targetY, targetRadius, targetPaint)
        canvas.drawCircle(targetX, targetY, targetRadius / 2f, targetPaint)
        canvas.drawCircle(ballX, ballY, ballRadius + 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 59, 130, 246) })
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
        smallTextPaint.color = Color.WHITE; smallTextPaint.textSize = 25f
        canvas.drawText(gameMessage, 40f, bottom - 24f, smallTextPaint)
    }

    private fun drawStats(canvas: Canvas) {
        val top = height - 315f
        cardPaint.color = Color.rgb(31, 41, 55)
        canvas.drawRoundRect(RectF(22f, top, width - 22f, top + 145f), 24f, 24f, cardPaint)
        smallTextPaint.color = Color.WHITE; smallTextPaint.textSize = 26f
        canvas.drawText("Live sensor interpretation", 42f, top + 38f, smallTextPaint)
        smallTextPaint.textSize = 23f
        canvas.drawText("Posture: $posture", 42f, top + 72f, smallTextPaint)
        canvas.drawText("Environment: $environment (${lux.toInt()} lux)", 42f, top + 104f, smallTextPaint)
        canvas.drawText("Tilt: x=${one(ax)}  y=${one(ay)}  z=${one(az)}", 42f, top + 136f, smallTextPaint)
    }

    private fun drawReport(canvas: Canvas) {
        val top = height - 160f
        cardPaint.color = Color.rgb(31, 41, 55)
        canvas.drawRoundRect(RectF(22f, top, width - 22f, top + 92f), 24f, 24f, cardPaint)
        smallTextPaint.color = Color.WHITE; smallTextPaint.textSize = 25f
        canvas.drawText("Session posture report", 42f, top + 33f, smallTextPaint)
        smallTextPaint.textSize = 21f
        canvas.drawText("Flat ${sec(flatMs)}s   Vertical ${sec(verticalMs)}s   Angled ${sec(angledMs)}s   Active ${sec(activeMs)}s", 42f, top + 66f, smallTextPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        val y = height - 56f
        buttonPaint.color = Color.rgb(148, 30, 50)
        canvas.drawRoundRect(RectF(22f, y - 42f, width - 22f, y + 8f), 20f, 20f, buttonPaint)
        smallTextPaint.color = Color.WHITE; smallTextPaint.textSize = 25f
        canvas.drawText("Tap here to reset score and move target", 50f, y - 10f, smallTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && event.y > height - 105f) resetGame()
        return true
    }

    private fun resetGame() {
        ballX = width / 2f; ballY = height / 2f + 160f
        velX = 0f; velY = 0f; score = 0; streakFrames = 0
        targetX = width * (0.35f + rand.nextFloat() * 0.30f)
        targetY = height * (0.35f + rand.nextFloat() * 0.25f)
    }

    private fun one(v: Float): String = String.format(Locale.US, "%.1f", v)
    private fun sec(ms: Long): Long = ms / 1000L
}
