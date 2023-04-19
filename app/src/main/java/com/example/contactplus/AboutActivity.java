package com.example.contactplus;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "ContactPlusAbout";

    ContactPlusApp app;
    Context context;

    void deleteContacts(boolean all) {

        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = null;
        try {
            if (all) {
                cursor = contentResolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        null,null, null, null
                );
                while (cursor != null && cursor.moveToNext()) {
                    @SuppressLint("Range")
                    long rawContactId = cursor.getLong(cursor.getColumnIndex(ContactsContract.RawContacts._ID));
                    Uri rawContactUri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId);
                    contentResolver.delete(rawContactUri, null, null);
                }
            } else {  // email must not be empty
                cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Email.DATA + " IS NOT NULL",
                        null,
                        null
                );
                while (cursor != null && cursor.moveToNext()) {
                    @SuppressLint("Range")
                    long contactId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID));
                    Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
                    getContentResolver().delete(uri, null, null);
                }
            }

            // Show a message indicating the number of contacts deleted
            int numDeleted = cursor.getCount();
            String message = String.format("%d contacts deleted", numDeleted);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Error deleting contacts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        app = (ContactPlusApp) getApplication();
        context = this;

        Button getContactsButton = findViewById(R.id.delete_button);
        getContactsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Подтвердите удаление")
                        .setMessage("Удалить контакты?")
                        .setPositiveButton("Все", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                deleteContacts(true);
                            }
                        })
                        .setNeutralButton("Отмена", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled, do nothing
                            }
                        })
                        .setNegativeButton("Некоторые", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                deleteContacts(false);
                            }
                        })
                        .setCancelable(true);
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}