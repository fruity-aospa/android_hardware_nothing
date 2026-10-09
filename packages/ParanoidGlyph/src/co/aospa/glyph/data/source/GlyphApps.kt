/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.glyph.data.source

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import co.aospa.glyph.data.model.APPS_TO_IGNORE

/** The apps the notification screens offer, by the name they show. */
fun launchableApps(packageManager: PackageManager): List<ApplicationInfo> =
    packageManager
        .getInstalledApplications(0)
        .filter { app ->
            packageManager.getLaunchIntentForPackage(app.packageName) != null &&
                app.packageName !in APPS_TO_IGNORE
        }
        .sortedWith(ApplicationInfo.DisplayNameComparator(packageManager))
