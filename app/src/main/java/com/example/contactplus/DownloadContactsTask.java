package com.example.contactplus;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DownloadContactsTask extends AsyncTask<Void, Void, String> {

    private static final String TAG = "DownloadContactsTask";
    ContactPlusApp app;
    Context context;

    private final OnContactsDownloadedListener listener;

    public DownloadContactsTask(ContactPlusApp theApp, Context theContext, OnContactsDownloadedListener theListener) {
        super();
        app = theApp;
        context = theContext;
        listener = theListener;
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            URL url = new URL(app.url());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                Log.d(TAG, "received data");
                return response.toString();
            } else {
                Log.d(TAG, "conn not OK: " + conn.getResponseCode());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Exception in background");
            return null;
        }
    }


    private static class CallbackAsyncTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            String result = null;
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                // Get the response code and message from the server
                int responseCode = urlConnection.getResponseCode();
                String responseMessage = urlConnection.getResponseMessage();

                // Check if the response code is in the 200 range to indicate a successful request
                if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < 300) {
                    // Request was successful, log the response message
                    Log.d(TAG, "Response message: " + responseMessage);
                } else {
                    // Request failed, log the response code and message
                    Log.e(TAG, "Request failed with response code " + responseCode + " and message: " + responseMessage);
                }

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                result = readStream(in);

                urlConnection.disconnect();

            } catch (IOException e) {
                Log.e(TAG, "Exception occurred during request: " + e.getMessage());
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            // Handle the result here
        }

        private String readStream(InputStream is) throws IOException {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while(i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        }
    }


    int addContacts(JSONArray contactsArray) throws RemoteException, OperationApplicationException {
        if (contactsArray == null) return 0;

        ContentResolver content = context.getContentResolver();

        long startTime = System.currentTimeMillis();

        List<String> numbers = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> dupNames = new ArrayList<>();
        ArrayList<JSONObject> contacts = new ArrayList<>();
        for (int i = 0; i < contactsArray.length(); i++) {
            JSONObject contact = contactsArray.optJSONObject(i);
            if (contact == null) continue;

            String phone = contact.optString("phone", "").trim();
            if (phone.isEmpty()) continue;
            numbers.add("+" + phone);

            String name = contact.optString("name", "").trim();
            if (!name.isEmpty()) {
                if (names.contains(name)) {
                    dupNames.add(name);
                } else {
                    names.add(name);
                }
            }

            contacts.add(contact);
        }

        Set<String> knownPhoneNumbers = getKnownPhoneNumbers(content, numbers);
        Set<String> knownNames = getKnownNames(content, names);
        // for (String name : dupNames) knownNames.add(name);

        app.addList("Known names: " + knownNames.size());
        app.addList("Duplicate names: " + dupNames.size());

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        int count = 0, id, maxId = 0;
        for (int i = 0; i < contacts.size(); i++) {
            JSONObject contact = contacts.get(i);
            id = contact.optInt("id", 0);
            if (id > maxId) maxId = id;
            String phone = contact.optString("phone", "").trim();
            if (phone.isEmpty()) continue;
            phone = "+" + phone;
            if (!knownPhoneNumbers.contains(phone)) {
                if (addContact(content, ops, phone, contact, knownNames)) count++;
            }
        }

        if (ops.size() > 0) content.applyBatch(ContactsContract.AUTHORITY, ops);

        app.addList("Добавлено контактов: " + count
                + " за " + (System.currentTimeMillis() - startTime) + " ms");

        if (maxId > 0) checkBack(maxId);

        return count;
    }

    public Set<String> getKnownPhoneNumbers(ContentResolver content, List<String> phoneNumbers) {
        Set<String> matchedPhoneNumbers = new HashSet<>();
        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " IN ("
                + TextUtils.join(",", Collections.nCopies(phoneNumbers.size(), "?")) + ")";
        Cursor cursor = content.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                selection,
                phoneNumbers.toArray(new String[0]),
                null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range")
                String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                matchedPhoneNumbers.add(phoneNumber);
            }
            cursor.close();
        }
        return matchedPhoneNumbers;
    }

    public Set<String> getKnownNames(ContentResolver content, List<String> names) {
        Set<String> matchedNames = new HashSet<>();

        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " IN ("
                + TextUtils.join(",", Collections.nCopies(names.size(), "?")) + ")";
        Cursor cursor = content.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                selection,
                names.toArray(new String[0]),
                null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range")
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                matchedNames.add(name);
            }
            cursor.close();
        }
        return matchedNames;
    }

    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            try {
                if (result.startsWith("{")) {  // This is a JSON object, not an array
                    JSONObject jsonObject = new JSONObject(result);
                    String error = jsonObject.optString("error", "").trim();
                    app.addList("Ошибка: " + error);
                    app.setError(error);
                } else {
                    JSONArray tasksArray = new JSONArray(result);  // array of tasks
                    int count = 0;
                    for (int i = 0; i < tasksArray.length(); i++) {
                        JSONObject task = tasksArray.optJSONObject(i);
                        if (task == null) continue;
                        switch (task.optString("task", "")) {
                            case "add":
                                count = addContacts(task.optJSONArray("contacts"));
                                break;
                        }
                    }
                    if (count == 0) app.addList("Нет новых контактов");
                }
            } catch (Exception e) {
                e.printStackTrace();
                app.addList("Ошибка при обработке данных: " + e.getMessage());
            }
        } else {
            app.addList("Ошибка при скачивании данных");
        }
        if (listener != null) {
            listener.onContactsDownloaded();
        }
    }

    void checkBack(int lastId) {
        try {
            app.setLastContactId(lastId);
            CallbackAsyncTask task = new CallbackAsyncTask();
            task.execute(app.url() + "&done=1");
        } catch (Exception e) {
            // Log any exceptions that occurred during the request
            Log.e(TAG, "Exception occurred during request: " + e.getMessage(), e);
        }
    }

    boolean addContact(
            ContentResolver content,
            ArrayList<ContentProviderOperation> ops,
            String phone,
            JSONObject contactObject,
            Set<String> knownNames
    ) {
        // Check if phone number already exists in phonebook
        // if (checkIfPhoneNumberExists(content, phone)) return false;

        String sourceName = contactObject.optString("name", "").trim();
        String name = sourceName;
        if (sourceName.isEmpty()) {
            name = phone;
        } else {
            int nameTry = 1;
            do {
                if (!knownNames.contains(name)) {
                    if (nameTry == 1) {
                        break;
                    } else {
                        if (!checkIfNameExists(content, name)) break;
                    }
                }
                nameTry++;
                name = sourceName + " / " + nameTry;  // Alice / 2
            } while (true);
            if (nameTry > 1) app.addList("Deduplicate name: " + name + ", try: " + nameTry);
            knownNames.add(name);  // add to map
        }
        app.addList(phone + " / " + name);

        String firstName = contactObject.optString("first_name", "").trim();
        String lastName = contactObject.optString("last_name", "").trim();
        String url = contactObject.optString("url", "").trim();
        String notes = contactObject.optString("notes", "").trim();
        String email = contactObject.optString("email", "").trim();
        String org = contactObject.optString("org", "Академия спорта").trim();
        String role = contactObject.optString("role", "Клиент").trim();
        int backReference = ops.size();

        // step 1: id
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.AGGREGATION_MODE, ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
                .build()
        );

        // step 2: name
        if (firstName.isEmpty() || lastName.isEmpty()) {  // step by step
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            );
            if (!firstName.isEmpty()) {
            }
            if (!lastName.isEmpty()) {
            }
        } else {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                    .build()
            );
        }

        // step 3: phone
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .build()
        );

        // step 4: website
        if (!url.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOME)
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, url)
                    .build()
            );
        }

        // step 5: email
        if (!email.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                    .build()
            );
        }

        // note : real name for the contact
        if (!notes.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                    .build()
            );
        }

        // organization
        if (!org.isEmpty() && !role.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, org)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, role)
                    .build()
            );
        }

        return true;
    }

    public interface OnContactsDownloadedListener {
        void onContactsDownloaded();
    }

    private boolean checkIfPhoneNumberExists(ContentResolver content, String phoneNumber) {
        if (phoneNumber.isEmpty()) return true;  // empty phone number always exists
        Cursor cursor = content.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.NUMBER + "=?",
                new String[]{phoneNumber},
                null);
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) {
            cursor.close();
        }
        return exists;
    }

    boolean checkIfNameExists(ContentResolver content, String name) {
        Cursor cursor = content.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + "=?",
                new String[] { name },
                null);
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) {
            cursor.close();
        }
        return exists;
    }
}
