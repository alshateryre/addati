package com.addati.app;

import android.app.Activity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    public static MainActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.rgb(7, 17, 12));
        window.setNavigationBarColor(Color.rgb(7, 17, 12));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 17, 12));
        setContentView(webView);

        webView.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        webView.requestApplyInsets();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new NativeWorkoutBridge(this), "NativeWorkout");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            String cmd = getSharedPreferences("addati_native", MODE_PRIVATE).getString("pending_command", "");
            if (!cmd.isEmpty()) {
                getSharedPreferences("addati_native", MODE_PRIVATE).edit().remove("pending_command").apply();
                if (cmd.equals("complete_set")) webView.evaluateJavascript("window.nativeCompletePendingSet && window.nativeCompletePendingSet()", null);
                if (cmd.equals("skip_rest")) webView.evaluateJavascript("window.nativeSkipRest && window.nativeSkipRest()", null);
            }
        }
    }

    public void runJs(String js) {
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public static class NativeWorkoutBridge {
        private final Context context;
        NativeWorkoutBridge(Context context) { this.context = context.getApplicationContext(); }

        private void send(String action, String name, String exercise, String setLabel, int rest, double weight, int reps) {
            Intent i = new Intent(context, WorkoutService.class);
            i.setAction(action);
            i.putExtra("name", name);
            i.putExtra("exercise", exercise);
            i.putExtra("set", setLabel);
            i.putExtra("rest", rest);
            i.putExtra("weight", weight);
            i.putExtra("reps", reps);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i); else context.startService(i);
        }

        @JavascriptInterface public void startWorkout(String name, String exercise, String setLabel, int rest) {
            send(WorkoutService.ACTION_START, name, exercise, setLabel, rest, 0, 0);
        }
        @JavascriptInterface public void updateWorkout(String name, String exercise, String setLabel, double weight, int reps) {
            send(WorkoutService.ACTION_UPDATE, name, exercise, setLabel, -1, weight, reps);
        }
        @JavascriptInterface public void startRest(String name, String exercise, String setLabel, int seconds) {
            send(WorkoutService.ACTION_REST, name, exercise, setLabel, seconds, 0, 0);
        }
        @JavascriptInterface public void stopRest() { send(WorkoutService.ACTION_SKIP_REST, "", "", "", 0, 0, 0); }
        @JavascriptInterface public void stopWorkout() { send(WorkoutService.ACTION_STOP, "", "", "", 0, 0, 0); }
    }
}
