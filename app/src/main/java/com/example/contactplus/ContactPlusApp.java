package com.example.contactplus;

import android.app.Application;
import android.content.SharedPreferences;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class ContactPlusApp extends Application {

    public static final String PHONES_URL = "https://sport-raspisanie.ru/run/phones/";
//    public static final String PHONES_URL = "http://www.sport-raspisanie.ru/run/phones/";
//    public static final String PHONES_URL = "http://192.168.0.10/run/phones/";
    public static final int NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "ContactPlus_channel_id";
    public static final String ACTION = "com.example.comtactplus.action";
    public static final String ALARM_ACTION = "com.example.comtactplus.alarm";
    public static final String PREFS = "com.example.comtactplus.prefs";
//    public static final String MESSAGE = "com.example.comtactplus.message";
    public ArrayList<String> list;
    public final int MAX_LIST_SIZE = 200;

    public String email = "";
    public String password = "";
    public String badCredentials = "";
    public int lastContactId = 0;
    public boolean serviceRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        list = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        email = prefs.getString("email", "").trim();
        password = prefs.getString("password", "").trim();
        lastContactId = prefs.getInt("last", 0);
        serviceRunning = prefs.getInt("servicerunning", 0) > 0;
    }

    public String url() {
        return PHONES_URL
            + "?last=" + lastContactId
            + "&email=" + email
            + "&password=" + password
            + "&v=" + BuildConfig.VERSION_NAME;
    }

    public void savePrefs(String newEmail, String newPassword) {
        if (newEmail == null || newPassword == null) return;
        email = newEmail.trim();
        password = newPassword.trim();
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString("email", email);
        editor.putString("password", password);
        editor.apply();
        badCredentials = "";  // reset bad credentials
    }


    public String getListStr() {
        return String.join("\n", list);
//        StringBuilder b = new StringBuilder();
//        for (int i = 0, ii = list.size(); i < ii; i++) b.append(i + ": " + list.get(i) + "\n");
//        return b.toString();
    }

    public void addList(String s) {
        if (list.size() > MAX_LIST_SIZE) {
            list.subList(0, list.size() - MAX_LIST_SIZE).clear();
        }
        list.add(s);
    }

    public boolean hasGoodPassword() {
        if (email.isEmpty() || password.isEmpty()) return false;
        String credentials = email + " " + password;
        if (credentials.equalsIgnoreCase(badCredentials)) return false;
        return true;
    }

    public void setError(String error) {
        badCredentials = (email + " " + password);
        addList("Ошибка доступа: [" + badCredentials + "]");
    }

    public void setLastContactId(int theLastContactId) {
        lastContactId = theLastContactId;
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putInt("last", lastContactId);
        editor.apply();
    }

    public void setServiceRunning(boolean running) {
        serviceRunning = running;
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putInt("servicerunning", serviceRunning ? 1 : 0);
        editor.apply();
    }

    public static void appendLog(String text) {
        File logFile = new File("sdcard/log.file");
        boolean ok = logFile.exists();
        if (!ok) {
            try {
                logFile.createNewFile();
                ok = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (ok) {
            try {
                //BufferedWriter for performance, true to set append to file flag
                BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
                buf.append(text);
                buf.newLine();
                buf.flush();
                buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
