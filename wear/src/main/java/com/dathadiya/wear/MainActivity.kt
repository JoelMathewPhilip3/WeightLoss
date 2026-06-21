package com.dathadiya.wear

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var summaryCard: LinearLayout
    private lateinit var missionContainer: LinearLayout
    private lateinit var weightScreen: LinearLayout

    private val collectionName = "daThadiyaUsers"
    private val documentName = "joel"
    private var currentAtlantaDate = todayKey()
    private var selectedDate = currentAtlantaDate

    private var missionsByDate: MutableMap<String, Any> = mutableMapOf()
    private var completions: MutableMap<String, Any> = mutableMapOf()
    private var dayResults: MutableMap<String, Any> = mutableMapOf()
    private var permanentMissions: MutableList<String> = mutableListOf()
    private var missionRules: List<Map<String, Any>> = emptyList()
    private var excludedMissionsByDate: MutableMap<String, Any> = mutableMapOf()
    private var weights: MutableList<MutableMap<String, Any>> = mutableListOf()
    private var targetWeight: Double = 91.0
    private var tempWeight: Double = 98.7

    private val defaultMissions = listOf(
        "10,000 steps",
        "Drink three glasses of water",
        "Read 10 minutes",
        "Clean room by end of day",
        "Read the Bible and say a prayer",
        "Brush teeth"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFirebase()
        buildUi()
        fetchOnce()
    }

    override fun onResume() {
        super.onResume()
        val newAtlantaDate = todayKey()
        if (selectedDate == currentAtlantaDate) {
            selectedDate = newAtlantaDate
        }
        currentAtlantaDate = newAtlantaDate
        if (::db.isInitialized) fetchOnce()
    }

    private fun initFirebase() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAFhJsKzgnTDxOoFV9DqO22rR29fLP6DeY")
                .setApplicationId("1:544005056031:web:16c9127e3a3c221c8e572a")
                .setProjectId("da-thadiya")
                .setStorageBucket("da-thadiya.firebasestorage.app")
                .setGcmSenderId("544005056031")
                .build()

            FirebaseApp.initializeApp(this, options)
        }

        db = FirebaseFirestore.getInstance()
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(BLACK)

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(8), dp(10), dp(8), dp(24))

        val title = text("DA THADIYA", 18f, GOLD, true)
        title.gravity = Gravity.CENTER
        root.addView(title, matchWrap())

        statusText = text("Loading...", 10f, MUTED, false)
        statusText.gravity = Gravity.CENTER
        root.addView(statusText, matchWrap())

        summaryCard = LinearLayout(this)
        summaryCard.orientation = LinearLayout.VERTICAL
        summaryCard.gravity = Gravity.CENTER
        summaryCard.setPadding(0, dp(12), 0, dp(14))
        root.addView(summaryCard, matchWrap(top = 12))

        missionContainer = LinearLayout(this)
        missionContainer.orientation = LinearLayout.VERTICAL
        missionContainer.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(missionContainer, matchWrap())

        weightScreen = LinearLayout(this)
        weightScreen.orientation = LinearLayout.VERTICAL
        weightScreen.gravity = Gravity.CENTER_HORIZONTAL
        weightScreen.setPadding(0, dp(12), 0, dp(8))
        root.addView(weightScreen, matchWrap())

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun fetchOnce() {
        statusText.text = "Syncing..."

        db.collection(collectionName)
            .document(documentName)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    applyCloudData(snapshot.data ?: emptyMap())
                    ensureSelectedDateMissions()
                    renderAll()
                    statusText.text = "Sync: loaded"
                } else {
                    createInitialDocument()
                }
            }
            .addOnFailureListener {
                statusText.text = "Sync: offline cache"
                renderAll()
            }
    }

    private fun applyCloudData(data: Map<String, Any>) {
        missionsByDate = (data["missionsByDate"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
        completions = (data["completions"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
        dayResults = (data["dayResults"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()

        permanentMissions = (data["permanentMissions"] as? List<*>)
            ?.mapNotNull { it?.toString() }
            ?.toMutableList()
            ?: mutableListOf()

        missionRules = (data["missionRules"] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any> }
            ?: emptyList()

        excludedMissionsByDate =
            (data["excludedMissionsByDate"] as? Map<String, Any>)?.toMutableMap()
                ?: mutableMapOf()

        targetWeight = when (val t = data["targetWeight"]) {
            is Number -> t.toDouble()
            is String -> t.toDoubleOrNull() ?: 91.0
            else -> 91.0
        }

        val rawWeights = data["weights"] as? List<Map<String, Any>> ?: emptyList()
        weights = rawWeights.map { it.toMutableMap() }.toMutableList()

        loadLatestWeight()
    }

    private fun createInitialDocument() {
        val currentDate = todayKey()
        selectedDate = currentDate
        missionsByDate[currentDate] = defaultMissions
        completions[currentDate] = mutableMapOf<String, Boolean>()
        weights = mutableListOf(
            mutableMapOf(
                "date" to currentDate,
                "weight" to tempWeight
            )
        )

        val data = mapOf(
            "missionsByDate" to missionsByDate,
            "completions" to completions,
            "dayResults" to dayResults,
            "permanentMissions" to permanentMissions,
            "weights" to weights,
            "targetWeight" to targetWeight,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection(collectionName)
            .document(documentName)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                renderAll()
                statusText.text = "Sync: created"
            }
    }

    private fun ensureSelectedDateMissions() {
        if (!completions.containsKey(selectedDate)) {
            completions[selectedDate] = mutableMapOf<String, Boolean>()
        }
    }

    private fun renderAll() {
        renderSummary()
        renderMissions()
        renderWeightScreen()
    }

    private fun renderSummary() {
        summaryCard.removeAllViews()
        summaryCard.background = null
        summaryCard.orientation = LinearLayout.VERTICAL
        summaryCard.gravity = Gravity.CENTER

        val stats = missionStats()
        val pctColor = progressColor(stats.percent)

        val circleRow = LinearLayout(this)
        circleRow.orientation = LinearLayout.HORIZONTAL
        circleRow.gravity = Gravity.CENTER

        val ring = ProgressRingView(this)
        ring.progress = stats.percent
        ring.ringColor = pctColor

        val missionParams = LinearLayout.LayoutParams(dp(88), dp(88))
        missionParams.setMargins(dp(2), 0, dp(2), 0)
        circleRow.addView(ring, missionParams)

        val weightCircle = LinearLayout(this)
        weightCircle.orientation = LinearLayout.VERTICAL
        weightCircle.gravity = Gravity.CENTER
        weightCircle.setPadding(dp(8), dp(8), dp(8), dp(8))
        weightCircle.background = roundedCard("#101418", CYAN, 44f)

        val weightLabel = text("WEIGHT", 10f, MUTED, true)
        weightLabel.gravity = Gravity.CENTER
        weightCircle.addView(weightLabel, matchWrap())

        val weightValue = text(
            String.format(Locale.US, "%.1f kg", tempWeight),
            15f,
            CYAN,
            true
        )
        weightValue.gravity = Gravity.CENTER
        weightCircle.addView(weightValue, matchWrap(top = 3))

        val weightParams = LinearLayout.LayoutParams(dp(88), dp(88))
        weightParams.setMargins(dp(2), 0, dp(2), 0)
        circleRow.addView(weightCircle, weightParams)

        summaryCard.addView(circleRow, matchWrap())

        val navigation = LinearLayout(this)
        navigation.orientation = LinearLayout.HORIZONTAL
        navigation.gravity = Gravity.CENTER

        val previousDay = pillButton("-", DARK_CARD, WHITE)
        previousDay.setOnClickListener { changeMissionDay(-1) }
        navigation.addView(previousDay, fixedWrap(42, 40))

        val dateLabel = text(selectedDateLabel(), 13f, GOLD, true)
        dateLabel.gravity = Gravity.CENTER
        navigation.addView(dateLabel, fixedWrap(92, 40))

        val nextDay = pillButton("+", DARK_CARD, WHITE)
        nextDay.setOnClickListener { changeMissionDay(1) }
        navigation.addView(nextDay, fixedWrap(42, 40))

        summaryCard.addView(navigation, matchWrap(top = 10))
    }

    private fun renderMissions() {
        missionContainer.removeAllViews()

        val missions = selectedMissions()
        val doneMap = selectedCompletions()

        missions.forEach { mission ->
            val done = doneMap[mission] as? Boolean ?: false
            val card = missionCard(mission, done)

            card.setOnClickListener {
                toggleMission(mission)
            }

            missionContainer.addView(card)
        }

        val stats = missionStats()
        if (stats.percent == 100) {
            val perfect = text(
                "✓ Perfect Day",
                18f,
                GREEN,
                true
            )
            perfect.gravity = Gravity.CENTER
            perfect.background = roundedCard("#06160d", GREEN, 20f)
            perfect.setPadding(dp(16), dp(18), dp(16), dp(18))
            missionContainer.addView(perfect, matchWrap(top = 12, bottom = 10))
        }
    }

    private fun renderWeightScreen() {
        weightScreen.removeAllViews()

        val title = text("Quick Weight", 16f, WHITE, true)
        title.gravity = Gravity.CENTER
        weightScreen.addView(title, matchWrap())

        val value = text(String.format(Locale.US, "%.1f kg", tempWeight), 32f, CYAN, true)
        value.gravity = Gravity.CENTER
        weightScreen.addView(value, matchWrap(top = 4, bottom = 8))

        val row1 = LinearLayout(this)
        row1.gravity = Gravity.CENTER
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(weightButton("-0.1") { adjustWeight(-0.1) })
        row1.addView(weightButton("+0.1") { adjustWeight(0.1) })
        weightScreen.addView(row1, matchWrap())

        val row2 = LinearLayout(this)
        row2.gravity = Gravity.CENTER
        row2.orientation = LinearLayout.HORIZONTAL
        row2.addView(weightButton("-0.5") { adjustWeight(-0.5) })
        row2.addView(weightButton("+0.5") { adjustWeight(0.5) })
        weightScreen.addView(row2, matchWrap(top = 6))

        val save = pillButton("SAVE", GOLD, BLACK)
        save.setOnClickListener {
            saveWeight()
        }
        weightScreen.addView(save, fixedWrap(160, 48, top = 10, bottom = 8))

        val refresh = pillButton("REFRESH", DARK_CARD, WHITE)
        refresh.setOnClickListener {
            fetchOnce()
        }
        weightScreen.addView(refresh, fixedWrap(160, 42, top = 4))
    }

    private fun missionCard(mission: String, done: Boolean): LinearLayout {
        val size = dp(190)

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.setPadding(dp(18), dp(16), dp(18), dp(16))

        val color = if (done) GREEN else GOLD
        val fill = if (done) "#052e16" else "#101418"

        card.background = roundedCard(fill, color, 95f)

        val title = text(shortMission(mission), 19f, WHITE, true)
        title.gravity = Gravity.CENTER
        title.maxLines = 4
        card.addView(title, matchWrap())

        val tap = text(if (done) "Tap to undo" else "Tap to complete", 11f, MUTED, false)
        tap.gravity = Gravity.CENTER
        card.addView(tap, matchWrap(top = 12))

        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(0, dp(12), 0, dp(12))
        card.layoutParams = params

        return card
    }

    private fun toggleMission(mission: String) {
        val statsBefore = missionStats()

        val doneMap = selectedCompletions()
        val current = doneMap[mission] as? Boolean ?: false
        val newValue = !current

        doneMap[mission] = newValue
        completions[selectedDate] = doneMap

        updateDayResult()
        renderAll()

        val statsAfter = missionStats()

        when {
            statsAfter.percent == 100 && statsBefore.percent != 100 -> hapticStrong()
            newValue -> hapticComplete()
            else -> hapticUndo()
        }

        db.collection(collectionName)
            .document(documentName)
            .set(
                mapOf(
                        "completions" to mapOf(selectedDate to doneMap),
                        "dayResults" to mapOf(selectedDate to dayResults[selectedDate]),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                statusText.text = "Mission saved"
            }
            .addOnFailureListener {
                statusText.text = "Mission offline"
            }
    }

    private fun saveWeight() {
        val weightDate = todayKey()

        weights.removeAll {
            it["date"].toString() == weightDate
        }

        weights.add(
            mutableMapOf(
                "date" to weightDate,
                "weight" to tempWeight
            )
        )

        weights.sortBy {
            it["date"].toString()
        }

        hapticComplete()

        db.collection(collectionName)
            .document(documentName)
            .set(
                mapOf(
                    "weights" to weights,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                statusText.text = "Weight saved"
            }
            .addOnFailureListener {
                statusText.text = "Weight offline"
            }
    }

    private fun adjustWeight(delta: Double) {
        tempWeight = roundOne(tempWeight + delta)
        hapticTiny()
        renderAll()
    }

    private fun updateDayResult() {
        val missions = selectedMissions()
        val doneMap = selectedCompletions()

        val missed = missions.filter {
            !(doneMap[it] as? Boolean ?: false)
        }

        val complete = missions.size - missed.size
        val pct = if (missions.isEmpty()) {
            0
        } else {
            ((complete.toDouble() / missions.size) * 100).roundToInt()
        }

        dayResults[selectedDate] = mapOf(
            "status" to if (pct == 100) "green" else "partial",
            "missed" to missed,
            "complete" to complete,
            "total" to missions.size,
            "pct" to pct
        )
    }

    private fun selectedMissions(): List<String> {
        val raw = missionsByDate[selectedDate]

        val savedMissions = when (raw) {
            is List<*> -> raw.mapNotNull {
                it?.toString()
            }
            else -> emptyList()
        }

        val missions = linkedSetOf<String>()
        missions.addAll(defaultMissions)
        missions.addAll(permanentMissions)
        missions.addAll(savedMissions)

        missionRules.filter { missionRuleApplies(it, selectedDate) }.forEach { rule ->
            val name = rule["name"]?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                if (rule["action"]?.toString() == "remove") {
                    missions.remove(name)
                } else {
                    missions.add(name)
                }
            }
        }

        val excluded = when (val value = excludedMissionsByDate[selectedDate]) {
            is List<*> -> value.mapNotNull { it?.toString() }
            else -> emptyList()
        }
        missions.removeAll(excluded.toSet())

        return missions.toList()
    }

    private fun missionRuleApplies(rule: Map<String, Any>, dateKey: String): Boolean {
        val startDate = rule["startDate"]?.toString().orEmpty()
        val duration = rule["duration"]?.toString().orEmpty()
        val endDate = rule["endDate"]?.toString().orEmpty()

        if (startDate.isEmpty()) return false
        if (duration == "one-day") return dateKey == startDate
        if (dateKey < startDate) return false
        if (duration == "temporary" && endDate.isNotEmpty() && dateKey > endDate) {
            return false
        }

        val days = (rule["days"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?: emptyList()
        if (days.isEmpty()) return true

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val atlanta = TimeZone.getTimeZone("America/New_York")
        formatter.timeZone = atlanta
        val date = formatter.parse(dateKey) ?: return false
        val calendar = java.util.Calendar.getInstance(atlanta)
        calendar.time = date
        val weekday = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1

        return weekday in days
    }

    private fun selectedCompletions(): MutableMap<String, Any> {
        val raw = completions[selectedDate]

        return when (raw) {
            is Map<*, *> -> raw.entries.associate {
                it.key.toString() to (it.value as? Boolean ?: false)
            }.toMutableMap()
            else -> mutableMapOf()
        }
    }

    private fun missionStats(): MissionStats {
        val missions = selectedMissions()
        val done = selectedCompletions()

        val complete = missions.count {
            done[it] as? Boolean ?: false
        }

        val pct = if (missions.isEmpty()) {
            0
        } else {
            ((complete.toDouble() / missions.size) * 100).roundToInt()
        }

        return MissionStats(complete, missions.size, pct)
    }

    private fun changeMissionDay(days: Int) {
        selectedDate = addDays(selectedDate, days)
        ensureSelectedDateMissions()
        hapticTiny()
        renderAll()
    }

    private fun selectedDateLabel(): String {
        if (selectedDate == todayKey()) return "Today"

        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val output = SimpleDateFormat("MMM d", Locale.US)
        val atlanta = TimeZone.getTimeZone("America/New_York")
        input.timeZone = atlanta
        output.timeZone = atlanta

        return input.parse(selectedDate)?.let(output::format) ?: selectedDate
    }

    private fun loadLatestWeight() {
        if (weights.isEmpty()) {
            tempWeight = 98.7
            return
        }

        val latest = weights.maxByOrNull {
            it["date"].toString()
        }

        tempWeight = when (val value = latest?.get("weight")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 98.7
            else -> 98.7
        }

        tempWeight = roundOne(tempWeight)
    }

    private fun shortMission(mission: String): String {
        return mission
            .replace("Drink three glasses of water", "Drink three glasses\nof water")
            .replace("Read the Bible and say a prayer", "Read Bible\nand pray")
            .replace("Clean room by end of day", "Clean room")
            .replace("10,000 steps", "10,000 steps")
            .replace("Read 10 minutes", "Read 10 minutes")
            .replace("Brush teeth", "Brush teeth")
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = value
        t.textSize = sp
        t.setTextColor(color)

        if (bold) {
            t.typeface = Typeface.DEFAULT_BOLD
        }

        return t
    }

    private fun pillButton(label: String, bgColor: Int, textColor: Int): TextView {
        val b = text(label, 14f, textColor, true)
        b.gravity = Gravity.CENTER
        b.background = roundedFill(bgColor, 24f)
        return b
    }

    private fun weightButton(label: String, click: () -> Unit): TextView {
        val b = pillButton(label, DARK_CARD, WHITE)
        b.setOnClickListener {
            click()
        }

        val params = LinearLayout.LayoutParams(dp(76), dp(44))
        params.setMargins(dp(4), 0, dp(4), 0)
        b.layoutParams = params

        return b
    }

    private fun roundedCard(fill: String, stroke: Int, radius: Float): GradientDrawable {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(radius.toInt()).toFloat()
        bg.setColor(Color.parseColor(fill))
        bg.setStroke(dp(2), stroke)
        return bg
    }

    private fun roundedFill(fill: Int, radius: Float): GradientDrawable {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(radius.toInt()).toFloat()
        bg.setColor(fill)
        return bg
    }

    private fun progressColor(percent: Int): Int {
        return when {
            percent >= 80 -> GREEN
            percent >= 40 -> GOLD
            else -> RED
        }
    }

    private fun hapticTiny() = vibrate(12)
    private fun hapticComplete() = vibrate(30)
    private fun hapticUndo() = vibrate(18)
    private fun hapticStrong() = vibrate(75)

    private fun vibrate(ms: Long) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        ms,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(0, dp(top), 0, dp(bottom))
        return p
    }

    private fun fixedWrap(w: Int, h: Int, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(dp(w), dp(h))
        p.setMargins(0, dp(top), 0, dp(bottom))
        return p
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun roundOne(v: Double): Double {
        return String.format(Locale.US, "%.1f", v).toDouble()
    }

    private fun todayKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("America/New_York")
        return formatter.format(Date())
    }

    private fun addDays(dateKey: String, days: Int): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val atlanta = TimeZone.getTimeZone("America/New_York")
        formatter.timeZone = atlanta

        val date = formatter.parse(dateKey) ?: Date()
        val cal = java.util.Calendar.getInstance(atlanta)
        cal.time = date
        cal.add(java.util.Calendar.DATE, days)

        return formatter.format(cal.time)
    }

    data class MissionStats(
        val done: Int,
        val total: Int,
        val percent: Int
    )

    companion object {
        val BLACK = Color.parseColor("#000000")
        val DARK_CARD = Color.parseColor("#101418")
        val WHITE = Color.parseColor("#f8fafc")
        val MUTED = Color.parseColor("#94a3b8")
        val GOLD = Color.parseColor("#f5c451")
        val GREEN = Color.parseColor("#22c55e")
        val RED = Color.parseColor("#ef4444")
        val CYAN = Color.parseColor("#38bdf8")
    }
}

class ProgressRingView(context: android.content.Context) : View(context) {
    var progress: Int = 0
    var ringColor: Int = Color.parseColor("#f5c451")

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        val stroke = width * 0.10f
        val pad = stroke / 2f + 2f
        val rect = android.graphics.RectF(
            pad,
            pad,
            width - pad,
            height - pad
        )

        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = android.graphics.Paint.Cap.ROUND
        paint.color = Color.parseColor("#1f2937")
        canvas.drawArc(rect, -90f, 360f, false, paint)

        paint.color = ringColor
        canvas.drawArc(
            rect,
            -90f,
            progress.coerceIn(0, 100) * 3.6f,
            false,
            paint
        )

        paint.style = android.graphics.Paint.Style.FILL
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = width * 0.23f
        paint.color = Color.parseColor("#f8fafc")
        canvas.drawText(
            "$progress%",
            width / 2f,
            height / 2f + paint.textSize / 5f,
            paint
        )

        paint.textSize = width * 0.09f
        paint.color = Color.parseColor("#94a3b8")
        canvas.drawText(
            "MISSIONS",
            width / 2f,
            height / 2f + width * 0.22f,
            paint
        )
    }
}
