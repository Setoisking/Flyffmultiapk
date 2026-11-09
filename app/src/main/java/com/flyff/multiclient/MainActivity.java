package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.EditText;
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
    private SharedPreferences prefs;

    private String currentLang = "de";

    public static final int ROLE_DD = 0;
    public static final int ROLE_HEAL = 1;
    public static final int ROLE_LEECH = 2;
    private int[] sessionRoles = new int[8];
    private int totalSavedSessions = 1;

    private final Handler loopHandler = new Handler(Looper.getMainLooper());

    private final int[] CLIENT_COLORS = {
            Color.parseColor("#00E676"),
            Color.parseColor("#00E5FF"),
            Color.parseColor("#FF007F"),
            Color.parseColor("#FFD600"),
            Color.parseColor("#7C4DFF"),
            Color.parseColor("#FF6D00"),
            Color.parseColor("#1DE9B6")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("main_client");
            } catch (Exception ignored) {}
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);
        hideSystemUI();

        prefs = getSharedPreferences("FlyffMultiClient_Prefs", MODE_PRIVATE);
        currentLang = prefs.getString("selected_language", "");

        rootContainer = findViewById(R.id.rootContainer);
        overlayLayer = findViewById(R.id.overlayLayer);

        WebView mainWebView = findViewById(R.id.webView1);
        configureWebView(mainWebView, 1, false);
        mainWebView.loadUrl("https://universe.flyff.com/play");
        activeWebViews.add(mainWebView);

        if (currentLang.isEmpty()) {
            promptLanguageSelection();
        } else {
            checkForUpdates();
        }
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
                    int overlayW = (int) (270 * density);
                    int overlayH = (int) (175 * density);
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

    private void promptLanguageSelection() {
        final String[] languages = {"Deutsch (DE)", "English (EN)"};

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Select Language / Sprache wählen")
                .setCancelable(false)
                .setItems(languages, (dialog, which) -> {
                    if (which == 0) {
                        currentLang = "de";
                    } else {
                        currentLang = "en";
                    }
                    prefs.edit().putString("selected_language", currentLang).apply();
                    checkForUpdates();
                })
                .show();
    }

    private boolean isDe() {
        return "de".equalsIgnoreCase(currentLang);
    }

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
            } catch (Exception ignored) {}

            new Handler(Looper.getMainLooper()).post(this::checkSavedSessionSetup);
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
        String title = isDe() ? "Neues Update verfügbar!" : "New update available!";
        String msg = isDe()
                ? ("Installiert: v" + currentVer + "\nNeueste Version: v" + newVer + "\n\nJetzt aktualisieren?")
                : ("Installed: v" + currentVer + "\nLatest version: v" + newVer + "\n\nUpdate now?");
        String btnUpdate = isDe() ? "Aktualisieren" : "Update";
        String btnLater = isDe() ? "Später" : "Later";

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(btnUpdate, (dialog, which) -> downloadAndInstallApk(downloadUrl))
                .setNegativeButton(btnLater, (dialog, which) -> checkSavedSessionSetup())
                .show();
    }

    private void downloadAndInstallApk(String fileUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                String reqMsg = isDe()
                        ? "Bitte 'Unbekannte Apps installieren' erlauben und erneut tippen"
                        : "Please allow 'Install unknown apps' and tap again";
                Toast.makeText(this, reqMsg, Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                settingsIntent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                return;
            }
        }

        String dlMsg = isDe() ? "Lade Update herunter... Bitte warten" : "Downloading update... Please wait";
        Toast.makeText(this, dlMsg, Toast.LENGTH_SHORT).show();

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
                    throw new Exception("Incomplete download");
                }

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    String failMsg = isDe() ? "Download fehlgeschlagen, öffne Browser..." : "Download failed, opening browser...";
                    Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl));
                    startActivity(browserIntent);
                    checkSavedSessionSetup();
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
            Toast.makeText(this, "Installer error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            checkSavedSessionSetup();
        }
    }

    private void checkSavedSessionSetup() {
        boolean hasSaved = prefs.getBoolean("has_saved_setup", false);
        if (hasSaved) {
            totalSavedSessions = prefs.getInt("saved_total_sessions", 1);
            StringBuilder sb = new StringBuilder();

            if (isDe()) {
                sb.append("Gespeichertes Setup (").append(totalSavedSessions).append(" Sessions):\n");
            } else {
                sb.append("Saved setup (").append(totalSavedSessions).append(" sessions):\n");
            }

            for (int i = 0; i < totalSavedSessions; i++) {
                int r = prefs.getInt("role_session_" + i, ROLE_DD);
                sessionRoles[i] = r;
                String rName = (r == ROLE_HEAL) ? "Heal (RM)" : (r == ROLE_LEECH ? "Leech" : "Main (DD)");
                sb.append("• Session ").append(i + 1).append(": ").append(rName).append("\n");
            }

            sb.append("\n").append(isDe() ? "Möchtest du dieses Setup erneut übernehmen?" : "Would you like to load this setup again?");

            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle(isDe() ? "Setup übernehmen?" : "Load setup?")
                    .setMessage(sb.toString())
                    .setCancelable(false)
                    .setPositiveButton(isDe() ? "Ja, übernehmen" : "Yes, load", (dialog, which) -> launchSessions(totalSavedSessions))
                    .setNegativeButton(isDe() ? "Neu einrichten" : "Configure new", (dialog, which) -> promptSessionCount())
                    .show();
        } else {
            promptSessionCount();
        }
    }

    private void promptSessionCount() {
        final String[] options;
        if (isDe()) {
            options = new String[]{
                    "1 Session (Vollbild)", "2 Sessions", "3 Sessions", "4 Sessions",
                    "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"
            };
        } else {
            options = new String[]{
                    "1 Session (Fullscreen)", "2 Sessions", "3 Sessions", "4 Sessions",
                    "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"
            };
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(isDe() ? "Wie viele Sessions starten?" : "How many sessions to launch?")
                .setCancelable(false)
                .setItems(options, (dialog, which) -> {
                    totalSavedSessions = which + 1;
                    if (totalSavedSessions == 1) {
                        sessionRoles[0] = ROLE_DD;
                        saveCurrentSetup(1);
                        launchSessions(1);
                    } else {
                        promptSessionRole(0, totalSavedSessions);
                    }
                })
                .show();
    }

    private void promptSessionRole(final int currentSessionIndex, final int totalSessions) {
        final String[] roles = {"Main (DD)", "Heal (RM / Support)", "Leech"};

        String sessionName;
        if (isDe()) {
            sessionName = (currentSessionIndex == 0) ? "Hauptfenster (Vollbild)" : "Overlay " + (currentSessionIndex + 1);
        } else {
            sessionName = (currentSessionIndex == 0) ? "Main Window (Fullscreen)" : "Overlay " + (currentSessionIndex + 1);
        }

        String title = isDe()
                ? ("Rolle für Session " + (currentSessionIndex + 1) + " (" + sessionName + ")")
                : ("Role for Session " + (currentSessionIndex + 1) + " (" + sessionName + ")");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle(title)
                .setCancelable(false)
                .setItems(roles, (dialog, which) -> {
                    sessionRoles[currentSessionIndex] = which;

                    if (currentSessionIndex + 1 < totalSessions) {
                        promptSessionRole(currentSessionIndex + 1, totalSessions);
                    } else {
                        saveCurrentSetup(totalSessions);
                        launchSessions(totalSessions);
                    }
                })
                .show();
    }

    private void saveCurrentSetup(int count) {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean("has_saved_setup", true);
        ed.putInt("saved_total_sessions", count);
        for (int i = 0; i < count; i++) {
            ed.putInt("role_session_" + i, sessionRoles[i]);
        }
        ed.apply();
    }

    private void launchSessions(int totalSessions) {
        int overlayCount = totalSessions - 1;
        for (int i = 0; i < overlayCount; i++) {
            int clientIndex = i + 1;
            createOverlaySession(clientIndex + 1, CLIENT_COLORS[i % CLIENT_COLORS.length], i, sessionRoles[clientIndex]);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createOverlaySession(int clientNumber, int accentColor, int index, int role) {
        float density = getResources().getDisplayMetrics().density;
        final int overlayIndex = index;
        final int clientUid = clientNumber;

        FrameLayout container = new FrameLayout(this);
        int defaultWidth = (int) (270 * density);
        int defaultHeight = (int) (175 * density);

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
        // Höhe der Leiste angepasst (24 dp statt 28 dp)
        webParams.topMargin = (int) (24 * density);
        webView.setLayoutParams(webParams);
        configureWebView(webView, clientNumber, true);
        webView.loadUrl("https://universe.flyff.com/play");
        setClientAudioMuted(webView, true);
        activeWebViews.add(webView);
        container.addView(webView);

        // 10 % kleinere Schnellleiste (24 dp Höhe)
        LinearLayout topDock = new LinearLayout(this);
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (24 * density));
        dockParams.gravity = Gravity.TOP;
        topDock.setLayoutParams(dockParams);
        topDock.setOrientation(LinearLayout.HORIZONTAL);
        topDock.setGravity(Gravity.CENTER_VERTICAL);
        topDock.setBackgroundColor(Color.parseColor("#CC101014"));
        topDock.setPadding((int) (3 * density), 0, (int) (3 * density), 0);

        final String badgeText;
        if (role == ROLE_HEAL) badgeText = "RM";
        else if (role == ROLE_LEECH) badgeText = "LC";
        else if (role == ROLE_DD) badgeText = "DD";
        else badgeText = "C" + clientNumber;

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accentColor);

        // Kleineres Badge (21×21 dp)
        TextView floatingBadge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams((int) (21 * density), (int) (21 * density));
        floatingBadge.setLayoutParams(badgeParams);
        floatingBadge.setText(badgeText);
        floatingBadge.setTextColor(Color.WHITE);
        floatingBadge.setTextSize(8);
        floatingBadge.setGravity(Gravity.CENTER);
        floatingBadge.setBackground(badgeBg);
        topDock.addView(floatingBadge);

        // Mini-Dock Buttons für minimierten Zustand
        TextView miniBtnHeal = null;
        if (role == ROLE_HEAL) {
            miniBtnHeal = createDockButton("H", accentColor, density, 20, 18);
            miniBtnHeal.setVisibility(View.GONE);
            miniBtnHeal.setOnClickListener(v -> executeRecordedTap(webView, "c" + clientUid + "_heal", "Digit1", 49));
            topDock.addView(miniBtnHeal);
        }

        TextView miniBtnBuff = null;
        if (role == ROLE_HEAL) {
            miniBtnBuff = createDockButton("B", accentColor, density, 20, 18);
            miniBtnBuff.setVisibility(View.GONE);
            miniBtnBuff.setOnClickListener(v -> executeRecordedBuffSequence(webView, clientUid));
            topDock.addView(miniBtnBuff);
        }

        final boolean[] isRecordingMode = {false};
        final String[] targetKeyToRecord = {""};

        if (role == ROLE_HEAL) {
            // RM Buttons: speziell schmaler gehalten (26×19 dp)
            TextView btnHeal = createDockButton("Heal", accentColor, density, 26, 19);
            final TextView finalBtnHeal = btnHeal;
            btnHeal.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "heal";
                    Toast.makeText(this, isDe() ? "Tippe jetzt auf das HEAL-Icon!" : "Tap the HEAL icon now!", Toast.LENGTH_SHORT).show();
                    finalBtnHeal.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_heal", "Digit1", 49);
                }
            });
            topDock.addView(btnHeal);

            TextView btnBuff = createDockButton("Buff", accentColor, density, 26, 19);
            btnBuff.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    promptBuffCountDialog(clientUid, isRecordingMode, targetKeyToRecord);
                } else {
                    executeRecordedBuffSequence(webView, clientUid);
                }
            });
            topDock.addView(btnBuff);

            TextView btnBuffFast = createDockButton("BFast", accentColor, density, 28, 19);
            final TextView finalBtnFast = btnBuffFast;
            btnBuffFast.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "bfast";
                    Toast.makeText(this, isDe() ? "Tippe jetzt auf das Fast-Buff/Skill-Icon!" : "Tap the Fast-Buff/Skill icon now!", Toast.LENGTH_SHORT).show();
                    finalBtnFast.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_bfast", "Digit4", 52);
                }
            });
            topDock.addView(btnBuffFast);

            TextView btnFollow = createDockButton("Fol", accentColor, density, 25, 19);
            final TextView finalBtnFol = btnFollow;
            btnFollow.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "follow";
                    Toast.makeText(this, isDe() ? "Tippe jetzt auf das FOLLOW-Icon!" : "Tap the FOLLOW icon now!", Toast.LENGTH_SHORT).show();
                    finalBtnFol.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_follow", "Digit3", 51);
                }
            });
            topDock.addView(btnFollow);

            final boolean[] isAutoFollowActive = {false};
            TextView btnAutoFollow = createDockButton("AFol", accentColor, density, 27, 19);
            Runnable autoFollowTask = new Runnable() {
                @Override
                public void run() {
                    if (isAutoFollowActive[0]) {
                        executeRecordedTap(webView, "c" + clientUid + "_follow", "Digit3", 51);
                        loopHandler.postDelayed(this, 15000);
                    }
                }
            };
            btnAutoFollow.setOnClickListener(v -> {
                isAutoFollowActive[0] = !isAutoFollowActive[0];
                if (isAutoFollowActive[0]) {
                    btnAutoFollow.setTextColor(Color.GREEN);
                    Toast.makeText(this, isDe() ? "Auto-Follow alle 15s aktiv" : "Auto-Follow active every 15s", Toast.LENGTH_SHORT).show();
                    loopHandler.post(autoFollowTask);
                } else {
                    btnAutoFollow.setTextColor(Color.WHITE);
                    loopHandler.removeCallbacks(autoFollowTask);
                    Toast.makeText(this, isDe() ? "Auto-Follow gestoppt" : "Auto-Follow stopped", Toast.LENGTH_SHORT).show();
                }
            });
            topDock.addView(btnAutoFollow);

        } else if (role == ROLE_LEECH) {
            // LEECHER: Ausschließlich Follow & Auto-Follow (KEIN Heal, Buff oder Cheer)
            TextView btnFollow = createDockButton("Fol", accentColor, density, 28, 19);
            final TextView finalBtnFol2 = btnFollow;
            btnFollow.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "follow";
                    Toast.makeText(this, isDe() ? "Tippe jetzt auf das FOLLOW-Icon!" : "Tap the FOLLOW icon now!", Toast.LENGTH_SHORT).show();
                    finalBtnFol2.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_follow", "Digit1", 49);
                }
            });
            topDock.addView(btnFollow);

            final boolean[] isAutoFollowActive = {false};
            TextView btnAutoFollow = createDockButton("AFol", accentColor, density, 30, 19);
            Runnable autoFollowTask = new Runnable() {
                @Override
                public void run() {
                    if (isAutoFollowActive[0]) {
                        executeRecordedTap(webView, "c" + clientUid + "_follow", "Digit1", 49);
                        loopHandler.postDelayed(this, 15000);
                    }
                }
            };
            btnAutoFollow.setOnClickListener(v -> {
                isAutoFollowActive[0] = !isAutoFollowActive[0];
                if (isAutoFollowActive[0]) {
                    btnAutoFollow.setTextColor(Color.GREEN);
                    Toast.makeText(this, isDe() ? "Auto-Follow alle 15s aktiv" : "Auto-Follow active every 15s", Toast.LENGTH_SHORT).show();
                    loopHandler.post(autoFollowTask);
                } else {
                    btnAutoFollow.setTextColor(Color.WHITE);
                    loopHandler.removeCallbacks(autoFollowTask);
                    Toast.makeText(this, isDe() ? "Auto-Follow gestoppt" : "Auto-Follow stopped", Toast.LENGTH_SHORT).show();
                }
            });
            topDock.addView(btnAutoFollow);

        } else {
            TextView btnAtk = createDockButton("Atk", accentColor, density, 26, 19);
            btnAtk.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit1", 49));
            topDock.addView(btnAtk);

            TextView btnSkill = createDockButton("Skill", accentColor, density, 28, 19);
            btnSkill.setOnClickListener(v -> sendGameKeyEvent(webView, "Digit2", 50));
            topDock.addView(btnSkill);
        }

        // 10 % kleinere Funktions-Buttons (23×19 dp)
        TextView btnSettings = createDockButton("⚙", accentColor, density, 23, 19);
        btnSettings.setOnClickListener(v -> {
            isRecordingMode[0] = !isRecordingMode[0];
            if (isRecordingMode[0]) {
                btnSettings.setTextColor(Color.RED);
                String msg = isDe() ? "AUFNAHME-MODUS AKTIV: Button wählen und auf Skill tippen!" : "RECORDING ACTIVE: Select button and tap skill icon!";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            } else {
                btnSettings.setTextColor(Color.WHITE);
                targetKeyToRecord[0] = "";
                Toast.makeText(this, isDe() ? "Aufnahme-Modus beendet." : "Recording mode stopped.", Toast.LENGTH_SHORT).show();
            }
        });
        topDock.addView(btnSettings);

        final boolean[] isTransparent = {false};
        TextView btnGhost = createDockButton("👻", accentColor, density, 23, 19);
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
        TextView btnLock = createDockButton("🔓", accentColor, density, 23, 19);
        btnLock.setOnClickListener(v -> {
            if (!isTouchLocked[0]) {
                webView.setOnTouchListener((view, motionEvent) -> false);
                isTouchLocked[0] = true;
                btnLock.setText("🔒");
                btnLock.setTextColor(Color.RED);
                String msg = isDe() ? ": Ghost-Touch aktiv" : ": Ghost-Touch active";
                Toast.makeText(this, badgeText + msg, Toast.LENGTH_SHORT).show();
            } else {
                webView.setOnTouchListener(null);
                isTouchLocked[0] = false;
                btnLock.setText("🔓");
                btnLock.setTextColor(Color.WHITE);
            }
        });
        topDock.addView(btnLock);

        container.addView(topDock);

        startHpWatcherLoop(webView, clientUid, floatingBadge, accentColor);

        final Handler thermalHandler = new Handler(Looper.getMainLooper());
        final boolean[] isDimmed = {false};

        Runnable dimmingTask = () -> {
            if (!isTransparent[0] && container.getVisibility() == View.VISIBLE) {
                container.animate().alpha(0.45f).setDuration(400).start();
                isDimmed[0] = true;
            }
        };

        thermalHandler.postDelayed(dimmingTask, 20000);

        Runnable resetDimmingTimer = () -> {
            thermalHandler.removeCallbacks(dimmingTask);
            if (isDimmed[0] && !isTransparent[0]) {
                container.animate().alpha(1.0f).setDuration(200).start();
                isDimmed[0] = false;
            }
            thermalHandler.postDelayed(dimmingTask, 20000);
        };

        webView.setOnTouchListener((v, event) -> {
            resetDimmingTimer.run();

            if (isRecordingMode[0] && !targetKeyToRecord[0].isEmpty() && event.getAction() == MotionEvent.ACTION_UP) {
                float clickX = event.getX();
                float clickY = event.getY();
                float width = v.getWidth();
                float height = v.getHeight();

                if (width > 0 && height > 0) {
                    float relX = clickX / width;
                    float relY = clickY / height;

                    if (targetKeyToRecord[0].startsWith("buff_step_")) {
                        prefs.edit().putFloat("c" + clientUid + "_" + targetKeyToRecord[0] + "_x", relX)
                                .putFloat("c" + clientUid + "_" + targetKeyToRecord[0] + "_y", relY).apply();
                        String savedMsg = isDe() ? "Gespeichert: " : "Saved: ";
                        Toast.makeText(this, savedMsg + targetKeyToRecord[0], Toast.LENGTH_SHORT).show();
                        advanceBuffRecording(clientUid, targetKeyToRecord, isRecordingMode);
                    } else {
                        prefs.edit().putFloat("c" + clientUid + "_" + targetKeyToRecord[0] + "_x", relX)
                                .putFloat("c" + clientUid + "_" + targetKeyToRecord[0] + "_y", relY).apply();
                        String savedPosMsg = isDe()
                                ? ("Position für " + targetKeyToRecord[0].toUpperCase() + " gespeichert!")
                                : ("Position for " + targetKeyToRecord[0].toUpperCase() + " saved!");
                        Toast.makeText(this, savedPosMsg, Toast.LENGTH_SHORT).show();
                        targetKeyToRecord[0] = "";
                    }
                }
                return false;
            }
            return false;
        });

        TextView resizeHandle = new TextView(this);
        FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams((int) (22 * density), (int) (22 * density));
        resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
        resizeHandle.setLayoutParams(resizeParams);
        resizeHandle.setText("⇲");
        resizeHandle.setTextColor(accentColor);
        resizeHandle.setTextSize(12);
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

        final TextView finalMiniHeal = miniBtnHeal;
        final TextView finalMiniBuff = miniBtnBuff;

        topDock.setOnTouchListener((v, event) -> {
            resetDimmingTimer.run();

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

                    if (duration < 250 && event.getX() < 35 * density) {
                        if (!isMinimized[0]) {
                            savedWidth[0] = container.getWidth();
                            savedHeight[0] = container.getHeight();

                            webView.setVisibility(View.INVISIBLE);
                            resizeHandle.setVisibility(View.GONE);

                            int startIndex = (role == ROLE_HEAL) ? 3 : 1;
                            for (int i = startIndex; i < topDock.getChildCount(); i++) {
                                topDock.getChildAt(i).setVisibility(View.GONE);
                            }
                            if (finalMiniHeal != null) finalMiniHeal.setVisibility(View.VISIBLE);
                            if (finalMiniBuff != null) finalMiniBuff.setVisibility(View.VISIBLE);
                            container.setBackground(null);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = (role == ROLE_HEAL) ? (int) (80 * density) : (int) (26 * density);
                            p.height = (int) (24 * density);
                            container.setLayoutParams(p);
                            isMinimized[0] = true;
                            setClientLowSpecMode(webView, true);
                        } else {
                            webView.setVisibility(View.VISIBLE);
                            resizeHandle.setVisibility(View.VISIBLE);

                            if (finalMiniHeal != null) finalMiniHeal.setVisibility(View.GONE);
                            if (finalMiniBuff != null) finalMiniBuff.setVisibility(View.GONE);

                            int startIndex = (role == ROLE_HEAL) ? 3 : 1;
                            for (int i = startIndex; i < topDock.getChildCount(); i++) {
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

                    if (duration >= 600 && event.getX() < 35 * density) {
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
            resetDimmingTimer.run();

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

    private void startHpWatcherLoop(WebView wv, int clientUid, TextView badgeView, int defaultColor) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (wv != null) {
                    String checkJs = "(function() {" +
                            "  var hpBar = document.querySelector('.player-hp, .progress-bar-danger, [class*=\"hp\"]');" +
                            "  if (hpBar && hpBar.style && hpBar.style.width) {" +
                            "    var val = parseInt(hpBar.style.width);" +
                            "    return (val > 0 && val < 30) ? 'LOW' : 'OK';" +
                            "  }" +
                            "  return 'OK';" +
                            "})();";

                    wv.evaluateJavascript(checkJs, val -> {
                        if (val != null && val.contains("LOW")) {
                            badgeView.setBackgroundColor(Color.RED);
                            executeRecordedTap(wv, "c" + clientUid + "_heal", "Digit1", 49);
                        } else {
                            badgeView.setBackgroundColor(defaultColor);
                        }
                    });
                }
                loopHandler.postDelayed(this, 3000);
            }
        };
        loopHandler.postDelayed(r, 4000);
    }

    private void promptBuffCountDialog(int clientUid, boolean[] isRecordingMode, String[] targetKeyToRecord) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(isDe() ? "Anzahl (1 bis 15)" : "Count (1 to 15)");

        String title = isDe() ? "Buff-Sequenz aufnehmen" : "Record buff sequence";
        String msg = isDe()
                ? "Wie viele Buffs möchtest du hintereinander ausführen? (1 - 15):"
                : "How many buffs do you want to chain? (1 - 15):";
        String btnStart = isDe() ? "Starten" : "Start";
        String btnCancel = isDe() ? "Abbrechen" : "Cancel";

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setMessage(msg)
                .setView(input)
                .setPositiveButton(btnStart, (dialog, which) -> {
                    int count = 1;
                    try {
                        count = Integer.parseInt(input.getText().toString());
                    } catch (Exception ignored) {}
                    if (count < 1) count = 1;
                    if (count > 15) count = 15;

                    prefs.edit().putInt("c" + clientUid + "_buff_count", count).apply();
                    targetKeyToRecord[0] = "buff_step_1";

                    String tapMsg = isDe() ? ("Tippe jetzt Buff 1 von " + count + " an!") : ("Tap Buff 1 of " + count + " now!");
                    Toast.makeText(this, tapMsg, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(btnCancel, null)
                .show();
    }

    private void advanceBuffRecording(int clientUid, String[] targetKeyToRecord, boolean[] isRecordingMode) {
        int total = prefs.getInt("c" + clientUid + "_buff_count", 1);
        try {
            int currentStep = Integer.parseInt(targetKeyToRecord[0].replace("buff_step_", ""));
            if (currentStep < total) {
                targetKeyToRecord[0] = "buff_step_" + (currentStep + 1);
                String stepMsg = isDe()
                        ? ("Tippe jetzt Buff " + (currentStep + 1) + " von " + total + " an!")
                        : ("Tap Buff " + (currentStep + 1) + " of " + total + " now!");
                Toast.makeText(this, stepMsg, Toast.LENGTH_SHORT).show();
            } else {
                String finishedMsg = isDe() ? ("Alle " + total + " Buffs gespeichert!") : ("All " + total + " buffs saved!");
                Toast.makeText(this, finishedMsg, Toast.LENGTH_LONG).show();
                targetKeyToRecord[0] = "";
                isRecordingMode[0] = false;
            }
        } catch (Exception ignored) {}
    }

    private void executeRecordedTap(WebView wv, String prefKey, String fallbackCode, int fallbackKeyCode) {
        if (prefs.contains(prefKey + "_x")) {
            float rx = prefs.getFloat(prefKey + "_x", 0.5f);
            float ry = prefs.getFloat(prefKey + "_y", 0.5f);
            dispatchRelativeTouch(wv, rx, ry);
        } else {
            sendGameKeyEvent(wv, fallbackCode, fallbackKeyCode);
        }
    }

    private void executeRecordedBuffSequence(WebView wv, int clientUid) {
        int total = prefs.getInt("c" + clientUid + "_buff_count", 0);
        if (total == 0) {
            sendGameKeyEvent(wv, "Digit2", 50);
            return;
        }

        String buffingMsg = isDe() ? ("Buffe " + total + " Skills...") : ("Buffing " + total + " skills...");
        Toast.makeText(this, buffingMsg, Toast.LENGTH_SHORT).show();
        Handler h = new Handler(Looper.getMainLooper());
        for (int i = 1; i <= total; i++) {
            final int step = i;
            h.postDelayed(() -> {
                String key = "c" + clientUid + "_buff_step_" + step;
                if (prefs.contains(key + "_x")) {
                    float rx = prefs.getFloat(key + "_x", 0.5f);
                    float ry = prefs.getFloat(key + "_y", 0.5f);
                    dispatchRelativeTouch(wv, rx, ry);
                }
            }, (long) (i - 1) * 350);
        }
    }

    private void dispatchRelativeTouch(WebView wv, float relX, float relY) {
        if (wv == null) return;
        String js = "(function() {" +
                "  var canvas = document.querySelector('canvas') || document.body;" +
                "  var rect = canvas.getBoundingClientRect();" +
                "  var clientX = rect.left + (rect.width * " + relX + ");" +
                "  var clientY = rect.top + (rect.height * " + relY + ");" +
                "  var opts = {bubbles: true, cancelable: true, view: window, clientX: clientX, clientY: clientY};" +
                "  canvas.dispatchEvent(new MouseEvent('mousedown', opts));" +
                "  canvas.dispatchEvent(new MouseEvent('mouseup', opts));" +
                "  canvas.dispatchEvent(new MouseEvent('click', opts));" +
                "})();";
        wv.evaluateJavascript(js, null);
    }

    // Erstellt ca. 10 % kleinere Buttons mit anpassbarer Breite
    private TextView createDockButton(String text, int accentColor, float density, int widthDp, int heightDp) {
        TextView btn = new TextView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams((int) (widthDp * density), (int) (heightDp * density));
        p.leftMargin = (int) (3 * density);
        btn.setLayoutParams(p);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(8);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable b = new GradientDrawable();
        b.setColor(Color.parseColor("#44222226"));
        b.setCornerRadius(3 * density);
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

        viewB.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams pA = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        pA.topMargin = (int) (24 * getResources().getDisplayMetrics().density);
        viewA.setLayoutParams(pA);

        rootContainer.addView(viewB, 0);
        targetContainer.addView(viewA, 0);

        setClientAudioMuted(viewB, false);
        setClientAudioMuted(viewA, true);

        if (activeSwapIndex == targetOverlayIndex) {
            activeSwapIndex = -1;
            Toast.makeText(this, isDe() ? "Zurückgetauscht" : "Swapped back", Toast.LENGTH_SHORT).show();
        } else {
            activeSwapIndex = targetOverlayIndex;
            Toast.makeText(this, isDe() ? "Ansicht gewechselt (ohne Reload)" : "View swapped (without reload)", Toast.LENGTH_SHORT).show();
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

        s.setSaveFormData(false);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            s.setSavePassword(false);
        }

        s.setTextZoom(100);

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

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);

                // Zieht den tatsächlichen Flyff-Schieberegler und die Engine-Werte auf 65 %
                String scaleJs = "(function() {" +
                        "  function force65Scale() {" +
                        "    try {" +
                        "      for (var k in localStorage) {" +
                        "        if (k.indexOf('option') !== -1 || k.indexOf('setting') !== -1) {" +
                        "          try {" +
                        "            var item = JSON.parse(localStorage.getItem(k));" +
                        "            if (item && typeof item === 'object') {" +
                        "              if ('scale' in item) item.scale = 65;" +
                        "              if ('uiScale' in item) item.uiScale = 0.65;" +
                        "              localStorage.setItem(k, JSON.stringify(item));" +
                        "            }" +
                        "          } catch(e) {}" +
                        "        }" +
                        "      }" +
                        "      localStorage.setItem('option:scale', '65');" +
                        "      localStorage.setItem('scale', '65');" +
                        "      var sliders = document.querySelectorAll('input[type=\"range\"]');" +
                        "      sliders.forEach(function(slider) {" +
                        "        var row = slider.closest('tr') || slider.parentElement;" +
                        "        if (row && (row.innerText.indexOf('Skaliert') !== -1 || row.innerText.indexOf('Scale') !== -1)) {" +
                        "          slider.value = 65;" +
                        "          slider.dispatchEvent(new Event('input', {bubbles: true}));" +
                        "          slider.dispatchEvent(new Event('change', {bubbles: true}));" +
                        "        }" +
                        "      });" +
                        "      window.dispatchEvent(new Event('resize'));" +
                        "    } catch(err) {}" +
                        "  }" +
                        "  force65Scale();" +
                        "  setTimeout(force65Scale, 1500);" +
                        "  setTimeout(force65Scale, 3000);" +
                        "  setTimeout(force65Scale, 5500);" +
                        "})();";
                v.evaluateJavascript(scaleJs, null);
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
        loopHandler.removeCallbacksAndMessages(null);
        for (WebView w : activeWebViews) {
            if (w != null) w.destroy();
        }
    }
}
