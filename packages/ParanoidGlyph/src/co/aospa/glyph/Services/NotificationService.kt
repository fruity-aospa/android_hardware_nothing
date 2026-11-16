/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import co.aospa.glyph.data.model.APPS_TO_IGNORE
import co.aospa.glyph.data.model.NOTIFICATIONS_TO_IGNORE
import co.aospa.glyph.data.source.GlyphPreferences
import co.aospa.glyph.util.GlyphLog
import co.aospa.glyph.util.acquireForAnimation
import co.aospa.glyph.util.glyph
import co.aospa.glyph.util.newWakeLock
import co.aospa.glyph.util.requireSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Plays an animation for each notification the user asked for, and keeps a glyph lit while an
 * essential one stands. Runs for as long as the user grants it notification access.
 */
class NotificationService : NotificationListenerService() {
    companion object {
        private const val TAG = "GlyphNotificationService"

        /** The settings that change which notifications count. */
        private val KEYS_WATCHED =
            setOf(
                GlyphPreferences.KEY_ESSENTIAL_APPS,
                GlyphPreferences.KEY_NOTIFICATIONS,
                GlyphPreferences.KEY_NOTIFICATIONS_SILENT,
                GlyphPreferences.KEY_PROGRESS,
                GlyphPreferences.KEY_ENABLED,
            )
    }

    private val notificationManager by lazy { requireSystemService<NotificationManager>() }
    private val wakeLock by lazy { newWakeLock(TAG) }

    /** The system owns this service's lifecycle, so it cannot be a lifecycle service itself. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val preferences by lazy { glyph.preferences }
    private val animator by lazy { glyph.animator }

    /** Whether the system has connected the listener, before which notifications cannot be read. */
    private var isConnected = false

    /** Determinate notification progress, newest update last. */
    private val progressNotifications = linkedMapOf<String, Int>()

    override fun onCreate() {
        GlyphLog.d(TAG) { "Notifications watched" }
        super.onCreate()

        preferences.changes
            .filter { key -> key in KEYS_WATCHED }
            .onEach { key ->
                GlyphLog.d(TAG) { "$key changed" }
                holdEssential()
                syncProgress()
            }
            .launchIn(scope)

        // Quiet hours darken the essential glyph, so it is lit again once they end.
        glyph.schedule.isQuiet.onEach { holdEssential() }.launchIn(scope)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        holdEssential()
        syncProgress()
    }

    override fun onListenerDisconnected() {
        isConnected = false
        animator.stopEssential()
        progressNotifications.clear()
        animator.endProgress()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        GlyphLog.d(TAG) { "Notifications no longer watched" }
        scope.cancel()
        animator.stopEssential()
        progressNotifications.clear()
        animator.endProgress()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        updateProgress(sbn)

        if (!preferences.areNotificationsEnabled || !sbn.isWanted(rankingMap)) {
            return
        }

        if (preferences.isAppEnabled(sbn.packageName)) {
            wakeLock.acquireForAnimation()
            val hasOverride = preferences.hasNotificationAnimationOverride(sbn.packageName)
            animator.playNotification(
                preferences.notificationAnimation(sbn.packageName),
                preferences.isNotificationAnimationReversed(sbn.packageName),
                preferences.areNotificationTonesSynced && !hasOverride,
            )
        }

        if (
            preferences.isAppEssential(sbn.packageName) &&
                notificationManager.isNotificationPolicyAccessGranted
        ) {
            animator.playEssential()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        progressNotifications.remove(sbn.key)
        showProgress()
        holdEssential()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = holdEssential(rankingMap)

    /** Rebuilds progress after connecting or changing its setting. */
    private fun syncProgress() {
        progressNotifications.clear()
        if (isConnected && preferences.isProgressEnabled) {
            activeNotifications.orEmpty().forEach { sbn ->
                sbn.progressPercent()?.let { progressNotifications[sbn.key] = it }
            }
        }
        showProgress()
    }

    /** Adds, updates, or removes one determinate progress notification. */
    private fun updateProgress(sbn: StatusBarNotification) {
        progressNotifications.remove(sbn.key)
        if (preferences.isProgressEnabled) {
            sbn.progressPercent()?.let { progressNotifications[sbn.key] = it }
        }
        showProgress()
    }

    private fun showProgress() {
        val percent = progressNotifications.values.lastOrNull()
        if (percent == null) animator.endProgress() else animator.setProgress(percent)
    }

    private fun StatusBarNotification.progressPercent(): Int? {
        val extras = notification.extras ?: return null
        if (extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) return null

        val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val maximum = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
        if (progress < 0 || maximum <= 0) return null
        return ((progress.coerceAtMost(maximum).toLong() * 100L) / maximum).toInt()
    }

    /** Holds a glyph lit for as long as an essential notification stands. */
    private fun holdEssential(rankingMap: RankingMap? = null) {
        if (!isConnected) return

        val rankings = rankingMap ?: currentRanking
        val isEssential =
            preferences.areNotificationsEnabled &&
                notificationManager.isNotificationPolicyAccessGranted &&
                activeNotifications.orEmpty().any {
                    preferences.isAppEssential(it.packageName) && it.isWanted(rankings)
                }

        if (isEssential) animator.playEssential() else animator.stopEssential()
    }

    /**
     * Whether the strips should answer this notification at all: ongoing ones are already on
     * screen, some apps and channels are never interesting, and do not disturb outranks the rest.
     */
    private fun StatusBarNotification.isWanted(rankingMap: RankingMap?): Boolean {
        if (isOngoing || packageName in APPS_TO_IGNORE) {
            return false
        }

        val channelId = notification.channelId
        if ("$packageName:$channelId" in NOTIFICATIONS_TO_IGNORE) {
            return false
        }

        // Ranking carries the posted notification's user and conversation channel.
        val ranking = Ranking()
        val hasRanking = rankingMap?.getRanking(key, ranking) == true
        val channel = if (hasRanking) ranking.channel else null
        val importance = if (hasRanking) ranking.importance else channel?.importance
        val canBypassDnd = channel?.canBypassDnd() == true

        GlyphLog.d(TAG) {
            "$packageName:$channelId importance $importance, bypasses dnd $canBypassDnd"
        }

        val isVisible =
            importance == null ||
                importance >= NotificationManager.IMPORTANCE_DEFAULT ||
                (preferences.areSilentNotificationsEnabled &&
                    importance > NotificationManager.IMPORTANCE_NONE)
        val isAllowed =
            notificationManager.currentInterruptionFilter <=
                NotificationManager.INTERRUPTION_FILTER_ALL || canBypassDnd

        return isVisible && isAllowed
    }
}
