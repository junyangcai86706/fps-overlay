package com.dsh.fpsmeter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private TextView status, sensorCount;
    private int dayFg = 0xFFFFFFFF, daySub = 0xFF9AA4B2, dayLabel = 0xFFD1D5DB, dayHint = 0xFF6B7280;
    private LinearLayout sensorBox, extraBox;
    private List<String[]> sensors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);
        boolean day = Prefs.dayMode(this);
        dayFg = day ? 0xFF1A1A1A : 0xFFFFFFFF;
        daySub = day ? 0xFF5F6B7A : 0xFF9AA4B2;
        dayLabel = day ? 0xFF3C4450 : 0xFFD1D5DB;
        dayHint = day ? 0xFF525C6B : 0xFF6B7280;
        root.setBackgroundColor(day ? 0xFFF7F8FA : 0xFF0F1115);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("FPS 悬浮窗");
        title.setTextColor(dayFg);
        title.setTextSize(25f);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("帧率 / 全部温度传感器，可逐项开关；可拖动，长按关闭");
        desc.setTextColor(daySub);
        desc.setTextSize(12f);
        desc.setPadding(0, 8, 0, 16);
        root.addView(desc);

        status = new TextView(this);
        status.setTextColor(day ? 0xFFB26A00 : 0xFFE8B04B);
        status.setTextSize(13f);
        status.setPadding(0, 0, 0, 12);
        root.addView(status);

        root.addView(check("显示帧率", Prefs.showFps(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("showFps", c).apply();
            }
        }));
        root.addView(check("显示屏幕刷新率", Prefs.showRefresh(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("showRefresh", c).apply();
            }
        }));
        root.addView(check("显示电池温度", Prefs.showBattery(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("showBattery", c).apply();
            }
        }));
        root.addView(check("仅在游戏内显示", Prefs.gameOnly(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("gameOnly", c).apply();
            }
        }));

        root.addView(check("允许触摸悬浮窗（关掉后完全不挡游戏触控）", Prefs.touchable(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("touchable", c).apply();
            }
        }));
        root.addView(check("温度名称显示中文（CPU核/GPU核/内存…）", Prefs.friendly(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("friendly", c).apply();
            }
        }));
        root.addView(check("日间模式（设置页 + 悬浮窗都变浅色）", Prefs.dayMode(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("dayMode", c).apply();
                recreate();
            }
        }));
        root.addView(seek("悬浮窗不透明度 %（0=全透明）", 0, 100, Prefs.alpha(this), new IntSetter() {
            @Override public void set(int v) { Prefs.get(MainActivity.this).edit().putInt("alpha", v).apply(); }
        }));
        root.addView(seek("悬浮窗缩放 %", 60, 200, Prefs.scale(this), new IntSetter() {
            @Override public void set(int v) { Prefs.get(MainActivity.this).edit().putInt("scale", v).apply(); }
        }));

        final CheckBox cbRec = check("记录帧率/温度曲线（低频写入，不影响游戏）", Prefs.recordOn(this),
                new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("recordOn", c).apply();
                writeRecordCfg();
            }
        });
        root.addView(cbRec);
        root.addView(seek("采样间隔 秒", 2, 10, Prefs.recordInterval(this), new IntSetter() {
            @Override public void set(int v) {
                Prefs.get(MainActivity.this).edit().putInt("recordInterval", v).apply();
                writeRecordCfg();
            }
        }));
        root.addView(btn("查看曲线", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(MainActivity.this, CurveActivity.class)); } catch (Throwable ignored) {}
            }
        }));
        root.addView(check("熄屏 / 待机时也记录", Prefs.recordSleep(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                Prefs.get(MainActivity.this).edit().putBoolean("recordSleep", c).apply();
                writeRecordCfg();
            }
        }));

        final EditText etTarget = new EditText(this);
        etTarget.setHint("只在指定 App 记录留包名，如 com.tencent.tmgp.pubgmhd（空=全部）");
        etTarget.setText(Prefs.recordTarget(this));
        etTarget.setTextColor(dayFg);
        etTarget.setHintTextColor(dayHint);
        etTarget.setTextSize(12f);
        etTarget.setSingleLine(true);
        root.addView(etTarget);
        root.addView(btn("保存指定 App", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.get(MainActivity.this).edit()
                        .putString("recordTarget", etTarget.getText().toString().trim()).apply();
                writeRecordCfg();
                status.setText("已保存：" + (Prefs.recordTarget(MainActivity.this).isEmpty()
                        ? "全部应用" : Prefs.recordTarget(MainActivity.this)));
            }
        }));

        root.addView(seek("单次最长记录 分钟（0=不限）", 0, 120, Prefs.recordMaxMin(this), new IntSetter() {
            @Override public void set(int v) {
                Prefs.get(MainActivity.this).edit().putInt("recordMaxMin", v).apply();
                writeRecordCfg();
            }
        }));
        root.addView(seek("只保留最近 分钟（0=不限）", 0, 720, Prefs.recordKeepMin(this), new IntSetter() {
            @Override public void set(int v) {
                Prefs.get(MainActivity.this).edit().putInt("recordKeepMin", v).apply();
                writeRecordCfg();
            }
        }));


        root.addView(btn("开启悬浮窗", new View.OnClickListener() {
            @Override public void onClick(View v) { startMeter(); }
        }));
        root.addView(btn("关闭悬浮窗", new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopService(new Intent(MainActivity.this, FpsService.class));
                refresh();
            }
        }));
        root.addView(btn("授予悬浮窗权限", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Throwable ignored) {}
            }
        }));

        sensorCount = new TextView(this);
        sensorCount.setTextColor(dayFg);
        sensorCount.setTextSize(14f);
        sensorCount.setPadding(0, 20, 0, 8);
        root.addView(sensorCount);

        root.addView(btn("全选", new View.OnClickListener() {
            @Override public void onClick(View v) { Prefs.selectAll(MainActivity.this); buildSensors(); }
        }));
        root.addView(btn("全不选", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setSelected(MainActivity.this, new HashSet<String>());
                buildSensors();
            }
        }));
        root.addView(btn("预设：CPU 大核 + GPU", new View.OnClickListener() {
            @Override public void onClick(View v) {
                loadSensors();
                Set<String> s = new HashSet<>();
                for (String[] kv : sensors) {
                    String t = kv[0];
                    if (t.startsWith("cpu-1-") || t.startsWith("gpuss-")) s.add(t);
                }
                Prefs.setSelected(MainActivity.this, s);
                buildSensors();
            }
        }));
        root.addView(btn("刷新列表", new View.OnClickListener() {
            @Override public void onClick(View v) { buildSensors(); }
        }));

        sensorBox = new LinearLayout(this);
        sensorBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(sensorBox);

        extraBox = new LinearLayout(this);
        extraBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(extraBox);

        setContentView(sv);
    }



    private void writeRecordCfg() {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(getFilesDir(), "record.cfg"));
            String tgt = Prefs.recordTarget(this);
            if (tgt == null || tgt.trim().isEmpty()) tgt = "-";
            fw.write((Prefs.recordOn(this) ? "1 " : "0 ")
                    + Prefs.recordInterval(this) + " "
                    + (Prefs.recordSleep(this) ? "1" : "0") + " "
                    + tgt + " "
                    + Prefs.recordMaxMin(this) + " "
                    + Prefs.recordKeepMin(this));
            fw.close();
        } catch (Throwable ignored) {}
    }
    private interface IntSetter { void set(int v); }

    private View seek(String label, final int min, final int max, int cur, final IntSetter setter) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView t = new TextView(this);
        t.setText(label + "：" + cur);
        t.setTextColor(dayLabel);
        t.setTextSize(13f);
        t.setPadding(0, 10, 0, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, cur - min)));
        final String lab = label;
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = min + p;
                t.setText(lab + "：" + v);
                if (fromUser) setter.set(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(t);
        box.addView(sb);
        return box;
    }
    private CheckBox check(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(dayFg);
        cb.setTextSize(14f);
        cb.setChecked(on);
        cb.setOnCheckedChangeListener(l);
        return cb;
    }

    private Button btn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14f);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#2563EB"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 12;
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void startMeter() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("先授予悬浮窗权限");
            return;
        }
        Intent i = new Intent(this, FpsService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        status.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 1500);
    }

    private void loadSensors() {
        sensors.clear();
        try {
            BufferedReader br = new BufferedReader(new FileReader("/data/data/com.dsh.fpsmeter/files/thermal.txt"));
            String line;
            while ((line = br.readLine()) != null) {
                int i = line.indexOf('\t');
                if (i <= 0) continue;
                sensors.add(new String[]{line.substring(0, i), line.substring(i + 1)});
            }
            br.close();
        } catch (Throwable ignored) {}
    }

    private void buildSensors() {
        loadSensors();
        sensorBox.removeAllViews();
        if (sensors.isEmpty()) {
            sensorCount.setText("温度传感器：读不到数据（先开启一次悬浮窗，root 侧喂送脚本需在跑）");
            return;
        }
        Set<String> sel = Prefs.selected(this);
        sensorCount.setText("温度传感器（" + sensors.size() + " 项，" + (sel == null ? "全选" : sel.size() + " 项已选") + "）");
        for (final String[] kv : sensors) {
            CheckBox cb = new CheckBox(this);
            cb.setText(kv[0] + "   " + kv[1] + "°C");
            cb.setTextColor(daySub);
            cb.setTextSize(12f);
            cb.setChecked(sel == null || sel.contains(kv[0]));
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                    Set<String> cur = Prefs.selected(MainActivity.this);
                    if (cur == null) {
                        cur = new HashSet<>();
                        for (String[] s : sensors) cur.add(s[0]);
                    }
                    if (c) cur.add(kv[0]); else cur.remove(kv[0]);
                    Prefs.setSelected(MainActivity.this, cur);
                    sensorCount.setText("温度传感器（" + sensors.size() + " 项，" + cur.size() + " 项已选）");
                }
            });
            sensorBox.addView(cb);
        }
        buildExtras();
    }

    /** 非温度节点：单独分组、只读、带注释 */
    private void buildExtras() {
        if (extraBox == null) return;
        extraBox.removeAllViews();
        java.util.List<String[]> ex = new java.util.ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader("/data/data/com.dsh.fpsmeter/files/extras.txt"));
            String line;
            while ((line = br.readLine()) != null) {
                int i = line.indexOf('\t');
                if (i > 0) ex.add(new String[]{line.substring(0, i), line.substring(i + 1)});
            }
            br.close();
        } catch (Throwable ignored) {}
        if (ex.isEmpty()) return;

        TextView h = new TextView(this);
        h.setText("\n其他节点（非温度，不做悬浮窗显示）");
        h.setTextColor(daySub);
        h.setTextSize(13f);
        h.setPadding(0, 20, 0, 6);
        extraBox.addView(h);

        for (String[] kv : ex) {
            TextView t = new TextView(this);
            t.setText("· " + kv[0] + "  =  " + Names.value(kv[0], kv[1]) + "\n    " + Names.note(kv[0]));
            t.setTextColor(daySub);
            t.setTextSize(11.5f);
            t.setPadding(0, 4, 0, 4);
            extraBox.addView(t);
        }
    }

    private void refresh() {
        status.setText(FpsService.isRunning() ? "状态：运行中" : "状态：未运行");
        buildSensors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
