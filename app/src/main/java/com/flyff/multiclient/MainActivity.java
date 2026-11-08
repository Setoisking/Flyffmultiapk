package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private FrameLayout rootContainer;
    private FrameLayout overlayLayer;
    private final List<WebView> activeWebViews = new ArrayList<>();
    private final List<FrameLayout> overlayContainers = new ArrayList<>();
    private SharedPreferences prefs;

    private int activeSwapIndex = -1;
    private static final String GITHUB_REPO = "Setoisking/Flyffmultiapk";

    private final int[] CLIENT_COLORS = {
            Color.parseColor("#00E676"), // C2: Grün
            Color.parseColor("#00E5FF"), // C3: Cyan
            Color.parseColor("#FF007F"), // C4: Pink
            Color.parseColor("#FFD600"), // C5: Gelb
            Color.parseColor("#7C4DFF"), // C6: Lila
            Color.parseColor("#FF6D00"), // C7: Orange
            Color.parseColor("#1DE9B6")  // C8: Türkis
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);
        hideSystemUI();

        prefs = getSharedPreferences("NeuzOS_Config", MODE_PRIVATE);
        rootContainer = findViewById(R.id.rootContainer);
        overlayLayer = findViewById(R.id.overlayLayer);

        WebView mainWebView = findViewById(R.id.webView1);
        configureWebView(mainWebView, 1);
        mainWebView.loadUrl("https://universe.flyff.com/play");
        activeWebViews.add(mainWebView);

        checkForUpdates();
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    // ==========================================
    // UPDATE CHECKER (GitHub API)
    // ==========================================
    private void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject releaseJson = new JSONObject(response.toString());
                    String latestTag = releaseJson.optString("tag_name", "").replace("v", "").trim();

                    String apkDownloadUrl = null;
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "");
                            if (assetName.endsWith(".apk")) {
                                apkDownloadUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }

                    String currentVersion = getAppVersionName();
                    final String downloadUrlFinal = apkDownloadUrl;
                    final String latestVersionFinal = latestTag;

                    if (isNewerVersion(currentVersion, latestTag) && downloadUrlFinal != null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                showUpdateDialog(currentVersion, latestVersionFinal, downloadUrlFinal)
                        );
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            new Handler(Looper.getMainLooper()).post(this::promptSessionCount);
        }).start();
    }

    private String getAppVersionName() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName.replace("v", "").trim() : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private boolean isNewerVersion(String current, String remote) {
        if (remote == null || remote.isEmpty()) return false;
        try {
            String[] currParts = current.split("\\.");
            String[] remoteParts = remote.split("\\.");
            int length = Math.max(currParts.length, remoteParts.length);

            for (int i = 0; i < length; i++) {
                int c = i < currParts.length ? Integer.parseInt(currParts[i].replaceAll("[^0-9]", "")) : 0;
                int r = i < remoteParts.length ? Integer.parseInt(remoteParts[i].replaceAll("[^0-9]", "")) : 0;
                if (r > c) return true;
                if (r < c) return false;
            }
        } catch (Exception e) {
            return !current.equalsIgnoreCase(remote);
        }
        return false;
    }

    private void showUpdateDialog(String currentVer, String newVer, String downloadUrl) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Neues Update verfügbar!")
                .setMessage("Installiert: v" + currentVer + "\nNeueste Version: v" + newVer + "\n\nJetzt aktualisieren?")
                .setCancelable(false)
                .setPositiveButton("Aktualisieren", (dialog, which) -> downloadAndInstallApk(downloadUrl))
                .setNegativeButton("Später", (dialog, which) -> promptSessionCount())
                .show();
    }

    private void downloadAndInstallApk(String fileUrl) {
        Toast.makeText(this, "Lade Update herunter...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.connect();

                File cachePath = new File(getExternalCacheDir(), "updates");
                if (!cachePath.exists()) cachePath.mkdirs();
                File outputFile = new File(cachePath, "update.apk");

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
                out.close();
                in.close();

                new Handler(Looper.getMainLooper()).post(() -> installApk(outputFile));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Öffne Browser für Download...", Toast.LENGTH_SHORT).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl));
                    startActivity(browserIntent);
                    promptSessionCount();
                });
            }
        }).start();
    }

    private void installApk(File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(this, "Bitte 'Unbekannte Apps installieren' erlauben", Toast.LENGTH_LONG).show();
                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settingsIntent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(settingsIntent);
                    return;
                }
            }

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            promptSessionCount();
        }
    }

    // ==========================================
    // MULTI-SESSION OVERLAYS & SESSION SEPARATION
    // ==========================================
    private void promptSessionCount() {
        final String[] options = {
                "1 Session (Vollbild)", "2 Sessions", "3 Sessions", "4 Sessions",
                "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Sessions wählen")
                .setCancelable(false)
                .setItems(options, (dialog, which) -> {
                    int totalSessions = which + 1;
                    int overlayCount = totalSessions - 1;
                    for (int i = 0; i < overlayCount; i++) {
                        createOverlaySession(i + 2, CLIENT_COLORS[i % CLIENT_COLORS.length], i);
                    }
                })
                .show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createOverlaySession(int clientNumber, int accentColor, int index) {
        float density = getResources().getDisplayMetrics().density;
        final int overlayIndex = index;

        FrameLayout container = new FrameLayout(this);
        int defaultWidth = (int) (250 * density);
        int defaultHeight = (int) (160 * density);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(defaultWidth, defaultHeight);
        containerParams.leftMargin = (int) (10 * density + (index * 20 * density));
        containerParams.topMargin = (int) (40 * density + (index * 20 * density));
        container.setLayoutParams(containerParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#151518"));
        bg.setCornerRadius(6 * density);
        bg.setStroke((int) (2 * density), accentColor);
        container.setBackground(bg);

        WebView webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(webParams);
        configureWebView(webView, clientNumber);
        webView.loadUrl("https://universe.flyff.com/play");
        setClientAudioMuted(webView, true);
        activeWebViews.add(webView);
        container.addView(webView);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accentColor);

        TextView floatingBadge = new TextView(this);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams((int) (32 * density), (int) (32 * density));
        badgeParams.gravity = Gravity.TOP | Gravity.START;
        badgeParams.leftMargin = (int) (4 * density);
        badgeParams.topMargin = (int) (4 * density);
        floatingBadge.setLayoutParams(badgeParams);
        floatingBadge.setText("C" + clientNumber);
        floatingBadge.setTextColor(Color.WHITE);
        floatingBadge.setTextSize(10);
        floatingBadge.setGravity(Gravity.CENTER);
        floatingBadge.setBackground(badgeBg);
        container.addView(floatingBadge);

        TextView resizeHandle = new TextView(this);
        FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams((int) (24 * density), (int) (24 * density));
        resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
        resizeHandle.setLayoutParams(resizeParams);
        resizeHandle.setText("⇲");
        resizeHandle.setTextColor(accentColor);
        resizeHandle.setTextSize(14);
        resizeHandle.setGravity(Gravity.CENTER);
        container.addView(resizeHandle);

        overlayLayer.addView(container);
        overlayContainers.add(container);

        final float[] dX = {0};
        final float[] dY = {0};
        final long[] downTime = {0};

        final int[] savedWidth = {defaultWidth};
        final int[] savedHeight = {defaultHeight};
        final boolean[] isMinimized = {false};

        floatingBadge.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    downTime[0] = System.currentTimeMillis();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    container.setX(event.getRawX() + dX[0]);
                    container.setY(event.getRawY() + dY[0]);
                    return true;

                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    long duration = System.currentTimeMillis() - downTime[0];

                    // Klick (unter 250ms): Minimieren / Wiederherstellen mit FPS-Limiter
                    if (duration < 250) {
                        if (!isMinimized[0]) {
                            savedWidth[0] = container.getWidth();
                            savedHeight[0] = container.getHeight();
                            View clientView = container.getChildAt(0);
                            if (clientView != null) clientView.setVisibility(View.GONE);
                            resizeHandle.setVisibility(View.GONE);
                            container.setBackground(null);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = (int) (36 * density);
                            p.height = (int) (36 * density);
                            container.setLayoutParams(p);
                            isMinimized[0] = true;
                            setClientLowSpecMode(webView, true); // FPS auf 10 drosseln
                        } else {
                            View clientView = container.getChildAt(0);
                            if (clientView != null) clientView.setVisibility(View.VISIBLE);
                            resizeHandle.setVisibility(View.VISIBLE);
                            container.setBackground(bg);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = savedWidth[0];
                            p.height = savedHeight[0];
                            container.setLayoutParams(p);
                            isMinimized[0] = false;
                            setClientLowSpecMode(webView, false); // FPS wieder normalisieren
                        }
                        return true;
                    }

                    // Langer Druck (ab 600ms): Echter View-Tausch ohne Neuladen
                    if (duration >= 600) {
                        swapViewsWithoutReload(overlayIndex);
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return false;
        });

        // Diagonales Resizen
        final float[] startX = {0};
        final float[] startY = {0};
        final int[] startW = {0};
        final int[] startH = {0};

        resizeHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    startW[0] = container.getWidth();
                    startH[0] = container.getHeight();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - startX[0];
                    float deltaY = event.getRawY() - startY[0];
                    int newW = (int) Math.max(120 * density, startW[0] + deltaX);
                    int newH = (int) Math.max(90 * density, startH[0] + deltaY);

                    ViewGroup.LayoutParams p = container.getLayoutParams();
                    p.width = newW;
                    p.height = newH;
                    container.setLayoutParams(p);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return false;
        });
    }

    private void swapViewsWithoutReload(int targetOverlayIndex) {
        FrameLayout targetContainer = overlayContainers.get(targetOverlayIndex);

        WebView viewA = (WebView) rootContainer.getChildAt(0);
        WebView viewB = (WebView) targetContainer.getChildAt(0);

        if (viewA == null || viewB == null) return;

        rootContainer.removeView(viewA);
        targetContainer.removeView(viewB);

        viewB.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewA.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootContainer.addView(viewB, 0);
        targetContainer.addView(viewA, 0);

        setClientAudioMuted(viewB, false);
        setClientAudioMuted(viewA, true);

        if (activeSwapIndex == targetOverlayIndex) {
            activeSwapIndex = -1;
            Toast.makeText(this, "Zurückgetauscht", Toast.LENGTH_SHORT).show();
        } else {
            activeSwapIndex = targetOverlayIndex;
            Toast.makeText(this, "Ansicht gewechselt (ohne Reload)", Toast.LENGTH_SHORT).show();
        }
    }

    // NeuzOS Low-Spec FPS Limiter
    private void setClientLowSpecMode(WebView wv, boolean enableLowSpec) {
        if (wv == null) return;
        if (enableLowSpec) {
            String js = "(function() {" +
                    "  window.__origRAF = window.__origRAF || window.requestAnimationFrame;" +
                    "  window.requestAnimationFrame = function(cb) {" +
                    "    return setTimeout(function() { cb(performance.now()); }, 100);" + // drosselt auf ~10 FPS
                    "  };" +
                    "})();";
            wv.evaluateJavascript(js, null);
        } else {
            String js = "(function() {" +
                    "  if (window.__origRAF) { window.requestAnimationFrame = window.__origRAF; }" +
                    "})();";
            wv.evaluateJavascript(js, null);
        }
    }

    private void setClientAudioMuted(WebView wv, boolean mute) {
        if (wv == null) return;
        String js = "(function() {" +
                "  document.querySelectorAll('audio, video').forEach(function(el) {" +
                "    el.muted = " + (mute ? "true" : "false") + ";" +
                "  });" +
                "})();";
        wv.evaluateJavascript(js, null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view, int clientSessionId) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // Multi-Profile Isolation: Eigener AppCache & WebDB-Pfad pro Client
        File profileDir = new File(getFilesDir(), "client_profile_" + clientSessionId);
        if (!profileDir.exists()) profileDir.mkdirs();
        s.setDatabasePath(profileDir.getPath());

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        for (WebView w : activeWebViews) {
            if (w != null) w.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (WebView w : activeWebViews) {
            if (w != null) w.destroy();
        }
    }
}
