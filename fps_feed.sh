#!/system/bin/sh
# FPS 悬浮窗数据喂送：帧率 + 温度 + 刷新率 + 前台包名 +（可选）曲线记录
PIDF=/data/adb/JN/bin/fps_feed.pid
if [ -f "$PIDF" ]; then
  OLD=$(cat "$PIDF" 2>/dev/null)
  [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null && exit 0
fi
echo $$ > "$PIDF" 2>/dev/null
OUTDIR=/data/data/com.dsh.fpsmeter/files
FPS=$OUTDIR/fps.txt; TH=$OUTDIR/thermal.txt; RF=$OUTDIR/refresh.txt; FG=$OUTDIR/foreground.txt
EXTRAS=$OUTDIR/extras.txt; REC=$OUTDIR/record.csv; RCFG=$OUTDIR/record.cfg; RSTART=$OUTDIR/record.start
SCR=$OUTDIR/screen_state
NODE=/sys/class/drm/card0-sde-crtc-0/measured_fps
BATT=/sys/class/power_supply/battery/temp
U=$(stat -c %u /data/data/com.dsh.fpsmeter 2>/dev/null); U=${U:-10461}
G=$(stat -c %g /data/data/com.dsh.fpsmeter 2>/dev/null); G=${G:-10461}
own() { chown "$U:$G" "$1" 2>/dev/null; chmod 600 "$1" 2>/dev/null; restorecon "$1" 2>/dev/null; }
pub() { own "$1.tmp" && mv -f "$1.tmp" "$1" && own "$1"; }
n=0
while true; do
  FV=""
  V=$(cat "$NODE" 2>/dev/null)
  if [ -n "$V" ]; then
    printf '%s\n' "$V" > "$FPS.tmp" 2>/dev/null; pub "$FPS"
    FV=$(printf '%s' "$V" | sed 's/.*fps: *//; s/ .*//')
  fi
  : > "$TH.tmp" 2>/dev/null
  : > "$EXTRAS.tmp" 2>/dev/null
  CPUMAX=""
  for z in /sys/class/thermal/thermal_zone*; do
    t=$(cat "$z/type" 2>/dev/null); v=$(cat "$z/temp" 2>/dev/null)
    [ -z "$t" ] && continue
    case "$t" in
      *trip*|*lvl*|*ibat*|*vbat*|*bcl*|mmw*|sdr*|socd*|usb|vbat)
        printf '%s\t%s\n' "$t" "$v" >> "$EXTRAS.tmp" 2>/dev/null
        continue;;
    esac
    case "$v" in ''|-*) continue;; esac
    i=$((v / 1000)); r=$((v % 1000)); d=$(((r + 50) / 100))
    [ "$d" -ge 10 ] && { i=$((i + 1)); d=0; }
    # 只保留真实温度：1~120℃
    [ "$i" -ge 1 ] && [ "$i" -le 120 ] || continue
    printf '%s\t%s.%s\n' "$t" "$i" "$d" >> "$TH.tmp" 2>/dev/null
    case "$t" in
      cpu-*|cpullc-*) [ -z "$CPUMAX" ] || [ "$v" -gt "$CPUMAX" ] && CPUMAX=$v;;
    esac
  done
  pub "$TH"
  pub "$EXTRAS"
  L=$(dumpsys window 2>/dev/null | grep -m1 "mCurrentFocus")
  case "$L" in
    *" u0 "*) P=${L#* u0 }; P=${P%%/*}; P=$(printf '%s' "$P" | tr -d '}');;
    *) P="";;
  esac
  if [ -n "$P" ]; then printf '%s\n' "$P" > "$FG.tmp" 2>/dev/null; pub "$FG"; fi
  n=$((n + 1))
  # ── 曲线记录 ──
  if [ -f "$RCFG" ]; then
    set -- $(cat "$RCFG" 2>/dev/null)
    EN="$1"; IV="$2"; SLEEP="$3"; TARGET="$4"; MAXMIN="$5"; KEEPMIN="$6"
    [ -z "$IV" ] && IV=2
    STEP=$((IV / 2)); [ "$STEP" -lt 1 ] && STEP=1
    GO=1
    # 熄屏/待机是否记录
    if [ "$GO" = "1" ] && [ "$SLEEP" != "1" ]; then
      S=$(cat "$SCR" 2>/dev/null)
      [ "$S" = "0" ] && GO=0
    fi
    # 指定 App 才记录
    if [ "$GO" = "1" ] && [ -n "$TARGET" ] && [ "$TARGET" != "-" ]; then
      CUR=$(cat "$FG" 2>/dev/null)
      [ "$CUR" != "$TARGET" ] && GO=0
    fi
    # 单次最长时长
    if [ "$GO" = "1" ] && [ -n "$MAXMIN" ] && [ "$MAXMIN" -gt 0 ] 2>/dev/null; then
      if [ ! -f "$RSTART" ]; then date +%s > "$RSTART" 2>/dev/null; own "$RSTART"; fi
      ST0=$(cat "$RSTART" 2>/dev/null); ST0=${ST0:-0}
      NOW0=$(date +%s)
      [ $((NOW0 - ST0)) -gt $((MAXMIN * 60)) ] && GO=0
    fi
    if [ "$EN" = "1" ] && [ "$GO" = "1" ] && [ $((n % STEP)) -eq 0 ] && [ -n "$FV" ]; then
      FI=$(printf '%.0f' "$FV" 2>/dev/null); FI=${FI:-0}
      if [ "$FI" -ge 1 ]; then
        BT=$(cat "$BATT" 2>/dev/null)
        case "$BT" in ''|-*) BT=0;; esac
        CT=${CPUMAX:-0}
        printf '%s,%s,%s,%s\n' "$(date +%s)" "$FV" "$((BT / 10))" "$((CT / 1000))" >> "$REC" 2>/dev/null
        own "$REC"
      fi
    fi
    # 保留最近 N 分钟
    if [ -n "$KEEPMIN" ] && [ "$KEEPMIN" -gt 0 ] 2>/dev/null && [ $((n % 30)) -eq 0 ] && [ -f "$REC" ]; then
      CUT=$(( $(date +%s) - KEEPMIN * 60 ))
      awk -F, -v c="$CUT" '$1 >= c' "$REC" > "$REC.tmp" 2>/dev/null && mv -f "$REC.tmp" "$REC" 2>/dev/null && own "$REC"
    fi
  fi
  if [ $((n % 5)) -eq 1 ]; then
    R=$(dumpsys display 2>/dev/null | grep -m1 -oE 'vsyncRate=[0-9.]+' | cut -d= -f2)
    [ -z "$R" ] && R=$(dumpsys SurfaceFlinger 2>/dev/null | grep -m1 -oE 'vsyncRate=[0-9.]+' | cut -d= -f2)
    [ -n "$R" ] && { printf '%s\n' "$R" > "$RF.tmp" 2>/dev/null; pub "$RF"; }
  fi
  sleep 2
done
