package com.quittrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val type: String,
    val time: Long,
    val intensity: Int = 0,
    val source: String = "",
    val context: String = "",
    val morning: Boolean = false
)

data class DailyReview(
    val date: String,
    val entries: List<LogEntry>
)

data class PlanPhase(
    val phase: Int,
    val name: String,
    val dayStart: Int,
    val dayEnd: Int,
    val description: String,
    val focus: List<String>,
    val todaysFocus: String,
    val action: String
)

private val planPhases = listOf(
    PlanPhase(
        phase = 1,
        name = "Foundation",
        dayStart = 1,
        dayEnd = 1,
        description = "Start by understanding your current smoking pattern without trying to change everything at once.",
        focus = listOf(
            "Log every cigarette you smoke.",
            "Mark which cigarettes are morning cigarettes.",
            "Record cravings when they happen.",
            "Notice when, where, and why you smoke."
        ),
        todaysFocus = "Observe, don't judge.",
        action = "Start today's tracking"
    ),

    PlanPhase(
        phase = 2,
        name = "Preparation",
        dayStart = 2,
        dayEnd = 2,
        description = "Identify the situations and routines that make smoking feel automatic.",
        focus = listOf(
            "Review your smoking pattern.",
            "Identify your strongest triggers.",
            "Notice routines connected to smoking.",
            "Think about difficult situations ahead."
        ),
        todaysFocus = "Know your triggers before they surprise you.",
        action = "Review your triggers"
    ),

    PlanPhase(
        phase = 3,
        name = "Strengthening",
        dayStart = 3,
        dayEnd = 3,
        description = "Start practicing ways to handle cravings without immediately reaching for a cigarette.",
        focus = listOf(
            "Practice delaying a cigarette.",
            "Use the emergency craving tools.",
            "Notice how cravings change over time.",
            "Record what helps."
        ),
        todaysFocus = "A craving is something you can work through, not an order you have to obey.",
        action = "Practice a craving strategy"
    ),

    PlanPhase(
        phase = 4,
        name = "Building skills",
        dayStart = 4,
        dayEnd = 36,
        description = "Build and repeat the skills you'll need to become smoke-free.",
        focus = listOf(
            "Manage cravings.",
            "Delay cigarettes.",
            "Recognize and avoid triggers.",
            "Change smoking routines.",
            "Handle difficult situations.",
            "Increase control over smoking decisions."
        ),
        todaysFocus = "Practice today's skill and keep working toward your quit day.",
        action = "Start today's exercise"
    ),

    PlanPhase(
        phase = 5,
        name = "Quit day",
        dayStart = 37,
        dayEnd = 37,
        description = "Today is the transition to being smoke-free.",
        focus = listOf(
            "Don't smoke.",
            "Expect cravings and use the skills you've practiced.",
            "Take each craving one at a time.",
            "Use Emergency craving help when needed.",
            "Record difficult moments and what helped."
        ),
        todaysFocus = "You don't need to get through forever today. You only need to get through today.",
        action = "Start your smoke-free day"
    ),

    PlanPhase(
        phase = 6,
        name = "Smoke-free maintenance",
        dayStart = 38,
        dayEnd = 40,
        description = "Strengthen your smoke-free routine and prepare to maintain it after the 40-day program.",
        focus = listOf(
            "Keep tracking cravings and difficult situations.",
            "Identify any remaining triggers.",
            "Reinforce the strategies that worked best.",
            "Prepare for future high-risk situations."
        ),
        todaysFocus = "Protect what you've built.",
        action = "Review your progress"
    )
)

private val QuitGreen = androidx.compose.ui.graphics.Color(0xFF850707)
private val QuitGreenDark = androidx.compose.ui.graphics.Color(0xFF850707)
private val QuitGreenLight = androidx.compose.ui.graphics.Color(0xFF121111)
private val QuitOrange = androidx.compose.ui.graphics.Color(0xFF850707)
private val QuitOrangeLight = androidx.compose.ui.graphics.Color(0xFF121111)
private val WarmBackground = androidx.compose.ui.graphics.Color(0xFF000000)
private val CardWhite = androidx.compose.ui.graphics.Color(0xFF121111)
private val TextDark = androidx.compose.ui.graphics.Color(0xFFE6E7E8)
private val TextMuted = androidx.compose.ui.graphics.Color(0xFFE6E7E8)
private val BorderLight = androidx.compose.ui.graphics.Color(0xFF121111)
private val SystemFontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif


/* ============================================================
   DAILY REVIEW ALARM SYSTEM
   ============================================================ */

private const val REVIEW_ALARM_REQUEST_CODE = 159
private const val PREFS_NAME = "quit_track"
private const val MANUAL_REVIEW_SAVED_DATE = "manualReviewSavedDate"


/*
 * Returns today's date at midnight.
 */
private fun todayMidnight(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}


/*
 * Returns a Calendar representing a specific date at 23:59:00.
 */
private fun reviewAlarmTime(
    daysFromToday: Int = 0
): Calendar {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (daysFromToday != 0) {
            add(Calendar.DAY_OF_YEAR, daysFromToday)
        }
    }
}


/*
 * Creates the PendingIntent used by the daily review alarm.
 */
private fun dailyReviewPendingIntent(
    context: Context
): PendingIntent {

    val intent = Intent(
        context,
        DailyReviewAlarmReceiver::class.java
    )

    return PendingIntent.getBroadcast(
        context,
        REVIEW_ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
    )
}


/*
 * Cancels the current daily review alarm.
 */
fun cancelDailyReviewAlarm(
    context: Context
) {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    alarmManager.cancel(
        dailyReviewPendingIntent(context)
    )
}


/*
 * Schedules the next daily review alarm.

 * RULE:
 *
 * If today's review has already been manually saved,
 * schedule tomorrow at 23:59.
 *
 * Otherwise:
 *   - if today's 23:59 has not happened yet,
 *     schedule today at 23:59.
 *   - otherwise schedule tomorrow at 23:59.
 */
fun scheduleDailyReview(
    context: Context
) {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val today = fmtDate(todayMidnight())

    val manualSavedDate =
        prefs.getString(
            MANUAL_REVIEW_SAVED_DATE,
            ""
        ) ?: ""

    val now = System.currentTimeMillis()

    val alarmCalendar: Calendar

    if (manualSavedDate == today) {

        /*
         * Today's review was manually saved.
         * Therefore there must NOT be another automatic
         * save tonight.
         *
         * Schedule tomorrow instead.
         */
        alarmCalendar = reviewAlarmTime(1)

    } else {

        /*
         * No manual save for today.
         *
         * Schedule today's 23:59 if it is still in the future.
         * Otherwise schedule tomorrow.
         */
        val todayAlarm = reviewAlarmTime(0)

        alarmCalendar =
            if (todayAlarm.timeInMillis > now) {
                todayAlarm
            } else {
                reviewAlarmTime(1)
            }
    }

    val pendingIntent =
        dailyReviewPendingIntent(context)

    /*
     * Cancel first so that an old alarm can never remain
     * alongside the new one.
     */
    alarmManager.cancel(pendingIntent)

    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !alarmManager.canScheduleExactAlarms()
    ) {

        /*
         * Exact alarm permission has not been granted.
         *
         * We still schedule an alarm so the feature remains
         * functional, but Android may deliver it slightly later.
         */
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarmCalendar.timeInMillis,
            pendingIntent
        )

    } else {

        /*
         * Exact 23:59 alarm.
         */
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarmCalendar.timeInMillis,
            pendingIntent
        )
    }
}


/*
 * Automatically saves the review for today.
 *
 * This function is called by the BroadcastReceiver at 23:59.
 */
private fun autoSaveDailyReview(
    context: Context
) {
    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val midnight = todayMidnight()
    val todayDate = fmtDate(midnight)

    /*
     * Safety check:
     *
     * If a manual save has already happened today,
     * DO NOT overwrite it with an automatic save.
     */
    val manualSavedDate =
        prefs.getString(
            MANUAL_REVIEW_SAVED_DATE,
            ""
        ) ?: ""

    if (manualSavedDate == todayDate) {

        scheduleDailyReview(context)
        return
    }

    val entriesJson =
        prefs.getString(
            "entries",
            "[]"
        ) ?: "[]"

    val entriesArray = JSONArray(entriesJson)

    val todayEntries = buildList {

        for (i in 0 until entriesArray.length()) {

            val o =
                entriesArray.getJSONObject(i)

            val entry = LogEntry(
                type = o.getString("type"),
                time = o.getLong("time"),
                intensity = o.optInt("intensity"),
                source = o.optString("source"),
                context = o.optString("context"),
                morning = o.optBoolean(
                    "morning",
                    false
                )
            )

            if (sameDay(entry.time, midnight)) {
                add(entry)
            }
        }
    }

    val review = DailyReview(
        date = todayDate,
        entries = todayEntries
    )

    val reviews =
        JSONArray(
            prefs.getString(
                "dailyReviews",
                "[]"
            ) ?: "[]"
        )

    var replaced = false

    for (i in 0 until reviews.length()) {

        val existing =
            reviews.getJSONObject(i)

        if (
            existing.optString("date") ==
            review.date
        ) {

            reviews.put(
                i,
                reviewToJson(review)
            )

            replaced = true
            break
        }
    }

    if (!replaced) {
        reviews.put(
            reviewToJson(review)
        )
    }

    prefs.edit()
        .putString(
            "dailyReviews",
            reviews.toString()
        )
        .apply()

    /*
     * The automatic save is complete.
     * Schedule tomorrow at exactly 23:59.
     */
    scheduleDailyReview(context)
}


