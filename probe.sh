#!/system/bin/sh
# 在目标手机上探测本工具需要的能力，输出一份报告
# 用法: su -c "sh probe.sh"
echo "======== 设备信息 ========"
for p in ro.product.brand ro.product.model ro.product.device ro.build.version.release \
         ro.build.version.sdk ro.board.platform ro.hardware ro.boot.verifiedbootstate; do
  printf '%-32s %s\n' "$p" "$(getprop $p 2>/dev/null)"
done
printf '%-32s %s\n' "kernel" "$(uname -r 2>/dev/null)"

echo
echo "======== root ========"
ID=$(su -c 'id' 2>/dev/null)
if [ -n "$ID" ]; then echo "root: $ID"; else echo "root: 不可用（su 无法执行）"; fi
printf '%-32s %s\n' "su path" "$(command -v su 2>/dev/null)"
[ -d /data/adb/ksu ] && echo "KernelSU: 存在 /data/adb/ksu"
[ -d /data/adb/magisk ] && echo "Magisk: 存在 /data/adb/magisk"
[ -d /data/adb/ap ] && echo "APatch: 存在 /data/adb/ap"

echo
echo "======== 帧率数据源（关键）========"
FOUND=0
for f in /sys/class/drm/*/measured_fps; do
  [ -f "$f" ] || continue
  FOUND=1
  printf '%-52s -> %s\n' "$f" "$(cat "$f" 2>/dev/null)"
done
for f in /sys/kernel/fpsgo/common/fps /proc/mtk_fps /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq; do
  [ -f "$f" ] && { FOUND=1; printf '%-52s -> %s\n' "$f" "$(cat "$f" 2>/dev/null)"; }
done
if [ "$FOUND" = "0" ]; then
  echo "未找到硬件帧率节点 —— 该机型帧率功能不可用（温度/刷新率仍可用）"
fi

echo
echo "======== 温度传感器 ========"
N=$(ls -d /sys/class/thermal/thermal_zone* 2>/dev/null | wc -l)
echo "thermal_zone 数量: $N"
for z in /sys/class/thermal/thermal_zone0 /sys/class/thermal/thermal_zone1 /sys/class/thermal/thermal_zone2; do
  [ -e "$z" ] || continue
  printf '  %-32s type=%-18s temp=%s\n' "$(basename $z)" "$(cat $z/type 2>/dev/null)" "$(cat $z/temp 2>/dev/null)"
done

echo
echo "======== 刷新率 / 前台 ========"
printf 'vsyncRate: %s\n' "$(dumpsys display 2>/dev/null | grep -m1 -oE 'vsyncRate=[0-9.]+')"
printf 'mCurrentFocus: %s\n' "$(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)"

echo
echo "======== 电池温度 ========"
printf 'battery temp: %s\n' "$(cat /sys/class/power_supply/battery/temp 2>/dev/null)"

echo
echo "======== 结论 ========"
if [ "$FOUND" = "1" ]; then echo "帧率: 可用"; else echo "帧率: 不可用（需要改 fps_feed.sh 的 NODE）"; fi
[ "$N" -gt 0 ] && echo "温度: 可用（$N 个传感器）" || echo "温度: 不可用"
[ -n "$ID" ] && echo "root: 可用" || echo "root: 不可用（只能看电池温度）"
