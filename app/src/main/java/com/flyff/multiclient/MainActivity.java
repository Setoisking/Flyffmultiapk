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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Xml;
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
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    private FrameLayout rootContainer;
    private FrameLayout overlayLayer;
    private final List<WebView> activeWebViews = new ArrayList<>();
    private final List<FrameLayout> overlayContainers = new ArrayList<>();

    private int activeSwapIndex = -1;
    private static final String GITHUB_REPO = "Setoisking/Flyffmultiapk";
    private static final String XLSX_FILE_NAME = "flyff_accounts.xlsx";
    private SharedPreferences prefs;

    public static final int ROLE_DD = 0;
    public static final int ROLE_HEAL = 1;
    public static final int ROLE_LEECH = 2;
    private int[] sessionRoles = new int[8];
    private int totalSavedSessions = 1;

    private final Handler loopHandler = new Handler(Looper.getMainLooper());

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
        rootContainer = findViewById(R.id.rootContainer);
        overlayLayer = findViewById(R.id.overlayLayer);

        // Prüft beim allerersten Start, ob die Excel-Datei existiert; falls nicht, wird sie als Vorlage erstellt
        ensureXlsxFileExists();

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

    // ==========================================
    // EXCEL (.XLSX) GENERIERUNG & AUSLESEN
    // ==========================================
    private File getAccountsXlsxFile() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        return new File(downloadDir, XLSX_FILE_NAME);
    }

    private void ensureXlsxFileExists() {
        File file = getAccountsXlsxFile();
        if (!file.exists()) {
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ZipOutputStream zos = new ZipOutputStream(fos);

                // 1. [Content_Types].xml
                addZipEntry(zos, "[Content_Types].xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                                "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                                "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                                "</Types>");

                // 2. _rels/.rels
                addZipEntry(zos, "_rels/.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                                "</Relationships>");

                // 3. xl/_rels/workbook.xml.rels
                addZipEntry(zos, "xl/_rels/workbook.xml.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                                "</Relationships>");

                // 4. xl/workbook.xml
                addZipEntry(zos, "xl/workbook.xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                                "  <sheets>\n" +
                                "    <sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                                "  </sheets>\n" +
                                "</workbook>");

                // 5. xl/worksheets/sheet1.xml (Vorlage mit Session 1 bis 8 zum manuellen Ausfüllen)
                StringBuilder sheetXml = new StringBuilder();
                sheetXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
                sheetXml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
                sheetXml.append("  <sheetData>\n");
                sheetXml.append("    <row r=\"1\">\n");
                sheetXml.append("      <c r=\"A1\" t=\"inlineStr\"><is><t>Session</t></is></c>\n");
                sheetXml.append("      <c r=\"B1\" t=\"inlineStr\"><is><t>Email</t></is></c>\n");
                sheetXml.append("      <c r=\"C1\" t=\"inlineStr\"><is><t>Passwort</t></is></c>\n");
                sheetXml.append("    </row>\n");
                for (int i = 1; i <= 8; i++) {
                    int rowNum = i + 1;
                    sheetXml.append("    <row r=\"").append(rowNum).append("\">\n");
                    sheetXml.append("      <c r=\"A").append(rowNum).append("\"><v>").append(i).append("</v></c>\n");
                    sheetXml.append("      <c r=\"B").append(rowNum).append("\" t=\"inlineStr\"><is><t></t></is></c>\n");
                    sheetXml.append("      <c r=\"C").append(rowNum).append("\" t=\"inlineStr\"><is><t></t></is></c>\n");
                    sheetXml.append("    </row>\n");
                }
                sheetXml.append("  </sheetData>\n");
                sheetXml.append("</worksheet>");

                addZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml.toString());

                zos.flush();
                zos.close();
                fos.close();
                Toast.makeText(this, "Vorlage 'flyff_accounts.xlsx' in Downloads erstellt!", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        byte[] data = content.getBytes("UTF-8");
        zos.write(data, 0, data.length);
        zos.closeEntry();
    }

    private String[] readAccountFromXlsx(int sessionId) {
        File file = getAccountsXlsxFile();
        if (!file.exists()) return new String[]{"", ""};

        ZipFile zip = null;
        try {
            zip = new ZipFile(file);

            // 1. Shared Strings einlesen (falls von Excel gespeichert)
            List<String> sharedStrings = new ArrayList<>();
            ZipEntry sstEntry = zip.getEntry("xl/sharedStrings.xml");
            if (sstEntry != null) {
                InputStream is = zip.getInputStream(sstEntry);
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(is, "UTF-8");
                int eventType = parser.getEventType();
                StringBuilder currentText = new StringBuilder();
                boolean insideT = false;

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if ("t".equalsIgnoreCase(parser.getName())) {
                            insideT = true;
                            currentText.setLength(0);
                        }
                    } else if (eventType == XmlPullParser.TEXT) {
                        if (insideT) {
                            currentText.append(parser.getText());
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if ("t".equalsIgnoreCase(parser.getName())) {
                            insideT = false;
                        } else if ("si".equalsIgnoreCase(parser.getName())) {
                            sharedStrings.add(currentText.toString());
                            currentText.setLength(0);
                        }
                    }
                    eventType = parser.next();
                }
                is.close();
            }

            // 2. Sheet einlesen (xl/worksheets/sheet1.xml)
            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry == null) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.getName().startsWith("xl/worksheets/sheet") && e.getName().endsWith(".xml")) {
                        sheetEntry = e;
                        break;
                    }
                }
            }

            if (sheetEntry != null) {
                InputStream is = zip.getInputStream(sheetEntry);
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(is, "UTF-8");

                int eventType = parser.getEventType();
                int currentCol = -1;
                String cellType = "";
                StringBuilder cellValue = new StringBuilder();
                boolean insideValue = false;
                boolean insideInlineText = false;

                String rowSession = "";
                String rowEmail = "";
                String rowPass = "";

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("row".equalsIgnoreCase(tag)) {
                            rowSession = "";
                            rowEmail = "";
                            rowPass = "";
                            currentCol = -1;
                        } else if ("c".equalsIgnoreCase(tag)) {
                            cellType = parser.getAttributeValue(null, "t");
                            if (cellType == null) cellType = "";
                            String ref = parser.getAttributeValue(null, "r");
                            if (ref != null && !ref.isEmpty()) {
                                char colChar = Character.toUpperCase(ref.charAt(0));
                                currentCol = (colChar == 'A') ? 0 : (colChar == 'B' ? 1 : (colChar == 'C' ? 2 : -1));
                            } else {
                                currentCol++;
                            }
                            cellValue.setLength(0);
                        } else if ("v".equalsIgnoreCase(tag)) {
                            insideValue = true;
                        } else if ("t".equalsIgnoreCase(tag)) {
                            insideInlineText = true;
                        }
                    } else if (eventType == XmlPullParser.TEXT) {
                        if (insideValue || insideInlineText) {
                            cellValue.append(parser.getText());
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        String tag = parser.getName();
                        if ("v".equalsIgnoreCase(tag)) {
                            insideValue = false;
                        } else if ("t".equalsIgnoreCase(tag)) {
                            insideInlineText = false;
                        } else if ("c".equalsIgnoreCase(tag)) {
                            String finalVal = cellValue.toString().trim();
                            if ("s".equalsIgnoreCase(cellType)) {
                                try {
                                    int sIdx = Integer.parseInt(finalVal);
                                    if (sIdx >= 0 && sIdx < sharedStrings.size()) {
                                        finalVal = sharedStrings.get(sIdx);
                                    }
                                } catch (Exception ignored) {}
                            }
                            if (currentCol == 0) rowSession = finalVal;
                            else if (currentCol == 1) rowEmail = finalVal;
                            else if (currentCol == 2) rowPass = finalVal;
                        } else if ("row".equalsIgnoreCase(tag)) {
                            try {
                                int sid = Integer.parseInt(rowSession.replaceAll("[^0-9]", ""));
                                if (sid == sessionId) {
                                    is.close();
                                    zip.close();
                                    return new String[]{rowEmail, rowPass};
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    eventType = parser.next();
                }
                is.close();
            }
        } catch (Exception ignored) {
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (Exception ignored) {}
            }
        }
        return new String[]{"", ""};
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
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Neues Update verfügbar!")
                .setMessage("Installiert: v" + currentVer + "\nNeueste Version: v" + newVer + "\n\nJetzt aktualisieren?")
                .setCancelable(false)
                .setPositiveButton("Aktualisieren", (dialog, which) -> downloadAndInstallApk(downloadUrl))
                .setNegativeButton("Später", (dialog, which) -> checkSavedSessionSetup())
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
            Toast.makeText(this, "Installer-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            checkSavedSessionSetup();
        }
    }

    // ==========================================
    // GESPEICHERTES SETUP LADEN ODER NEU WÄHLEN
    // ==========================================
    private void checkSavedSessionSetup() {
        boolean hasSaved = prefs.getBoolean("has_saved_setup", false);
        if (hasSaved) {
            totalSavedSessions = prefs.getInt("saved_total_sessions", 1);
            StringBuilder sb = new StringBuilder();
            sb.append("Gespeichertes Setup (").append(totalSavedSessions).append(" Sessions):\n");
            for (int i = 0; i < totalSavedSessions; i++) {
                int r = prefs.getInt("role_session_" + i, ROLE_DD);
                sessionRoles[i] = r;
                String rName = (r == ROLE_HEAL) ? "Heal (RM)" : (r == ROLE_LEECH ? "Leech" : "Main (DD)");
                sb.append("• Session ").append(i + 1).append(": ").append(rName).append("\n");
            }
            sb.append("\nMöchtest du dieses Setup erneut übernehmen?");

            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Setup übernehmen?")
                    .setMessage(sb.toString())
                    .setCancelable(false)
                    .setPositiveButton("Ja, übernehmen", (dialog, which) -> launchSessions(totalSavedSessions))
                    .setNegativeButton("Neu einrichten", (dialog, which) -> promptSessionCount())
                    .show();
        } else {
            promptSessionCount();
        }
    }

    private void promptSessionCount() {
        final String[] options = {
                "1 Session (Vollbild)", "2 Sessions", "3 Sessions", "4 Sessions",
                "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Wie viele Sessions starten?")
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
        String sessionName = (currentSessionIndex == 0) ? "Hauptfenster (Vollbild)" : "Overlay " + (currentSessionIndex + 1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Rolle für Session " + (currentSessionIndex + 1) + " (" + sessionName + ")")
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

    // ==========================================
    // MULTI-SESSION OVERLAYS & BEDIENUNG
    // ==========================================
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

        final String badgeText;
        if (role == ROLE_HEAL) badgeText = "RM";
        else if (role == ROLE_LEECH) badgeText = "LC";
        else if (role == ROLE_DD) badgeText = "DD";
        else badgeText = "C" + clientNumber;

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

        TextView miniBtnHeal = createDockButton("H", accentColor, density);
        miniBtnHeal.setVisibility(View.GONE);
        miniBtnHeal.setOnClickListener(v -> executeRecordedTap(webView, "c" + clientUid + "_heal", "Digit1", 49));
        topDock.addView(miniBtnHeal);

        TextView miniBtnBuff = createDockButton("B", accentColor, density);
        miniBtnBuff.setVisibility(View.GONE);
        miniBtnBuff.setOnClickListener(v -> executeRecordedBuffSequence(webView, clientUid));
        topDock.addView(miniBtnBuff);

        final boolean[] isRecordingMode = {false};
        final String[] targetKeyToRecord = {""};

        TextView btnHeal = null;
        TextView btnBuff = null;
        TextView btnBuffFast = null;
        TextView btnFollow = null;

        if (role == ROLE_HEAL) {
            btnHeal = createDockButton("Heal", accentColor, density);
            final TextView finalBtnHeal = btnHeal;
            btnHeal.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "heal";
                    Toast.makeText(this, "Tippe jetzt auf das HEAL-Icon!", Toast.LENGTH_SHORT).show();
                    finalBtnHeal.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_heal", "Digit1", 49);
                }
            });
            topDock.addView(btnHeal);

            btnBuff = createDockButton("Buff", accentColor, density);
            btnBuff.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    promptBuffCountDialog(clientUid, isRecordingMode, targetKeyToRecord);
                } else {
                    executeRecordedBuffSequence(webView, clientUid);
                }
            });
            topDock.addView(btnBuff);

            btnBuffFast = createDockButton("BFast", accentColor, density);
            final TextView finalBtnFast = btnBuffFast;
            btnBuffFast.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "bfast";
                    Toast.makeText(this, "Tippe jetzt auf das Fast-Buff/Skill-Icon!", Toast.LENGTH_SHORT).show();
                    finalBtnFast.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_bfast", "Digit4", 52);
                }
            });
            topDock.addView(btnBuffFast);

            btnFollow = createDockButton("Fol", accentColor, density);
            final TextView finalBtnFol = btnFollow;
            btnFollow.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "follow";
                    Toast.makeText(this, "Tippe jetzt auf das FOLLOW-Icon!", Toast.LENGTH_SHORT).show();
                    finalBtnFol.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_follow", "Digit3", 51);
                }
            });
            topDock.addView(btnFollow);

            final boolean[] isAutoFollowActive = {false};
            TextView btnAutoFollow = createDockButton("AFol", accentColor, density);
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
                    Toast.makeText(this, "Auto-Follow alle 15s aktiv", Toast.LENGTH_SHORT).show();
                    loopHandler.post(autoFollowTask);
                } else {
                    btnAutoFollow.setTextColor(Color.WHITE);
                    loopHandler.removeCallbacks(autoFollowTask);
                    Toast.makeText(this, "Auto-Follow gestoppt", Toast.LENGTH_SHORT).show();
                }
            });
            topDock.addView(btnAutoFollow);

        } else if (role == ROLE_LEECH) {
            btnFollow = createDockButton("Fol", accentColor, density);
            final TextView finalBtnFol2 = btnFollow;
            btnFollow.setOnClickListener(v -> {
                if (isRecordingMode[0]) {
                    targetKeyToRecord[0] = "follow";
                    Toast.makeText(this, "Tippe jetzt auf das FOLLOW-Icon!", Toast.LENGTH_SHORT).show();
                    finalBtnFol2.setTextColor(Color.YELLOW);
                } else {
                    executeRecordedTap(webView, "c" + clientUid + "_follow", "Digit1", 49);
                }
            });
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

        // Auto-Login: Liest Session direkt aus Download/flyff_accounts.xlsx
        TextView btnAutoLogin = createDockButton("Key", accentColor, density);
        btnAutoLogin.setOnClickListener(v -> {
            String[] acc = readAccountFromXlsx(clientUid);
            if (acc[0].isEmpty() && acc[1].isEmpty()) {
                Toast.makeText(this, "Keine Daten in Download/flyff_accounts.xlsx für Session " + clientUid + " gefunden!", Toast.LENGTH_LONG).show();
            } else {
                injectCredentials(webView, acc[0], acc[1]);
            }
        });
        topDock.addView(btnAutoLogin);

        TextView btnSettings = createDockButton("⚙", accentColor, density);
        btnSettings.setOnClickListener(v -> {
            isRecordingMode[0] = !isRecordingMode[0];
            if (isRecordingMode[0]) {
                btnSettings.setTextColor(Color.RED);
                Toast.makeText(this, "AUFNAHME-MODUS AKTIV: Button wählen und auf Skill tippen!", Toast.LENGTH_LONG).show();
            } else {
                btnSettings.setTextColor(Color.WHITE);
                targetKeyToRecord[0] = "";
                Toast.makeText(this, "Aufnahme-Modus beendet.", Toast.LENGTH_SHORT).show();
            }
        });
        topDock.addView(btnSettings);

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

        startHpWatcherLoop(webView, clientUid, floatingBadge, accentColor);

        // Akku- & Thermal-Guard
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
                        Toast.makeText(this, "Gespeichert: " + targetKeyToRecord[0], Toast.LENGTH_SHORT).show();
                        advanceBuffRecording(clientUid, targetKeyToRecord, isRecordingMode);
                    } else {
                        prefs.edit().putFloat("c" + clientUid + "_" + targetKeyToRecord[0] + "_x", relX)
                                .putFloat("c" + clientUid + "_" + targetKeyToRecord[0] + "_y", relY).apply();
                        Toast.makeText(this, "Position für " + targetKeyToRecord[0].toUpperCase() + " gespeichert!", Toast.LENGTH_SHORT).show();
                        targetKeyToRecord[0] = "";
                    }
                }
                return false;
            }
            return false;
        });

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

                    if (duration < 250 && event.getX() < 40 * density) {
                        if (!isMinimized[0]) {
                            savedWidth[0] = container.getWidth();
                            savedHeight[0] = container.getHeight();

                            webView.setVisibility(View.INVISIBLE);
                            resizeHandle.setVisibility(View.GONE);

                            for (int i = 3; i < topDock.getChildCount(); i++) {
                                topDock.getChildAt(i).setVisibility(View.GONE);
                            }
                            miniBtnHeal.setVisibility(View.VISIBLE);
                            miniBtnBuff.setVisibility(View.VISIBLE);
                            container.setBackground(null);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = (int) (105 * density);
                            p.height = (int) (32 * density);
                            container.setLayoutParams(p);
                            isMinimized[0] = true;
                            setClientLowSpecMode(webView, true);
                        } else {
                            webView.setVisibility(View.VISIBLE);
                            resizeHandle.setVisibility(View.VISIBLE);

                            miniBtnHeal.setVisibility(View.GONE);
                            miniBtnBuff.setVisibility(View.GONE);

                            for (int i = 3; i < topDock.getChildCount(); i++) {
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

    private void injectCredentials(WebView wv, String user, String pass) {
        if (wv == null) return;
        String js = "(function() {" +
                "  var inputs = document.querySelectorAll('input');" +
                "  for(var i=0; i<inputs.length; i++) {" +
                "    var type = inputs[i].getAttribute('type') || '';" +
                "    if(type.toLowerCase() === 'password') {" +
                "      inputs[i].value = '" + pass + "';" +
                "      inputs[i].dispatchEvent(new Event('input', {bubbles: true}));" +
                "    } else if(type.toLowerCase() === 'email' || type.toLowerCase() === 'text') {" +
                "      inputs[i].value = '" + user + "';" +
                "      inputs[i].dispatchEvent(new Event('input', {bubbles: true}));" +
                "    }" +
                "  }" +
                "})();";
        wv.evaluateJavascript(js, null);
        Toast.makeText(this, "Login-Daten aus Excel eingefügt", Toast.LENGTH_SHORT).show();
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
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Anzahl (1 bis 15)");

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Buff-Sequenz aufnehmen")
                .setMessage("Wie viele Buffs möchtest du hintereinander ausführen? (1 - 15):")
                .setView(input)
                .setPositiveButton("Starten", (dialog, which) -> {
                    int count = 1;
                    try {
                        count = Integer.parseInt(input.getText().toString());
                    } catch (Exception ignored) {}
                    if (count < 1) count = 1;
                    if (count > 15) count = 15;

                    prefs.edit().putInt("c" + clientUid + "_buff_count", count).apply();
                    targetKeyToRecord[0] = "buff_step_1";
                    Toast.makeText(this, "Tippe jetzt Buff 1 von " + count + " an!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void advanceBuffRecording(int clientUid, String[] targetKeyToRecord, boolean[] isRecordingMode) {
        int total = prefs.getInt("c" + clientUid + "_buff_count", 1);
        try {
            int currentStep = Integer.parseInt(targetKeyToRecord[0].replace("buff_step_", ""));
            if (currentStep < total) {
                targetKeyToRecord[0] = "buff_step_" + (currentStep + 1);
                Toast.makeText(this, "Tippe jetzt Buff " + (currentStep + 1) + " von " + total + " an!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Alle " + total + " Buffs gespeichert!", Toast.LENGTH_LONG).show();
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

        Toast.makeText(this, "Buffe " + total + " Skills...", Toast.LENGTH_SHORT).show();
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
        loopHandler.removeCallbacksAndMessages(null);
        for (WebView w : activeWebViews) {
            if (w != null) w.destroy();
        }
    }
}