/*
 * Receives the 23:59 alarm.
 */
class DailyReviewAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        if (
            intent?.action == null ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_DATE_CHANGED
        ) {
            /*
             * For normal alarm delivery intent.action is null.
             * We also allow the system time/date broadcasts to
             * reschedule the next review correctly.
             */
        }

        autoSaveDailyReview(context)
    }
}


/*
 * Reschedules the daily review alarm after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        if (
            intent?.action ==
            Intent.ACTION_BOOT_COMPLETED
        ) {
            scheduleDailyReview(context)
        }
    }
}


/* ============================================================
   MAIN ACTIVITY
   ============================================================ */

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /*
         * Make sure the daily 23:59 review alarm exists
         * whenever the application starts.
         */
        scheduleDailyReview(this)

        /*
         * If exact alarms are not yet permitted on Android 12+,
         * open the system's "Alarms & reminders" permission page.
         *
         * The fallback alarm still works without this permission,
         * but it may not fire exactly at 23:59.
         */
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !getSystemService(AlarmManager::class.java)
                .canScheduleExactAlarms()
        ) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    )
                )
            } catch (_: Exception) {
                /*
                 * Some devices do not expose the exact-alarm
                 * settings screen. The fallback alarm remains active.
                 */
            }
        }

        setContent {

            QuitTrackApp(
                loadStartDate = {
                    prefs.getLong(
                        "startDate",
                        0L
                    )
                },

                saveStartDate = {
                    prefs.edit()
                        .putLong(
                            "startDate",
                            it
                        )
                        .apply()
                },

                loadEntries = {
                    loadEntries()
                },

                saveEntries = {
                    saveEntries(it)
                },

                loadDailyReviews = {
                    loadDailyReviews()
                },

                saveDailyReview = {
                    saveDailyReview(it)
                }
            )
        }
    }

    private fun loadEntries(): List<LogEntry> {

        val a = JSONArray(
            prefs.getString(
                "entries",
                "[]"
            ) ?: "[]"
        )

        return buildList {

            for (i in 0 until a.length()) {

                val o =
                    a.getJSONObject(i)

                add(
                    LogEntry(
                        o.getString("type"),
                        o.getLong("time"),
                        o.optInt("intensity"),
                        o.optString("source"),
                        o.optString("context"),
                        o.optBoolean(
                            "morning",
                            false
                        )
                    )
                )
            }
        }
    }

    private fun saveEntries(
        entries: List<LogEntry>
    ) {

        val a = JSONArray()

        entries.forEach { e ->

            a.put(
                JSONObject().apply {
                    put(
                        "type",
                        e.type
                    )
                    put(
                        "time",
                        e.time
                    )
                    put(
                        "intensity",
                        e.intensity
                    )
                    put(
                        "source",
                        e.source
                    )
                    put(
                        "context",
                        e.context
                    )
                    put(
                        "morning",
                        e.morning
                    )
                }
            )
        }

        prefs.edit()
            .putString(
                "entries",
                a.toString()
            )
            .apply()
    }

    private fun saveDailyReview(
        review: DailyReview
    ) {

        val reviews = JSONArray(
            prefs.getString(
                "dailyReviews",
                "[]"
            ) ?: "[]"
        )

        var replaced = false

        for (i in 0 until reviews.length()) {

            val existing =
                reviews.getJSONObject(i)

            if (
                existing.optString("date") ==
                review.date
            ) {

                reviews.put(
                    i,
                    reviewToJson(review)
                )

                replaced = true
                break
            }
        }

        if (!replaced) {
            reviews.put(
                reviewToJson(review)
            )
        }

        prefs.edit()
            .putString(
                "dailyReviews",
                reviews.toString()
            )
            .apply()
    }

    private fun loadDailyReviews(): List<DailyReview> {

        val reviews = JSONArray(
            prefs.getString(
                "dailyReviews",
                "[]"
            ) ?: "[]"
        )

        return buildList {

            for (i in 0 until reviews.length()) {

                val root =
                    reviews.getJSONObject(i)

                val array =
                    root.optJSONArray(
                        "entries"
                    ) ?: JSONArray()

                val reviewEntries =
                    buildList {

                        for (
                            j in 0 until array.length()
                        ) {

                            val o =
                                array.getJSONObject(j)

                            add(
                                LogEntry(
                                    type =
                                        o.getString(
                                            "type"
                                        ),
                                    time =
                                        o.getLong(
                                            "time"
                                        ),
                                    intensity =
                                        o.optInt(
                                            "intensity"
                                        ),
                                    source =
                                        o.optString(
                                            "source"
                                        ),
                                    context =
                                        o.optString(
                                            "context"
                                        ),
                                    morning =
                                        o.optBoolean(
                                            "morning",
                                            false
                                        )
                                )
                            )
                        }
                    }

                add(
                    DailyReview(
                        date =
                            root.optString(
                                "date"
                            ),
                        entries =
                            reviewEntries
                    )
                )
            }
        }
    }
}


/* ============================================================
   JSON HELPERS
   ============================================================ */

private fun reviewToJson(
    review: DailyReview
): JSONObject {

    val root = JSONObject()

    root.put(
        "date",
        review.date
    )

    val array = JSONArray()

    review.entries.forEach { e ->

        array.put(
            JSONObject().apply {
                put(
                    "type",
                    e.type
                )
                put(
                    "time",
                    e.time
                )
                put(
                    "intensity",
                    e.intensity
                )
                put(
                    "source",
                    e.source
                )
                put(
                    "context",
                    e.context
                )
                put(
                    "morning",
                    e.morning
                )
            }
        )
    }

    root.put(
        "entries",
        array
    )

    return root
}


/* ============================================================
   THEME
   ============================================================ */

@Composable
fun QuitTrackTheme(
    content: @Composable () -> Unit
) {

    val colors = darkColorScheme(
        primary = QuitGreen,
        onPrimary =
            androidx.compose.ui.graphics.Color.White,
        secondary = QuitOrange,
        background = WarmBackground,
        surface = CardWhite,
        onBackground = TextDark,
        onSurface = TextDark
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            bodyLarge =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp
                ),
            bodyMedium =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp
                ),
            titleLarge =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp
                ),
            headlineSmall =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 28.sp
                ),
            headlineMedium =
                MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp
                )
        ),
        content = content
    )
}


/* ============================================================
   MAIN APP
   ============================================================ */

