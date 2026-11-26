package com.dsh.fpsmeter;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class Prefs {
    public static final String FILE = "cfg";
    public static final String[] DEFAULT_GAMES = {
            "com.tencent.tmgp.pubgmhd",
            "com.tencent.tmgp.sgame",
            "com.tencent.mf.uam",
            "com.miHoYo.Yuanshen",
            "com.miHoYo.hkrpg"
    };

    public static SharedPreferences get(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static boolean gameOnly(Context c) { return get(c).getBoolean("gameOnly", true); }
    public static boolean showFps(Context c) { return get(c).getBoolean("showFps", true); }
    public static boolean showRefresh(Context c) { return get(c).getBoolean("showRefresh", true); }
    public static boolean showBattery(Context c) { return get(c).getBoolean("showBattery", true); }

    /** null = 全选 */
    public static boolean recordOn(Context c) { return get(c).getBoolean("recordOn", false); }
    public static int recordInterval(Context c) { return get(c).getInt("recordInterval", 2); }
    public static boolean recordSleep(Context c) { return get(c).getBoolean("recordSleep", false); }
    public static String recordTarget(Context c) { return get(c).getString("recordTarget", ""); }
    public static int recordMaxMin(Context c) { return get(c).getInt("recordMaxMin", 0); }
    public static int recordKeepMin(Context c) { return get(c).getInt("recordKeepMin", 0); }

    public static int alpha(Context c) { return get(c).getInt("alpha", 210); }
    public static int scale(Context c) { return get(c).getInt("scale", 100); }
    public static boolean touchable(Context c) { return get(c).getBoolean("touchable", true); }
    public static boolean friendly(Context c) { return get(c).getBoolean("friendly", true); }

    public static Set<String> selected(Context c) {
        Set<String> s = get(c).getStringSet("sel", null);
        return s == null ? null : new HashSet<>(s);
    }

    public static void setSelected(Context c, Set<String> s) {
        get(c).edit().putStringSet("sel", new HashSet<>(s)).apply();
    }

    public static void selectAll(Context c) { get(c).edit().remove("sel").apply(); }

    public static Set<String> games(Context c) {
        Set<String> s = get(c).getStringSet("games", null);
        if (s == null) {
            s = new HashSet<>();
            for (String g : DEFAULT_GAMES) s.add(g);
            get(c).edit().putStringSet("games", s).apply();
        }
        return new HashSet<>(s);
    }

    public static void setGames(Context c, Set<String> s) {
        get(c).edit().putStringSet("games", new HashSet<>(s)).apply();
    }
}
