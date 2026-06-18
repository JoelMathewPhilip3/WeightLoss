package com.dathadiya.wear

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var mainLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var missionContainer: LinearLayout
    private lateinit var weightText: TextView

    private val docPathCollection = "daThadiyaUsers"
    private val docPathDocument = "joel"

    private var today = todayKey()
    private var missionsByDate: MutableMap<String, Any> = mutableMapOf()
    private var completions: MutableMap<String, Any> = mutableMapOf()
    private var dayResults: MutableMap<String, Any> = mutableMapOf()
    private var weights: MutableList<MutableMap<String, Any>> = mutableListOf()
    private var tempWeight = 98.7

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
        listenToFirestore()
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
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#070b14"))

        mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.gravity = Gravity.CENTER_HORIZONTAL
        mainLayout.setPadding(dp(10), dp(12), dp(10), dp(20))

        val title = TextView(this)
        title.text = "DA THADIYA"
        title.setTextColor(Color.parseColor("#f5c451"))
        title.textSize = 18f
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        mainLayout.addView(title)

        statusText = TextView(this)
        statusText.text = "Syncing..."
        statusText.setTextColor(Color.parseColor("#94a3b8"))
        statusText.textSize = 10f
        statusText.gravity = Gravity.CENTER
        mainLayout.addView(statusText)

        missionContainer = LinearLayout(this)
        missionContainer.orientation = LinearLayout.VERTICAL
        missionContainer.gravity = Gravity.CENTER_HORIZONTAL
        mainLayout.addView(missionContainer)

        val weightTitle = TextView(this)
        weightTitle.text = "WEIGHT"
        weightTitle.setTextColor(Color.WHITE)
        weightTitle.textSize = 15f
        weightTitle.typeface = Typeface.DEFAULT_BOLD
        weightTitle.gravity = Gravity.CENTER
        weightTitle.setPadding(0, dp(18), 0, dp(6))
        mainLayout.addView(weightTitle)

        weightText = TextView(this)
        weightText.text = "--.- kg"
        weightText.setTextColor(Color.parseColor("#f5c451"))
        weightText.textSize = 24f
        weightText.typeface = Typeface.DEFAULT_BOLD
        weightText.gravity = Gravity.CENTER
        mainLayout.addView(weightText)

        val weightButtons = LinearLayout(this)
        weightButtons.orientation = LinearLayout.HORIZONTAL
        weightButtons.gravity = Gravity.CENTER
        weightButtons.setPadding(0, dp(8), 0, dp(8))

        val minus = makeSmallButton("-0.1")
        val plus = makeSmallButton("+0.1")

        minus.setOnClickListener {
            tempWeight = roundOne(tempWeight - 0.1)
            updateWeightText()
        }

        plus.setOnClickListener {
            tempWeight = roundOne(tempWeight + 0.1)
            updateWeightText()
        }

        weightButtons.addView(minus)
        weightButtons.addView(plus)
        mainLayout.addView(weightButtons)

        val saveWeight = makeWideButton("SAVE WEIGHT")
        saveWeight.setOnClickListener {
            saveWeightToFirestore()
        }
        mainLayout.addView(saveWeight)

        scroll.addView(mainLayout)
        setContentView(scroll)
    }

    private fun listenToFirestore() {
        db.collection(docPathCollection)
            .document(docPathDocument)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    statusText.text = "Sync error"
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    createInitialDocument()
                    return@addSnapshotListener
                }

                val data = snapshot.data ?: return@addSnapshotListener

                missionsByDate = (data["missionsByDate"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
                completions = (data["completions"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
                dayResults = (data["dayResults"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()

                val rawWeights = data["weights"] as? List<Map<String, Any>> ?: emptyList()
                weights = rawWeights.map { it.toMutableMap() }.toMutableList()

                ensureTodayMissions()
                loadLatestWeight()
                renderMissions()
                statusText.text = "Sync connected"
            }
    }

    private fun createInitialDocument() {
        missionsByDate[today] = defaultMissions
        completions[today] = mutableMapOf<String, Boolean>()
        weights = mutableListOf(
            mutableMapOf(
                "date" to today,
                "weight" to tempWeight
            )
        )

        val data = mapOf(
            "missionsByDate" to missionsByDate,
            "completions" to completions,
            "dayResults" to dayResults,
            "weights" to weights,
            "targetWeight" to 91,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection(docPathCollection)
            .document(docPathDocument)
            .set(data, SetOptions.merge())
    }

    private fun ensureTodayMissions() {
        if (!missionsByDate.containsKey(today)) {
            missionsByDate[today] = defaultMissions
            saveCoreData()
        }
    }

    private fun todayMissions(): List<String> {
        val raw = missionsByDate[today]
        return when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            else -> defaultMissions
        }
    }

    private fun todayCompletions(): MutableMap<String, Any> {
        val raw = completions[today]
        return when (raw) {
            is Map<*, *> -> raw.entries.associate {
                it.key.toString() to (it.value as? Boolean ?: false)
            }.toMutableMap()
            else -> mutableMapOf()
        }
    }

    private fun renderMissions() {
        missionContainer.removeAllViews()

        val missions = todayMissions()
        val doneMap = todayCompletions()

        missions.forEach { mission ->
            val isDone = doneMap[mission] as? Boolean ?: false
            val circle = makeMissionCircle(mission, isDone)

            circle.setOnClickListener {
                toggleMission(mission)
            }

            missionContainer.addView(circle)
        }
    }

    private fun toggleMission(mission: String) {
        val doneMap = todayCompletions()
        val current = doneMap[mission] as? Boolean ?: false
        doneMap[mission] = !current
        completions[today] = doneMap

        updateDayResult()

        db.collection(docPathCollection)
            .document(docPathDocument)
            .set(
                mapOf(
                    "completions" to completions,
                    "dayResults" to dayResults,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                statusText.text = "Mission saved"
            }
            .addOnFailureListener {
                statusText.text = "Save failed"
            }

        renderMissions()
    }

    private fun updateDayResult() {
        val missions = todayMissions()
        val doneMap = todayCompletions()

        val missed = missions.filter { mission ->
            !(doneMap[mission] as? Boolean ?: false)
        }

        dayResults[today] = mapOf(
            "status" to if (missed.isEmpty()) "green" else "red",
            "missed" to missed
        )
    }

    private fun loadLatestWeight() {
        if (weights.isEmpty()) {
            tempWeight = 98.7
            updateWeightText()
            return
        }

        val latest = weights.maxByOrNull {
            it["date"].toString()
        }

        val value = latest?.get("weight")
        tempWeight = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 98.7
            else -> 98.7
        }

        tempWeight = roundOne(tempWeight)
        updateWeightText()
    }

    private fun saveWeightToFirestore() {
        val todayDate = today

        weights.removeAll {
            it["date"].toString() == todayDate
        }

        weights.add(
            mutableMapOf(
                "date" to todayDate,
                "weight" to tempWeight
            )
        )

        weights.sortBy {
            it["date"].toString()
        }

        db.collection(docPathCollection)
            .document(docPathDocument)
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
                statusText.text = "Weight failed"
            }
    }

    private fun saveCoreData() {
        db.collection(docPathCollection)
            .document(docPathDocument)
            .set(
                mapOf(
                    "missionsByDate" to missionsByDate,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
    }

    private fun makeMissionCircle(text: String, done: Boolean): TextView {
        val view = TextView(this)

        val size = dp(185)
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(0, dp(12), 0, dp(12))
        view.layoutParams = params

        view.text = if (done) "✓\n$text" else "✕\n$text"
        view.gravity = Gravity.CENTER
        view.textSize = 14f
        view.typeface = Typeface.DEFAULT_BOLD
        view.setTextColor(Color.WHITE)
        view.setPadding(dp(18), dp(18), dp(18), dp(18))

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(
            if (done) Color.parseColor("#15803d")
            else Color.parseColor("#991b1b")
        )
        bg.setStroke(dp(3), Color.parseColor("#f5c451"))

        view.background = bg

        return view
    }

    private fun makeSmallButton(text: String): TextView {
        val button = TextView(this)
        val params = LinearLayout.LayoutParams(dp(76), dp(46))
        params.setMargins(dp(4), 0, dp(4), 0)
        button.layoutParams = params

        button.text = text
        button.gravity = Gravity.CENTER
        button.textSize = 16f
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(Color.parseColor("#111827"))

        val bg = GradientDrawable()
        bg.cornerRadius = dp(22).toFloat()
        bg.setColor(Color.parseColor("#f5c451"))
        button.background = bg

        return button
    }

    private fun makeWideButton(text: String): TextView {
        val button = TextView(this)
        val params = LinearLayout.LayoutParams(dp(180), dp(48))
        params.setMargins(0, dp(4), 0, dp(12))
        button.layoutParams = params

        button.text = text
        button.gravity = Gravity.CENTER
        button.textSize = 13f
        button.typeface = Typeface.DEFAULT_BOLD
        button.setTextColor(Color.parseColor("#111827"))

        val bg = GradientDrawable()
        bg.cornerRadius = dp(24).toFloat()
        bg.setColor(Color.parseColor("#f5c451"))
        button.background = bg

        return button
    }

    private fun updateWeightText() {
        weightText.text = String.format(Locale.US, "%.1f kg", tempWeight)
    }

    private fun roundOne(value: Double): Double {
        return String.format(Locale.US, "%.1f", value).toDouble()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun todayKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // This matches your website's current JS date style using toISOString().
        formatter.timeZone = TimeZone.getTimeZone("UTC")

        return formatter.format(Date())
    }
}
