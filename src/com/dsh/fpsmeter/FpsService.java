package com.dsh.fpsmeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.PowerManager;
import java.io.FileWriter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FpsService extends Service {

    private static final String DIR = "/data/data/com.dsh.fpsmeter/files/";
    private static final String CH = "fps_overlay";

    private static volatile boolean running = false;
    public static boolean isRunning() { return running; }

    private WindowManager wm;
    private WindowManager.LayoutParams lpBar, lpContent;
    private LinearLayout bar, container;
    private TextView handle, hideBtn, closeBtn, tv;
    private MaxScroll scroll;
    private Handler ui;
    private SharedPreferences sp;
    private Thread ticker;
    private volatile float lastTemp = -1000f;
    private BroadcastReceiver screenRecv;

    private void writeScreenState(boolean on) {
        try {
            FileWriter fw = new FileWriter(new java.io.File(getFilesDir(), "screen_state"));
            fw.write(on ? "1" : "0");
            fw.close();
        } catch (Throwable ignored) {}
    }

    private void registerScreenState() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            writeScreenState(pm == null || pm.isInteractive());
        } catch (Throwable ignored) {}
        try {
            screenRecv = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    String a = i == null ? "" : i.getAction();
                    if (Intent.ACTION_SCREEN_ON.equals(a)) writeScreenState(true);
                    else if (Intent.ACTION_SCREEN_OFF.equals(a)) writeScreenState(false);
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenRecv, f);
        } catch (Throwable ignored) {}
    }

    private int lastAlpha = -1, lastFlags = -1, lastScale = -1;
    private boolean canDrag = false;
    private volatile boolean dragging = false;
    private long lastMoveTs = 0;
    private int lastDay = -1;

    static class MaxScroll extends ScrollView {
        int maxH;
        MaxScroll(Context c, int maxH) { super(c); this.maxH = maxH; }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            int mh = Math.min(getMeasuredHeight(), maxH);
            setMeasuredDimension(getMeasuredWidth(), mh);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ui = new Handler(Looper.getMainLooper());
        sp = getSharedPreferences("pos", MODE_PRIVATE);
        running = true;
        startForegroundSafe();
        registerScreenState();
        showOverlay();
        startTicker();
    }

    private void startForegroundSafe() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(CH, "FPS 悬浮窗", NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
            startForeground(1001, new Notification.Builder(this, CH)
                    .setContentTitle("FPS 悬浮窗").setContentText("实时帧率 / 温度")
                    .setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build());
        } catch (Throwable ignored) {}
    }

    private void showOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);

        // ── 1) 把手条（始终可触摸，很小，不影响游戏）──
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(Color.BLACK);
        bbg.setCornerRadius(20f);
        bar.setBackground(bbg);

        handle = new TextView(this);
        handle.setText("\u2725");
        handle.setTextSize(13f);
        handle.setTextColor(Color.parseColor("#AAB4C0"));
        handle.setPadding(20, 8, 20, 10);

        hideBtn = new TextView(this);
        hideBtn.setText("\u2014");
        hideBtn.setTextSize(13f);
        hideBtn.setTextColor(Color.parseColor("#AAB4C0"));
        hideBtn.setPadding(20, 8, 20, 10);

        closeBtn = new TextView(this);
        closeBtn.setText("\u2715");
        closeBtn.setTextSize(12f);
        closeBtn.setTextColor(Color.parseColor("#FF8A80"));
        closeBtn.setPadding(20, 8, 20, 10);

        bar.addView(handle);
        bar.addView(hideBtn);
        bar.addView(closeBtn);

        // ── 2) 内容区（可独立设为不可触摸）──
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setCornerRadius(22f);
        container.setBackground(bg);

        tv = new TextView(this);
        tv.setText("-- FPS");
        tv.setTextSize(11f);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(20, 6, 20, 10);

        scroll = new MaxScroll(this, (int) (dm.heightPixels * 0.45f));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(tv, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        int px = sp.getInt("x", 24), py = sp.getInt("y", 300);

        lpBar = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseFlags, PixelFormat.TRANSLUCENT);
        lpBar.gravity = Gravity.TOP | Gravity.START;
        lpBar.x = px; lpBar.y = py;

        lpContent = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseFlags, PixelFormat.TRANSLUCENT);
        lpContent.gravity = Gravity.TOP | Gravity.START;
        lpContent.x = px; lpContent.y = py + 60;

        lpBar.alpha = 1f;
        lpContent.alpha = 1f;
        lastAlpha = -1;

        // 长按 280ms 后才能拖动；拖动时两个窗口一起动
        final Runnable enableDrag = new Runnable() {
            @Override public void run() { canDrag = true; handle.setText("\u2725\u62d6"); }
        };
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int dx, dy;
            private boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = (int) e.getRawX() - lpBar.x;
                        dy = (int) e.getRawY() - lpBar.y;
                        moved = false;
                        dragging = true;
                        canDrag = false;
                        handle.setText("\u2725");
                        v.postDelayed(enableDrag, 280);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (!canDrag) return true;
                        long now = System.currentTimeMillis();
                        if (now - lastMoveTs < 16) return true;   // 最多 ~60fps，避免 Binder 打爆
                        lastMoveTs = now;
                        int nx = (int) e.getRawX() - dx, ny = (int) e.getRawY() - dy;
                        if (Math.abs(nx - lpBar.x) + Math.abs(ny - lpBar.y) > 4) moved = true;
                        lpBar.x = nx; lpBar.y = ny;
                        lpContent.x = nx; lpContent.y = ny + bar.getHeight();
                        try {
                            wm.updateViewLayout(bar, lpBar);
                            wm.updateViewLayout(container, lpContent);
                        } catch (Throwable ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(enableDrag);
                        if (moved) sp.edit().putInt("x", lpBar.x).putInt("y", lpBar.y).apply();
                        dragging = false;
                        canDrag = false;
                        handle.setText("\u2725");
                        return true;
                }
                return false;
            }
        });

        hideBtn.setOnClickListener(new View.OnClickListener() {
            private boolean collapsed = false;
            @Override public void onClick(View v) {
                collapsed = !collapsed;
                container.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                hideBtn.setText(collapsed ? "\u25b8" : "\u2014");
            }
        });
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(FpsService.this, "\u60ac\u6d6e\u7a97\u5df2\u5173\u95ed", Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        });

        try { wm.addView(bar, lpBar); } catch (Throwable ignored) {}
        try { wm.addView(container, lpContent); } catch (Throwable ignored) {}

        // 初始按配置设置：内容区是否可触摸
        applyVisuals();
    }

    private String readLine(String name) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(DIR + name));
            String l = br.readLine(); br.close(); return l;
        } catch (Throwable t) { return null; }
    }

    private List<String[]> readThermal() {
        List<String[]> out = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(DIR + "thermal.txt"));
            String line;
            while ((line = br.readLine()) != null) {
                int i = line.indexOf('\t');
                if (i > 0) out.add(new String[]{line.substring(0, i), line.substring(i + 1)});
            }
            br.close();
        } catch (Throwable ignored) {}
        return out;
    }

    private static String fpsOf(String line) {
        if (line == null) return null;
        try {
            int i = line.indexOf("fps:");
            if (i < 0) return null;
            String s = line.substring(i + 4).trim();
            int sp = s.indexOf(' ');
            if (sp > 0) s = s.substring(0, sp);
            float f = Float.parseFloat(s);
            return f <= 0 ? null : String.valueOf(Math.round(f));
        } catch (Throwable t) { return null; }
    }

    private float batteryTemp() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1000f;
            int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1000);
            return t <= -1000 ? -1000f : t / 10f;
        } catch (Throwable t) { return -1000f; }
    }

    private String topPkg() {
        String fg = readLine("foreground.txt");
        return fg == null ? null : fg.trim();
    }

    private static String trimHz(String s) {
        try {
            float f = Float.parseFloat(s.trim());
            if (f >= 10) return String.valueOf(Math.round(f));
        } catch (Throwable ignored) {}
        return s;
    }

    private void applyVisuals() {
        int a = Prefs.alpha(this);
        boolean day = Prefs.dayMode(this);
        int dayFlag = day ? 1 : 0;
        int sc = Prefs.scale(this);
        boolean touch = Prefs.touchable(this);

        boolean needLayoutUpdate = false;

        if (a != lastAlpha) {
            lastAlpha = a;
            if (container != null) container.setAlpha(a / 100f);
            if (bar != null) bar.setAlpha(Math.min(1f, a / 100f + 0.35f));
        }
        if (dayFlag != lastDay) {
            lastDay = dayFlag;
            applyTheme(day);
        }
        if (sc != lastScale) {
            lastScale = sc;
            if (tv != null) tv.setTextSize(11f * sc / 100f);
            if (handle != null) handle.setTextSize(13f * sc / 100f);
            if (hideBtn != null) hideBtn.setTextSize(13f * sc / 100f);
            if (closeBtn != null) closeBtn.setTextSize(12f * sc / 100f);
        }
        if (lpContent != null) {
            int flags = (lpContent.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            if (!touch) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (flags != lastFlags) {
                lastFlags = flags;
                lpContent.flags = flags;
                needLayoutUpdate = true;
            }
        }
        if (needLayoutUpdate && container != null) {
            try { wm.updateViewLayout(container, lpContent); } catch (Throwable ignored) {}
        }
    }

    /** 日间/夜间配色 */
    private void applyTheme(boolean day) {
        try {
            int bgColor = day ? 0xF2FFFFFF : 0xAA000000;
            int fgColor = day ? 0xFF1A1A1A : 0xFFFFFFFF;
            int btnColor = day ? 0xFF5F6B7A : 0xFFAAB4C0;
            int closeColor = day ? 0xFFD32F2F : 0xFFFF8A80;

            if (container != null) {
                GradientDrawable cbg = new GradientDrawable();
                cbg.setColor(bgColor);
                cbg.setCornerRadius(22f);
                container.setBackground(cbg);
            }
            if (bar != null) {
                GradientDrawable bbg = new GradientDrawable();
                bbg.setColor(day ? 0xFFEDEFF3 : 0xCC000000);
                bbg.setCornerRadius(20f);
                bar.setBackground(bbg);
            }
            if (tv != null) tv.setTextColor(fgColor);
            if (handle != null) handle.setTextColor(btnColor);
            if (hideBtn != null) hideBtn.setTextColor(btnColor);
            if (closeBtn != null) closeBtn.setTextColor(closeColor);
        } catch (Throwable ignored) {}
    }

    private void startTicker() {
        ticker = new Thread(new Runnable() {
            @Override public void run() {
                int tick = 0;
                while (running) {
                    if (dragging) {
                        try { Thread.sleep(120); } catch (InterruptedException e) { return; }
                        continue;
                    }
                    final String fps = fpsOf(readLine("fps.txt"));
                    final String refresh = readLine("refresh.txt");
                    final List<String[]> th = readThermal();
                    final Set<String> sel = Prefs.selected(FpsService.this);
                    if (tick % 3 == 0) lastTemp = batteryTemp();
                    final boolean wantBat = Prefs.showBattery(FpsService.this);
                    final boolean wantFps = Prefs.showFps(FpsService.this);
                    final boolean wantRef = Prefs.showRefresh(FpsService.this);
                    final boolean friendly = Prefs.friendly(FpsService.this);
                    final String pkg = Prefs.gameOnly(FpsService.this) ? topPkg() : null;
                    final Set<String> games = Prefs.games(FpsService.this);
                    final boolean show = !Prefs.gameOnly(FpsService.this) || (pkg != null && games.contains(pkg));

                    StringBuilder head = new StringBuilder();
                    if (wantRef && refresh != null) head.append(trimHz(refresh)).append("Hz  ");
                    if (wantFps) head.append(fps == null ? "--" : fps).append(" FPS");
                    if (wantBat && lastTemp > -100) head.append("  ").append(String.format("%.1f°C", lastTemp));

                    List<String> lines = new ArrayList<>();
                    List<String> types = new ArrayList<>();
                    int shown = 0, extra = 0;
                    for (String[] kv : th) {
                        if (sel != null && !sel.contains(kv[0])) continue;
                        String n = friendly ? Names.friendly(kv[0]) : kv[0];
                        lines.add(n + " " + kv[1] + "°");
                        types.add(kv[0]);
                        shown++;
                    }
                    if (extra > 0) lines.add("…+" + extra);

                    final SpannableStringBuilder ssb = new SpannableStringBuilder();
                    if (head.length() > 0) {
                        int s0 = ssb.length();
                        ssb.append(head);
                        if (wantBat && lastTemp > -100) {
                            boolean day0 = Prefs.dayMode(FpsService.this);
                            int c = lastTemp > 80 ? 0xFFEF5350
                                    : (lastTemp > 60 ? (day0 ? 0xFFD9820A : 0xFFFFD166)
                                                     : (day0 ? 0xFF1A1A1A : 0xFFFFFFFF));
                            ssb.setSpan(new ForegroundColorSpan(c), s0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    for (int i = 0; i < lines.size(); i++) {
                        String l = lines.get(i);
                        String num = l.substring(l.lastIndexOf(' ') + 1).replace("°", "");
                        float val = -1000f;
                        try { val = Float.parseFloat(num); } catch (Throwable ignored) {}
                        float[] thc = Names.thresholds(i < types.size() ? types.get(i) : "");
                        boolean dayF = Prefs.dayMode(FpsService.this);
                        int c = val > thc[1] ? 0xFFEF5350
                                : (val > thc[0] ? (dayF ? 0xFFD9820A : 0xFFFFD166)
                                                : (dayF ? 0xFF1A1A1A : 0xFFFFFFFF));
                        if (ssb.length() > 0) ssb.append('\n');
                        int st = ssb.length();
                        ssb.append(l);
                        ssb.setSpan(new ForegroundColorSpan(c), st, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    final CharSequence text = ssb.length() == 0 ? "--" : ssb;

                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (tv == null) return;
                            tv.setText(text);
                            container.setVisibility(show ? View.VISIBLE : View.GONE);
                            applyVisuals();
                        }
                    });
                    tick++;
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                }
            }
        });
        ticker.start();
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (screenRecv != null) unregisterReceiver(screenRecv); } catch (Throwable ignored) {}
        try { if (container != null && wm != null) wm.removeView(container); } catch (Throwable ignored) {}
        try { if (bar != null && wm != null) wm.removeView(bar); } catch (Throwable ignored) {}
        try { if (ticker != null) ticker.interrupt(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