@Composable
fun QuitTrackApp(
    loadStartDate: () -> Long,
    saveStartDate: (Long) -> Unit,
    loadEntries: () -> List<LogEntry>,
    saveEntries: (List<LogEntry>) -> Unit,
    loadDailyReviews: () -> List<DailyReview>,
    saveDailyReview: (DailyReview) -> Unit
) {

    var startDate by remember {
        mutableLongStateOf(
            loadStartDate()
        )
    }

    var entries by remember {
        mutableStateOf(
            loadEntries()
        )
    }

    var dailyReviews by remember {
        mutableStateOf(
            loadDailyReviews()
        )
    }

    var screen by remember {
        mutableStateOf("Today")
    }

    var selectedPhase by remember {
        mutableStateOf<PlanPhase?>(null)
    }

    var smokeDialog by remember {
        mutableStateOf(false)
    }

    var cravingDialog by remember {
        mutableStateOf(false)
    }

    var reviewSaved by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current
val prefs = context.getSharedPreferences(
    "quit_track",
    android.content.Context.MODE_PRIVATE
)

val objective = prefs.getString(
    "objective",
    "Reduce gradually"
) ?: "Reduce gradually"

val cigarettesPerDay = prefs.getInt(
    "cigarettesPerDay",
    8
)

val morningCigarettes = prefs.getInt(
    "morningCigarettes",
    3
)

    BackHandler(
        enabled =
            screen != "Today" ||
                    smokeDialog ||
                    cravingDialog
    ) {

        when {
            smokeDialog ->
                smokeDialog = false

            cravingDialog ->
                cravingDialog = false

            screen == "Entries" ->
                screen = "Today"

            screen == "Emergency" ->
                screen = "Today"

            screen == "DailyReviews" ->
                screen = "Stats"

            else ->
                screen = "Today"
        }
    }

    val midnight = Calendar.getInstance().apply {
        set(
            Calendar.HOUR_OF_DAY,
            0
        )
        set(
            Calendar.MINUTE,
            0
        )
        set(
            Calendar.SECOND,
            0
        )
        set(
            Calendar.MILLISECOND,
            0
        )
    }.timeInMillis

    if (startDate == 0L) {

        LaunchedEffect(Unit) {

            startDate = midnight

            saveStartDate(
                midnight
            )
        }
    }

    val day =
        (((midnight - startDate) /
                86_400_000L)
            .toInt() + 1)
            .coerceIn(1, 40)

    val today =
        entries.filter {
            sameDay(
                it.time,
                midnight
            )
        }

    val smoked =
        today.count {
            it.type == "SMOKED"
        }

    val morning =
        today.count {
            it.type == "SMOKED" &&
                    it.morning
        }

    val cravings =
        today.count {
            it.type == "CRAVING"
        }

    QuitTrackTheme {

        Scaffold(
            containerColor =
                MaterialTheme.colorScheme.background,

            bottomBar = {

                NavigationBar(
                    containerColor =
                        MaterialTheme.colorScheme.surface
                ) {

                    listOf(
                        "Today" to "⌂",
                        "Plan" to "☷",
                        "Stats" to "▥",
                        "Settings" to "⚙"
                    ).forEach { (name, icon) ->

                        NavigationBarItem(
                            selected =
                                screen == name,

                            onClick = {
                                screen = name
                            },

                            icon = {
                                Text(
                                    icon,
                                    fontSize = 22.sp
                                )
                            },

                            label = {
                                Text(
                                    name,
                                    textAlign =
                                        TextAlign.Center
                                )
                            }
                        )
                    }
                }
            }
        ) { pad ->

            when (screen) {

                "Today" -> TodayScreen(
                    Modifier.padding(pad),
                    day,
                    smoked,
                    morning,
                    cravings,

                    onSmoke = {
                        smokeDialog = true
                    },

                    onCraving = {
                        cravingDialog = true
                    },

                    onEntries = {
                        screen = "Entries"
                    },

                    onPlan = {
                        selectedPhase =
                            planPhases.firstOrNull {
                                day in
                                        it.dayStart..it.dayEnd
                            }

                        screen = "PhaseDetail"
                    },

                    onEmergency = {
                        screen = "Emergency"
                    },

                    onSaveReview = {

                        val todayEntries =
                            entries.filter {
                                sameDay(
                                    it.time,
                                    midnight
                                )
                            }

                        val review =
                            DailyReview(
                                date =
                                    fmtDate(
                                        midnight
                                    ),
                                entries =
                                    todayEntries
                            )

                        /*
                         * Save the review immediately.
                         */
                        saveDailyReview(
                            review
                        )

                        dailyReviews =
                            dailyReviews
                                .filterNot {
                                    it.date ==
                                            review.date
                                } + review

                        /*
                         * IMPORTANT:
                         *
                         * Mark today's review as
                         * MANUALLY saved.
                         *
                         * This flag prevents the 23:59
                         * automatic save from running for
                         * today's review.
                         */
                        context
                            .getSharedPreferences(
                                PREFS_NAME,
                                Context.MODE_PRIVATE
                            )
                            .edit()
                            .putString(
                                MANUAL_REVIEW_SAVED_DATE,
                                review.date
                            )
                            .apply()

                        /*
                         * Cancel today's 23:59 alarm.
                         */
                        cancelDailyReviewAlarm(
                            context
                        )

                        /*
                         * Schedule tomorrow's 23:59 alarm.
                         */
                        scheduleDailyReview(
                            context
                        )

                        reviewSaved = true
                    },

                    reviewSaved = reviewSaved
                )

                "Plan" -> PlanScreen(
                    Modifier.padding(pad),
                    day,
                    selectedPhase,

                    onPhaseClick = { phase ->

                        selectedPhase =
                            phase

                        screen =
                            "PhaseDetail"
                    }
                )

                "PhaseDetail" ->
                    selectedPhase?.let { phase ->

                        PhaseDetailScreen(
                            Modifier.padding(pad),
                            phase = phase,

                            onBack = {
                                selectedPhase =
                                    null

                                screen =
                                    "Plan"
                            },

                            onAction = {

                                when (phase.phase) {

                                    1 ->
                                        screen =
                                            "Today"

                                    2 ->
                                        screen =
                                            "Today"

                                    3 ->
                                        screen =
                                            "Emergency"

                                    4 ->
                                        screen =
                                            "Emergency"

                                    5 ->
                                        screen =
                                            "Emergency"

                                    6 ->
                                        screen =
                                            "Stats"
                                }
                            }
                        )
                    }

                "Stats" -> StatsScreen(
    Modifier.padding(pad),
    entries,
    onDailyReviews = { screen = "DailyReviews" },
    onProgress = { screen = "Progress" }
)
                "Progress" -> ProgressScreen(
    Modifier.padding(pad),
    entries = entries,
    dailyReviews = dailyReviews,
    startDate = startDate,
    objective = objective,
    cigarettesPerDay = cigarettesPerDay,
    morningCigarettes = morningCigarettes,
    onBack = { screen = "Stats" }
)

                "DailyReviews" ->
                    DailyReviewsScreen(
                        Modifier.padding(pad),
                        reviews =
                            dailyReviews,

                        onBack = {
                            screen =
                                "Stats"
                        }
                    )

                "Settings" ->
                    SettingsScreen(
                        Modifier.padding(pad),
                        startDate =
                            startDate,
                        entries =
                            entries,

                        onReset = {

                            val now =
                                Calendar.getInstance()
                                    .apply {
                                        set(
                                            Calendar.HOUR_OF_DAY,
                                            0
                                        )
                                        set(
                                            Calendar.MINUTE,
                                            0
                                        )
                                        set(
                                            Calendar.SECOND,
                                            0
                                        )
                                        set(
                                            Calendar.MILLISECOND,
                                            0
                                        )
                                    }
                                    .timeInMillis

                            startDate =
                                now

                            entries =
                                emptyList()

                            saveStartDate(
                                now
                            )

                            saveEntries(
                                entries
                            )
                        },

                        onImport = {
                                importedStartDate,
                                importedEntries ->

                            startDate =
                                importedStartDate

                            entries =
                                importedEntries

                            saveStartDate(
                                importedStartDate
                            )

                            saveEntries(
                                importedEntries
                            )
                        }
                    )

                "Entries" ->
                    EntriesScreen(
                        Modifier.padding(pad),
                        entries,

                        onBack = {
                            screen =
                                "Today"
                        }
                    )

                "Emergency" ->
                    EmergencyScreen(
                        Modifier.padding(pad),

                        onBack = {
                            screen =
                                "Today"
                        }
                    )
            }
        }

        if (smokeDialog) {

            SmokeDialog(
                dismiss = {
                    smokeDialog = false
                }
            ) {
                    source,
                    contextValue,
                    intensity,
                    morningValue ->

                entries =
                    entries + LogEntry(
                        "SMOKED",
                        System.currentTimeMillis(),
                        intensity,
                        source,
                        contextValue,
                        morningValue
                    )

                saveEntries(
                    entries
                )

                smokeDialog =
                    false
            }
        }

        if (cravingDialog) {

            CravingDialog(
                dismiss = {
                    cravingDialog = false
                }
            ) {
                    intensity,
                    contextValue,
                    morningValue ->

                entries =
                    entries + LogEntry(
                        "CRAVING",
                        System.currentTimeMillis(),
                        intensity,
                        context = contextValue,
                        morning = morningValue
                    )

                saveEntries(
                    entries
                )

                cravingDialog =
                    false
            }
        }
    }
}


/* ============================================================
   APP CARD
   ============================================================ */

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {

    Card(
        modifier =
            modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            ),

        shape =
            RoundedCornerShape(18.dp),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 1.dp
            ),

        content = content
    )
}


/* ============================================================
   TODAY SCREEN
   ============================================================ */

