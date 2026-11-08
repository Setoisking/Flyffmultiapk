package com.flyff.multiclient;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private boolean isMinimized = false;
    private float dX, dY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        webView1 = (WebView) findViewById(R.id.webView1);
        webView2 = (WebView) findViewById(R.id.webView2);
        overlayContainer = findViewById(R.id.overlayContainer);
        btnToggle = (Button) findViewById(R.id.btnToggleSize);

        setup(webView1);
        setup(webView2);

        webView1.loadUrl("https://universe.flyff.com/play");
        webView2.loadUrl("https://universe.flyff.com/play");

        overlayContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
            }
        });

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewGroup.LayoutParams params = overlayContainer.getLayoutParams();
                float density = getResources().getDisplayMetrics().density;
                if (isMinimized) {
                    params.width = (int) (320 * density);
                    params.height = (int) (220 * density);
                    webView2.setVisibility(View.VISIBLE);
                    btnToggle.setText("—");
                    isMinimized = false;
                } else {
                    params.width = (int) (48 * density);
                    params.height = (int) (48 * density);
                    webView2.setVisibility(View.INVISIBLE);
                    btnToggle.setText("+");
                    isMinimized = true;
                }
                overlayContainer.setLayoutParams(params);
            }
        });
    }

    private void setup(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        w.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView1 != null) webView1.destroy();
        if (webView2 != null) webView2.destroy();
    }
}
