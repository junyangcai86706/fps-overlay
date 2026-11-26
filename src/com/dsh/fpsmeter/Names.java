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

    public static String label(String type, boolean friendly) {
        return friendly ? friendly(type) + "（" + type + "）" : type;
    }
}
