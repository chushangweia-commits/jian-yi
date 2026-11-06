package com.jianyi.yixue.app;

import android.app.Activity;
import android.os.Bundle;

/** Minimal launcher for the security-clean build; no legacy Application or SDK startup. */
public final class JianYiActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        JianYiUi.applySystemBars(this);
        JianYiHome.render(this);
    }

    @Override protected void onResume() {
        super.onResume();
        JianYiHome.onResume(this);
    }

    @Override public void onBackPressed() {
        if (!JianYiHome.onBackPressed(this)) super.onBackPressed();
    }
}
