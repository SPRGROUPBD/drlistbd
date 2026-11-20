package com.example

import android.content.Context
import android.content.pm.PackageInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.analytics.AppInstallationTracker
import com.example.data.local.AppDatabase
import com.example.ui.ads.ConnectivityMonitor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppInstallationAnalyticsTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private class InMemoryRtdbTransport : AppInstallationTracker.RemoteTransport {
        val nodes = ConcurrentHashMap<String, String>()
        var failAll = false

        override fun nodeExists(path: String): Boolean {
            if (failAll) return false
            if (path.endsWith("/firstInstallDate")) {
                val base = path.removeSuffix("/firstInstallDate")
                val body = nodes[base] ?: return false
                return runCatching { JSONObject(body).has("firstInstallDate") }.getOrDefault(false)
            }
            return nodes.containsKey(path)
        }

        override fun putJson(path: String, jsonBody: String): Boolean {
            if (failAll) return false
            // Write-once rule on app_analytics/ matches Firebase database.rules.json
            if (path.startsWith("app_analytics/") && nodes.containsKey(path)) return true
            nodes[path] = jsonBody
            return true
        }

        override fun patchJson(path: String, jsonBody: String): Boolean {
            if (failAll) return false
            val existing = nodes[path]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
            val patch = JSONObject(jsonBody)
            val keys = patch.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                existing.put(k, patch.get(k))
            }
            nodes[path] = existing.toString()
            return true
        }
    }

    private lateinit var fakeTransport: InMemoryRtdbTransport

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppInstallationTracker.resetForTesting(context, clearPrefs = true)
        fakeTransport = InMemoryRtdbTransport()
        AppInstallationTracker.transport = fakeTransport
        ConnectivityMonitor.setOnlineForTesting(true)

        val pkgInfo = PackageInfo().apply {
            packageName = context.packageName
            versionName = "1.0"
            @Suppress("DEPRECATION")
            versionCode = 1
            firstInstallTime = 1_780_000_000_000L
        }
        shadowOf(context.packageManager).installPackage(pkgInfo)
    }

    @After
    fun tearDown() {
        AppInstallationTracker.resetForTesting(context, clearPrefs = true)
        db.close()
        ConnectivityMonitor.setOnlineForTesting(true)
    }

    @Test
    fun generatesPrivacySafeRandomIdAndPreventsDuplicateFirstInstall() = runBlocking {
        val t0 = 1_780_000_010_000L
        val first = AppInstallationTracker.trackAppLaunch(context, db, nowMs = t0)
        assertTrue(first.installationId.startsWith("inst_"))
        assertTrue(first.syncedFirstInstall)
        assertEquals(1, first.totalAppOpens)

        // Ensure ZERO PII fields in uploaded JSON
        val remoteInstallJson = JSONObject(fakeTransport.nodes["app_installations/${first.installationId}"]!!)
        assertFalse(remoteInstallJson.has("imei"))
        assertFalse(remoteInstallJson.has("phoneNumber"))
        assertFalse(remoteInstallJson.has("contacts"))
        assertFalse(remoteInstallJson.has("email"))
        assertTrue(remoteInstallJson.has("firstInstallDate"))
        assertEquals("ACTIVE", remoteInstallJson.getString("status"))

        // Second process launch same install -> keeps same installationId, does NOT duplicate FIRST_INSTALL
        val second = AppInstallationTracker.trackAppLaunch(
            context,
            db,
            nowMs = t0 + 120_000L,
            forceColdLaunchForTest = true
        )
        assertEquals(first.installationId, second.installationId)
        assertEquals(2, second.totalAppOpens)

        val firstInstallEvents = fakeTransport.nodes.keys.filter { it.startsWith("app_analytics/evt_inst_") }
        val openEvents = fakeTransport.nodes.keys.filter { it.startsWith("app_analytics/evt_open_") }
        assertEquals(1, firstInstallEvents.size)
        assertEquals(2, openEvents.size)
        assertEquals(0, db.pendingAppAnalyticsEventDao().getPendingOnce().size)
    }

    @Test
    fun offlineLaunchQueuesInRoomAndFlushesAutomaticallyWhenOnline() = runBlocking {
        ConnectivityMonitor.setOnlineForTesting(false)
        val t0 = 1_780_050_000_000L

        val offlineInstall = AppInstallationTracker.trackAppLaunch(context, db, nowMs = t0)
        assertFalse(offlineInstall.syncedFirstInstall)
        // Both FIRST_INSTALL and APP_OPEN queued offline
        val queued = db.pendingAppAnalyticsEventDao().getPendingOnce()
        assertEquals(2, queued.size)
        assertTrue(queued.all { it.wasOffline })
        assertTrue(fakeTransport.nodes.isEmpty())

        // Reconnect and sync
        ConnectivityMonitor.setOnlineForTesting(true)
        val flushed = AppInstallationTracker.syncPendingEvents(context, db, nowMs = t0 + 30_000L)
        assertEquals(2, flushed)
        assertEquals(0, db.pendingAppAnalyticsEventDao().getPendingOnce().size)
        assertTrue(fakeTransport.nodes.containsKey("app_installations/${offlineInstall.installationId}"))
        assertTrue(fakeTransport.nodes.containsKey("app_analytics/evt_inst_${offlineInstall.installationId}"))
    }

    @Test
    fun reinstallationDetectedViaPackageFirstInstallTimeCreatesNewInstallId() = runBlocking {
        val t0 = 1_780_100_000_000L
        val install1 = AppInstallationTracker.trackAppLaunch(context, db, nowMs = t0)

        // Simulate uninstall + reinstall where Android PackageManager has a newer firstInstallTime
        val reinstalledPkg = PackageInfo().apply {
            packageName = context.packageName
            versionName = "1.0"
            @Suppress("DEPRECATION")
            versionCode = 1
            firstInstallTime = 1_785_000_000_000L
        }
        shadowOf(context.packageManager).installPackage(reinstalledPkg)

        val install2 = AppInstallationTracker.trackAppLaunch(
            context,
            db,
            nowMs = 1_785_000_010_000L,
            forceColdLaunchForTest = true
        )
        assertNotEquals(install1.installationId, install2.installationId)
        val installEvents = fakeTransport.nodes.keys.filter { it.startsWith("app_analytics/evt_inst_") }
        assertEquals(2, installEvents.size)
    }

    @Test
    fun activityHeartbeatThrottlesAndFcmTokenUpdatesInstallation() = runBlocking {
        val t0 = 1_780_200_000_000L
        val inst = AppInstallationTracker.trackAppLaunch(context, db, nowMs = t0)

        // Within 5 minutes -> throttled (no duplicate APP_ACTIVE)
        AppInstallationTracker.recordForegroundActivity(context, db, nowMs = t0 + 60_000L)
        assertEquals(0, fakeTransport.nodes.keys.count { it.startsWith("app_analytics/evt_act_") })

        // After 6 minutes -> recorded
        AppInstallationTracker.recordForegroundActivity(context, db, nowMs = t0 + 360_000L)
        assertEquals(1, fakeTransport.nodes.keys.count { it.startsWith("app_analytics/evt_act_") })

        // Register optional FCM token for push-based uninstall detection
        AppInstallationTracker.updateFcmToken(context, db, "fcm_tok_sample_12345", nowMs = t0 + 400_000L)
        val remoteJson = JSONObject(fakeTransport.nodes["app_installations/${inst.installationId}"]!!)
        assertEquals("fcm_tok_sample_12345", remoteJson.getString("fcmToken"))
        assertEquals("VALID", remoteJson.getString("fcmTokenStatus"))
    }
}
