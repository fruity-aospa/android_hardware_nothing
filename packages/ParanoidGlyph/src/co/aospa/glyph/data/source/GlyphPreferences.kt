/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.source

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import co.aospa.glyph.R
import co.aospa.glyph.data.model.ScheduleMode
import java.time.DayOfWeek
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The user's glyph settings. */
class GlyphPreferences(private val context: Context, private val config: GlyphConfig) {
    companion object {
        const val KEY_ENABLED = "glyph_enable"
        const val KEY_BRIGHTNESS = "glyph_settings_brightness"
        const val KEY_AUTO_BRIGHTNESS = "glyph_settings_auto_brightness"
        const val KEY_CALL = "glyph_settings_call_toggle"
        const val KEY_CALL_SWITCH = "glyph_settings_call_sub_toggle"
        const val KEY_CALL_ANIMATION = "glyph_settings_call_sub_animations"
        const val KEY_CALL_PREVIEW = "glyph_settings_call_sub_preview"
        const val KEY_CALL_PLAY = "glyph_settings_call_sub_play"
        const val KEY_NOTIFICATIONS = "glyph_settings_notifs_toggle"
        const val KEY_NOTIFICATIONS_SWITCH = "glyph_settings_notifs_sub_toggle"
        const val KEY_NOTIFICATIONS_ANIMATION = "glyph_settings_notifs_sub_animations"
        const val KEY_NOTIFICATIONS_PREVIEW = "glyph_settings_notifs_sub_preview"
        const val KEY_NOTIFICATIONS_PLAY = "glyph_settings_notifs_sub_play"
        const val KEY_NOTIFICATIONS_CATEGORY = "glyph_settings_notifs_sub"
        const val KEY_ESSENTIAL_APPS = "glyph_settings_notifs_sub_essential"
        const val KEY_CHARGING_METER = "glyph_settings_charging_level"
        const val KEY_POWERSHARE = "glyph_settings_charging_powershare"
        const val KEY_VOLUME_METER = "glyph_settings_volume_level_toggle"
        const val KEY_FLIP = "glyph_settings_flip_toggle"
        const val KEY_SCHEDULE_MODE = "glyph_schedule_mode"
        const val KEY_SCHEDULE_START = "glyph_schedule_start"
        const val KEY_SCHEDULE_END = "glyph_schedule_end"
        const val KEY_SCHEDULE_DAYS = "glyph_schedule_days"
        const val KEY_DND_SYNC = "glyph_schedule_dnd_sync"
        const val KEY_BATTERY_SAVER_SYNC = "glyph_schedule_battery_saver_sync"
        const val KEY_MUSIC_VISUALIZER = "glyph_settings_music_visualizer_toggle"
        const val KEY_CALL_REVERSE = "glyph_settings_call_sub_animations_reverse_toggle"
        const val KEY_NOTIFICATIONS_REVERSE = "glyph_settings_notifs_sub_animations_reverse_toggle"
        const val KEY_CALL_TONE_SYNC = "glyph_settings_call_ogg_sync_toggle"
        const val KEY_NOTIFICATIONS_TONE_SYNC = "glyph_settings_notifs_ogg_sync_toggle"
        const val KEY_FLIP_ANIMATION = "glyph_settings_flip_sub_animation_toggle"
        const val KEY_FLIP_RINGER_MODE = "glyph_settings_flip_sub_ringer_mode"
        const val KEY_PROGRESS = "glyph_settings_progress_toggle"
        const val KEY_MEDIA_PROGRESS = "glyph_settings_progress_media_toggle"
        const val KEY_MEDIA_PROGRESS_APPS = "glyph_settings_progress_media_app_whitelist"
        const val KEY_MIC_ACTIVITY = "glyph_settings_mic_activity_toggle"
        const val KEY_MIC_APPS = "glyph_settings_mic_activity_whitelist"

        /** Where the slider starts, of the levels the device offers. */
        private const val DEFAULT_BRIGHTNESS_STEP = 3

        /** When custom quiet hours begin and end, as minutes into the day. */
        private const val DEFAULT_SCHEDULE_START = 22 * 60
        private const val DEFAULT_SCHEDULE_END = 7 * 60
    }

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    init {
        if (!preferences.getBoolean("glyph_v2_storage_migrated", false)) {
            val legacy = preferences.contains(KEY_ENABLED) || preferences.contains("glyph_schedule_enabled") ||
                Settings.Secure.getString(context.contentResolver, KEY_ENABLED) != null
            preferences.edit {
                // The previous app stored these toggles in Settings.Secure.
                for (key in listOf(KEY_ENABLED, KEY_CALL, KEY_NOTIFICATIONS, KEY_FLIP)) {
                    val secure = Settings.Secure.getString(context.contentResolver, key)
                    if (secure != null || legacy) {
                        val enabled = secure?.toIntOrNull()?.let { it != 0 } ?: false
                        putBoolean(key, if (key == KEY_ENABLED) {
                            enabled || preferences.getBoolean(key, false)
                        } else { enabled })
                    }
                }
                // Calendar.SUNDAY=1 in v1; DayOfWeek.MONDAY=1 in v2.
                if (!preferences.contains(KEY_SCHEDULE_MODE) &&
                    preferences.contains("glyph_schedule_enabled")) {
                    putString(KEY_SCHEDULE_MODE, if (preferences.getBoolean("glyph_schedule_enabled", false)) "custom" else "none")
                    putInt(KEY_SCHEDULE_START, preferences.getInt("glyph_schedule_start_hour", 22).coerceIn(0, 23) * 60 + preferences.getInt("glyph_schedule_start_minute", 0).coerceIn(0, 59))
                    putInt(KEY_SCHEDULE_END, preferences.getInt("glyph_schedule_end_hour", 7).coerceIn(0, 23) * 60 + preferences.getInt("glyph_schedule_end_minute", 0).coerceIn(0, 59))
                    preferences.getStringSet(KEY_SCHEDULE_DAYS, null)?.let { days ->
                        putStringSet(KEY_SCHEDULE_DAYS, days.mapNotNull { day ->
                            day.toIntOrNull()?.takeIf { it in 1..7 }?.let { ((it + 5) % 7 + 1).toString() }
                        }.toSet())
                    }
                }
                putBoolean("glyph_v2_storage_migrated", true)
            }
        }
        if (!preferences.getBoolean("glyph_v2_custom_storage_migrated", false)) {
            preferences.edit {
                for ((old, current) in listOf(
                    "glyph_settings_auto_brightness_toggle" to KEY_AUTO_BRIGHTNESS,
                    "glyph_settings_battery_saver_toggle" to KEY_BATTERY_SAVER_SYNC,
                )) {
                    if (!preferences.contains(current) && preferences.contains(old)) {
                        putBoolean(current, preferences.getBoolean(old, false))
                    }
                }
                putBoolean("glyph_v2_custom_storage_migrated", true)
            }
        }
    }

