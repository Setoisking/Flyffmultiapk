package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private FrameLayout rootContainer;
    private FrameLayout overlayLayer;
    private final List<WebView> activeWebViews = new ArrayList<>();
    private final List<FrameLayout> overlayContainers = new ArrayList<>();
    private SharedPreferences prefs;

    private int activeSwapIndex = -1;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);

        prefs = getSharedPreferences("NeuzOS_Config", MODE_PRIVATE);
        rootContainer = findViewById(R.id.rootContainer);
        overlayLayer = findViewById(R.id.overlayLayer);

        WebView mainWebView = findViewById(R.id.webView1);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(mainWebView, true);

        configureWebView(mainWebView);
        mainWebView.loadUrl("https://universe.flyff.com/play");
        activeWebViews.add(mainWebView);

        addGridPresetButton();
        promptSessionCount();
    }

    private void addGridPresetButton() {
        float density = getResources().getDisplayMetrics().density;
        Button gridBtn = new Button(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int) (70 * density), (int) (32 * density));
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = (int) (10 * density);
        params.topMargin = (int) (10 * density);
        gridBtn.setLayoutParams(params);
        gridBtn.setText("⊞ Grid");
        gridBtn.setTextColor(Color.WHITE);
        gridBtn.setTextSize(11);
        gridBtn.setBackgroundColor(Color.parseColor("#88111115"));
        gridBtn.setOnClickListener(v -> applyGridLayout());
        rootContainer.addView(gridBtn);
    }

    private void promptSessionCount() {
        final String[] options = {
                "1 Session (Vollbild)", "2 Sessions", "3 Sessions", "4 Sessions",
                "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("NeuzOS Multi-Client: Sessions wählen")
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
        int defaultWidth = (int) (260 * density);
        int defaultHeight = (int) (180 * density);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(defaultWidth, defaultHeight);
        containerParams.leftMargin = (int) (15 * density + (index * 20 * density));
        containerParams.topMargin = (int) (50 * density + (index * 20 * density));
        container.setLayoutParams(containerParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1A1A1E"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int) (2 * density), accentColor);
        container.setBackground(bg);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Titelleiste
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (32 * density)));
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(Color.parseColor("#282830"));
        titleBar.setPadding((int) (6 * density), 0, (int) (4 * density), 0);

        // Badge C2..C8
        TextView badge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams((int) (22 * density), (int) (22 * density));
        badgeParams.setMarginEnd((int) (4 * density));
        badge.setLayoutParams(badgeParams);
        badge.setText("C" + clientNumber);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accentColor);
        badge.setBackground(badgeBg);

        // Drag-Bereich
        TextView dragArea = new TextView(this);
        LinearLayout.LayoutParams dragParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        dragArea.setLayoutParams(dragParams);
        dragArea.setGravity(Gravity.CENTER_VERTICAL);
        dragArea.setText("S" + clientNumber);
        dragArea.setTextColor(Color.parseColor("#CCCCCC"));
        dragArea.setTextSize(11);

        // Quick-Login Button
        Button btnLogin = createBarButton("🔑", (int) (24 * density), density);
        btnLogin.setOnClickListener(v -> handleQuickLogin(clientNumber, overlayIndex + 1));

        // Audio Mute Toggle
        final boolean[] isMuted = {true};
        Button btnMute = createBarButton("🔇", (int) (24 * density), density);
        btnMute.setOnClickListener(v -> {
            isMuted[0] = !isMuted[0];
            setClientAudioMuted(activeWebViews.get(overlayIndex + 1), isMuted[0]);
            btnMute.setText(isMuted[0] ? "🔇" : "🔊");
        });

        // Quick-Swap Button
        Button btnSwap = createBarButton("⇄", (int) (24 * density), density);
        btnSwap.setOnClickListener(v -> toggleSwapClient(overlayIndex));

        // Minimieren-Button
        Button minBtn = createBarButton("—", (int) (24 * density), density);

        titleBar.addView(badge);
        titleBar.addView(dragArea);
        titleBar.addView(btnLogin);
        titleBar.addView(btnMute);
        titleBar.addView(btnSwap);
        titleBar.addView(minBtn);

        // WebView
        WebView webView = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(webParams);
        configureWebView(webView);
        webView.loadUrl("https://universe.flyff.com/play");
        setClientAudioMuted(webView, true);
        activeWebViews.add(webView);

        contentLayout.addView(titleBar);
        contentLayout.addView(webView);
        container.addView(contentLayout);

        // Diagonales Resize Handle
        TextView resizeHandle = new TextView(this);
        FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams((int) (28 * density), (int) (28 * density));
        resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
        resizeHandle.setLayoutParams(resizeParams);
        resizeHandle.setText("⇲");
        resizeHandle.setTextColor(accentColor);
        resizeHandle.setTextSize(14);
        resizeHandle.setGravity(Gravity.CENTER);
        container.addView(resizeHandle);

        // Runder Mini-Button bei Minimierung
        Button miniBubble = new Button(this);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams((int) (38 * density), (int) (38 * density));
        miniBubble.setLayoutParams(bubbleParams);
        miniBubble.setText("C" + clientNumber);
        miniBubble.setTextColor(Color.WHITE);
        miniBubble.setTextSize(11);
        miniBubble.setBackground(badgeBg);
        miniBubble.setVisibility(View.GONE);
        container.addView(miniBubble);

        overlayLayer.addView(container);
        overlayContainers.add(container);

        // 1. Drag & Drop Logik
        final float[] dX = {0};
        final float[] dY = {0};
        View.OnTouchListener dragListener = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    container.setX(event.getRawX() + dX[0]);
                    container.setY(event.getRawY() + dY[0]);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        };
        titleBar.setOnTouchListener(dragListener);
        dragArea.setOnTouchListener(dragListener);

        // 2. Diagonales Resize
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
                    int newH = (int) Math.max(120 * density, startH[0] + deltaY);
                    ViewGroup.LayoutParams p = container.getLayoutParams();
                    p.width = newW;
                    p.height = newH;
                    container.setLayoutParams(p);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });

        // 3. Minimieren & Wiederherstellen
        final int[] savedWidth = {defaultWidth};
        final int[] savedHeight = {defaultHeight};

        minBtn.setOnClickListener(v -> {
            savedWidth[0] = container.getWidth();
            savedHeight[0] = container.getHeight();

            contentLayout.setVisibility(View.GONE);
            resizeHandle.setVisibility(View.GONE);
            container.setBackground(null);
            miniBubble.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = (int) (38 * density);
            p.height = (int) (38 * density);
            container.setLayoutParams(p);

            setClientLowSpecMode(webView, true);
        });

        miniBubble.setOnClickListener(v -> {
            miniBubble.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
            resizeHandle.setVisibility(View.VISIBLE);
            container.setBackground(bg);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = savedWidth[0];
            p.height = savedHeight[0];
            container.setLayoutParams(p);

            setClientLowSpecMode(webView, false);
        });

        miniBubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() + dX[0] - container.getX()) > 5 ||
                            Math.abs(event.getRawY() + dY[0] - container.getY()) > 5) {
                        container.setX(event.getRawX() + dX[0]);
                        container.setY(event.getRawY() + dY[0]);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    private Button createBarButton(String text, int sizePx, float density) {
        Button b = new Button(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(sizePx, (int) (26 * density));
        p.setMarginStart((int) (2 * density));
        b.setLayoutParams(p);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.parseColor("#444450"));
        return b;
    }

    private void toggleSwapClient(int overlayIndex) {
        WebView mainView = activeWebViews.get(0);
        WebView targetView = activeWebViews.get(overlayIndex + 1);

        String urlMain = mainView.getUrl();
        String urlTarget = targetView.getUrl();

        if (activeSwapIndex == overlayIndex) {
            Toast.makeText(this, "Zurück zum Hauptfenster gewechselt", Toast.LENGTH_SHORT).show();
            activeSwapIndex = -1;
        } else {
            Toast.makeText(this, "Session getauscht mit Vollbild!", Toast.LENGTH_SHORT).show();
            activeSwapIndex = overlayIndex;
        }

        mainView.loadUrl(urlTarget != null ? urlTarget : "https://universe.flyff.com/play");
        targetView.loadUrl(urlMain != null ? urlMain : "https://universe.flyff.com/play");
    }

    private void applyGridLayout() {
        int count = overlayContainers.size();
        if (count == 0) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int cols = count >= 3 ? 2 : 1;
        int rows = (int) Math.ceil((double) count / cols);
        int tileW = screenW / cols;
        int tileH = screenH / rows;

        for (int i = 0; i < count; i++) {
            FrameLayout c = overlayContainers.get(i);
            int col = i % cols;
            int row = i / cols;

            ViewGroup.LayoutParams p = c.getLayoutParams();
            p.width = tileW - 10;
            p.height = tileH - 10;
            c.setLayoutParams(p);
            c.setX(col * tileW + 5);
            c.setY(row * tileH + 5);
        }
        Toast.makeText(this, "Grid-Preset angewendet!", Toast.LENGTH_SHORT).show();
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

    private void handleQuickLogin(int clientNum, int webViewIndex) {
        String keyUser = "user_c" + clientNum;
        String keyPass = "pass_c" + clientNum;
        String savedUser = prefs.getString(keyUser, "");
        String savedPass = prefs.getString(keyPass, "");

        if (savedUser.isEmpty() || savedPass.isEmpty()) {
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(40, 20, 40, 10);

            final EditText editUser = new EditText(this);
            editUser.setHint("Account / E-Mail für C" + clientNum);
            final EditText editPass = new EditText(this);
            editPass.setHint("Passwort");
            l.addView(editUser);
            l.addView(editPass);

            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Zugangsdaten speichern (C" + clientNum + ")")
                    .setView(l)
                    .setPositiveButton("Speichern & Einloggen", (d, w) -> {
                        String u = editUser.getText().toString().trim();
                        String p = editPass.getText().toString().trim();
                        prefs.edit().putString(keyUser, u).putString(keyPass, p).apply();
                        injectLogin(activeWebViews.get(webViewIndex), u, p);
                    })
                    .setNegativeButton("Abbrechen", null)
                    .show();
        } else {
            injectLogin(activeWebViews.get(webViewIndex), savedUser, savedPass);
            Toast.makeText(this, "Logindaten für C" + clientNum + " übermittelt", Toast.LENGTH_SHORT).show();
        }
    }

    private void injectLogin(WebView wv, String username, String password) {
        if (wv == null) return;
        String js = "(function() {" +
                "  var userInputs = document.querySelectorAll('input[type=email], input[type=text]');" +
                "  var passInputs = document.querySelectorAll('input[type=password]');" +
                "  if(userInputs.length > 0) { userInputs[0].value = '" + username + "'; userInputs[0].dispatchEvent(new Event('input', { bubbles: true })); }" +
                "  if(passInputs.l         case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    container.setX(event.getRawX() + dX[0]);
                    container.setY(event.getRawY() + dY[0]);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        };
        titleBar.setOnTouchListener(dragListener);
        dragArea.setOnTouchListener(dragListener);

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
                    int newW = (int) Math.max(160 * density, startW[0] + deltaX);
                    int newH = (int) Math.max(120 * density, startH[0] + deltaY);
                    ViewGroup.LayoutParams p = container.getLayoutParams();
                    p.width = newW;
                    p.height = newH;
                    container.setLayoutParams(p);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });

        // Minimieren (inklusive FPS-Drosselung)
        final int[] savedWidth = {defaultWidth};
        final int[] savedHeight = {defaultHeight};

        minBtn.setOnClickListener(v -> {
            savedWidth[0] = container.getWidth();
            savedHeight[0] = container.getHeight();

            contentLayout.setVisibility(View.GONE);
            resizeHandle.setVisibility(View.GONE);
            container.setBackground(null);
            miniBubble.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = (int) (38 * density);
            p.height = (int) (38 * density);
            container.setLayoutParams(p);

            // FPS drosseln zum Energiesparen
            setClientLowSpecMode(webView, true);
        });

        // Wiederherstellen (volles 60 FPS Rendering)
        miniBubble.setOnClickListener(v -> {
            miniBubble.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
            resizeHandle.setVisibility(View.VISIBLE);
            container.setBackground(bg);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = savedWidth[0];
            p.height = savedHeight[0];
            container.setLayoutParams(p);

            setClientLowSpecMode(webView, false);
        });

        miniBubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() + dX[0] - container.getX()) > 5 ||
                            Math.abs(event.getRawY() + dY[0] - container.getY()) > 5) {
                        container.setX(event.getRawX() + dX[0]);
                        container.setY(event.getRawY() + dY[0]);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    private Button createBarButton(String text, int sizePx, float density) {
        Button b = new Button(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(sizePx, (int) (26 * density));
        p.setMarginStart((int) (2 * density));
        b.setLayoutParams(p);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.parseColor("#444450"));
        return b;
    }

    // Quick-Swap Logik
    private void toggleSwapClient(int overlayIndex) {
        WebView mainView = activeWebViews.get(0);
        WebView targetView = activeWebViews.get(overlayIndex + 1);

        String urlMain = mainView.getUrl();
        String urlTarget = targetView.getUrl();

        if (activeSwapIndex == overlayIndex) {
            // Zurücktauschen
            Toast.makeText(this, "Zurück zum Hauptfenster gewechselt", Toast.LENGTH_SHORT).show();
            activeSwapIndex = -1;
        } else {
            Toast.makeText(this, "Session getauscht mit Vollbild!", Toast.LENGTH_SHORT).show();
            activeSwapIndex = overlayIndex;
        }

        // URLs und Session-Zustände synchron übergeben
        mainView.loadUrl(urlTarget != null ? urlTarget : "https://universe.flyff.com/play");
        targetView.loadUrl(urlMain != null ? urlMain : "https://universe.flyff.com/play");
    }

    // Grid-Preset Layout (Kacheln)
    private void applyGridLayout() {
        int count = overlayContainers.size();
        if (count == 0) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int cols = count >= 3 ? 2 : 1;
        int rows = (int) Math.ceil((double) count / cols);
        int tileW = screenW / cols;
        int tileH = screenH / rows;

        for (int i = 0; i < count; i++) {
            FrameLayout c = overlayContainers.get(i);
            int col = i % cols;
            int row = i / cols;

            ViewGroup.LayoutParams p = c.getLayoutParams();
            p.width = tileW - 10;
            p.height = tileH - 10;
            c.setLayoutParams(p);
            c.setX(col * tileW + 5);
            c.setY(row * tileH + 5);
        }
        Toast.makeText(this, "Grid-Preset angewendet!", Toast.LENGTH_SHORT).show();
    }

    // NeuzOS Low-Spec & FPS Limiter
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

    // Audio-Mute Injection
    private void setClientAudioMuted(WebView wv, boolean mute) {
        if (wv == null) return;
        String js = "(function() {" +
                "  document.querySelectorAll('audio, video').forEach(function(el) {" +
                "    el.muted = " + (mute ? "true" : "false") + ";" +
                "  });" +
                "})();";
        wv.evaluateJavascript(js, null);
    }

    // Quick-Login Manager
    private void handleQuickLogin(int clientNum, int webViewIndex) {
        String keyUser = "user_c" + clientNum;
        String keyPass = "pass_c" + clientNum;
        String savedUser = prefs.getString(keyUser, "");
        String savedPass = prefs.getString(keyPass, "");

        if (savedUser.isEmpty() || savedPass.isEmpty()) {
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(40, 20, 40, 10);

            final EditText editUser = new EditText(this);
            editUser.setHint("Account / E-Mail für C" + clientNum);
            final EditText editPass = new EditText(this);
            editPass.setHint("Passwort");
            l.addView(editUser);
            l.addView(editPass);

            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Zugangsdaten speichern (C" + clientNum + ")")
                    .setView(l)
                    .setPositiveButton("Speichern & Einloggen", (d, w) -> {
                        String u = editUser.getText().toString().trim();
                        String p = editPass.getText().toString().trim();
                        prefs.edit().putString(keyUser, u).putString(keyPass, p).apply();
                        injectLogin(activeWebViews.get(webViewIndex), u, p);
                    })
                    .setNegativeButton("Abbrechen", null)
                    .show();
        } else {
            injectLogin(activeWebViews.get(webViewIndex), savedUser, savedPass);
            Toast.makeText(this, "Logindaten für C" + clientNum + " übermittelt", Toast.LENGTH_SHORT).show();
        }
    }

    private void injectLogin(WebView wv, String username, String password) {
                 int newW = (int) Math.max(160 * density, startW[0] + deltaX);
                    int newH = (int) Math.max(120 * density, startH[0] + deltaY);

                    ViewGroup.LayoutParams p = container.getLayoutParams();
                    p.width = newW;
                    p.height = newH;
                    container.setLayoutParams(p);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });

        // 3. Minimieren & Wiederherstellen
        final int[] savedWidth = {defaultWidth};
        final int[] savedHeight = {defaultHeight};

        minBtn.setOnClickListener(v -> {
            savedWidth[0] = container.getWidth();
            savedHeight[0] = container.getHeight();

            contentLayout.setVisibility(View.GONE);
            resizeHandle.setVisibility(View.GONE);
            container.setBackground(null);
            miniBubble.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = (int) (38 * density);
            p.height = (int) (38 * density);
            container.setLayoutParams(p);
        });

        miniBubble.setOnClickListener(v -> {
            miniBubble.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
            resizeHandle.setVisibility(View.VISIBLE);
            container.setBackground(bg);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = savedWidth[0];
            p.height = savedHeight[0];
            container.setLayoutParams(p);
        });

        miniBubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() + dX[0] - container.getX()) > 5 ||
                            Math.abs(event.getRawY() + dY[0] - container.getY()) > 5) {
                        container.setX(event.getRawX() + dX[0]);
                        container.setY(event.getRawY() + dY[0]);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
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
density, startH[0] + deltaY);

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
        };

        // 3. Minimieren & Wiederherstellen
        final int[] savedWidth = {defaultWidth};
        final int[] savedHeight = {defaultHeight};

        minBtn.setOnClickListener(v -> {
            savedWidth[0] = container.getWidth();
            savedHeight[0] = container.getHeight();

            contentLayout.setVisibility(View.GONE);
            resizeHandle.setVisibility(View.GONE);
            container.setBackground(null); // transparenter Hintergrund
            miniBubble.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = (int) (38 * density);
            p.height = (int) (38 * density);
            container.setLayoutParams(p);
        });

        // Klick auf den kleinen Kreis stellt das Fenster wieder her
        miniBubble.setOnClickListener(v -> {
            miniBubble.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
            resizeHandle.setVisibility(View.VISIBLE);
            container.setBackground(bg);

            ViewGroup.LayoutParams p = container.getLayoutParams();
            p.width = savedWidth[0];
            p.height = savedHeight[0];
            container.setLayoutParams(p);
        });

        // Auch der Mini-Kreis lässt sich frei verschieben
        miniBubble.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX[0] = container.getX() - event.getRawX();
                    dY[0] = container.getY() - event.getRawY();
                    return false; // Erlaubt auch den Klick-Trigger
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() + dX[0] - container.getX()) > 5 ||
                            Math.abs(event.getRawY() + dY[0] - container.getY()) > 5) {
                        container.setX(event.getRawX() + dX[0]);
                        container.setY(event.getRawY() + dY[0]);
                        return true;
                    }
                    return false;
            }
            return false;
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
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
