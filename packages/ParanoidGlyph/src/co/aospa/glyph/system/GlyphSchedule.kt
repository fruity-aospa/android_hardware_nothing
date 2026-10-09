/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.system

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import co.aospa.glyph.data.model.QuietReason
import co.aospa.glyph.data.model.ScheduleMode
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.sensors.PocketSensor
import co.aospa.glyph.util.requireSystemService
import com.android.settingslib.notification.modes.ZenModesBackend
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Works out when the glyphs keep quiet: during Bedtime mode or the hours the user chose, while Do
 * Not Disturb or Battery Saver is on if they asked for that, and while the phone is in a pocket.
 */
class GlyphSchedule(
    private val context: Context,
    private val preferences: GlyphPreferences,
    private val pocket: PocketSensor,
) {
    private companion object {
        private const val TAG = "GlyphSchedule"

        /** The settings that change when the glyphs keep quiet. */
        private val KEYS_WATCHED =
            setOf(
                GlyphPreferences.KEY_SCHEDULE_MODE,
                GlyphPreferences.KEY_SCHEDULE_START,
                GlyphPreferences.KEY_SCHEDULE_END,
                GlyphPreferences.KEY_SCHEDULE_DAYS,
                GlyphPreferences.KEY_DND_SYNC,
                GlyphPreferences.KEY_BATTERY_SAVER_SYNC,
            )
    }

    private val alarms = context.requireSystemService<AlarmManager>()
    private val powerManager = context.requireSystemService<PowerManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _isQuiet = MutableStateFlow(false)

    /** Whether the glyphs keep quiet, as it is now and each time that changes. */
    val isQuiet: StateFlow<Boolean> = _isQuiet.asStateFlow()

    private val _reason = MutableStateFlow(QuietReason.NONE)

    /** Why the glyphs keep quiet, as it is now and each time that changes. */
    val reason: StateFlow<QuietReason> = _reason.asStateFlow()

    /** Goes off when quiet hours begin or end. */
    private val alarm = AlarmManager.OnAlarmListener { scope.launch { refresh() } }

    /** Each time something that quiet depends on, other than the settings, changes. */
    private val systemChanges: Flow<Unit> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(Unit)
                }
            }
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }

        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        for (name in listOf(Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_CONFIG_ETAG)) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(name),
                false,
                observer,
            )
        }

        awaitClose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    /** Whether the phone is in a pocket, which it is not while the glyphs are off. */
    private var isInPocket = false

    /** Each time the phone goes into a pocket or comes out, read only while the glyphs are on. */
    private val pocketChanges: Flow<Unit> =
        preferences
            .observe { isEnabled }
            .flatMapLatest { if (it) pocket.isInPocket else flowOf(false) }
            .map { isInPocket = it }

    /** Keeps [isQuiet] in line with the settings and the system from now on. */
    fun watch() {
        scope.launch {
            merge(
                    preferences.changes.filter { it in KEYS_WATCHED }.map {},
                    systemChanges,
                    pocketChanges,
                )
                .onStart { emit(Unit) }
                .collect { refresh() }
        }
    }

    private fun refresh() {
        val now = ZonedDateTime.now()
        val hours = if (preferences.scheduleMode == ScheduleMode.CUSTOM) hours(now) else emptyList()
        val modes = ZenModesBackend.getInstance(context).modes

        val isScheduled =
            when (preferences.scheduleMode) {
                ScheduleMode.BEDTIME ->
                    modes.any { it.isActive && it.type == AutomaticZenRule.TYPE_BEDTIME }
                ScheduleMode.CUSTOM ->
                    hours.any { (start, end) -> !now.isBefore(start) && now.isBefore(end) }
                ScheduleMode.NONE -> false
            }
        val isDnd = preferences.isDndSyncEnabled && modes.any { it.isActive && it.isManualDnd }
        val isBatterySaver = preferences.isBatterySaverSyncEnabled && powerManager.isPowerSaveMode
        val reason =
            when {
                isScheduled -> QuietReason.SCHEDULE
                isDnd -> QuietReason.DND
                isBatterySaver -> QuietReason.BATTERY_SAVER
                isInPocket -> QuietReason.POCKET
                else -> QuietReason.NONE
            }
        _reason.value = reason
        _isQuiet.value = reason != QuietReason.NONE

        alarms.cancel(alarm)
        hours
            .flatMap { it.toList() }
            .filter { it.isAfter(now) }
            .minByOrNull { it.toInstant() }
            ?.let {
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    it.toInstant().toEpochMilli(),
                    TAG,
                    context.mainExecutor,
                    alarm,
                )
            }
    }

    /**
     * The custom quiet hours around [now], from the day before to a week ahead, as when each begins
     * and ends. Hours that end by the time they begin run into the next day.
     */
    private fun hours(now: ZonedDateTime): List<Pair<ZonedDateTime, ZonedDateTime>> =
        (-1L..7L).mapNotNull { offset ->
            val day = now.toLocalDate().plusDays(offset)
            if (day.dayOfWeek !in preferences.scheduleDays) return@mapNotNull null

            val endsNextDay = preferences.scheduleEnd <= preferences.scheduleStart
            val start = day.at(preferences.scheduleStart, now.zone)
            val end = day.plusDays(if (endsNextDay) 1 else 0).at(preferences.scheduleEnd, now.zone)
            start to end
        }

    /** The time [minute] minutes into this day, moved on if a clock change skips it. */
    private fun LocalDate.at(minute: Int, zone: ZoneId) =
        atTime(minute / 60, minute % 60).atZone(zone)
}
