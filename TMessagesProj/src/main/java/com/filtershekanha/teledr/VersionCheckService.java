package com.filtershekanha.teledr;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.filtershekanha.teledr.messenger.ApplicationLoader;
import com.filtershekanha.teledr.messenger.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static java.lang.System.currentTimeMillis;

public class VersionCheckService extends Service {

    private static final String[] URLS = new String[]{
            "https://d1ra5t3jiquy8n.cloudfront.net",
            "https://d1lxhgzrm7ynb5.cloudfront.net",
            "https://d1yyncg9ld85jq.cloudfront.net",
            "https://d3sjcjatynv8iz.cloudfront.net"
    };

    private static final String FILE_PATH = "/ver-com.teledr.json";
    private static final String FILE_PATH_TEST = "/ver-com.teledr-test.json";

    public static final String ACTION_UPGRADE_NEEDED = "ACTION_UPGRADE_NEEDED";
    public static final String EXTRA_REQUIRED = "EXTRA_REQUIRED";
    public static final String EXTRA_PLAY_URL = "EXTRA_PLAY_URL";
    public static final String EXTRA_APK_URL = "EXTRA_APK_URL";

    private SharedPreferences preferences;
    private OkHttpClient okHttpClient;
    private Call checkCall;

    private boolean isWorking = false;
    private int urlIndex = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        okHttpClient = new OkHttpClient.Builder().build();
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        if (checkCall != null) checkCall.cancel();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int versionCode = getVersionCode();
        Log.d("VersionCheckService", "Check service started. Version code: " + versionCode);

        if (versionCode < 0) {
            Log.d("VersionCheckService", "Version code unavailable...");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (isWorking) {
            Log.d("VersionCheckService", "Version check already in progress...");
            return START_NOT_STICKY;
        }

        isWorking = true;

        loadVersionJson(URLS[urlIndex] + (BuildConfig.DEBUG ? FILE_PATH_TEST : FILE_PATH));

        return START_NOT_STICKY;
    }

    private void loadVersionJson(String url) {
        Log.d("VersionCheckService", "Loading version JSON from " + url);

        Request request = new Request.Builder().url(url).build();

        if (checkCall != null && !checkCall.isCanceled()) checkCall.cancel();
        checkCall = okHttpClient.newCall(request);
        checkCall.enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    onLoadVersionJsonFailure();
                    return;
                }

                try {
                    ResponseBody body = response.body();
                    if (body == null) {
                        onLoadVersionJsonFailure();
                        return;
                    }

                    String responseJson = body.string();
                    Log.d("VersionCheckService", "Check response data: " + responseJson);
                    JSONObject jsonObject = new JSONObject(responseJson);

                    int thisVersion = getVersionCode();
                    int currentVersion = jsonObject.getInt("currentVersion");
                    int minVersion = jsonObject.getInt("minVersion");
                    String playUrl = jsonObject.getString("playUrl");
                    String apkUrl = jsonObject.getString("apkUrl");

                    if (currentVersion > thisVersion) {
                        Log.d("VersionCheckService", "New version available: " + currentVersion);
                        boolean required = thisVersion < minVersion;
                        onUpdateAvailable(required, apkUrl, playUrl);
                    }

                    finish();
                } catch (JSONException e) {
                    onLoadVersionJsonFailure();
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                onLoadVersionJsonFailure();
            }
        });
    }

    private void onUpdateAvailable(boolean required, String apkUrl, String playUrl) {
        Intent intent = new Intent(ACTION_UPGRADE_NEEDED);
        intent.putExtra(EXTRA_REQUIRED, required);
        intent.putExtra(EXTRA_APK_URL, apkUrl);
        intent.putExtra(EXTRA_PLAY_URL, playUrl);

        LocalBroadcastManager.getInstance(ApplicationLoader.applicationContext).sendBroadcast(intent);
    }

    private void onLoadVersionJsonFailure() {
        urlIndex++;

        if (urlIndex >= URLS.length) {
            finish();
        } else {
            loadVersionJson(URLS[urlIndex] + (BuildConfig.DEBUG ? FILE_PATH_TEST : FILE_PATH));
        }
    }

    private void setVersionCheckTimestamp(long timeMillis) {
        preferences.edit().putLong("version_check_timestamp", timeMillis).apply();
    }

    private void finish() {
        Log.d("VersionCheckService", "Finishing...");
        setVersionCheckTimestamp(currentTimeMillis());
        urlIndex = 0;
        isWorking = false;
        stopSelf();
    }

    private int getVersionCode() {
        int v = -1;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return v;
        }
        return v;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
