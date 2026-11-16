/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.Services

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

    override fun onCreate() {
        GlyphLog.d(TAG) { "Notifications watched" }
        super.onCreate()

        preferences.changes
            .filter { key -> key in KEYS_WATCHED }
            .onEach { key ->
                GlyphLog.d(TAG) { "$key changed" }
                holdEssential()
            }
            .launchIn(scope)

        // Quiet hours darken the essential glyph, so it is lit again once they end.
        glyph.schedule.isQuiet.onEach { holdEssential() }.launchIn(scope)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        holdEssential()
    }

    override fun onListenerDisconnected() {
        isConnected = false
        animator.stopEssential()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        GlyphLog.d(TAG) { "Notifications no longer watched" }
        scope.cancel()
        animator.stopEssential()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (!preferences.areNotificationsEnabled || !sbn.isWanted(rankingMap)) {
            return
        }

        if (preferences.isAppEnabled(sbn.packageName)) {
            wakeLock.acquireForAnimation()
            animator.playCsv(preferences.notificationAnimation)
        }

        if (
            preferences.isAppEssential(sbn.packageName) &&
                notificationManager.isNotificationPolicyAccessGranted
        ) {
            animator.playEssential()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = holdEssential()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = holdEssential(rankingMap)

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

        // Silent channels are still visible notifications; only fully blocked channels are ignored.
        val isVisible = importance == null || importance > NotificationManager.IMPORTANCE_NONE
        val isAllowed =
            notificationManager.currentInterruptionFilter <=
                NotificationManager.INTERRUPTION_FILTER_ALL || canBypassDnd

        return isVisible && isAllowed
    }
}
