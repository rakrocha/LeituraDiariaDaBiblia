package com.example.leituradiariadabiblia

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import java.util.Calendar

class BibleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("lembrete_biblia", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val hour = prefs.getInt("hour", 20)
        val minute = prefs.getInt("minute", 0)
        createChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openApp = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context, 7102, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, "leitura_biblia_diaria")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Leitura Diária")
            .setContentText("Está na hora da sua leitura da Bíblia. Reserve alguns minutos para ela.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canNotify) manager.notify(7103, builder.build())

        // Reprograma para o próximo dia no horário escolhido.
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarmIntent = Intent(context, BibleReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 7101, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "leitura_biblia_diaria",
                "Lembrete diário da Bíblia",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Lembrete diário para fazer a leitura da Bíblia" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}

class BibleReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("lembrete_biblia", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return
        val hour = prefs.getInt("hour", 20)
        val minute = prefs.getInt("minute", 0)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarmIntent = Intent(context, BibleReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 7101, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }
}
