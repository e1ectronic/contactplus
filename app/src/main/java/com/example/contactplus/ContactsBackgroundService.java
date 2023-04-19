package com.example.contactplus;

import static android.os.Build.VERSION.SDK_INT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class ContactsBackgroundService extends Service {

    ContactPlusApp app;
    private static final String TAG = "ContactsBackgroundSvc";
    private Timer timer;
    private int delay = 3 * 1000; // initial delay of 5 seconds
    public static Boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");
        timer = new Timer();
        app = (ContactPlusApp) getApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");  // intent could be null

        // Create a notification channel if targeting Android Oreo (API 26) or higher
        if (SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(ContactPlusApp.NOTIFICATION_CHANNEL_ID, "My Channel", NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create the notification and start the service in the foreground
        Notification notification = new NotificationCompat.Builder(this, ContactPlusApp.NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Contact+ фоновая служба")
                .setContentText("Запущена...")
                .setSmallIcon(android.R.drawable.btn_star)
                .build();

        startForeground(ContactPlusApp.NOTIFICATION_ID, notification);
        timer.schedule(new CheckDataTimerTask(this), delay);
        return START_STICKY;
    }

    /*
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
        wakeLock.acquire();
        // Do your background work here
        wakeLock.release();
     */

    public static void refresh(Context theContext) {
        LocalBroadcastManager.getInstance(theContext).sendBroadcast(new Intent(ContactPlusApp.ACTION));
    }

    public static void runJob(ContactPlusApp theApp, Context theContext) {
        if (isRunning) return;
        isRunning = true;
        try {
            theApp.addList("[" + getTime() + "] Передача данных");
            if (theApp.hasGoodPassword()) {
                LocalBroadcastManager.getInstance(theContext).sendBroadcast(new Intent(ContactPlusApp.ACTION));
                new DownloadContactsTask(theApp, theContext, new DownloadContactsTask.OnContactsDownloadedListener() {
                    @Override
                    public void onContactsDownloaded() {
                        refresh(theContext);
                        isRunning = false;
                    }
                }).execute();
            } else {
                theApp.addList("Проверьте e-mail и код доступа");
                refresh(theContext);
                isRunning = false;
            }
        } catch (Exception e) {
            theApp.addList("Ошибка: " + e.getMessage());
            refresh(theContext);
            isRunning = false;
        }
    }


    @NonNull
    private static String getTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        return dateFormat.format(calendar.getTime());
    }

    private class CheckDataTimerTask extends TimerTask {

        Context context;

        public CheckDataTimerTask(Context theContext) {
            super();
            context = theContext;
        }

        @Override
        public void run() {

            // Perform network request to check for new data
            // If new data is available, create and display a notification

            Log.d(TAG, "timer task run");

            runJob(app, context);

            // Check battery and Wi-Fi status
            // Set new delay based on current status
            if (getBatteryLevel() < 20 || !isWifiConnected()) {
                delay = 3 * 60 * 1000; // set delay to 3 minutes
            } else {
                delay = 30 * 1000; // set delay to 30 seconds
            }
//            delay = 5 * 1000; //!! temp fix

            // Reschedule the TimerTask with the new delay
            timer.cancel();
            timer = new Timer();
            timer.schedule(new CheckDataTimerTask(context), delay);
            Log.d(TAG, "New timer task " + delay);
        }

        private int getBatteryLevel() {
            BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int batteryLevel = 0;
            if (SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } else {
                batteryLevel = 100;
            }
            return batteryLevel;
        }

        private Boolean isWifiConnected() {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifiInfo.isConnected();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
        timer.cancel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
