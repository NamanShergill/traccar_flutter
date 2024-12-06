/*
 * Copyright 2012 - 2021 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.mostafamovahhed.traccar_flutter.client

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.lang.RuntimeException
import dev.mostafamovahhed.traccar_flutter.R

class TrackingService : Service() {

    private var wakeLock: WakeLock? = null
    private var trackingController: TrackingController? = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        try {
            startForeground(NOTIFICATION_ID, createNotification(this))
            Log.i(TAG, "service create")
            sendBroadcast(Intent(ACTION_STARTED).setPackage(packageName))
            TraccarController.addStatusLog(getString(R.string.status_service_create))

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (sharedPreferences.getBoolean(TraccarController.KEY_WAKELOCK, true)) {
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    wakeLock =
                        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.name)
                    wakeLock?.acquire()
                }
                trackingController = TrackingController(this)
                trackingController?.start()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, e)
            sharedPreferences.edit().putBoolean(TraccarController.KEY_STATUS, false).apply()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WakefulBroadcastReceiver.completeWakefulIntent(intent)
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "service destroy")
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        TraccarController.addStatusLog(getString(R.string.status_service_destroy))
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        trackingController?.stop()
    }

    companion object {

        // Explicit package name should be specified when broadcasting START/STOP notifications -
        // it is required for manifest-declared receiver of the status widget (when running on Android 8+).
        // Refer to https://developer.android.com/guide/components/broadcasts#manifest-declared-receivers
        const val ACTION_STARTED = "org.traccar.action.SERVICE_STARTED"
        const val ACTION_STOPPED = "org.traccar.action.SERVICE_STOPPED"
        private val TAG = TrackingService::class.java.simpleName
        private const val NOTIFICATION_ID = 1

        @SuppressLint("UnspecifiedImmutableFlag")
        private fun createNotification(context: Context): Notification {

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

            val startIntent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("action", NotificationReceiver.START_SERVICE_ACTION)
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context,
                NotificationReceiver.START_SERVICE_ACTION,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("action", NotificationReceiver.STOP_SERVICE_ACTION)
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                NotificationReceiver.STOP_SERVICE_ACTION,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val icon = sharedPreferences.getString(TraccarController.KEY_NOTIFICATION_ICON, null)
                ?: "ic_stat_notify"

            val builder = NotificationCompat.Builder(context, TraccarController.PRIMARY_CHANNEL)
                .setSmallIcon(
                    context.resources.getIdentifier(
                        "ic_notification",
                        "drawable",
                        context.packageName
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentTitle(context.getString(R.string.settings_status_on_summary))
                .setTicker(context.getString(R.string.settings_status_on_summary))
//                .setColor(ContextCompat.getColor(context, R.color.primary_dark))
//                .addAction(android.R.drawable.ic_media_play, "Start", startPendingIntent)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.shortcut_stop),
                    stopPendingIntent
                )

//            val intent = Intent(context, TraccarController::class.java)
//            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                PendingIntent.FLAG_IMMUTABLE
//            } else {
//                PendingIntent.FLAG_UPDATE_CURRENT
//            }
//            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, flags))

            return builder.build()
        }
    }
}