    private val defaultCall = context.getString(R.string.config_defaultCallAnimation)
    private val defaultNotification =
        context.getString(R.string.config_defaultNotificationAnimation)

    /** The key of each setting as it is saved, for as long as the flow is collected. */
    val changes: Flow<String> = callbackFlow {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key -> key?.let(::trySend) }

        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Whether the strips are used at all. */
    var isEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    /** What [read] gives, as it is now and each time it changes. */
    fun <T> observe(read: GlyphPreferences.() -> T): Flow<T> =
        flow {
                emit(read())
                emitAll(changes.map { read() })
            }
            .distinctUntilChanged()

    /** Which step of [GlyphConfig.brightnessLevels] the user chose, kept to the steps there are. */
    val brightnessStep: Int
        get() =
            preferences
                .getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS_STEP)
                .coerceIn(1, config.brightnessLevels.size)

    /** The level that step stands for. */
    val brightness: Int
        get() = config.brightnessLevels[brightnessStep - 1]

    /** Whether the light around the phone sets the brightness, rather than the slider. */
    val isAutoBrightnessEnabled: Boolean
        get() = isFeatureEnabled(KEY_AUTO_BRIGHTNESS)

    /** What decides when the glyphs keep quiet. */
    val scheduleMode: ScheduleMode
        get() = ScheduleMode.of(preferences.getString(KEY_SCHEDULE_MODE, null))

    /** When custom quiet hours begin, as minutes into the day. */
    var scheduleStart: Int
        get() = preferences.getInt(KEY_SCHEDULE_START, DEFAULT_SCHEDULE_START)
        set(value) = preferences.edit { putInt(KEY_SCHEDULE_START, value) }

    /** When custom quiet hours end, as minutes into the day. */
    var scheduleEnd: Int
        get() = preferences.getInt(KEY_SCHEDULE_END, DEFAULT_SCHEDULE_END)
        set(value) = preferences.edit { putInt(KEY_SCHEDULE_END, value) }

    /** The days custom quiet hours begin on, every day until the user chooses. */
    var scheduleDays: Set<DayOfWeek>
        get() =
            preferences
                .getStringSet(KEY_SCHEDULE_DAYS, null)
                ?.map { DayOfWeek.of(it.toInt()) }
                ?.toSet() ?: DayOfWeek.values().toSet()
        set(value) =
            preferences.edit {
                putStringSet(KEY_SCHEDULE_DAYS, value.map { it.value.toString() }.toSet())
            }

    /** Whether Do Not Disturb keeps the glyphs quiet. */
    val isDndSyncEnabled: Boolean
        get() = preferences.getBoolean(KEY_DND_SYNC, false)

    /** Whether Battery Saver keeps the glyphs quiet, as it does until the user chooses. */
    val isBatterySaverSyncEnabled: Boolean
        get() = preferences.getBoolean(KEY_BATTERY_SAVER_SYNC, true)

    /** Whether flipping the phone lights the glyphs. */
    var isFlipEnabled: Boolean
        get() = isFeatureEnabled(KEY_FLIP)
        set(value) = preferences.edit { putBoolean(KEY_FLIP, value) }

    /** Whether charging shows the battery level. */
    val isChargingMeterEnabled: Boolean
        get() = isFeatureEnabled(KEY_CHARGING_METER)

    /** Whether charging another device shows on the strips. */
    val isPowershareEnabled: Boolean
        get() = config.isPowershareSupported && isFeatureEnabled(KEY_POWERSHARE)

    /** Whether a ringing call plays an animation. */
    var isCallEnabled: Boolean
        get() = isFeatureEnabled(KEY_CALL, default = true)
        set(value) = preferences.edit { putBoolean(KEY_CALL, value) }

    /** The ringing animation the user chose. */
    val callAnimation: String
        get() = preferences.getString(KEY_CALL_ANIMATION, defaultCall) ?: defaultCall

    val isCallAnimationReversed: Boolean
        get() = preferences.getBoolean(KEY_CALL_REVERSE, false)

    val isCallToneSyncEnabled: Boolean
        get() = isCallEnabled && preferences.getBoolean(KEY_CALL_TONE_SYNC, true)

    /** The old files remain the source of app/contact overrides, preserving their saved choices. */
    fun callPreferences(packageName: String) =
        context.getSharedPreferences("call_$packageName", Context.MODE_PRIVATE)

    fun contactPreferences(contactId: Long) =
        context.getSharedPreferences("call_contact_$contactId", Context.MODE_PRIVATE)

    fun hasCallOverride(settings: SharedPreferences) = settings.all.isNotEmpty()

    fun callAnimation(settings: SharedPreferences): String =
        settings.getString(KEY_CALL_ANIMATION, defaultCall) ?: defaultCall

    fun isCallAnimationReversed(settings: SharedPreferences) =
        settings.getBoolean(KEY_CALL_REVERSE, false)

    fun isCallAllowed(settings: SharedPreferences) =
        settings.getBoolean(KEY_CALL, true)

    /** Whether music drives the glyphs. */
    var isMusicVisualizerEnabled: Boolean
        get() = isFeatureEnabled(KEY_MUSIC_VISUALIZER)
        set(value) = preferences.edit { putBoolean(KEY_MUSIC_VISUALIZER, value) }

    /** Whether the volume keys show a meter. */
    val isVolumeMeterEnabled: Boolean
        get() = isFeatureEnabled(KEY_VOLUME_METER)

    /** Whether notifications play an animation. */
    var areNotificationsEnabled: Boolean
        get() = isFeatureEnabled(KEY_NOTIFICATIONS, default = true)
        set(value) = preferences.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    /** The notification animation the user chose. */
    val notificationAnimation: String
        get() =
            preferences.getString(KEY_NOTIFICATIONS_ANIMATION, defaultNotification)
                ?: defaultNotification

    val isNotificationAnimationReversed: Boolean
        get() = preferences.getBoolean(KEY_NOTIFICATIONS_REVERSE, false)

    val areNotificationTonesSynced: Boolean
        get() = areNotificationsEnabled && preferences.getBoolean(KEY_NOTIFICATIONS_TONE_SYNC, true)

    fun notificationPreferences(packageName: String) =
        context.getSharedPreferences("notif_$packageName", Context.MODE_PRIVATE)

    fun hasNotificationAnimationOverride(packageName: String): Boolean {
        val settings = notificationPreferences(packageName)
        return settings.contains(KEY_NOTIFICATIONS_ANIMATION) || settings.contains(KEY_NOTIFICATIONS_REVERSE)
    }

    fun notificationAnimation(packageName: String): String =
        if (hasNotificationAnimationOverride(packageName)) {
            notificationPreferences(packageName).getString(KEY_NOTIFICATIONS_ANIMATION, defaultNotification)
                ?: defaultNotification
        } else notificationAnimation

    fun isNotificationAnimationReversed(packageName: String) =
        if (hasNotificationAnimationOverride(packageName)) {
            notificationPreferences(packageName).getBoolean(KEY_NOTIFICATIONS_REVERSE, false)
        } else isNotificationAnimationReversed

    /** Whether the user left an app's notifications switched on. */
    fun isAppAllowed(packageName: String): Boolean =
        preferences.getBoolean(packageName,
            notificationPreferences(packageName).getBoolean(KEY_NOTIFICATIONS_SWITCH, true))

    /** Lets an app's notifications reach the strips, or stops them. */
    fun setAppAllowed(packageName: String, allowed: Boolean) =
        preferences.edit { putBoolean(packageName, allowed) }

    /** Whether an app's notifications reach the strips. */
    fun isAppEnabled(packageName: String): Boolean =
        areNotificationsEnabled && isAppAllowed(packageName)

    /** The apps whose notifications hold a glyph lit until they are read. */
    val essentialApps: Set<String>
        get() = preferences.getStringSet(KEY_ESSENTIAL_APPS, emptySet()).orEmpty()

    /** Whether an app's notifications hold a glyph lit until they are read. */
    fun isAppEssential(packageName: String): Boolean =
        areNotificationsEnabled && packageName in essentialApps

    /** Takes an app's notifications as essential, or stops doing so. */
    fun setAppEssential(packageName: String, essential: Boolean) {
        val apps = if (essential) essentialApps + packageName else essentialApps - packageName
        preferences.edit { putStringSet(KEY_ESSENTIAL_APPS, apps) }
    }

    val isFlipAnimationEnabled: Boolean
        get() = preferences.getBoolean(KEY_FLIP_ANIMATION, true)

    val flipRingerMode: Int
        get() = preferences.getString(KEY_FLIP_RINGER_MODE, "${AudioManager.RINGER_MODE_VIBRATE}")
            ?.toIntOrNull()?.takeIf { it in AudioManager.RINGER_MODE_SILENT..AudioManager.RINGER_MODE_NORMAL }
            ?: AudioManager.RINGER_MODE_VIBRATE

    val isProgressEnabled: Boolean
        get() = isFeatureEnabled(KEY_PROGRESS)

    val isMediaProgressEnabled: Boolean
        get() = isProgressEnabled && preferences.getBoolean(KEY_MEDIA_PROGRESS, false)

    val mediaProgressApps: Set<String>
        get() = preferences.getStringSet(KEY_MEDIA_PROGRESS_APPS, emptySet()).orEmpty()

    val isMicActivityEnabled: Boolean
        get() = isFeatureEnabled(KEY_MIC_ACTIVITY)

    val microphoneApps: Set<String>
        get() = preferences.getStringSet(KEY_MIC_APPS, emptySet()).orEmpty()

    /** Whether the user chose the feature with [key], whether or not the strips are used. */
    fun isFeatureChosen(key: String, default: Boolean) = preferences.getBoolean(key, default)

    /** Whether a feature is enabled, which it can only be while the strips are used at all. */
    private fun isFeatureEnabled(key: String, default: Boolean = false) =
        isEnabled && isFeatureChosen(key, default)
}
