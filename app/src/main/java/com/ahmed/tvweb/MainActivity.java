package com.ahmed.tvweb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Opens a website full-screen on Fire TV / Android TV.
 *
 * Remote control:
 *   D-pad        move the on-screen pointer (page scrolls when the pointer reaches an edge)
 *   Select (OK)  click where the pointer is (hold for a long-press)
 *   Back         go back a page; on the first page, exit
 *   Menu (≡)     options: change website, reload, home, desktop mode, pointer on/off
 *   Play/Pause   turn the pointer on/off (off = normal D-pad focus navigation)
 *   ⏪ / ⏩       scroll a page up / down
 */
public class MainActivity extends Activity {

    // ── Website opened when the app starts. ──
    static final String DEFAULT_URL = "https://translefy.net/join";

    private static final String PREFS = "tvweb";
    private static final String KEY_URL = "url";
    private static final String KEY_DESKTOP = "desktop";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private FrameLayout root;
    private WebView web;
    private PointerView pointer;
    private boolean pointerMode = true;
    private long touchDownTime;

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        setupWebView();
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pointer = new PointerView(this);
        root.addView(pointer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        hideSystemUi();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            String url = homeUrl();
            if (url.isEmpty()) askForUrl(true);
            else web.loadUrl(url);
        }
    }

    // ───────────────────────── WebView setup ─────────────────────────

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        applyDesktopMode();

        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) return false;
                // Other links (intent:, market:, mailto: …) → hand to the system if possible.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Full-screen video (e.g. the ⛶ button in a video player)
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                pointer.setVisibility(View.GONE);
                hideSystemUi();
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private void exitFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        pointer.setVisibility(pointerMode ? View.VISIBLE : View.GONE);
        hideSystemUi();
    }

    private void applyDesktopMode() {
        boolean desktop = prefs.getBoolean(KEY_DESKTOP, false);
        web.getSettings().setUserAgentString(desktop ? DESKTOP_UA : null);
    }

    private String homeUrl() {
        return prefs.getString(KEY_URL, DEFAULT_URL).trim();
    }

    // ───────────────────────── Remote control ─────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;

        // While a video is full-screen, let the page handle keys; Back leaves full-screen.
        if (fullscreenView != null) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (up) exitFullscreen();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                if (up) {
                    if (web.canGoBack()) web.goBack();
                    else finish();
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (up) showMenu();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (up) togglePointer();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) scrollPage(0, (int) (root.getHeight() * 0.8f));
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) scrollPage(0, -(int) (root.getHeight() * 0.8f));
                return true;
        }

        if (pointerMode) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) movePointer(code, e.getRepeatCount());
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down && e.getRepeatCount() == 0) sendTouch(MotionEvent.ACTION_DOWN);
                    else if (up) sendTouch(MotionEvent.ACTION_UP);
                    return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void movePointer(int code, int repeat) {
        float d = getResources().getDisplayMetrics().density;
        float step = (10 + Math.min(repeat, 25) * 2.5f) * d;   // accelerates while held
        float dx = 0, dy = 0;
        if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -step;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = step;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -step;
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = step;

        float w = root.getWidth(), h = root.getHeight();
        float margin = 6 * d;
        float nx = pointer.x + dx, ny = pointer.y + dy;
        int sx = 0, sy = 0;
        if (nx < margin) { sx = (int) (nx - margin); nx = margin; }
        if (nx > w - margin) { sx = (int) (nx - (w - margin)); nx = w - margin; }
        if (ny < margin) { sy = (int) (ny - margin); ny = margin; }
        if (ny > h - margin) { sy = (int) (ny - (h - margin)); ny = h - margin; }
        pointer.moveTo(nx, ny);
        if (sx != 0 || sy != 0) scrollPage(sx, sy);
    }

    /** Scrolls the scrollable element under the pointer (or the whole page). Values in screen px. */
    private void scrollPage(int dxPx, int dyPx) {
        float scale = web.getScale();
        if (scale <= 0) scale = getResources().getDisplayMetrics().density;
        int x = (int) (pointer.x / scale), y = (int) (pointer.y / scale);
        int dx = (int) (dxPx / scale), dy = (int) (dyPx / scale);
        String js = "(function(x,y,dx,dy){"
                + "var el=document.elementFromPoint(x,y);"
                + "while(el&&el!==document.body&&el!==document.documentElement){"
                + "var s=getComputedStyle(el);"
                + "if((dy&&el.scrollHeight>el.clientHeight&&/(auto|scroll)/.test(s.overflowY))||"
                + "(dx&&el.scrollWidth>el.clientWidth&&/(auto|scroll)/.test(s.overflowX))){"
                + "el.scrollTop+=dy;el.scrollLeft+=dx;return;}"
                + "el=el.parentElement;}"
                + "window.scrollBy(dx,dy);"
                + "})(" + x + "," + y + "," + dx + "," + dy + ")";
        if (Build.VERSION.SDK_INT >= 19) web.evaluateJavascript(js, null);
        else web.scrollBy(dxPx, dyPx);
    }

    private void sendTouch(int action) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownTime = now;
            web.requestFocus();
        }
        MotionEvent ev = MotionEvent.obtain(touchDownTime, now, action, pointer.x, pointer.y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
        pointer.setPressed(action == MotionEvent.ACTION_DOWN);
    }

    private void togglePointer() {
        pointerMode = !pointerMode;
        pointer.setVisibility(pointerMode ? View.VISIBLE : View.GONE);
        if (!pointerMode) web.requestFocus();
        Toast.makeText(this, pointerMode ? "Pointer on" : "Pointer off (D-pad moves focus)",
                Toast.LENGTH_SHORT).show();
    }

    // ───────────────────────── Menu & settings ─────────────────────────

    private void showMenu() {
        final boolean desktop = prefs.getBoolean(KEY_DESKTOP, false);
        String[] items = {
                "Change website",
                "Reload",
                "Go to home page",
                desktop ? "Use TV/mobile site" : "Use desktop site",
                pointerMode ? "Turn pointer off" : "Turn pointer on",
                "Exit"
        };
        new AlertDialog.Builder(this)
                .setTitle(web.getUrl() != null ? web.getUrl() : getString(R.string.app_name))
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: askForUrl(false); break;
                        case 1: web.reload(); break;
                        case 2: {
                            String u = homeUrl();
                            if (u.isEmpty()) askForUrl(false); else web.loadUrl(u);
                            break;
                        }
                        case 3:
                            prefs.edit().putBoolean(KEY_DESKTOP, !desktop).apply();
                            applyDesktopMode();
                            web.reload();
                            break;
                        case 4: togglePointer(); break;
                        case 5: finish(); break;
                    }
                })
                .show();
    }

    private void askForUrl(final boolean firstRun) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("example.com");
        input.setText(homeUrl());
        input.setSelectAllOnFocus(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Website address")
                .setView(box)
                .setPositiveButton("Open", (d, w) -> {
                    String url = normalize(input.getText().toString());
                    if (url.isEmpty()) {
                        if (firstRun) askForUrl(true);
                        return;
                    }
                    prefs.edit().putString(KEY_URL, url).apply();
                    web.loadUrl(url);
                })
                .setCancelable(!firstRun);
        if (!firstRun) b.setNegativeButton("Cancel", null);
        else b.setNegativeButton("Exit", (d, w) -> finish());
        b.show();
        input.requestFocus();
    }

    private static String normalize(String s) {
        s = s.trim();
        if (s.isEmpty()) return s;
        if (!s.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) s = "https://" + s;
        return s;
    }

    // ───────────────────────── Lifecycle ─────────────────────────

    private void hideSystemUi() {
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        root.removeAllViews();
        web.destroy();
        super.onDestroy();
    }

    // ───────────────────────── On-screen pointer ─────────────────────────

    static class PointerView extends View {
        float x = -1, y = -1;
        private boolean pressed;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final float d;

        PointerView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.5f * d);
            stroke.setColor(Color.BLACK);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            setFocusable(false);
            setClickable(false);
        }

        void moveTo(float nx, float ny) {
            x = nx;
            y = ny;
            invalidate();
        }

        @Override
        public void setPressed(boolean p) {
            pressed = p;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (x < 0) { x = w / 2f; y = h / 2f; }
            else { x = Math.min(x, w); y = Math.min(y, h); }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false; // never block real touches
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = 1.6f * d;  // arrow size on a TV
            arrow.reset();
            arrow.moveTo(x, y);
            arrow.lineTo(x, y + 17 * s);
            arrow.lineTo(x + 4.5f * s, y + 13 * s);
            arrow.lineTo(x + 7.5f * s, y + 19.5f * s);
            arrow.lineTo(x + 10 * s, y + 18.3f * s);
            arrow.lineTo(x + 7 * s, y + 12 * s);
            arrow.lineTo(x + 12.5f * s, y + 12 * s);
            arrow.close();
            fill.setColor(pressed ? Color.rgb(255, 193, 7) : Color.WHITE);
            c.drawPath(arrow, fill);
            c.drawPath(arrow, stroke);
        }
    }
}
