package com.flyff.multiclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

public class MainActivity extends Activity {

    private WebView[] webViews = new WebView[4];
    private View[] containers = new View[3];
    private View[] dragHandles = new View[3];
    private Button[] toggleButtons = new Button[3];
    private Button[] ftoolButtons = new Button[4];

    private boolean[] isMinimized = new boolean[3];
    private float[] dX = new float[3];
    private float[] dY = new float[3];

    // F-Tool Status & Taktung
    private final Handler ftoolHandler = new Handler(Looper.getMainLooper());
    private boolean[] isFtoolRunning = new boolean[4];
    private final int[] INTERVALS = {1500, 2000, 2500, 3000};
    private final String[] KEYS = {"1", "1", "1", "1"};

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);

        // WebViews initialisieren
        webViews[0] = findViewById(R.id.webView1);
        webViews[1] = findViewById(R.id.webView2);
        webViews[2] = findViewById(R.id.webView3);
        webViews[3] = findViewById(R.id.webView4);

        // Overlay Container (Clients 2, 3, 4)
        containers[0] = findViewById(R.id.overlayContainer2);
        containers[1] = findViewById(R.id.overlayContainer3);
        containers[2] = findViewById(R.id.overlayContainer4);

        dragHandles[0] = findViewById(R.id.dragHandle2);
        dragHandles[1] = findViewById(R.id.dragHandle3);
        dragHandles[2] = findViewById(R.id.dragHandle4);

        toggleButtons[0] = findViewById(R.id.btnToggle2);
        toggleButtons[1] = findViewById(R.id.btnToggle3);
        toggleButtons[2] = findViewById(R.id.btnToggle4);

        ftoolButtons[0] = findViewById(R.id.btnFtool1);
        ftoolButtons[1] = findViewById(R.id.btnFtool2);
        ftoolButtons[2] = findViewById(R.id.btnFtool3);
        ftoolButtons[3] = findViewById(R.id.btnFtool4);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        for (int i = 0; i < 4; i++) {
            cookieManager.setAcceptThirdPartyCookies(webViews[i], true);
            configureWebView(webViews[i]);
            webViews[i].loadUrl("https://universe.flyff.com/play");

            final int clientIndex = i;
            ftoolButtons[i].setOnClickListener(v -> toggleFtool(clientIndex));
        }

        // Drag & Minimize für Overlays 2, 3 und 4 registrieren
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            final View container = containers[idx];
            final View handle = dragHandles[idx];
            final Button toggleBtn = toggleButtons[idx];
            final WebView clientView = webViews[idx + 1];

            handle.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX[idx] = container.getX() - event.getRawX();
                        dY[idx] = container.getY() - event.getRawY();
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        container.animate()
                                .x(event.getRawX() + dX[idx])
                                .y(event.getRawY() + dY[idx])
                                .setDuration(0)
                                .start();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:
                        return false;
                }
            });

            toggleBtn.setOnClickListener(v -> {
                ViewGroup.LayoutParams params = container.getLayoutParams();
                float density = getResources().getDisplayMetrics().density;
                if (isMinimized[idx]) {
                    params.width = (int) (280 * density);
                    params.height = (int) (190 * density);
                    clientView.setVisibility(View.VISIBLE);
                    toggleBtn.setText("—");
                    isMinimized[idx] = false;
                } else {
                    params.width = (int) (160 * density);
                    params.height = (int) (36 * density);
                    clientView.setVisibility(View.GONE);
                    toggleBtn.setText("+");
                    isMinimized[idx] = true;
                }
                container.setLayoutParams(params);
            });
        }
    }

    private void toggleFtool(int index) {
        isFtoolRunning[index] = !isFtoolRunning[index];
        Button btn = ftoolButtons[index];
        if (isFtoolRunning[index]) {
            btn.setText("FT " + (index + 1) + ": ON");
            btn.setBackgroundColor(Color.parseColor("#8800AA00"));
            scheduleKey(index);
        } else {
            btn.setText("FT " + (index + 1) + ": OFF");
            btn.setBackgroundColor(Color.parseColor("#555566"));
        }
    }

    private void scheduleKey(int index) {
        if (!isFtoolRunning[index] || webViews[index] == null) return;
        pressGameKey(webViews[index], KEYS[index]);
        ftoolHandler.postDelayed(() -> scheduleKey(index), INTERVALS[index]);
    }

    private void pressGameKey(WebView targetWebView, String key) {
        String js = "(function() {" +
                "  var target = document.querySelector('canvas') || window;" +
                "  var eventDown = new KeyboardEvent('keydown', {" +
                "    key: '" + key + "'," +
                "    code: 'Digit" + key + "'," +
                "    which: " + (int) key.charAt(0) + "," +
                "    keyCode: " + (int) key.charAt(0) + "," +
                "    bubbles: true," +
                "    cancelable: true" +
                "  });" +
                "  target.dispatchEvent(eventDown);" +
                "  setTimeout(function() {" +
                "    var eventUp = new KeyboardEvent('keyup', {" +
                "      key: '" + key + "'," +
                "      code: 'Digit" + key + "'," +
                "      which: " + (int) key.charAt(0) + "," +
                "      keyCode: " + (int) key.charAt(0) + "," +
                "      bubbles: true," +
                "      cancelable: true" +
                "    });" +
                "    target.dispatchEvent(eventUp);" +
                "  }, 60);" +
                "})();";

        targetWebView.evaluateJavascript(js, null);
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
        for (WebView w : webViews) {
            if (w != null) w.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Kein Pause-Aufruf, damit WebSocket-Verbindungen aktiv bleiben
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ftoolHandler.removeCallbacksAndMessages(null);
        for (WebView w : webViews) {
            if (w != null) w.destroy();
        }
    }
}
