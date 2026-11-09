package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
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
import android.widget.LinearLayout;
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

    private int activeSwapIndex = -1;
    private static final String GITHUB_REPO = "Setoisking/Flyffmultiapk";

    // Client Rollen: 0 = DD (Main), 1 = Heal (RM), 2 = Leech
    public static final int ROLE_DD = 0;
    public static final int ROLE_HEAL = 1;
    public static final int ROLE_LEECH = 2;
    private int[] sessionRoles = new int[8];

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEPSCREEN_ON);

        setContentView(R.layout.main);
        hideSystemUI();

        rootContainer = findViewById(R.id.rootContainer);
        overlayLayer = findViewById(R.id.overlayLayer);

        WebView mainWebView = findViewById(R.id.webView1);
        configureWebView(mainWebView, 1, false);
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        float density = dm.density;

        boolean isExpandedFoldable = screenW > 1200 && (float) screenW / screenH < 1.6f;

        for (int i = 0; i < overlayContainers.size(); i++) {
            FrameLayout container = overlayContainers.get(i);
            if (container != null) {
                if (isExpandedFoldable) {
                    int overlayW = (int) (260 * density);
                    int overlayH = (int) (170 * density);
                    ViewGroup.LayoutParams p = container.getLayoutParams();
                    p.width = overlayW;
                    p.height = overlayH;
                    container.setLayoutParams(p);
                    container.setX(screenW - overlayW - (8 * density));
                    container.setY((20 * density) + (i * (overlayH + (10 * density))));
                } else {
                    if (container.getX() + container.getWidth() > screenW) {
                        container.setX(Math.max(0, screenW - container.getWidth() - (10 * density)));
                    }
                    if (container.getY() + container.getHeight() > screenH) {
                        container.setY(Math.max(0, screenH - container.getHeight() - (10 * density)));
                    }
                }
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Bitte 'Unbekannte Apps installieren' erlauben und erneut tippen", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                settingsIntent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                return;
            }
        }

        Toast.makeText(this, "Lade Update herunter... Bitte kurz warten", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String targetUrl = fileUrl;
                HttpURLConnection conn;
                int responseCode;
                int redirectCount = 0;

                while (redirectCount < 5) {
                    URL u = new URL(targetUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    responseCode = conn.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || responseCode == 307
                            || responseCode == 308) {
                        targetUrl = conn.getHeaderField("Location");
                        redirectCount++;
                    } else {
                        break;
                    }
                }

                URL finalUrl = new URL(targetUrl);
                HttpURLConnection finalConn = (HttpURLConnection) finalUrl.openConnection();
                finalConn.connect();

                File cachePath = new File(getExternalCacheDir(), "updates");
                if (!cachePath.exists()) cachePath.mkdirs();
                File outputFile = new File(cachePath, "update.apk");

                InputStream in = finalConn.getInputStream();
                FileOutputStream out = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                out.flush();
                out.close();
                in.close();

                if (totalBytes > 100000) {
                    new Handler(Looper.getMainLooper()).post(() -> installApk(outputFile));
                } else {
                    throw new Exception("Heruntergeladene Datei ist unvollständig.");
                }

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "Download fehlgeschlagen, öffne Browser...", Toast.LENGTH_SHORT).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl));
                    startActivity(browserIntent);
                    promptSessionCount();
                });
            }
        }).start();
    }

    private void installApk(File apkFile) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Installer-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            promptSessionCount();
        }
    }

    // ==========================================
    // SCHRITT 1 & 2: SESSIONS & ROLLEN-AUSWAHL
    // ==========================================
    private void promptSessionCount() {
        final String[] options = {
                "1 Session (Vollbild)", "2 Sessions", "3 Sessions", "4 Sessions",
                "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Wie viele Sessions starten?")
                .setCancelable(false)
                .setItems(options, (dialog, which) -> {
                    int totalSessions = which + 1;
                    if (totalSessions == 1) {
                        sessionRoles[0] = ROLE_DD;
                    } else {
                        promptSessionRole(0, totalSessions);
                    }
                })
                .show();
    }

    private void promptSessionRole(final int currentSessionIndex, final int totalSessions) {
        final String[] roles = {"Main (DD)", "Heal (RM / Support)", "Leech"};

        String sessionName = (currentSessionIndex == 0) ? "Hauptfenster (Vollbild)" : "Overlay " + (currentSessionIndex + 1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Rolle für Session " + (currentSessionIndex + 1) + " (" + sessionName + ")")
                .setCancelable(false)
                .setItems(roles, (dialog, which) -> {
                    sessionRoles[currentSessionIndex] = which;

                    if (currentSessionIndex + 1 < totalSessions) {
                        promptSessionRole(currentSessionIndex + 1, totalSessions);
                    } else {
                        int overlayCount = totalSessions - 1;
                        for (int i = 0; i < overlayCount; i++) {
                            int clientIndex = i + 1;
                            createOverlaySession(clientIndex + 1, CLIENT_COLORS[i % CLIENT_COLORS.length], i, sessionRoles[clientIndex]);
                        }
                    }
                })
                .show();
    }

    // ==========================================
    // MULTI-SESSION OVERLAYS & ROLLEN-BUTTONS
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private void createOverlaySession(int clientNumber, int accentColor, int index, int role) {
        float density = getResources().getDisplayMetrics().density;
        final int overlayIndex = index;

        FrameLayout container = new FrameLayout(this);
        int defaultWidth = (int) (260 * density);
        int defaultHeight = (int) (170 * density);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(defaultWidth, defaultHeight);
        containerParams.leftMargin = (int) (12 * density + (index * 24 * density));
        containerParams.topMargin = (int) (40 * density + (index * 24 * density));
        container.setLayoutParams(containerParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#18181C"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int) (2 * density), accentColor);
        container.setBackground(bg);

        WebView webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webParams.topMargin = (int) (28 * density);
        webView.setLayoutParams(webParams);
        configureWebView(webView, clientNumber, true);
        webView.loadUrl("https://universe.flyff.com/play");
        setClientAudioMuted(webView, true);
        activeWebViews.add(webView);
        container.addView(webView);

        LinearLayout topDock = new LinearLayout(this);
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (28 * density));
        dockParams.gravity = Gravity.TOP;
        topDock.setLayoutParams(dockParams);
        topDock.setOrientation(LinearLayout.HORIZONTAL);
        topDock.setGravity(Gravity.CENTER_VERTICAL);
        topDock.setBackgroundColor(Color.parseColor("#CC101014"));
        topDock.setPadding((int) (4 * density), 0, (int) (4 * density), 0);

        // Saubere final-Initialisierung für Lambdas
        final String badgeText;
        if (role == ROLE_HEAL) {
            badgeText = "RM";
        } else if (role == ROLE_LEECH) {
            badgeText = "LC";
        } else if (role == ROLE_DD) {
            badgeText = "DD";
        } else {
            badgeText = "C" + clientNumber;
        }

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accentColor);

        TextView floatingBadge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density));
        floatingBadge.setLayoutParams(badgeParams);
        floatingBadge.setText(badgeText);
        floatingBadge.setTextColor(Color.WHITE);
        floatingBadge.setTextSize(9);
        floatingBadge.setGravity(Gravity.CENTER);
        floatingBadge.setBackground(badgeBg);
        topDock.addView(floatingBadge);

        if (role == ROLE_HEAL) {
            TextView btnHeal = createDockButton("Heal", accentColor, density);
            btnHeal.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit1", 49));
            topDock.addView(btnHeal);

            TextView btnBuff = createDockButton("Buff", accentColor, density);
            btnBuff.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit2", 50));
            topDock.addView(btnBuff);

            TextView btnFollow = createDockButton("Fol", accentColor, density);
            btnFollow.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit3", 51));
            topDock.addView(btnFollow);
        } else if (role == ROLE_LEECH) {
            TextView btnFollow = createDockButton("Fol", accentColor, density);
            btnFollow.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit1", 49));
            topDock.addView(btnFollow);

            TextView btnCheer = createDockButton("Cheer", accentColor, density);
            btnCheer.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit2", 50));
            topDock.addView(btnCheer);
        } else {
            TextView btnAtk = createDockButton("Atk", accentColor, density);
            btnAtk.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit1", 49));
            topDock.addView(btnAtk);

            TextView btnSkill = createDockButton("Skill", accentColor, density);
            btnSkill.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit2", 50));
            topDock.addView(btnSkill);
        }

        final boolean[] isTransparent = {false};
        TextView btnGhost = createDockButton("👻", accentColor, density);
        btnGhost.setOnClickListener(v -> {
            if (!isTransparent[0]) {
                container.setAlpha(0.65f);
                isTransparent[0] = true;
                btnGhost.setTextColor(Color.YELLOW);
            } else {
                container.setAlpha(1.0f);
                isTransparent[0] = false;
                btnGhost.setTextColor(Color.WHITE);
            }
        });
        topDock.addView(btnGhost);

        final boolean[] isTouchLocked = {false};
        TextView btnLock = createDockButton("🔓", accentColor, density);
        btnLock.setOnClickListener(v -> {
            if (!isTouchLocked[0]) {
                webView.setOnTouchListener((view, motionEvent) -> false);
                isTouchLocked[0] = true;
                btnLock.setText("🔒");
                btnLock.setTextColor(Color.RED);
                Toast.makeText(this, badgeText + ": Ghost-Touch aktiv", Toast.LENGTH_SHORT).show();
            } else {
                webView.setOnTouchListener(null);
                isTouchLocked[0] = false;
                btnLock.setText("🔓");
                btnLock.setTextColor(Color.WHITE);
            }
        });
        topDock.addView(btnLock);

        container.addView(topDock);

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

        topDock.setOnTouchListener((v, event) -> {
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

                    if (!isMinimized[0]) {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        float snapThreshold = 35 * density;
                        if (container.getX() < snapThreshold) container.setX(4 * density);
                        if (container.getY() < snapThreshold) container.setY(4 * density);
                        if (container.getX() + container.getWidth() > dm.widthPixels - snapThreshold) {
                            container.setX(dm.widthPixels - container.getWidth() - (4 * density));
                        }
                    }

                    if (duration < 250 && event.getX() < 40 * density) {
                        if (!isMinimized[0]) {
                            savedWidth[0] = container.getWidth();
                            savedHeight[0] = container.getHeight();
                            webView.setVisibility(View.GONE);
                            resizeHandle.setVisibility(View.GONE);
                            for (int i = 1; i < topDock.getChildCount(); i++) {
                                topDock.getChildAt(i).setVisibility(View.GONE);
                            }
                            container.setBackground(null);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = (int) (32 * density);
                            p.height = (int) (32 * density);
                            container.setLayoutParams(p);
                            isMinimized[0] = true;
                            setClientLowSpecMode(webView, true);
                        } else {
                            webView.setVisibility(View.VISIBLE);
                            resizeHandle.setVisibility(View.VISIBLE);
                            for (int i = 1; i < topDock.getChildCount(); i++) {
                                topDock.getChildAt(i).setVisibility(View.VISIBLE);
                            }
                            container.setBackground(bg);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = savedWidth[0];
                            p.height = savedHeight[0];
                            container.setLayoutParams(p);
                            isMinimized[0] = false;
                            setClientLowSpecMode(webView, false);
                        }
                        return true;
                    }

                    if (duration >= 600 && event.getX() < 40 * density) {
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
                    int newW = (int) Math.max(160 * density, startW[0] + deltaX);
                    int newH = (int) Math.max(100 * density, startH[0] + deltaY);

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

    private TextView createDockButton(String text, int accentColor, float density) {
        TextView btn = new TextView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams((int) (34 * density), (int) (22 * density));
        p.leftMargin = (int) (4 * density);
        btn.setLayoutParams(p);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(9);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable b = new GradientDrawable();
        b.setColor(Color.parseColor("#44222226"));
        b.setCornerRadius(4 * density);
        b.setStroke(1, accentColor);
        btn.setBackground(b);
        return btn;
    }

    private void sendGameKeyEvent(WebView wv, String code, int keyCode) {
        if (wv == null) return;
        String js = "(function() {" +
                "  var evDown = new KeyboardEvent('keydown', {key: '" + code + "', code: '" + code + "', keyCode: " + keyCode + ", which: " + keyCode + ", bubbles: true});" +
                "  var evUp = new KeyboardEvent('keyup', {key: '" + code + "', code: '" + code + "', keyCode: " + keyCode + ", which: " + keyCode + ", bubbles: true});" +
                "  window.dispatchEvent(evDown);" +
                "  document.dispatchEvent(evDown);" +
                "  setTimeout(function() { window.dispatchEvent(evUp); document.dispatchEvent(evUp); }, 60);" +
                "})();";
        wv.evaluateJavascript(js, null);
    }

    private void swapViewsWithoutReload(int targetOverlayIndex) {
        FrameLayout targetContainer = overlayContainers.get(targetOverlayIndex);

        WebView viewA = (WebView) rootContainer.getChildAt(0);
        WebView viewB = null;

        for (int i = 0; i < targetContainer.getChildCount(); i++) {
            if (targetContainer.getChildAt(i) instanceof WebView) {
                viewB = (WebView) targetContainer.getChildAt(i);
                break;
            }
        }

        if (viewA == null || viewB == null) return;

        rootContainer.removeView(viewA);
        targetContainer.removeView(viewB);

        viewB.getSettings().setTextZoom(100);
        viewB.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        viewA.getSettings().setTextZoom(75);
        FrameLayout.LayoutParams pA = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        pA.topMargin = (int) (28 * getResources().getDisplayMetrics().density);
        viewA.setLayoutParams(pA);

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

    private void setClientLowSpecMode(WebView wv, boolean enableLowSpec) {
        if (wv == null) return;
        if (enableLowSpec) {
            String js = "(function() {" +
                    "  window.__origRAF = window.__origRAF || window.requestAnimationFrame;" +
                    "  window.requestAnimationFrame = function(cb) {" +
                    "    return setTimeout(function() { cb(performance.now()); }, 100);" +
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
    private void configureWebView(WebView view, int clientSessionId, boolean isOverlay) {
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

        s.setTextZoom(isOverlay ? 75 : 100);

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
