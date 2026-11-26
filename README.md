# FPS 悬浮窗（Android 帧率 / 温度监控）

一个轻量的 Android 悬浮窗，实时显示**硬件实测帧率**、屏幕刷新率、电池温度，以及**全部 thermal_zone 传感器温度**，并支持帧率/温度曲线记录、缩放查看。

> 本项目是在一台 **OnePlus Ace 7 Pro（PMS110 / Android 17 / 骁龙 8 系）** 上开发验证的，针对该平台做了适配。换机型前请先读「兼容性」一节。

## 功能

- **帧率**：读取高通 SDE 显示管线的 `measured_fps` 节点，是硬件真实出帧数（不是估算）
- **屏幕刷新率**：`dumpsys display` 的 `activeMode vsyncRate`
- **电池温度**：`BatteryManager`
- **全部温度传感器**：遍历 `/sys/class/thermal/thermal_zone*`，逐项可开关，自动过滤 `*trip*` 阈值节点
- **中文名称映射**：`cpu-0-2-1` → `CPU0核2-1`、`gpuss-3` → `GPU核3`、`qmx-1-0` → `内存1-0`
- **温度分色**：<60℃ 白、60–80℃ 黄、>80℃ 红
- **仅在游戏内显示**：读取前台包名，可配置游戏列表
- **悬浮窗自身**：可拖动、可收起（—）、可关闭（✕）、透明度与缩放可调、可设触摸穿透（不挡游戏）
- **曲线记录**：帧率 + CPU 温度按间隔写入 CSV，支持
  - 熄屏/待机是否记录
  - 只在指定 App 前台时记录
  - 单次最长记录时长
  - 只保留最近 N 分钟
- **曲线查看**：双指缩放、空白处平移、按白线拖动看数值、10 档双轴刻度、FPS/温度双虚线标记、横屏、数值列表点击跳转

## 原理

应用本身**不申请 root**，所有需要 root 的读取由一个独立脚本在 root shell 里完成，把数据写进应用私有目录：

```
/data/data/<pkg>/files/fps.txt          # 帧率
/data/data/<pkg>/files/thermal.txt      # 全部温度（TSV）
/data/data/<pkg>/files/refresh.txt      # 刷新率
/data/data/<pkg>/files/foreground.txt   # 前台包名
/data/data/<pkg>/files/record.csv       # 曲线记录
/data/data/<pkg>/files/screen_state     # 亮屏/熄屏（应用写入）
/data/data/<pkg>/files/record.cfg       # 记录配置
```

脚本 `fps_feed.sh` 每 2 秒采样一次，用 `chown` 把文件交给应用 uid，应用直接读文件即可。

## 构建

依赖：
- JDK 8+
- Android SDK：`platforms/android-XX/android.jar`、`build-tools`（含 `d8`、`zipalign`、`apksigner`）
- Python 3

```bash
export ANDROID_JAR=/path/to/android.jar
export BUILD_TOOLS=/path/to/build-tools/xx.x.x
./build.sh
```

`build.sh` 会：
1. 用 `mkmanifest.py` 生成二进制 `AndroidManifest.xml`（不依赖 aapt，绕开新版 `resources.arsc` 的兼容问题）
2. `javac` 编译 → `d8` 转 dex
3. 打包 + `zipalign` + `apksigner` 签名（首次运行会生成 `ks.jks`）

## 安装与使用

1. 安装 APK
2. 授予「显示在其他应用上层」权限
3. 把 `fps_feed.sh` 放到 `/data/adb/JN/bin/`（或任意路径），用 root 启动：
   ```bash
   su -c "setsid sh /data/adb/JN/bin/fps_feed.sh </dev/null >/dev/null 2>&1 &"
   ```
4. 打开 App → 勾选要显示的项 → 开启悬浮窗

> 重启后 root 失效，需要重新执行第 3 步。

## 兼容性

| 功能 | 依赖 | 说明 |
|---|---|---|
| 帧率 | `/sys/class/drm/card0-sde-crtc-0/measured_fps` | 高通 SDE 显示驱动专属；MTK/Exynos 无此节点 |
| 温度 | `/sys/class/thermal/thermal_zone*` | Linux 标准接口，绝大多数设备可用 |
| 刷新率 | `dumpsys display` | 通用 |
| 前台应用 | `dumpsys window` 的 `mCurrentFocus` | 通用，个别 ROM 输出格式不同 |
| 记录 | root 脚本 | 无 root 时只能读电池温度 |

## 安全说明

- 全部读取均为**只读**（`cat`/`dumpsys`），不写任何系统分区、不改 vbmeta、不动内核参数
- 不联网、不上传任何数据
- 记录文件只存在应用私有目录

## License

MIT
