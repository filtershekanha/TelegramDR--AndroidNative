package com.filtershekanha.teledr;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.filtershekanha.teledr.internal.ProxyManager;
import com.filtershekanha.teledr.messenger.ApplicationLoader;
import com.filtershekanha.teledr.messenger.R;
import com.filtershekanha.teledr.tgnet.ConnectionsManager;
import com.filtershekanha.teledr.ui.LaunchActivity;

public class SplashActivity extends Activity implements ProxyManager.ProxyListener {

    private final Handler uiHandler = new Handler();
    private ProxyManager proxyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        this.proxyManager = new ProxyManager(uiHandler);
        this.proxyManager.setProxyListener(this);

        ApplicationLoader.postInitApplication();

        if (ConnectionsManager.isNetworkOnline()) {
            proxyManager.initialize();
        } else {
            presentLaunchActivity();
        }
    }

    @Override
    protected void onDestroy() {
        proxyManager.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void presentLaunchActivity() {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, LaunchActivity.class));
                finish();
            }
        }, 1000);
    }

    private void setStatus(String text) {
        TextView statusText = findViewById(R.id.status);
        statusText.setText(text);
    }

    @Override
    public void onProxyAvailable() {
        presentLaunchActivity();
    }

    @Override
    public void onNoProxiesAvailable() {
        setStatus("There was an error while connecting to proxy.\nPlease try again later.");
    }

    @Override
    public void onProxyError(String message) {
        setStatus(message);
    }
}

