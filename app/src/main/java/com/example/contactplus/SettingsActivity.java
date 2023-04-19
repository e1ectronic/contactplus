package com.example.contactplus;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "ContactPlusSettings";

    ContactPlusApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        app = (ContactPlusApp) getApplication();

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "back clicked");
                finish();
            }
        });

        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "save clicked");
                if (saved()) finish();
            }
        });

        ((EditText) findViewById(R.id.email_input)).setText(app.email);
        ((EditText) findViewById(R.id.password_input)).setText(app.password);
        ((EditText) findViewById(R.id.lastid_input)).setText(Integer.toString(app.lastContactId));

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean validateInput() {

        EditText emailEditText = findViewById(R.id.email_input);
        EditText passwordEditText = findViewById(R.id.password_input);

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Invalid email address");
            emailEditText.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return false;
        } else if (password.length() < 4) {
            passwordEditText.setError("Password must be at least 4 characters long");
            passwordEditText.requestFocus();
            return false;
        }
        return true;
    }

    boolean saved() {

        Log.d(TAG, "saved");

        if (!validateInput()) return false;

        String email = ((EditText) findViewById(R.id.email_input)).getText().toString().trim();
        String password = ((EditText) findViewById(R.id.password_input)).getText().toString().trim();
        app.addList("E-mail: " + email + ", код доступа: " + password);
        app.savePrefs(email, password);

        String last = ((EditText) findViewById(R.id.lastid_input)).getText().toString().trim();
        if (!last.isEmpty()) {
            app.setLastContactId(Integer.parseInt(last));
            app.addList("Новый номер загрузки: " + last);
        }

        return true;
    }
}