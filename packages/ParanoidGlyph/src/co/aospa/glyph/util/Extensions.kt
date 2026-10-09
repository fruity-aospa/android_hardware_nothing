/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.ServiceManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import co.aospa.glyph.GlyphApplication
import co.aospa.glyph.Services.NotificationService
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** The application, for the components that need what it holds. */
val Context.glyph: GlyphApplication
    get() = applicationContext as GlyphApplication

/** The application, for the fragments that need what it holds. */
val Fragment.glyph: GlyphApplication
    get() = requireContext().glyph

/** A system service that every device has, which is not null as [getSystemService] says. */
inline fun <reified T : Any> Context.requireSystemService(): T = checkNotNull(getSystemService<T>())

/** The wake-up sensor of type [stringType], or null if the device has none. */
fun SensorManager.wakeUpSensor(stringType: String): Sensor? =
    getSensorList(Sensor.TYPE_ALL).firstOrNull { it.stringType == stringType && it.isWakeUpSensor }

/** Waits for the service called [name] to register, and whether the app may find it. */
suspend fun awaitService(name: String): Boolean =
    withContext(Dispatchers.IO) { ServiceManager.waitForService(name) != null }

/** Long enough for an animation to finish playing. */
private val ANIMATION_DURATION = 2.5.seconds

/** A wake lock that keeps the cpu running, under the [tag] it shows in dumpsys. */
fun Context.newWakeLock(tag: String): WakeLock =
    requireSystemService<PowerManager>().newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)

/** Keeps the device awake for as long as an animation takes to play. */
fun WakeLock.acquireForAnimation() = acquire(ANIMATION_DURATION.inWholeMilliseconds)

/** Whether the user has let the notification service read notifications. */
val Context.hasNotificationAccess: Boolean
    get() =
        getSystemService<NotificationManager>()
            ?.isNotificationListenerAccessGranted(
                ComponentName(this, NotificationService::class.java)
            ) == true

/**
 * Whether notifications may be turned on. Where the user has not granted access yet, this opens the
 * screen to grant it and returns false.
 */
fun Context.ensureNotificationAccess(): Boolean {
    if (hasNotificationAccess) return true

    startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, NotificationService::class.java).flattenToString(),
            )
    )
    return false
}

/**
 * Each broadcast that matches [filter], starting with the one held for it, if there is one.
 *
 * The receiver is registered while the flow is collected, and only then.
 */
fun Context.broadcasts(filter: IntentFilter): Flow<Intent> = callbackFlow {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent)
            }
        }

    // These are sent by the system, so it does not matter who else may send them.
    ContextCompat.registerReceiver(
        this@broadcasts,
        receiver,
        filter,
        ContextCompat.RECEIVER_EXPORTED,
    )
    awaitClose { unregisterReceiver(receiver) }
}

/** A minute of the day as a time, as the locale writes one. */
fun formatTime(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