@Composable
fun TodayScreen(
    m: Modifier,
    day: Int,
    smoked: Int,
    morning: Int,
    cravings: Int,
    onSmoke: () -> Unit,
    onCraving: () -> Unit,
    onEntries: () -> Unit,
    onPlan: () -> Unit,
    onEmergency: () -> Unit,
    onSaveReview: () -> Unit,
    reviewSaved: Boolean
) {

    LazyColumn(
        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Column(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    "Quit Track",
                    style =
                        MaterialTheme.typography.headlineSmall,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        QuitGreen,
                    textAlign =
                        TextAlign.Center
                )

                Spacer(
                    Modifier.height(4.dp)
                )
            }
        }

        item {

            Column(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                Text(
                    "Hi Faith!",
                    style =
                        MaterialTheme.typography.headlineSmall,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "One day at a time.",
                    color =
                        TextMuted,
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        item {

            AppCard(
                modifier =
                    Modifier.clickable {
                        onPlan()
                    }
            ) {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(14.dp)
                ) {

                    Text(
                        "DAY $day OF 40",
                        style =
                            MaterialTheme.typography.headlineMedium,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        "Phase ${phaseForDay(day)} • ${phaseName(day)}",
                        color =
                            TextMuted,
                        textAlign =
                            TextAlign.Center
                    )

                    HorizontalDivider(
                        color =
                            BorderLight
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceEvenly
                    ) {

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                smoked.toString(),
                                style =
                                    MaterialTheme.typography.headlineMedium,
                                fontWeight =
                                    FontWeight.Bold,
                                textAlign =
                                    TextAlign.Center
                            )

                            Text(
                                "Cigarettes today",
                                color =
                                    TextMuted,
                                style =
                                    MaterialTheme.typography.bodySmall,
                                textAlign =
                                    TextAlign.Center
                            )
                        }

                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                morning.toString(),
                                style =
                                    MaterialTheme.typography.headlineMedium,
                                fontWeight =
                                    FontWeight.Bold,
                                textAlign =
                                    TextAlign.Center
                            )

                            Text(
                                "Morning cigarettes",
                                color =
                                    TextMuted,
                                style =
                                    MaterialTheme.typography.bodySmall,
                                textAlign =
                                    TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    onClick =
                        onSmoke,

                    modifier =
                        Modifier
                            .weight(1f)
                            .height(82.dp),

                    shape =
                        RoundedCornerShape(18.dp),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                QuitGreen
                        )
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "＋",
                            fontSize =
                                25.sp,
                            textAlign =
                                TextAlign.Center
                        )

                        Text(
                            "Log a cigarette",
                            fontWeight =
                                FontWeight.Bold,
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick =
                        onCraving,

                    modifier =
                        Modifier
                            .weight(1f)
                            .height(82.dp),

                    shape =
                        RoundedCornerShape(18.dp),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                QuitOrange
                        )
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "🔥",
                            fontSize =
                                23.sp,
                            textAlign =
                                TextAlign.Center
                        )

                        Text(
                            "I have a craving",
                            fontWeight =
                                FontWeight.Bold,
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }
        }

        item {

            Button(
                onClick =
                    onSaveReview,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                shape =
                    RoundedCornerShape(16.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            QuitGreen
                    )
            ) {

                Text(
                    if (reviewSaved)
                        "✓  Today's review saved"
                    else
                        "✓  Save today's review",

                    fontWeight =
                        FontWeight.Bold,

                    textAlign =
                        TextAlign.Center
                )
            }
        }

        item {

            ActionCard(
                title =
                    "Today's plan",
                subtitle =
                    "Focus on today's targets",
                icon =
                    "🎯",
                onClick =
                    onPlan
            )
        }

        item {

            ActionCard(
                title =
                    "Emergency craving help",
                subtitle =
                    "Tools to get through strong urges",
                icon =
                    "🛟",
                onClick =
                    onEmergency
            )
        }

        item {

            ActionCard(
                title =
                    "View all entries",
                subtitle =
                    "$cravings cravings • $smoked cigarettes today",
                icon =
                    "📋",
                onClick =
                    onEntries
            )
        }
    }
}


/* ============================================================
   ACTION CARD
   ============================================================ */

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {

    AppCard(
        modifier =
            Modifier.clickable {
                onClick()
            }
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {

            Text(
                icon,
                fontSize =
                    28.sp,
                textAlign =
                    TextAlign.Center
            )

            Text(
                title,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center
            )

            Text(
                subtitle,
                color =
                    TextMuted,
                style =
                    MaterialTheme.typography.bodySmall,
                textAlign =
                    TextAlign.Center
            )
        }
    }
}


/* ============================================================
   PLAN SCREEN
   ============================================================ */

@Composable
fun PlanScreen(
    m: Modifier,
    currentDay: Int,
    selectedPhase: PlanPhase? = null,
    onPhaseClick: (PlanPhase) -> Unit
) {

    val listState =
        rememberLazyListState()

    LaunchedEffect(currentDay) {

        val index =
            planPhases.indexOfFirst {
                currentDay in
                        it.dayStart..it.dayEnd
            }

        if (index >= 0) {

            listState.animateScrollToItem(
                index + 2
            )
        }
    }

    LazyColumn(
        state =
            listState,

        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Text(
                "Plan",
                modifier =
                    Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    QuitGreen,
                textAlign =
                    TextAlign.Center
            )
        }

        item {

            Column(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                Text(
                    "Your 40-Day Plan",
                    modifier =
                        Modifier.fillMaxWidth(),
                    style =
                        MaterialTheme.typography.headlineMedium,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "Your journey toward a smoke-free life.",
                    modifier =
                        Modifier.fillMaxWidth(),
                    color =
                        TextMuted,
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        items(planPhases) { phase ->

            val current =
                currentDay in
                        phase.dayStart..phase.dayEnd

            val completed =
                currentDay > phase.dayEnd

            AppCard(
                modifier =
                    Modifier.clickable {
                        onPhaseClick(
                            phase
                        )
                    }
            ) {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        completed ->
                                            QuitGreen

                                        current ->
                                            QuitGreenLight

                                        else ->
                                            BorderLight
                                    }
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            if (completed)
                                "✓"
                            else
                                phase.phase.toString(),

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                if (completed)
                                    androidx.compose.ui.graphics.Color.White
                                else
                                    QuitGreen,

                            textAlign =
                                TextAlign.Center
                        )
                    }

                    Text(
                        "Phase ${phase.phase} • ${phase.name}",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        if (
                            phase.dayStart ==
                            phase.dayEnd
                        )
                            "Day ${phase.dayStart}"
                        else
                            "Day ${phase.dayStart} to Day ${phase.dayEnd}",

                        modifier =
                            Modifier.fillMaxWidth(),

                        fontWeight =
                            if (current)
                                FontWeight.Bold
                            else
                                FontWeight.Normal,

                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        phase.description,
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            TextMuted,
                        style =
                            MaterialTheme.typography.bodySmall,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        item {

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        "Every day is a step forward",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            QuitGreen,
                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        "Small steps. Big change.",
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            TextMuted,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }
    }
}


/* ============================================================
   STATS SCREEN
   ============================================================ */

@Composable
fun StatsScreen(
    m: Modifier,
    entries: List<LogEntry>,
    onDailyReviews: () -> Unit,
    onProgress: () -> Unit
) {

    val smoked =
        entries.filter {
            it.type == "SMOKED"
        }

    val cravings =
        entries.filter {
            it.type == "CRAVING"
        }

    val averageCraving =
        if (cravings.isEmpty())
            0.0
        else
            cravings
                .map {
                    it.intensity
                }
                .average()

    val highestCraving =
        cravings.maxOfOrNull {
            it.intensity
        } ?: 0

    LazyColumn(
        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Text(
                "Stats",
                modifier =
                    Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    QuitGreen,
                textAlign =
                    TextAlign.Center
            )
        }

        item {

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(14.dp)
                ) {

                    Text(
                        "Overview",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        "Your progress so far",
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            TextMuted,
                        style =
                            MaterialTheme.typography.bodySmall,
                        textAlign =
                            TextAlign.Center
                    )

                    Surface(
                        shape =
                            RoundedCornerShape(10.dp),
                        color =
                            QuitGreenLight
                    ) {

                        Text(
                            "All time",
                            modifier =
                                Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 8.dp
                                ),
                            color =
                                QuitGreen,
                            textAlign =
                                TextAlign.Center
                        )
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        StatBox(
                            modifier =
                                Modifier.weight(1f),
                            title =
                                "Cigarettes logged",
                            value =
                                smoked.size.toString()
                        )

                        StatBox(
                            modifier =
                                Modifier.weight(1f),
                            title =
                                "Cravings logged",
                            value =
                                cravings.size.toString()
                        )
                    }
                }
            }
        }

        item {

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        "Craving insights",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    StatRow(
                        "Average craving",
                        "${"%.1f".format(averageCraving)}/10"
                    )

                    StatRow(
                        "Highest craving",
                        "$highestCraving/10"
                    )
                }
            }
        }

        

        item {
    AppCard(
        modifier = Modifier.clickable {
    onProgress()
}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Progress",
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                "Analysis of your smoking, cravings and daily progress",
                modifier = Modifier.fillMaxWidth(),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

        item {

            ActionCard(
                title =
                    "Daily reviews",
                subtitle =
                    "View your saved daily reviews",
                icon =
                    "📖",
                onClick =
                    onDailyReviews
            )
        }
    }
}


