package com.otphub.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Ensures scheduled auto-sync and remote command workers are re-enqueued
 * after device reboot or direct-boot unlock when the screen is locked.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val app = context.applicationContext
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        if (app.getSharedPreferences("otp_hub_autosync", Context.MODE_PRIVATE).getBoolean("enabled", false)) {
            WorkManager.getInstance(app).enqueueUniqueWork(
                "otp-hub-otp-upload",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OtpUploadWorker>().setConstraints(constraints).build()
            )
        }

        if (app.getSharedPreferences("otp_hub_remote", Context.MODE_PRIVATE).getBoolean("enabled", false)) {
            WorkManager.getInstance(app).enqueueUniqueWork(
                "otp-hub-remote-commands-boot",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RemoteCommandWorker>().setConstraints(constraints).build()
            )
        }
    }
}
