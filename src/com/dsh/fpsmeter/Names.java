package com.dsh.fpsmeter;

/** 把内核节点名翻译成人能看懂的短名 */
public class Names {

    public static String friendly(String t) {
        try {
            if (t.startsWith("cpu-")) {
                String[] p = t.split("-");           // cpu-0-2-1
                if (p.length == 4) return "CPU" + p[1] + "核" + p[2] + "-" + p[3];
                if (p.length == 3) return "CPU" + p[1] + "核" + p[2];
                return "CPU" + t.substring(4);
            }
            if (t.startsWith("cpullc-")) return "CPU缓存" + t.substring(7).replace("-", "-");
            if (t.startsWith("gpuss-")) return "GPU核" + t.substring(6);
            if (t.startsWith("qmx-")) return "内存" + t.substring(4).replace("-", "-");
            if (t.startsWith("nsphvx-")) return "电源HVX" + t.substring(7).replace("-", "-");
            if (t.startsWith("nsphmx-")) return "电源HMX" + t.substring(7).replace("-", "-");
            if (t.equals("battery")) return "电池";
            if (t.startsWith("socd")) return "SoC";
            if (t.startsWith("sdr")) return "DDR" + t.substring(3);
            if (t.startsWith("mmw")) return "毫米波" + t.substring(3).replace("_", "-");
            if (t.startsWith("apc")) return "APC" + t.substring(3).replace("-", "-");
            if (t.equals("xo-therm")) return "晶振";
            if (t.startsWith("pa-")) return "功放" + t.substring(3).replace("-", "-");
            if (t.startsWith("cam")) return "相机" + t.substring(3).replace("-", "-");
            if (t.startsWith("disp")) return "屏幕" + t.substring(4).replace("-", "-");
            if (t.equals("quiet-therm")) return "主板";
            if (t.equals("pmr735a_tz")) return "PMIC";
            return t;
        } catch (Throwable e) {
            return t;
        }
    }

    /**
     * 每个传感器的变色阈值 [黄, 红]（摄氏度）。
     * PMIC / 供电类测点正常就比其他传感器高，用统一 60/80 会一直标黄，所以单独放宽。
     */
    public static float[] thresholds(String type) {
        if ("sys-therm-9".equals(type)) return new float[]{95f, 110f};       // SoC 供电热点
        if ("pmih010x_lite_tz".equals(type)) return new float[]{85f, 100f};  // PMIC lite 区
        if (type.startsWith("sys-therm-")) return new float[]{80f, 100f};
        if (type.startsWith("pmih") || type.startsWith("pmh") || type.contains("_tz"))
            return new float[]{80f, 100f};
        if ("battery".equals(type)) return new float[]{45f, 55f};            // 电池更敏感
        return new float[]{60f, 80f};                                        // 默认
    }

    /** 非温度节点的中文注释 */
    public static String note(String t) {
        try {
            if (t.contains("-bcl-lvl")) return "电池电流限制等级（0=未限制，非温度）";
            if (t.contains("-ibat-lvl")) return "输入电流阈值（mA，非温度）";
            if ("vbat".equals(t)) return "电池电压（mV，非温度）";
            if ("usb".equals(t)) return "USB 输入电压（mV，非温度）";
            if ("socd".equals(t)) return "SoC 裸片（毫度，未校准）";
            if (t.startsWith("mmw")) return "毫米波 IFIC（未接传感器，-273=无数据）";
            if (t.startsWith("sdr")) return "SDR 射频通路（未接传感器，-273=无数据）";
            if (t.contains("trip")) return "降频阈值（温度门限，不是当前温度）";
            return "内核注册的其它节点";
        } catch (Throwable e) { return ""; }
    }

    /** 把原始值转成可读字符串 */
    public static String value(String type, String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            if (type.contains("-bcl-lvl")) return String.valueOf(v) + " 级";
            if (type.contains("-ibat-lvl")) return v + " mA";
            if ("vbat".equals(type) || "usb".equals(type)) return v + " mV";
            if ("socd".equals(type)) return String.format("%.3f", v / 1000.0) + " ℃";
            if (v == -273000) return "无数据";
            return raw;
        } catch (Throwable e) { return raw; }
    }

    public static String label(String type, boolean friendly) {
        return friendly ? friendly(type) + "（" + type + "）" : type;
    }
}