/* ============================================================
   STAT BOX
   ============================================================ */

@Composable
fun StatBox(
    modifier: Modifier,
    title: String,
    value: String
) {

    Surface(
        modifier =
            modifier,

        shape =
            RoundedCornerShape(14.dp),

        color =
            MaterialTheme.colorScheme.background,

        tonalElevation =
            1.dp
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            Text(
                title,
                modifier =
                    Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    TextMuted,
                textAlign =
                    TextAlign.Center
            )

            Text(
                value,
                modifier =
                    Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight =
                    FontWeight.Bold,
                color =
                    QuitGreen,
                textAlign =
                    TextAlign.Center
            )
        }
    }
}


/* ============================================================
   STAT ROW
   ============================================================ */

@Composable
fun StatRow(
    label: String,
    value: String
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.spacedBy(2.dp)
    ) {

        Text(
            label,
            modifier =
                Modifier.fillMaxWidth(),
            textAlign =
                TextAlign.Center
        )

        Text(
            value,
            modifier =
                Modifier.fillMaxWidth(),
            fontWeight =
                FontWeight.Bold,
            color =
                QuitGreen,
            textAlign =
                TextAlign.Center
        )
    }
}


/* ============================================================
   SETTINGS SCREEN
   ============================================================ */

@Composable
fun SettingsScreen(
    m: Modifier,
    startDate: Long,
    entries: List<LogEntry>,
    onReset: () -> Unit,
    onImport: (Long, List<LogEntry>) -> Unit
) {

    var showReset by remember {
        mutableStateOf(false)
    }

    var showObjective by remember {
        mutableStateOf(false)
    }

    val context =
        LocalContext.current

    val prefs =
        context.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )

    var objective by remember {

        mutableStateOf(
            prefs.getString(
                "objective",
                "Reduce gradually"
            ) ?: "Reduce gradually"
        )
    }

    var cigarettesPerDay by remember {

        mutableStateOf(
            prefs.getInt(
                "cigarettesPerDay",
                8
            ).toString()
        )
    }

    var morningCigarettes by remember {

        mutableStateOf(
            prefs.getInt(
                "morningCigarettes",
                3
            ).toString()
        )
    }

    var savedMessage by remember {
        mutableStateOf(false)
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/json"
            )
        ) { uri ->

            if (uri != null) {

                val root =
                    JSONObject()

                root.put(
                    "startDate",
                    startDate
                )

                val array =
                    JSONArray()

                entries.forEach { e ->

                    array.put(
                        JSONObject().apply {

                            put(
                                "type",
                                e.type
                            )

                            put(
                                "time",
                                e.time
                            )

                            put(
                                "intensity",
                                e.intensity
                            )

                            put(
                                "source",
                                e.source
                            )

                            put(
                                "context",
                                e.context
                            )

                            put(
                                "morning",
                                e.morning
                            )
                        }
                    )
                }

                root.put(
                    "entries",
                    array
                )

                context
                    .contentResolver
                    .openOutputStream(uri)
                    ?.use {
                        it.write(
                            root
                                .toString(2)
                                .toByteArray()
                        )
                    }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    val json =
                        context
                            .contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    if (json != null) {

                        val root =
                            JSONObject(json)

                        val importedStart =
                            root.optLong(
                                "startDate",
                                startDate
                            )

                        val array =
                            root.optJSONArray(
                                "entries"
                            ) ?: JSONArray()

                        val importedEntries =
                            buildList {

                                for (
                                    i in 0 until array.length()
                                ) {

                                    val o =
                                        array.getJSONObject(
                                            i
                                        )

                                    add(
                                        LogEntry(
                                            o.getString(
                                                "type"
                                            ),
                                            o.getLong(
                                                "time"
                                            ),
                                            o.optInt(
                                                "intensity"
                                            ),
                                            o.optString(
                                                "source"
                                            ),
                                            o.optString(
                                                "context"
                                            ),
                                            o.optBoolean(
                                                "morning",
                                                false
                                            )
                                        )
                                    )
                                }
                            }

                        onImport(
                            importedStart,
                            importedEntries
                        )
                    }

                } catch (_: Exception) {
                    // Invalid import files are ignored.
                }
            }
        }

    LazyColumn(
        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Text(
                "Settings",
                modifier =
                    Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.Bold,
                color =
                    QuitGreen,
                textAlign =
                    TextAlign.Center
            )
        }

        item {

            SettingsSectionTitle(
                "GOALS & TARGETS"
            )

            AppCard {

                Column {

                    SettingsRow(
                        icon =
                            "🎯",
                        title =
                            "Objective",
                        subtitle =
                            objective,
                        onClick = {
                            showObjective = true
                        }
                    )

                    Divider()

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),

                        horizontalAlignment =
                            Alignment.CenterHorizontally,

                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        Text(
                            "Daily cigarette limit",
                            modifier =
                                Modifier.fillMaxWidth(),
                            style =
                                MaterialTheme.typography.titleMedium,
                            fontWeight =
                                FontWeight.SemiBold,
                            textAlign =
                                TextAlign.Center
                        )

                        Text(
                            "Maximum cigarettes permitted per day",
                            modifier =
                                Modifier.fillMaxWidth(),
                            style =
                                MaterialTheme.typography.bodySmall,
                            color =
                                TextMuted,
                            textAlign =
                                TextAlign.Center
                        )

                        OutlinedTextField(
                            value =
                                cigarettesPerDay,

                            onValueChange = {

                                if (
                                    it.length <= 3 &&
                                    it.all {
                                        c -> c.isDigit()
                                    }
                                ) {
                                    cigarettesPerDay =
                                        it
                                }
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            singleLine = true,

                            textStyle =
                                LocalTextStyle.current.copy(
                                    textAlign =
                                        TextAlign.Center
                                ),

                            label = {

                                Text(
                                    "Cigarettes per day",
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    textAlign =
                                        TextAlign.Center
                                )
                            }
                        )

                        OutlinedTextField(
                            value =
                                morningCigarettes,

                            onValueChange = {

                                if (
                                    it.length <= 2 &&
                                    it.all {
                                        c -> c.isDigit()
                                    }
                                ) {
                                    morningCigarettes =
                                        it
                                }
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            singleLine = true,

                            textStyle =
                                LocalTextStyle.current.copy(
                                    textAlign =
                                        TextAlign.Center
                                ),

                            label = {

                                Text(
                                    "Morning cigarettes",
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    textAlign =
                                        TextAlign.Center
                                )
                            }
                        )

                        Button(
                            onClick = {

                                val daily =
                                    cigarettesPerDay
                                        .toIntOrNull()
                                        ?.coerceIn(
                                            0,
                                            100
                                        ) ?: 8

                                val morning =
                                    morningCigarettes
                                        .toIntOrNull()
                                        ?.coerceIn(
                                            0,
                                            50
                                        ) ?: 3

                                cigarettesPerDay =
                                    daily.toString()

                                morningCigarettes =
                                    morning.toString()

                                prefs.edit()
                                    .putString(
                                        "objective",
                                        objective
                                    )
                                    .putInt(
                                        "cigarettesPerDay",
                                        daily
                                    )
                                    .putInt(
                                        "morningCigarettes",
                                        morning
                                    )
                                    .apply()

                                savedMessage =
                                    true
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                "Save targets",
                                textAlign =
                                    TextAlign.Center
                            )
                        }

                        if (savedMessage) {

                            Text(
                                "Targets saved",
                                modifier =
                                    Modifier.fillMaxWidth(),
                                color =
                                    QuitGreen,
                                style =
                                    MaterialTheme.typography.bodySmall,
                                fontWeight =
                                    FontWeight.SemiBold,
                                textAlign =
                                    TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        item {

            AppCard {
                Column {
                }
            }
        }

        item {

            SettingsSectionTitle(
                "DATA"
            )

            AppCard {

                Column {

                    SettingsRow(
                        icon =
                            "⇩",
                        title =
                            "Export my data",
                        subtitle =
                            "Save a copy of your data",
                        onClick = {
                            exportLauncher.launch(
                                "quit-track-backup.json"
                            )
                        }
                    )

                    Divider()

                    SettingsRow(
                        icon =
                            "⇧",
                        title =
                            "Import my data",
                        subtitle =
                            "Restore data from a file",
                        onClick = {

                            importLauncher.launch(
                                arrayOf(
                                    "application/json"
                                )
                            )
                        }
                    )

                    Divider()

                    SettingsRow(
                        icon =
                            "⌫",
                        title =
                            "Delete all my data",
                        subtitle =
                            "Permanently delete your local data",
                        titleColor =
                            MaterialTheme.colorScheme.error,
                        onClick = {
                            showReset = true
                        }
                    )
                }
            }
        }

        item {

            SettingsSectionTitle(
                "INFO"
            )

            AppCard {

                Column {

                    SettingsRow(
                        icon =
                            "#",
                        title =
                            "Version",
                        subtitle =
                            "unicus",
                        onClick = {}
                    )
                }
            }
        }

        item {

            Text(
                "Start date: ${fmtDate(startDate)}",
                modifier =
                    Modifier.fillMaxWidth(),
                color =
                    TextMuted,
                style =
                    MaterialTheme.typography.bodySmall,
                textAlign =
                    TextAlign.Center
            )
        }
    }

    if (showObjective) {

        AlertDialog(

            onDismissRequest = {
                showObjective = false
            },

            title = {
                Text("Objective")
            },

            text = {

                Column {

                    listOf(
                        "Quit completely",
                        "Reduce gradually",
                        "Quit by a specific date"
                    ).forEach { option ->

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        objective =
                                            option

                                        showObjective =
                                            false

                                        savedMessage =
                                            false
                                    }
                                    .padding(
                                        vertical = 12.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            RadioButton(
                                selected =
                                    objective ==
                                            option,

                                onClick = {

                                    objective =
                                        option

                                    showObjective =
                                        false

                                    savedMessage =
                                        false
                                }
                            )

                            Spacer(
                                Modifier.width(8.dp)
                            )

                            Text(
                                option
                            )
                        }
                    }
                }
            },

            confirmButton = {}
        )
    }

    if (showReset) {

        AlertDialog(

            onDismissRequest = {
                showReset = false
            },

            title = {
                Text(
                    "Delete everything?"
                )
            },

            text = {

                Text(
                    "This permanently deletes locally stored entries and restarts the plan at Day 1."
                )
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        onReset()

                        /*
                         * Also clear the manual-review flag
                         * so the new tracking period starts clean.
                         */
                        prefs.edit()
                            .remove(
                                MANUAL_REVIEW_SAVED_DATE
                            )
                            .apply()

                        /*
                         * Make sure the review alarm continues
                         * for the newly restarted plan.
                         */
                        scheduleDailyReview(
                            context
                        )

                        showReset = false
                    }
                ) {

                    Text(
                        "Delete",
                        color =
                            MaterialTheme.colorScheme.error
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showReset = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


/* ============================================================
   SETTINGS SECTION TITLE
   ============================================================ */

@Composable
fun SettingsSectionTitle(
    text: String
) {

    Text(
        text,

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    bottom = 4.dp
                ),

        style =
            MaterialTheme.typography.labelMedium,

        fontWeight =
            FontWeight.Bold,

        color =
            QuitGreen,

        textAlign =
            TextAlign.Center
    )
}


/* ============================================================
   SETTINGS ROW
   ============================================================ */

@Composable
fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String,
    titleColor:
        androidx.compose.ui.graphics.Color =
            MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(16.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {

        Text(
            icon,
            fontSize =
                21.sp,

            color =
                if (
                    titleColor ==
                    MaterialTheme.colorScheme.error
                )
                    titleColor
                else
                    QuitGreen,

            textAlign =
                TextAlign.Center
        )

        Text(
            title,
            modifier =
                Modifier.fillMaxWidth(),
            fontWeight =
                FontWeight.SemiBold,
            color =
                titleColor,
            textAlign =
                TextAlign.Center
        )

        Text(
            subtitle,
            modifier =
                Modifier.fillMaxWidth(),
            color =
                TextMuted,
            style =
                MaterialTheme.typography.bodySmall,
            textAlign =
                TextAlign.Center
        )
    }
}


/* ============================================================
   ENTRIES SCREEN
   ============================================================ */

@Composable
fun EntriesScreen(
    m: Modifier,
    entries: List<LogEntry>,
    onBack: () -> Unit
) {

    LazyColumn(
        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        item {

            Box(
                modifier =
                    Modifier.fillMaxWidth(),

                contentAlignment =
                    Alignment.Center
            ) {

                TextButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        "‹ Back",
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        item {

            Text(
                "All entries",
                modifier =
                    Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.headlineSmall,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center
            )
        }

        if (entries.isEmpty()) {

            item {

                AppCard {

                    Text(
                        "No entries yet.",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        color =
                            TextMuted,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        items(
            entries.sortedByDescending {
                it.time
            }
        ) { e ->

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {

                    Text(
                        if (
                            e.type ==
                            "SMOKED"
                        )
                            "🚬  Smoked"
                        else
                            "🔥  Craving",

                        fontWeight =
                            FontWeight.Bold,

                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        fmtDateTime(
                            e.time
                        ),

                        color =
                            TextMuted,

                        style =
                            MaterialTheme.typography.bodySmall,

                        textAlign =
                            TextAlign.Center
                    )

                    if (
                        e.source.isNotBlank()
                    ) {

                        Text(
                            "Source: ${e.source}",
                            textAlign =
                                TextAlign.Center
                        )
                    }

                    if (
                        e.intensity > 0
                    ) {

                        Text(
                            "Intensity: ${e.intensity}/10",
                            textAlign =
                                TextAlign.Center
                        )
                    }

                    if (
                        e.context.isNotBlank()
                    ) {

                        Text(
                            "Context: ${e.context}",
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


/* ============================================================
   EMERGENCY SCREEN
   ============================================================ */

@Composable
fun EmergencyScreen(
    m: Modifier,
    onBack: () -> Unit
) {

    var seconds by remember {
        mutableIntStateOf(
            600
        )
    }

    var running by remember {
        mutableStateOf(
            false
        )
    }

    LaunchedEffect(running) {

        while (
            running &&
            seconds > 0
        ) {

            kotlinx.coroutines.delay(
                1000
            )

            seconds--
        }

        if (seconds == 0) {
            running = false
        }
    }

    Column(
        modifier =
            m
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(20.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.spacedBy(18.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Box(
                modifier =
                    Modifier.fillMaxWidth(),

                contentAlignment =
                    Alignment.Center
            ) {

                TextButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        "‹ Back",
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        Text(
            "🔥",
            fontSize =
                48.sp,
            textAlign =
                TextAlign.Center
        )

        Text(
            "Emergency craving help",
            style =
                MaterialTheme.typography.headlineSmall,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.Center
        )

        Text(
            "Delay the decision. Move away from cigarettes. Drink water. Distract yourself. Reassess.",
            textAlign =
                TextAlign.Center,
            color =
                TextMuted
        )

        Text(
            "%02d:%02d".format(
                seconds / 60,
                seconds % 60
            ),

            style =
                MaterialTheme.typography.displayMedium,

            fontWeight =
                FontWeight.Bold,

            color =
                QuitGreen,

            textAlign =
                TextAlign.Center
        )

        Button(

            onClick = {
                running = !running
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),

            shape =
                RoundedCornerShape(16.dp)
        ) {

            Text(
                if (running)
                    "Pause"
                else
                    "Start 10-minute timer",

                textAlign =
                    TextAlign.Center
            )
        }

        AppCard {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    "While you wait",
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "• Move somewhere cigarettes aren't available.",
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "• Drink a glass of water.",
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "• Take a few slow breaths.",
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "• Distract yourself for a few minutes.",
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "• Reassess the craving when the timer ends.",
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        Text(
            "After the timer: Lower / Same / Higher",
            color =
                TextMuted,
            textAlign =
                TextAlign.Center
        )
    }
}


/* ============================================================
   SMOKE DIALOG
   ============================================================ */

@Composable
fun SmokeDialog(
    dismiss: () -> Unit,
    save: (
        String,
        String,
        Int,
        Boolean
    ) -> Unit
) {

    var source by remember {
        mutableStateOf(
            "Bought"
        )
    }

    var context by remember {
        mutableStateOf("")
    }

    var showContextOptions by remember {
        mutableStateOf(false)
    }

    var intensity by remember {
        mutableIntStateOf(0)
    }

    var morning by remember {
        mutableStateOf(false)
    }

    AlertDialog(

        onDismissRequest =
            dismiss,

        title = {
            Text("I smoked")
        },

        text = {

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    "How did you get it?"
                )

                listOf(
                    "Bought",
                    "Offered",
                    "Asked for"
                ).forEach {

                    Card(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    source = it
                                },

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (
                                        source == it
                                    )
                                        QuitGreen.copy(
                                            alpha = 0.15f
                                        )
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant
                            ),

                        shape =
                            RoundedCornerShape(
                                14.dp
                            )
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical = 16.dp
                                    ),

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                it,
                                textAlign =
                                    TextAlign.Center
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    "Was this a morning cigarette?"
                )

                Card(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                morning = true
                            },

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (morning)
                                    QuitGreen.copy(
                                        alpha = 0.15f
                                    )
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                        ),

                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 16.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            "Yes",
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }

                Card(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                morning = false
                            },

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (!morning)
                                    QuitGreen.copy(
                                        alpha = 0.15f
                                    )
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                        ),

                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 16.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            "No",
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }

                Card(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextOptions =
                                    !showContextOptions
                            },

                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                        )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 16.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            if (
                                context.isEmpty()
                            )
                                "Context"
                            else
                                context,

                            textAlign =
                                TextAlign.Center
                        )
                    }
                }

                if (showContextOptions) {

                    AlertDialog(

                        onDismissRequest = {
                            showContextOptions =
                                false
                        },

                        title = {

                            Text(
                                "Context",
                                modifier =
                                    Modifier.fillMaxWidth(),
                                textAlign =
                                    TextAlign.Center
                            )
                        },

                        text = {

                            Column(
                                modifier =
                                    Modifier.fillMaxWidth(),

                                verticalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    ),

                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {

                                listOf(
                                    "After waking",
                                    "Coffee",
                                    "Meal",
                                    "Work",
                                    "Stress",
                                    "Boredom",
                                    "Social",
                                    "Alcohol",
                                    "Routine/Habit"
                                ).forEach { option ->

                                    Card(

                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {

                                                    context =
                                                        option

                                                    showContextOptions =
                                                        false
                                                },

                                        shape =
                                            RoundedCornerShape(
                                                14.dp
                                            ),

                                        colors =
                                            CardDefaults.cardColors(
                                                containerColor =
                                                    if (
                                                        context ==
                                                        option
                                                    )
                                                        QuitGreen.copy(
                                                            alpha =
                                                                0.15f
                                                        )
                                                    else
                                                        MaterialTheme
                                                            .colorScheme
                                                            .surfaceVariant
                                            )
                                    ) {

                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        vertical =
                                                            14.dp
                                                    ),

                                            contentAlignment =
                                                Alignment.Center
                                        ) {

                                            Text(
                                                option,
                                                textAlign =
                                                    TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        },

                        confirmButton = {}
                    )
                }

                Text(
                    "Craving: ${
                        if (
                            intensity == 0
                        )
                            "Not rated"
                        else
                            "$intensity/10"
                    }"
                )

                Slider(
                    value =
                        intensity.toFloat(),

                    onValueChange = {
                        intensity =
                            it.toInt()
                    },

                    valueRange =
                        0f..10f,

                    steps =
                        9
                )
            }
        },

        confirmButton = {

            TextButton(

                onClick = {

                    save(
                        source,
                        context,
                        intensity,
                        morning
                    )
                }
            ) {

                Text("Save")
            }
        },

        dismissButton = {

            TextButton(
                onClick =
                    dismiss
            ) {
                Text("Cancel")
            }
        }
    )
}


/* ============================================================
   CRAVING DIALOG
   ============================================================ */

@Composable
fun CravingDialog(
    dismiss: () -> Unit,
    save: (
        Int,
        String,
        Boolean
    ) -> Unit
) {

    var intensity by remember {
        mutableIntStateOf(5)
    }

    var context by remember {
        mutableStateOf("")
    }

    var morning by remember {
        mutableStateOf(false)
    }

    var showContextDialog by remember {
        mutableStateOf(false)
    }

    AlertDialog(

        onDismissRequest =
            dismiss,

        title = {
            Text(
                "I have a craving"
            )
        },

        text = {

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    "Intensity: $intensity/10"
                )

                Slider(

                    value =
                        intensity.toFloat(),

                    onValueChange = {
                        intensity =
                            it.toInt()
                    },

                    valueRange =
                        1f..10f,

                    steps =
                        8
                )

                Text(
                    "Is this a morning craving?"
                )

                Card(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                morning = true
                            },

                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (morning)
                                    QuitGreen.copy(
                                        alpha = 0.15f
                                    )
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                        )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 12.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            "Yes"
                        )
                    }
                }

                Card(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                morning = false
                            },

                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (!morning)
                                    QuitGreen.copy(
                                        alpha = 0.15f
                                    )
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                        )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 12.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            "No"
                        )
                    }
                }

                Card(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextDialog =
                                    true
                            },

                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                        )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 12.dp
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            if (
                                context.isEmpty()
                            )
                                "Context"
                            else
                                context
                        )
                    }
                }
            }
        },

        confirmButton = {

            TextButton(

                onClick = {

                    save(
                        intensity,
                        context,
                        morning
                    )
                }
            ) {

                Text(
                    "Save"
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick =
                    dismiss
            ) {

                Text(
                    "Cancel"
                )
            }
        }
    )

    if (showContextDialog) {

        AlertDialog(

            onDismissRequest = {
                showContextDialog =
                    false
            },

            title = {

                Box(
                    modifier =
                        Modifier.fillMaxWidth(),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        "Context"
                    )
                }
            },

            text = {

                Column(
                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    listOf(
                        "After waking",
                        "Coffee",
                        "Meal",
                        "Work",
                        "Stress",
                        "Boredom",
                        "Social",
                        "Alcohol",
                        "Routine/Habit"
                    ).forEach { option ->

                        Card(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        context =
                                            option

                                        showContextDialog =
                                            false
                                    },

                            shape =
                                RoundedCornerShape(
                                    14.dp
                                ),

                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (
                                            context ==
                                            option
                                        )
                                            QuitGreen.copy(
                                                alpha =
                                                    0.15f
                                            )
                                        else
                                            MaterialTheme
                                                .colorScheme
                                                .surfaceVariant
                                )
                        ) {

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical =
                                                10.dp
                                        ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    option
                                )
                            }
                        }
                    }
                }
            },

            confirmButton = {}
        )
    }
}


