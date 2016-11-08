package com.example.yukinakajima.sheettest;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Data;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.sheets.v4.SheetsScopes;

import com.google.api.services.sheets.v4.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String BUTTON_TEXT = "Call Google Sheets API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {SheetsScopes.SPREADSHEETS};
    private Intent intent;

    /**
     * Create the main activity.
     *
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT + "\' button to test the API.");
        activityLayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Sheets API ...");

        setContentView(activityLayout);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        intent = getIntent();
        if (intent.hasExtra("androidaccel")) {
            getResultsFromApi();
            Toast.makeText(this, "スプレッドシートへの保存が完了しました", Toast.LENGTH_SHORT).show();
        } else if (intent.hasExtra("watch_accel_time")) {
            Log.d("tag", "ここは呼び出される");
            getResultsFromApi();
            Toast.makeText(this, "SmartWatchデータの保存が完了しました", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Sheets API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.sheets.v4.Sheets mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Sheets API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                putDataFromAPI(); //ボタンを押したときに反応させるために追記
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * データを書き込むメソッド
         *
         * @throws IOException
         */
        private void putDataFromAPI() throws IOException {
            String spreadsheetId = "1KGlEF4lHTvCd5gBDcSdj3pyK7Y3lAw9bCL-_RMBxVAg";
            Time time = new Time("Asia/Tokyo");
            time.setToNow();
            String sheetName = (time.month + 1) + "/" + time.monthDay + " " + time.hour + ":" + time.minute + ":" + time.second;
            BatchUpdateSpreadsheetRequest content = new BatchUpdateSpreadsheetRequest();
            List<Request> requests = new ArrayList<>();
            Request e = new Request();
            AddSheetRequest addSheet = new AddSheetRequest();
            SheetProperties properties = new SheetProperties();
            properties.setTitle(sheetName);
            properties.setIndex(1);
            addSheet.setProperties(properties);
            e.setAddSheet(addSheet);
            requests.add(e);
            content.setRequests(requests);
            BatchUpdateSpreadsheetResponse response = this.mService.spreadsheets().batchUpdate(
                    spreadsheetId,
                    content
            ).execute();
            ValueRange valueRange = new ValueRange();
            List row = new ArrayList<>();

            if (intent.hasExtra("androidaccel")) {
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "x", "y", "z", "3軸合成", "", "生x", "生y", "生z"));
                float[] androidaccelList = intent.getFloatArrayExtra("androidaccel");
                for (int i = 0; i < androidaccelList.length; i += 8) {
                    List col = new ArrayList<>();
                    col.add("Android ACCELEROMETER");
                    col.add(androidaccelList[i]); //経過時間
                    col.add(androidaccelList[i + 4]); //x
                    col.add(androidaccelList[i + 5]); //y
                    col.add(androidaccelList[i + 6]); //z
                    col.add(androidaccelList[i + 7]); //3軸
                    col.add("");
                    col.add(androidaccelList[i + 1]); //x生データ
                    col.add(androidaccelList[i + 2]); //y生データ
                    col.add(androidaccelList[i + 3]); //z生データ
                    row.add(col);
                }

                row.add(Arrays.asList("", "", "", "", "", "", "", "", "", ""));
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "x", "y", "z", "3軸合成", "", "生x", "生y", "生z"));
                float[] androidgyroList = intent.getFloatArrayExtra("androidgyro");
                for (int i = 0; i < androidgyroList.length; i += 8) {
                    List col = new ArrayList<>();
                    col.add("Android GYROSCOPE");
                    col.add(androidgyroList[i]);
                    col.add(androidgyroList[i + 4]);
                    col.add(androidgyroList[i + 5]);
                    col.add(androidgyroList[i + 6]);
                    col.add(androidgyroList[i + 7]);
                    col.add("");
                    col.add(androidgyroList[i + 1]);
                    col.add(androidgyroList[i + 2]);
                    col.add(androidgyroList[i + 3]);
                    row.add(col);
                }

                row.add(Arrays.asList("", "", "", "", "", "", "", "", "", ""));
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "x", "y", "z", "3軸合成", "", "生x", "生y", "生z"));
                float[] myoaccelList = intent.getFloatArrayExtra("myoaccel");
                for (int i = 0; i < myoaccelList.length; i += 8) {
                    List col = new ArrayList<>();
                    col.add("Myo ACCELEROMETER");
                    col.add(myoaccelList[i]);
                    col.add(myoaccelList[i + 4]);
                    col.add(myoaccelList[i + 5]);
                    col.add(myoaccelList[i + 6]);
                    col.add(myoaccelList[i + 7]);
                    col.add("");
                    col.add(myoaccelList[i + 1]);
                    col.add(myoaccelList[i + 2]);
                    col.add(myoaccelList[i + 3]);
                    row.add(col);
                }

                row.add(Arrays.asList("", "", "", "", "", "", "", "", "", ""));
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "x", "y", "z", "3軸合成", "", "生x", "生y", "生z"));
                float[] myogyroList = intent.getFloatArrayExtra("myogyro");
                for (int i = 0; i < myogyroList.length; i += 8) {
                    List col = new ArrayList<>();
                    col.add("Myo GYROSCOPE");
                    col.add(myogyroList[i]);
                    col.add(myogyroList[i + 4]);
                    col.add(myogyroList[i + 5]);
                    col.add(myogyroList[i + 6]);
                    col.add(myogyroList[i + 7]);
                    col.add("");
                    col.add(myogyroList[i + 1]);
                    col.add(myogyroList[i + 2]);
                    col.add(myogyroList[i + 3]);
                    row.add(col);
                }

                row.add(Arrays.asList("", "", "", "", "", "", "", "", "", ""));
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "x", "y", "z", "3軸合成", "", "生x", "生y", "生z"));
                float[] watchaccellist = intent.getFloatArrayExtra("watchaccel");
                for (int i = 0; i < watchaccellist.length; i += 7) {
                    List col = new ArrayList<>();
                    col.add("Watch ACCELEROMETER");
                    col.add(watchaccellist[i]);
                    col.add(watchaccellist[i + 4]);
                    col.add(watchaccellist[i + 5]);
                    col.add(watchaccellist[i + 6]);
                    float x2 = watchaccellist[i + 4] * watchaccellist[i + 4];
                    float y2 = watchaccellist[i + 5] * watchaccellist[i + 5];
                    float z2 = watchaccellist[i + 6] * watchaccellist[i + 6];
                    float A = (float) Math.sqrt(x2 + y2 + z2);
                    col.add(A);
                    col.add("");
                    col.add(watchaccellist[i + 1]);
                    col.add(watchaccellist[i + 2]);
                    col.add(watchaccellist[i + 3]);
                    row.add(col);
                }

                row.add(Arrays.asList("", "", "", "", "", "", "", "", "", ""));
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "x", "y", "z", "3軸合成", "", "生x", "生y", "生z"));
                float[] watchgyrolist = intent.getFloatArrayExtra("watchgyro");
                for (int i = 0; i < watchgyrolist.length; i += 7) {
                    List col = new ArrayList<>();
                    col.add("Watch GYROSCOPE");
                    col.add(watchgyrolist[i]);
                    col.add(watchgyrolist[i + 4]);
                    col.add(watchgyrolist[i + 5]);
                    col.add(watchgyrolist[i + 6]);
                    float gx2 = watchgyrolist[i + 4] * watchgyrolist[i + 4];
                    float gy2 = watchgyrolist[i + 5] * watchgyrolist[i + 5];
                    float gz2 = watchgyrolist[i + 6] * watchgyrolist[i + 6];
                    float AG = (float) Math.sqrt(gx2 + gy2 + gz2);
                    col.add(AG);
                    col.add("");
                    col.add(watchgyrolist[i + 1]);
                    col.add(watchgyrolist[i + 2]);
                    col.add(watchgyrolist[i + 3]);
                    row.add(col);
                }

                row.add(Arrays.asList("", "", "", "", "", "", "", "", "", ""));
                row.add(Arrays.asList("SensorType", "経過時間(ms)", "value", "", "生value"));
                float[] watchpresslist = intent.getFloatArrayExtra("watchpress");
                for (int i = 0; i < watchpresslist.length; i += 3) {
                    List col = new ArrayList<>();
                    col.add("Watch PRESSURE");
                    col.add(watchpresslist[i]);
                    col.add(watchpresslist[i+2]);
                    col.add("");
                    col.add(watchpresslist[i+1]);
                    row.add(col);
                }

            }

            valueRange.setValues(row);
            String range = sheetName + "!A1:J" + row.size();
            valueRange.setRange(range);
            this.mService.spreadsheets().
                    values().
                    update(spreadsheetId, range, valueRange).
                    setValueInputOption("USER_ENTERED").
                    execute();
        }

        /**
         * Fetch a list of names and majors of students in a sample spreadsheet:
         * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
         *
         * @return List of names and majors
         * @throws IOException
         */


        private List<String> getDataFromApi() throws IOException {
            String spreadsheetId = "1KGlEF4lHTvCd5gBDcSdj3pyK7Y3lAw9bCL-_RMBxVAg";
            String range = "シート1!A1:D1";
            List<String> results = new ArrayList<String>();
            ValueRange response = this.mService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            if (values != null) {
                for (List row : values) {
                    results.add(
                            row.get(0) + ", " +
                                    row.get(1) + ", " +
                                    row.get(2) + ", " +
                                    row.get(3)
                    );
                }
            }
            return results;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                output.add(0, "Data retrieved using the Google Sheets API:");
                //mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }
}
