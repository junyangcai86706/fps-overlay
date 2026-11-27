package com.dsh.fpsmeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.DashPathEffect;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CurveActivity extends Activity {

    private static final String CSV = "/data/data/com.dsh.fpsmeter/files/record.csv";
    private TextView title, detail;
    private CurveView view;
    private List<double[]> pts = new ArrayList<>();
    private int dayFg = 0xFFFFFFFF, daySub = 0xFF9AA4B2;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        boolean day = Prefs.dayMode(this);
        dayFg = day ? 0xFF1A1A1A : 0xFFFFFFFF;
        daySub = day ? 0xFF5F6B7A : 0xFF9AA4B2;
        root.setBackgroundColor(day ? 0xFFF7F8FA : 0xFF0F1115);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        root.setPadding(landscape ? 18 : 28, landscape ? 10 : 48, landscape ? 18 : 28, landscape ? 10 : 28);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back = smallBtn("← 返回", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(this);
        t.setText(landscape ? "  帧率 / 温度曲线" : "  帧率 / 温度曲线");
        t.setTextColor(dayFg);
        t.setTextSize(landscape ? 15f : 21f);
        topBar.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = new Button(this);
        clear.setText("清空");
        clear.setAllCaps(false);
        clear.setTextSize(13f);
        clear.setTextColor(Color.WHITE);
        clear.setPadding(28, 6, 28, 6);
        clear.setMinWidth(0); clear.setMinimumWidth(0);
        clear.setMinHeight(0); clear.setMinimumHeight(0);
        android.graphics.drawable.GradientDrawable cbg = new android.graphics.drawable.GradientDrawable();
        cbg.setColor(Color.parseColor("#D32F2F"));
        cbg.setCornerRadius(18f);
        clear.setBackground(cbg);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        topBar.addView(clear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(topBar);

        title = new TextView(this);
        title.setTextColor(daySub);
        title.setTextSize(landscape ? 10f : 12f);
        title.setPadding(0, 6, 0, 6);
        root.addView(title);

        detail = new TextView(this);
        detail.setTextColor(day ? 0xFF1565C0 : 0xFF7FD1FF);
        detail.setTextSize(13f);
        detail.setPadding(0, 0, 0, 8);
        detail.setText("拖动白线看数值 · 双指缩放 · 左移/右移平移");
        root.addView(detail);

        view = new CurveView(this);
        view.setDay(Prefs.dayMode(this));
        root.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(smallBtn("缩小", new View.OnClickListener() {
            @Override public void onClick(View v) { view.zoom(0.7f); }
        }), weight());
        row1.addView(smallBtn("放大", new View.OnClickListener() {
            @Override public void onClick(View v) { view.zoom(1.4f); }
        }), weight());
        row1.addView(smallBtn("复位", new View.OnClickListener() {
            @Override public void onClick(View v) { view.reset(); }
        }), weight());
        final boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        row1.addView(smallBtn(land ? "竖屏" : "横屏", new View.OnClickListener() {
            @Override public void onClick(View v) {
                setRequestedOrientation(land
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        }), weight());
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(smallBtn("◀ 左移", new View.OnClickListener() {
            @Override public void onClick(View v) { view.pan(-1); }
        }), weight());
        row2.addView(smallBtn("右移 ▶", new View.OnClickListener() {
            @Override public void onClick(View v) { view.pan(1); }
        }), weight());
        row2.addView(smallBtn("刷新", new View.OnClickListener() {
            @Override public void onClick(View v) { load(); }
        }), weight());
        row2.addView(smallBtn("数值", new View.OnClickListener() {
            @Override public void onClick(View v) { showList(); }
        }), weight());
        root.addView(row2);


        setContentView(root);
        load();
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private Button smallBtn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12.5f);
        b.setTextColor(Color.WHITE);
        b.setPadding(8, 14, 8, 14);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#1F6FE5"));
        bg.setCornerRadius(20f);
        b.setBackground(bg);
        b.setOnClickListener(l);
        return b;
    }


    private void confirmClear() {
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("清空曲线记录？")
                .setMessage("会删除全部已记录的帧率 / 温度数据，不可恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try { new java.io.File(CSV).delete(); } catch (Throwable ignored) {}
                        load();
                        detail.setText("记录已清空");
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                try {
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#D32F2F"));
                    dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#1E88E5"));
                } catch (Throwable ignored) {}
            }
        });
        dlg.show();
    }

    private void load() {
        pts = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(CSV));
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 4) continue;
                try {
                    pts.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]), Double.parseDouble(p[3])});
                } catch (Throwable ignored) {}
            }
            br.close();
        } catch (Throwable ignored) {}
        if (pts.size() > 2000) pts = pts.subList(pts.size() - 2000, pts.size());
        view.setData(pts);
        if (pts.isEmpty()) {
            title.setText("还没有记录（先在设置里打开「记录曲线」）");
            return;
        }
        double maxF = 0, maxT = 0, sumF = 0;
        for (double[] p : pts) {
            if (p[1] > maxF) maxF = p[1];
            if (p[3] > maxT) maxT = p[3];
            sumF += p[1];
        }
        long span = Math.round(pts.get(pts.size() - 1)[0] - pts.get(0)[0]);
        title.setText(pts.size() + " 个采样 · 跨度 " + span + "s\n"
                + "FPS 平均 " + Math.round(sumF / pts.size()) + " / 最高 " + Math.round(maxF)
                + " · CPU 最高 " + Math.round(maxT) + "℃");
    }

    private void showList() {
        if (pts.isEmpty()) { detail.setText("还没有记录"); return; }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        final int from = Math.max(0, pts.size() - 80);
        final int n = pts.size() - from;
        String[] items = new String[n];
        for (int i = 0; i < n; i++) {
            double[] p = pts.get(from + i);
            items[i] = f.format(new Date((long) p[0] * 1000L))
                    + "    " + Math.round(p[1]) + " FPS    CPU " + Math.round(p[3])
                    + "℃    电池 " + Math.round(p[2]) + "℃";
        }
        new AlertDialog.Builder(this)
                .setTitle("点击任意一条跳到曲线该位置")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        view.selectCentered(from + which);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    void onPoint(double[] p) {
        if (p == null) { detail.setText("拖动白线看数值 · 双指缩放 · 左移/右移平移"); return; }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        detail.setText(f.format(new Date((long) p[0] * 1000L))
                + "   " + Math.round(p[1]) + " FPS   CPU " + Math.round(p[3])
                + "℃   电池 " + Math.round(p[2]) + "℃");
    }

    static class CurveView extends View {
        private List<double[]> data = new ArrayList<>();
        private int dayBg = 0xFF12151B;
        private int visStart = 0, visEnd = 0, sel = -1;
        private final Paint pFps = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCpu = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pSel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pDash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pFpsTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pCpuTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ScaleGestureDetector scaleDet;
        private float lastX, downX, lastPanX;
        private int mode = 0;   // 0=无 1=拖白线 2=平移
        private boolean dragging;
        private Activity host;

        CurveView(Context c) {
            super(c);
            host = (Activity) c;
            pFps.setColor(Color.parseColor("#4FC3F7"));
            pFps.setStrokeWidth(3f);
            pFps.setStyle(Paint.Style.STROKE);
            pCpu.setColor(Color.parseColor("#FFB74D"));
            pCpu.setStrokeWidth(3f);
            pCpu.setStyle(Paint.Style.STROKE);
            pGrid.setColor(Color.parseColor("#2A2F38"));
            pGrid.setStrokeWidth(1.5f);
            pText.setColor(Color.parseColor("#8B93A1"));
            pText.setTextSize(26f);
            pSel.setColor(Color.WHITE);
            pSel.setStrokeWidth(2f);
            pDash.setStyle(Paint.Style.STROKE);
            pDash.setStrokeWidth(1.8f);
            pDash.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0f));
            pFpsTxt.setColor(Color.parseColor("#7FD1FF"));
            pFpsTxt.setTextSize(19f);
            pFpsTxt.setAntiAlias(true);
            pFpsTxt.setTextAlign(Paint.Align.RIGHT);
            pCpuTxt.setColor(Color.parseColor("#FFB74D"));
            pCpuTxt.setTextSize(19f);
            pCpuTxt.setAntiAlias(true);
            pCpuTxt.setTextAlign(Paint.Align.RIGHT);
            scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    if (data.size() < 4) return true;
                    zoom(d.getScaleFactor());
                    return true;
                }
            });
        }

        void setDay(boolean day) {
            dayBg = day ? 0xFFFFFFFF : 0xFF12151B;
            pGrid.setColor(day ? 0xFFDDE2E9 : 0xFF2A2F38);
            pText.setColor(day ? 0xFF4A5563 : 0xFF8B93A1);
            pFpsTxt.setColor(day ? 0xFF0369A1 : 0xFF7FD1FF);
            pCpuTxt.setColor(day ? 0xFFB45309 : 0xFFFFB74D);
            pFps.setColor(day ? 0xFF0284C7 : 0xFF4FC3F7);
            pCpu.setColor(day ? 0xFFD97706 : 0xFFFFB74D);
            pSel.setColor(day ? 0xFF334155 : 0xFFFFFFFF);
            invalidate();
        }

        void setData(List<double[]> d) { data = d; sel = -1; reset(); invalidate(); }

        void reset() {
            visStart = 0;
            visEnd = Math.max(0, data.size() - 1);
            invalidate();
        }

        private float xOf(int idx) {
            int padL = 128, padR = 20;
            int gw = getWidth() - padL - padR;
            if (gw <= 0 || visEnd <= visStart) return padL;
            return padL + gw * (idx - visStart) / (float) (visEnd - visStart);
        }

        void pickAt(float x) {
            if (data.size() < 2 || visEnd <= visStart) return;
            int padL = 128, padR = 20;
            int gw = getWidth() - padL - padR;
            if (gw <= 0) return;
            float xx = Math.max(padL, Math.min(padL + gw, x));
            int idx = visStart + Math.round((xx - padL) / gw * (visEnd - visStart));
            idx = Math.max(0, Math.min(data.size() - 1, idx));
            sel = idx;
            if (host instanceof CurveActivity) ((CurveActivity) host).onPoint(data.get(idx));
            invalidate();
        }

        void selectCentered(int idx) {
            if (data.isEmpty()) return;
            idx = Math.max(0, Math.min(data.size() - 1, idx));
            int span = visEnd - visStart;
            if (span < 8) span = Math.min(8, data.size() - 1);
            int s0 = Math.max(0, idx - span / 2);
            int e0 = Math.min(data.size() - 1, s0 + span);
            s0 = Math.max(0, e0 - span);
            visStart = s0;
            visEnd = e0;
            sel = idx;
            if (host instanceof CurveActivity) ((CurveActivity) host).onPoint(data.get(idx));
            invalidate();
        }

        void pan(int dir) {
            int span = visEnd - visStart;
            if (span <= 0) return;
            int step = Math.max(1, span / 4);
            int s = Math.max(0, Math.min(data.size() - 1 - span, visStart + dir * step));
            visStart = s;
            visEnd = s + span;
            invalidate();
        }

        void zoom(float factor) {
            if (data.size() < 4) return;
            int span = visEnd - visStart;
            if (span < 1) span = 1;
            int center = (visStart + visEnd) / 2;
            int newSpan = Math.max(8, Math.min(data.size() - 1, Math.round(span / factor)));
            int s = Math.max(0, center - newSpan / 2);
            int e = Math.min(data.size() - 1, s + newSpan);
            s = Math.max(0, e - newSpan);
            visStart = s;
            visEnd = e;
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            scaleDet.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = e.getX();
                    lastPanX = e.getX();
                    if (sel >= visStart && sel <= visEnd && sel < data.size()
                            && Math.abs(xOf(sel) - e.getX()) < 42f) {
                        mode = 1;                       // 按在白线上 → 拖白线
                    } else {
                        mode = 2;                       // 其它位置 → 平移曲线
                    }
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (scaleDet.isInProgress()) break;
                    if (e.getPointerCount() > 1) break;
                    if (mode == 1) {
                        pickAt(e.getX());
                    } else if (mode == 2 && data.size() > 1) {
                        float dx = e.getX() - lastPanX;
                        if (Math.abs(dx) > 4f) {
                            dragging = true;
                            int span = visEnd - visStart;
                            int gw = Math.max(1, getWidth() - 148);
                            int shift = (int) (-dx * span / (float) gw);
                            if (shift != 0) {
                                int s0 = Math.max(0, Math.min(data.size() - 1 - span, visStart + shift));
                                visStart = s0;
                                visEnd = s0 + span;
                                lastPanX = e.getX();
                                invalidate();
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mode == 2 && !dragging && Math.abs(e.getX() - downX) < 12f) {
                        pickAt(e.getX());                   // 轻点放置白线
                    }
                    mode = 0;
                    break;
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(dayBg);
            int padL = 128, padR = 20, padT = 26, padB = 46;
            int gw = w - padL - padR, gh = h - padT - padB;
            if (gw <= 0 || gh <= 0) return;

            double maxF = 60;
            for (double[] p : data) if (p[1] > maxF) maxF = p[1];
            maxF = Math.ceil(maxF / 60.0) * 60.0;

            pGrid.setStrokeWidth(1f);
            for (int i = 0; i <= 10; i++) {
                float y = padT + gh * i / 10f;
                boolean dayG = dayBg == 0xFFFFFFFF;
                pGrid.setColor(i % 5 == 0
                        ? (dayG ? Color.parseColor("#BFC7D2") : Color.parseColor("#3A4250"))
                        : (dayG ? Color.parseColor("#E6EAF0") : Color.parseColor("#232830")));
                c.drawLine(padL, y, padL + gw, y, pGrid);
                int fv = (int) Math.round(maxF * (10 - i) / 10.0);
                int tv = 100 * (10 - i) / 10;
                float ty = y + 7;
                c.drawText(fv + "fps", 66, ty, pFpsTxt);
                c.drawText(tv + "℃", 124, ty, pCpuTxt);
            }

            if (data.size() < 2 || visEnd <= visStart) {
                pText.setTextSize(30f);
                c.drawText(data.isEmpty() ? "暂无数据" : "放大后可见", padL + gw / 3f, padT + gh / 2f, pText);
                pText.setTextSize(26f);
                return;
            }

            Path pf = new Path(), pc = new Path();
            int n = visEnd - visStart;
            for (int i = visStart; i <= visEnd && i < data.size(); i++) {
                float x = padL + gw * (i - visStart) / (float) n;
                float yf = padT + gh - gh * (float) Math.min(1.0, data.get(i)[1] / maxF);
                float yc = padT + gh - gh * (float) Math.min(1.0, data.get(i)[3] / 100.0);
                if (i == visStart) { pf.moveTo(x, yf); pc.moveTo(x, yc); }
                else { pf.lineTo(x, yf); pc.lineTo(x, yc); }
            }
            c.drawPath(pc, pCpu);
            c.drawPath(pf, pFps);

            if (sel >= visStart && sel <= visEnd && sel < data.size()) {
                float x = padL + gw * (sel - visStart) / (float) n;
                c.drawLine(x, padT, x, padT + gh, pSel);
                float yf = padT + gh - gh * (float) Math.min(1.0, data.get(sel)[1] / maxF);
                float yc = padT + gh - gh * (float) Math.min(1.0, data.get(sel)[3] / 100.0);
                c.drawCircle(x, yf, 7f, pFps);
                c.drawCircle(x, yc, 7f, pCpu);
                pDash.setColor(0xCC4FC3F7);
                c.drawLine(padL, yf, x, yf, pDash);
                pDash.setColor(0xCCFFB74D);
                c.drawLine(padL, yc, x, yc, pDash);
            }
        }
    }
}
