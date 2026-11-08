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

    private WebView webView1;
    private WebView webView2;
    private View overlayContainer;
    private Button btnToggle;
    private Button btnFtoolMain;
    private Button btnFtoolOverlay;

    private boolean isMinimized = false;
    private float dX, dY;

    // F-Tool State & Handler
    private final Handler ftoolHandler = new Handler(Looper.getMainLooper());
    private boolean isFtool1Running = false;
    private boolean isFtool2Running = false;

    // Intervalle in Millisekunden (z. B. 1500 ms = alle 1,5 Sekunden)
    private final int INTERVAL_CLIENT_1 = 1500;
    private final int INTERVAL_CLIENT_2 = 2000;

    // Tasten, die gesendet werden (Standard: Taste '1' für Slot 1 der Actionbar)
    private final String KEY_CLIENT_1 = "1";
    private final String KEY_CLIENT_2 = "1";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);

        webView1 = findViewById(R.id.webView1);
        webView2 = findViewById(R.id.webView2);
        overlayContainer = findViewById(R.id.overlayContainer);
        btnToggle = findViewById(R.id.btnToggleSize);
        btnFtoolMain = findViewById(R.id.btnFtoolMain);
        btnFtoolOverlay = findViewById(R.id.btnFtoolOverlay);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView1, true);
        cookieManager.setAcceptThirdPartyCookies(webView2, true);

        configureWebView(webView1);
        configureWebView(webView2);

        webView1.loadUrl("https://universe.flyff.com/play");
        webView2.loadUrl("https://universe.flyff.com/play");

        // Drag & Drop des Zweit-Fensters
        overlayContainer.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX = v.getX() - event.getRawX();
                    dY = v.getY() - event.getRawY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    v.animate().x(event.getRawX() + dX).y(event.getRawY() + dY).setDuration(0).start();
                    return true;
                default:
                    return false;
            }
        });

        // Minimieren / Wiederherstellen
        btnToggle.setOnClickListener(v -> {
            ViewGroup.LayoutParams params = overlayContainer.getLayoutParams();
            float density = getResources().getDisplayMetrics().density;
            if (isMinimized) {
                params.width = (int) (320 * density);
                params.height = (int) (220 * density);
                webView2.setVisibility(View.VISIBLE);
                btnToggle.setText("—");
                isMinimized = false;
            } else {
                params.width = (int) (140 * density);
                params.height = (int) (48 * density);
                webView2.setVisibility(View.INVISIBLE); // WebView läuft unsichtbar im Hintergrund weiter
                btnToggle.setText("+");
                isMinimized = true;
            }
            overlayContainer.setLayoutParams(params);
        });

        // F-Tool Button Account 1
        btnFtoolMain.setOnClickListener(v -> {
            isFtool1Running = !isFtool1Running;
            if (isFtool1Running) {
                btnFtoolMain.setText("FTool 1: ON (1)");
                btnFtoolMain.setBackgroundColor(Color.parseColor("#8800AA00"));
                ftoolHandler.post(ftool1Runnable);
            } else {
                btnFtoolMain.setText("FTool 1: OFF");
                btnFtoolMain.setBackgroundColor(Color.parseColor("#88000000"));
                ftoolHandler.removeCallbacks(ftool1Runnable);
            }
        });

        // F-Tool Button Account 2 (z. B. Ringmaster / Buffer)
        btnFtoolOverlay.setOnClickListener(v -> {
            isFtool2Running = !isFtool2Running;
            if (isFtool2Running) {
                btnFtoolOverlay.setText("FT 2: ON");
                btnFtoolOverlay.setBackgroundColor(Color.parseColor("#8800AA00"));
                ftoolHandler.post(ftool2Runnable);
            } else {
                btnFtoolOverlay.setText("FT 2: OFF");
                btnFtoolOverlay.setBackgroundColor(Color.parseColor("#88000000"));
                ftoolHandler.removeCallbacks(ftool2Runnable);
            }
        });
    }

    // Schleife für Account 1
    private final Runnable ftool1Runnable = new Runnable() {
        @Override
        public void run() {
            if (isFtool1Running && webView1 != null) {
                pressGameKey(webView1, KEY_CLIENT_1);
                ftoolHandler.postDelayed(this, INTERVAL_CLIENT_1);
            }
        }
    };

    // Schleife für Account 2 (funktioniert auch bei minimiertem Fenster)
    private final Runnable ftool2Runnable = new Runnable() {
        @Override
        public void run() {
            if (isFtool2Running && webView2 != null) {
                pressGameKey(webView2, KEY_CLIENT_2);
                ftoolHandler.postDelayed(this, INTERVAL_CLIENT_2);
            }
        }
    };

    /**
     * Sendet ein synthetisches Tastatur-Event an den Canvas von Flyff Universe.
     */
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
        s.setMediaPlaybackRequiresUserGesture = false;
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
        if (webView1 != null) webView1.onResume();
        if (webView2 != null) webView2.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Kein Pause-Aufruf, damit Socket und F-Tool im Hintergrund aktiv bleiben
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isFtool1Running = false;
        isFtool2Running = false;
        ftoolHandler.removeCallbacksAndMessages(null);
        if (webView1 != null) webView1.destroy();
        if (webView2 != null) webView2.destroy();
    }
}
