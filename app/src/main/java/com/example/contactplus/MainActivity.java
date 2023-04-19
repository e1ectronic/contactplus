package com.example.contactplus;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;

@RequiresApi(api = Build.VERSION_CODES.P)
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ContactPlus";
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final int NEXT_ALARM = (5 * 60 + 2) * 1000;  // every five minutes

    TextView mainTextView;
    ScrollView scrollView;
    Button getContactsButton;
    Button startButton;
    Button stopButton;
    BroadcastReceiver broadcastReceiver;
    BroadcastReceiver alarmReceiver;
    ContactPlusApp app;
    Context ctx;

    @Override
    protected void onStart() {
        super.onStart();

        receiveUpdateBroadcast();
        receiveAlarmBroadcast();
        refresh();
    }

    void receiveUpdateBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ContactPlusApp.ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    void receiveAlarmBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ContactPlusApp.ALARM_ACTION);
        registerReceiver(alarmReceiver, intentFilter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void refresh() {
        if (mainTextView != null) {
            Log.d(TAG, "do refresh");
            mainTextView.setText(app.getListStr());
            // Scroll the scroll view to the bottom
            if (scrollView != null) {
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.smoothScrollTo(0, mainTextView.getBottom());
                    }
                });
            }
        }
    }

    private int getContactsCount() {
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    boolean checkPermissions(String[] permissions) {
        Log.d(TAG, "Check Permissions");
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
                Log.d(TAG, "Permission denied: " + permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            return true;
        } else {
            Log.d(TAG, "Request permissions");

            String[] requestPermissions = new String[permissionsToRequest.size()];
            requestPermissions = permissionsToRequest.toArray(requestPermissions);
            ActivityCompat.requestPermissions(this, requestPermissions, PERMISSIONS_REQUEST_CODE);
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // all permissions granted, do your work
                permissionOk();
            } else {
                // some permissions denied, ask again
                if (checkPermissions(permissions)) permissionOk();
            }
        }
    }

    void startBackgroundService() {
        // Create an explicit Intent for the service
        Intent serviceIntent = new Intent(this, ContactsBackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        scheduleAlarm(this, NEXT_ALARM);
    }

    void permissionOk() {
        Log.d(TAG, "Permission OK");
        if (app.serviceRunning) {
            startBackgroundService();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        } else {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
        getContactsButton.setEnabled(true);
        app.addList("Всего контактов: " + getContactsCount());
    }

    private void scheduleAlarm(Context context, long interval) {
        Intent intent = new Intent(ContactPlusApp.ALARM_ACTION);
        int flag = 0;
        if (SDK_INT >= Build.VERSION_CODES.M) flag = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flag);
        long alarmTime = (System.currentTimeMillis() + interval) / 1000 * 1000;

        Log.d(TAG, "Set alarm time: " + alarmTime);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.d(TAG, "set: " + alarmTime);
            alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
        }
        else if (Build.VERSION_CODES.KITKAT <= SDK_INT  && SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "setExact: " + alarmTime);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
        }
        else if (SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "setExactAndAllowWhileIdle: " + alarmTime);
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
        }
    }

    void cancelAlarm(Context context) {
        Intent intent = new Intent(ContactPlusApp.ALARM_ACTION);
        int flag = 0;
        if (SDK_INT >= Build.VERSION_CODES.M) flag = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flag);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Log.d(TAG, "unset alarm");
        alarmManager.cancel(pendingIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        app = (ContactPlusApp) getApplication();
        mainTextView = findViewById(R.id.main_text_view);
        scrollView = findViewById(R.id.scroll_view);
        getContactsButton = findViewById(R.id.get_contacts_button);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        ctx = this;

        getContactsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ContactsBackgroundService.runJob(app, ctx);
            }
        });

        Button settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!app.serviceRunning) {
                    app.setServiceRunning(true);
                    startBackgroundService();
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    app.addList("Фоновая служба работает");
                    refresh();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (app.serviceRunning) {
                    app.setServiceRunning(false);
//                    ContactsBackgroundService.isActive = false;
                    stopService(new Intent(MainActivity.this, ContactsBackgroundService.class));
                    cancelAlarm(ctx);
                    stopButton.setEnabled(false);
                    startButton.setEnabled(true);
                    app.addList("Фоновая служба остановлена");
                    refresh();
                }
            }
        });

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refresh();
            }
        };

        alarmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Alarm received ============");
                if (app.serviceRunning) {
                    if (!isServiceRunning(ContactsBackgroundService.class.getName())) {
                        startBackgroundService();
                    } else {
                        ContactsBackgroundService.runJob(app, ctx);
                        scheduleAlarm(ctx, NEXT_ALARM);
                    }
                }
            }
        };

        // setup permissions
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    Manifest.permission.USE_EXACT_ALARM,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[] {
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        Log.d(TAG, "Check permissions");

        if (checkPermissions(permissions)) {
            permissionOk();
        } else {
            Log.d(TAG, "Permissions NOT OK");
            startButton.setEnabled(false);
            stopButton.setEnabled(false);
            getContactsButton.setEnabled(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        unregisterReceiver(alarmReceiver);
    }

    public boolean isServiceRunning(String serviceName) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(serviceInfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