/* ============================================================
   PLAN HELPERS
   ============================================================ */

fun phaseForDay(
    day: Int
): Int {

    return when {

        day == 1 ->
            1

        day == 2 ->
            2

        day == 3 ->
            3

        day in 4..36 ->
            4

        day == 37 ->
            5

        day in 38..40 ->
            6

        else ->
            6
    }
}


fun phaseName(
    day: Int
): String {

    return when {

        day == 1 ->
            "Foundation"

        day == 2 ->
            "Preparation"

        day == 3 ->
            "Strengthening"

        day in 38..40 ->
            "Smoke-free maintenance"

        day == 37 ->
            "Quit Day"

        else ->
            "Building skills"
    }
}


fun planTitle(
    day: Int
): String {

    return when {

        day == 1 ->
            "Foundation"

        day == 2 ->
            "Preparation"

        day == 3 ->
            "Strengthening"

        day == 37 ->
            "Quit Day 🎯"

        day in 38..40 ->
            "Smoke-free maintenance"

        else ->
            "Building skills"
    }
}


fun planSubtitle(
    day: Int
): String {

    return when {

        day == 1 ->
            "Build awareness and track your habits."

        day == 2 ->
            "Understand your triggers and routines."

        day == 3 ->
            "Build skills to handle cravings."

        day == 37 ->
            "This is your quit day. You've got this!"

        day in 38..40 ->
            "Stay strong and keep the momentum."

        else ->
            "Continue working toward your quit day."
    }
}


