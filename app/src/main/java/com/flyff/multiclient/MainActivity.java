package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private FrameLayout rootContainer;
    private FrameLayout overlayLayer;
    private final List<WebView> activeWebViews = new ArrayList<>();
    private final List<FrameLayout> overlayContainers = new ArrayList<>();

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

        // Header und TitleBar komplett abschalten
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);
        hideSystemUI();

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

    private void addGridPresetButton() {
        float density = getResources().getDisplayMetrics().density;
        Button gridBtn = new Button(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int) (64 * density), (int) (28 * density));
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = (int) (8 * density);
        params.topMargin = (int) (8 * density);
        gridBtn.setLayoutParams(params);
        gridBtn.setText("⊞ Grid");
        gridBtn.setTextColor(Color.WHITE);
        gridBtn.setTextSize(10);
        gridBtn.setBackgroundColor(Color.parseColor("#66000000"));
        gridBtn.setOnClickListener(v -> applyGridLayout());
        rootContainer.addView(gridBtn);
    }

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

        // Haupt-Container
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

        // WebView füllt das komplette Overlay aus (kein Header)
        WebView webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(webParams);
        configureWebView(webView);
        webView.loadUrl("https://universe.flyff.com/play");
        setClientAudioMuted(webView, true);
        activeWebViews.add(webView);
        container.addView(webView);

        // Rundes Badge oben links (dient als Drag-Handle, Klick = Minimieren, Langklick = Swap)
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

        // Resize-Handle unten rechts
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

        // Drag & Drop über das runde Badge
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

                    // Klick (unter 250ms): Minimieren / Wiederherstellen
                    if (duration < 250) {
                        if (!isMinimized[0]) {
                            // Minimieren auf runden Kreis
                            savedWidth[0] = container.getWidth();
                            savedHeight[0] = container.getHeight();
                            webView.setVisibility(View.GONE);
                            resizeHandle.setVisibility(View.GONE);
                            container.setBackground(null);

                            ViewGroup.LayoutParams p = container.getLayoutParams();
                            p.width = (int) (36 * density);
                            p.height = (int) (36 * density);
                            container.setLayoutParams(p);
                            isMinimized[0] = true;
                            setClientLowSpecMode(webView, true);
                        } else {
                            // Wiederherstellen
                            webView.setVisibility(View.VISIBLE);
                            resizeHandle.setVisibility(View.VISIBLE);
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

                    // Langer Druck (über 700ms): Quick-Swap mit Vollbild-Main
                    if (duration >= 700) {
                        toggleSwapClient(overlayIndex);
                        return true;
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return false;
        });

        // Resize-Logik
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

    private void toggleSwapClient(int overlayIndex) {
        WebView mainView = activeWebViews.get(0);
        WebView targetView = activeWebViews.get(overlayIndex + 1);

        String urlMain = mainView.getUrl();
        String urlTarget = targetView.getUrl();

        if (activeSwapIndex == overlayIndex) {
            Toast.makeText(this, "Zurück zum Hauptfenster", Toast.LENGTH_SHORT).show();
            activeSwapIndex = -1;
        } else {
            Toast.makeText(this, "Session mit Vollbild getauscht!", Toast.LENGTH_SHORT).show();
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
            p.width = tileW - 8;
            p.height = tileH - 8;
            c.setLayoutParams(p);
            c.setX(col * tileW + 4);
            c.setY(row * tileH + 4);
        }
        Toast.makeText(this, "Grid angewendet!", Toast.LENGTH_SHORT).show();
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
