package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private WebView mainWebView;
    private FrameLayout overlayLayer;
    private final List<WebView> activeWebViews = new ArrayList<>();

    // Kräftige Farben für C2 bis C8
    private final int[] CLIENT_COLORS = {
            Color.parseColor("#00E676"), // C2: Grün
            Color.parseColor("#00E5FF"), // C3: Cyan
            Color.parseColor("#FF007F"), // C4: Pink/Magenta
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

        mainWebView = findViewById(R.id.webView1);
        overlayLayer = findViewById(R.id.overlayLayer);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(mainWebView, true);

        configureWebView(mainWebView);
        mainWebView.loadUrl("https://universe.flyff.com/play");
        activeWebViews.add(mainWebView);

        promptSessionCount();
    }

    private void promptSessionCount() {
        final String[] options = {"1 Session (Vollbild)", "2 Sessions", "3 Sessions", "4 Sessions", "5 Sessions", "6 Sessions", "7 Sessions", "8 Sessions"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Wie viele Sessions starten?")
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

        // Haupt-Container des Overlays
        FrameLayout container = new FrameLayout(this);
        int defaultWidth = (int) (260 * density);
        int defaultHeight = (int) (180 * density);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(defaultWidth, defaultHeight);
        containerParams.leftMargin = (int) (20 * density + (index * 24 * density));
        containerParams.topMargin = (int) (40 * density + (index * 24 * density));
        container.setLayoutParams(containerParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1A1A1E"));
        bg.setCornerRadius(8 * density);
        bg.setStroke((int) (2 * density), accentColor);
        container.setBackground(bg);

        // Layout für Titelleiste + WebView
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

        // Rundes Badge für Client-Nummer
        TextView badge = new TextView(this);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams((int) (22 * density), (int) (22 * density));
        badgeParams.setMarginEnd((int) (6 * density));
        badge.setLayoutParams(badgeParams);
        badge.setText("C" + clientNumber);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setGravity(Gravity.CENTER);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(accentColor);
        badge.setBackground(badgeBg);

        // Zieh-Bereich / Titel
        TextView dragArea = new TextView(this);
        LinearLayout.LayoutParams dragParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        dragArea.setLayoutParams(dragParams);
        dragArea.setGravity(Gravity.CENTER_VERTICAL);
        dragArea.setText("Session " + clientNumber);
        dragArea.setTextColor(Color.parseColor("#CCCCCC"));
        dragArea.setTextSize(11);

        // Minimieren-Button
        Button minBtn = new Button(this);
        LinearLayout.LayoutParams minParams = new LinearLayout.LayoutParams((int) (26 * density), (int) (26 * density));
        minBtn.setLayoutParams(minParams);
        minBtn.setText("—");
        minBtn.setTextColor(Color.WHITE);
        minBtn.setTextSize(12);
        minBtn.setBackground(badgeBg);

        titleBar.addView(badge);
        titleBar.addView(dragArea);
        titleBar.addView(minBtn);

        // WebView des Overlays
        WebView webView = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(webParams);
        configureWebView(webView);
        webView.loadUrl("https://universe.flyff.com/play");
        activeWebViews.add(webView);

        contentLayout.addView(titleBar);
        contentLayout.addView(webView);
        container.addView(contentLayout);

        // Diagonales Resize-Icon unten rechts
        TextView resizeHandle = new TextView(this);
        FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams((int) (28 * density), (int) (28 * density));
        resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
        resizeHandle.setLayoutParams(resizeParams);
        resizeHandle.setText("⇲");
        resizeHandle.setTextColor(accentColor);
        resizeHandle.setTextSize(14);
        resizeHandle.setGravity(Gravity.CENTER);
        container.addView(resizeHandle);

        // Runder Mini-Button (wird nur angezeigt, wenn minimiert)
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

        // 1. Drag & Drop Logik (über die Titelleiste)
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
            }
            return false;
        };
        titleBar.setOnTouchListener(dragListener);
        dragArea.setOnTouchListener(dragListener);

        // 2. Diagonales Resizen Logik (über das Resize-Icon)
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