/* ============================================================
   DATE HELPERS
   ============================================================ */

fun sameDay(
    a: Long,
    b: Long
): Boolean {

    val x =
        Calendar.getInstance().apply {
            timeInMillis = a
        }

    val y =
        Calendar.getInstance().apply {
            timeInMillis = b
        }

    return x.get(
        Calendar.YEAR
    ) == y.get(
        Calendar.YEAR
    ) &&
            x.get(
                Calendar.DAY_OF_YEAR
            ) ==
            y.get(
                Calendar.DAY_OF_YEAR
            )
}


fun hour(
    t: Long
): Int {

    return Calendar
        .getInstance()
        .apply {
            timeInMillis = t
        }
        .get(
            Calendar.HOUR_OF_DAY
        )
}


fun fmtDate(
    t: Long
): String {

    return SimpleDateFormat(
        "yyyy-MM-dd",
        Locale.getDefault()
    ).format(
        Date(t)
    )
}


fun fmtDateTime(
    t: Long
): String {

    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault()
    ).format(
        Date(t)
    )
}


/* ============================================================
   DAILY REVIEWS SCREEN
   ============================================================ */

@Composable
fun DailyReviewsScreen(
    m: Modifier,
    reviews: List<DailyReview>,
    onBack: () -> Unit
) {

    LazyColumn(
        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Box(
                modifier =
                    Modifier.fillMaxWidth(),

                contentAlignment =
                    Alignment.Center
            ) {

                TextButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        "‹ Back",
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        item {

            Column(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                Text(
                    "Daily reviews",
                    modifier =
                        Modifier.fillMaxWidth(),
                    style =
                        MaterialTheme.typography.headlineSmall,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        QuitGreen,
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    "Your saved daily summaries.",
                    modifier =
                        Modifier.fillMaxWidth(),
                    color =
                        TextMuted,
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        if (reviews.isEmpty()) {

            item {

                AppCard {

                    Text(
                        "No saved reviews yet.",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        color =
                            TextMuted,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }

        } else {

            items(
                reviews.sortedByDescending {
                    it.date
                }
            ) { review ->

                val smoked =
                    review.entries.count {
                        it.type ==
                                "SMOKED"
                    }

                val cravings =
                    review.entries.count {
                        it.type ==
                                "CRAVING"
                    }

                val morning =
                    review.entries.count {
                        it.type ==
                                "SMOKED" &&
                                it.morning
                    }

                AppCard {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),

                        horizontalAlignment =
                            Alignment.CenterHorizontally,

                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        Text(
                            review.date,
                            modifier =
                                Modifier.fillMaxWidth(),
                            fontWeight =
                                FontWeight.Bold,
                            style =
                                MaterialTheme.typography.titleMedium,
                            textAlign =
                                TextAlign.Center
                        )

                        Text(
                            "Cigarettes: $smoked",
                            modifier =
                                Modifier.fillMaxWidth(),
                            textAlign =
                                TextAlign.Center
                        )

                        Text(
                            "Morning cigarettes: $morning",
                            modifier =
                                Modifier.fillMaxWidth(),
                            textAlign =
                                TextAlign.Center
                        )

                        Text(
                            "Cravings: $cravings",
                            modifier =
                                Modifier.fillMaxWidth(),
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


/* ============================================================
   PHASE DETAIL SCREEN
   ============================================================ */

@Composable
fun PhaseDetailScreen(
    m: Modifier,
    phase: PlanPhase,
    onBack: () -> Unit,
    onAction: () -> Unit
) {

    LazyColumn(
        modifier =
            m.fillMaxSize(),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Box(
                modifier =
                    Modifier.fillMaxWidth(),

                contentAlignment =
                    Alignment.Center
            ) {

                TextButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        "‹ Back",
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        item {

            Column(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                Text(
                    "Phase ${phase.phase} • ${phase.name}",
                    modifier =
                        Modifier.fillMaxWidth(),
                    style =
                        MaterialTheme.typography.headlineSmall,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        QuitGreen,
                    textAlign =
                        TextAlign.Center
                )

                Text(
                    if (
                        phase.dayStart ==
                        phase.dayEnd
                    )
                        "Day ${phase.dayStart}"
                    else
                        "Day ${phase.dayStart} to Day ${phase.dayEnd}",

                    modifier =
                        Modifier.fillMaxWidth(),

                    fontWeight =
                        FontWeight.Bold,

                    textAlign =
                        TextAlign.Center
                )
            }
        }

        item {

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        "About this phase",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        phase.description,
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            TextMuted,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        item {

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        "What to focus on",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    phase.focus.forEach {
                        focusItem ->

                        Text(
                            "• $focusItem",
                            modifier =
                                Modifier.fillMaxWidth(),
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }
        }

        item {

            AppCard {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text(
                        "Today's focus",
                        modifier =
                            Modifier.fillMaxWidth(),
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    Text(
                        phase.todaysFocus,
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            TextMuted,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }
        }

        item {

            Button(

                onClick =
                    onAction,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                shape =
                    RoundedCornerShape(
                        16.dp
                    )
            ) {

                Text(
                    phase.action,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )
            }
        }
    }
    @Composable
fun ProgressScreen(
    m: Modifier,
    entries: List<LogEntry>,
    dailyReviews: List<DailyReview>,
    startDate: Long,
    objective: String,
    cigarettesPerDay: Int,
    morningCigarettes: Int,
    onBack: () -> Unit
) {
    val smoked = entries.filter { it.type == "SMOKED" }
    val cravings = entries.filter { it.type == "CRAVING" }

    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val currentDay = (((today - startDate) / 86_400_000L).toInt() + 1)
        .coerceIn(1, 40)

    val daysTracked = dailyReviews.size

    val averageCigarettes =
        if (daysTracked == 0) 0.0
        else dailyReviews.map {
            it.entries.count { entry ->
                entry.type == "SMOKED"
            }
        }.average()

    val averageCraving =
        if (cravings.isEmpty()) 0.0
        else cravings.map { it.intensity }.average()

    val morningCigarettes = smoked.count { it.morning }

    val strongCravings = cravings.count {
        it.intensity >= 8
    }

    val quitDay = Calendar.getInstance().apply {
        timeInMillis = startDate
        add(Calendar.DAY_OF_YEAR, 36)
    }.timeInMillis

    val daysUntilQuit =
        ((quitDay - today) / 86_400_000L)
            .toInt()
            .coerceAtLeast(0)

    LazyColumn(
        modifier = m.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        "‹ Back",
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "Progress & Analysis",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = QuitGreen,
                    textAlign = TextAlign.Center
                )

                Text(
                    "Your smoking, cravings and daily progress",
                    modifier = Modifier.fillMaxWidth(),
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Overall progress",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    StatRow(
                        "Current program day",
                        "Day $currentDay of 40"
                    )

                    StatRow(
                        "Daily reviews completed",
                        daysTracked.toString()
                    )

                    StatRow(
                        "Cigarettes logged",
                        smoked.size.toString()
                    )

                    StatRow(
                        "Cravings logged",
                        cravings.size.toString()
                    )
                }
            }
        }

        item {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Objective progress",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "Quit Day",
                        modifier = Modifier.fillMaxWidth(),
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        fmtDate(quitDay),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = QuitGreen,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        when {
                            currentDay < 37 ->
                                "$daysUntilQuit days until your planned quit day."

                            currentDay == 37 ->
                                "Today is your planned quit day."

                            else ->
                                "Your planned quit day has passed."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Smoking analysis",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    StatRow(
                        "Average cigarettes per reviewed day",
                        "%.1f".format(averageCigarettes)
                    )

                    StatRow(
                        "Morning cigarettes",
                        morningCigarettes.toString()
                    )

                    Text(
                        if (smoked.isEmpty())
                            "No cigarettes have been logged yet."
                        else
                            "Your smoking data will become more useful as more daily reviews are recorded.",
                        modifier = Modifier.fillMaxWidth(),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Craving analysis",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    StatRow(
                        "Average craving intensity",
                        if (cravings.isEmpty())
                            "No data"
                        else
                            "%.1f/10".format(averageCraving)
                    )

                    StatRow(
                        "Strong cravings (8/10 or higher)",
                        strongCravings.toString()
                    )

                    Text(
                        if (cravings.isEmpty())
                            "No cravings have been logged yet."
                        else
                            "Keep recording the context of cravings so Quit Track can identify your strongest patterns.",
                        modifier = Modifier.fillMaxWidth(),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Context analysis",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    val smokingContexts = smoked
                        .filter { it.context.isNotBlank() }
                        .groupingBy { it.context }
                        .eachCount()

                    val cravingContexts = cravings
                        .filter { it.context.isNotBlank() }
                        .groupingBy { it.context }
                        .eachCount()

                    val strongestSmokingContext =
                        smokingContexts.maxByOrNull { it.value }

                    val strongestCravingContext =
                        cravingContexts.maxByOrNull { it.value }

                    if (strongestSmokingContext == null &&
                        strongestCravingContext == null
                    ) {
                        Text(
                            "Context patterns will appear here as you record more cigarettes and cravings.",
                            modifier = Modifier.fillMaxWidth(),
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    } else {

                        strongestSmokingContext?.let {
                            StatRow(
                                "Most frequent smoking context",
                                "${it.key} (${it.value})"
                            )
                        }

                        strongestCravingContext?.let {
                            StatRow(
                                "Most frequent craving context",
                                "${it.key} (${it.value})"
                            )
                        }
                    }
                }
            }
        }

        item {
            AppCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "What your data shows",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        when {
                            dailyReviews.isEmpty() ->
                                "Your daily review analysis will become available once reviews have been recorded."

                            dailyReviews.size < 3 ->
                                "Keep recording daily reviews. A few more days of data will make the comparisons more meaningful."

                            smoked.isEmpty() && cravings.isEmpty() ->
                                "There is not enough activity data yet to identify patterns."

                            else ->
                                "Your data is beginning to show your smoking and craving patterns. Keep logging contexts so these patterns become clearer over time."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
}
