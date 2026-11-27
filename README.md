# FPS 悬浮窗（Android 帧率 / 温度监控）

一个轻量的 Android 悬浮窗：实时显示**硬件实测帧率**、屏幕刷新率、电池温度、**全部 thermal_zone 传感器温度**，并支持帧率/温度曲线记录与缩放查看。

开发验证机型：**OnePlus Ace 7 Pro（PMS110 / Android 17 / 骁龙 8 系 / 内核 6.12.69）**。

---

## 一、这个工具能用在哪台手机？

分三块看，**不是所有机型都全功能可用**。

| 能力 | 数据来源 | 谁有 |
|---|---|---|
| **帧率（核心）** | `/sys/class/drm/card0-sde-crtc-0/measured_fps` | **仅高通（骁龙）平台的 SDE 显示驱动**，且内核暴露该节点 |
| 温度 | `/sys/class/thermal/thermal_zone*` | Linux 标准接口，**几乎全部 Android** 都有 |
| 屏幕刷新率 | `dumpsys display` 的 `vsyncRate` | **通用** |
| 前台应用识别 | `dumpsys window` 的 `mCurrentFocus` | **通用**（个别 ROM 输出格式不同） |
| 读取以上 sysfs | root | 无 root 只能读电池温度 |

### 机型分类

| 平台 | 帧率 | 温度 | 说明 |
|---|---|---|---|
| 骁龙 8 系（SM8550/8650/8750 等） | ✅ 大概率可用 | ✅ | 本项目验证环境 |
| 骁龙 7 系 / 6 系 | ⚠️ 部分可用 | ✅ | 取决于内核是否导出 `measured_fps` |
| 联发科天玑 | ❌ 无此节点 | ✅ | 帧率会显示 `--`，温度正常 |
| 三星 Exynos | ❌ | ✅ | 同上 |
| 华为麒麟 | ❌ | ✅ | 同上 |
| Google Tensor | ⚠️ 部分 | ✅ | 需实测 |

### 30 秒判定你的手机能不能用

把 `probe.sh` 推到手机，用 root 跑一次：

```bash
adb push probe.sh /data/local/tmp/
su -c "sh /data/local/tmp/probe.sh"
```

它会输出：设备型号、root 状态、**是否存在帧率节点**、thermal 传感器数量、刷新率、前台窗口、电池温度，最后给一行结论。

- 看到 `帧率: 可用` → 全功能
- 看到 `帧率: 不可用` → 温度/刷新率可用，帧率需要自己改脚本里的节点路径（见第六节）

---

## 二、前置条件（配件 / 环境）

| 需要 | 说明 |
|---|---|
| **root 权限** | 能执行 `su -c`。Magisk / KernelSU / APatch 均可；本项目是在**临时 root（内核 LPE，不刷机）**环境下验证的 |
| **配套脚本 `fps_feed.sh`** | root 侧采样脚本，必须部署并运行，否则帧率/温度读不到 |
| Android 8.0+ | `minSdk 26` |
| 一个存放脚本的目录 | 建议 `/data/adb/JN/bin/`（KernelSU/Magisk 通用），任意可执行目录也可以 |

> 无 root 时应用能装、能开，但只显示电池温度，其余一律 `--`。

---

## 三、安装与部署（分步）

### 步骤 1：装 APK

```bash
adb install -r FPSOverlay.apk
```

或直接把 APK 拷到手机点安装。

### 步骤 2：授予悬浮窗权限

打开 App → 点「授予悬浮窗权限」→ 在系统页面允许「显示在其他应用上层」。
（也可用命令：`adb shell cmd appops set com.dsh.fpsmeter SYSTEM_ALERT_WINDOW allow`）

### 步骤 3：部署 root 侧脚本

```bash
adb push fps_feed.sh /data/local/tmp/
adb shell su -c "mkdir -p /data/adb/JN/bin && cp /data/local/tmp/fps_feed.sh /data/adb/JN/bin/ && chmod 755 /data/adb/JN/bin/fps_feed.sh"
```

### 步骤 4：启动脚本

```bash
adb shell su -c "setsid sh /data/adb/JN/bin/fps_feed.sh </dev/null >/dev/null 2>&1 &"
```

启动后检查数据是否产出：

```bash
adb shell su -c "cat /data/data/com.dsh.fpsmeter/files/fps.txt"
```

正常应看到类似 `fps: 62.4 duration:1000000 frame_count:64`。

### 步骤 5：开机自启（可选，推荐）

**Magisk / KernelSU**：把脚本放进 `/data/adb/service.d/`

```bash
adb shell su -c "cp /data/adb/JN/bin/fps_feed.sh /data/adb/service.d/fps_feed.sh && chmod 755 /data/adb/service.d/fps_feed.sh"
```

**临时 root / 手动方案**：每次重启后重跑步骤 4。

### 步骤 6：打开悬浮窗

App 内设置好要显示的项 → 点「开启悬浮窗」。

---

## 四、App 内各项设置说明

| 设置 | 作用 |
|---|---|
| 显示帧率 / 刷新率 / 电池温度 | 顶栏三行读数开关 |
| 仅在游戏内显示 | 勾上后只有前台包名命中「游戏列表」才显示 |
| 允许触摸悬浮窗 | 关掉后悬浮窗完全不拦截触摸（打游戏不误触） |
| 温度名称显示中文 | `cpu-0-2-1` → `CPU0核2-1`、`gpuss-3` → `GPU核3` |
| 悬浮窗透明度 / 缩放 | 10–100% / 60–200% |
| 温度传感器列表（98 项） | 逐项勾选，全选 / 全不选 / 预设「CPU 大核 + GPU」 |
| 记录帧率/温度曲线 | 开启后写 CSV |
| 采样间隔 秒 | 2–10 秒，越大越省 |
| 熄屏 / 待机时也记录 | 默认关；关掉时熄屏不产生数据 |
| 指定 App 记录 | 填包名 → 只有该 App 前台时才记录 |
| 单次最长记录 分钟 | 0=不限；到点自动停 |
| 只保留最近 分钟 | 0=不限；每 60 秒裁剪一次 |
| 清空记录 | 有二次确认 |

### 悬浮窗本体操作

| 位置 | 操作 |
|---|---|
| `⋮⋮` | 按住拖动位置 |
| `—` | 收起成一条小横条（再点 `▸` 展开） |
| `✕` | 关闭悬浮窗 |
| 内容区 | 上下滑动查看全部传感器 |

### 曲线页操作

| 操作 | 效果 |
|---|---|
| 点空白处 | 放置白线并显示该点数值 |
| 按在白线上拖 | 只移动白线，实时读数 |
| 其他位置横滑 | 平移曲线（看前/后时段） |
| 双指捏合 | 缩放时间轴 |
| 缩小 / 放大 / 复位 | 按钮 |
| 横屏 / 竖屏 | 切换方向，横屏时间轴更宽 |
| 数值 | 列表点任意一条，曲线跳到该位置 |

---

## 四、界面与交互

（原「四、App 内各项设置说明」保留在下一节）

### 悬浮窗（拆成两个窗口）

| 部件 | 内容 | 触摸行为 |
|---|---|---|
| 把手条 | `✥ — ✕` | **始终可触摸**（很小，不挡游戏） |
| 内容区 | 帧率 / 刷新率 / 温度列表 | 跟随「允许触摸悬浮窗」开关，关闭时 `FLAG_NOT_TOUCHABLE` 完全穿透 |

- **长按 `✥` 约 0.28 秒再滑动**才会移动窗口（避免误触），拖动时按钮变 `✥拖`
- `—` 收起成一条小横条，`✕` 直接关闭；内容区可上下滚动
- 不透明度 0–100%（0 = 全透明）、缩放 60–200%
- **日间模式**：设置页与悬浮窗一起切换成浅底深字

### 曲线页

| 操作 | 效果 |
|---|---|
| 点空白处 | 放置白线并显示该点数值 |
| 按在白线上拖 | 只移动白线，顶部实时更新 |
| 其他位置横滑 | 平移时间轴 |
| 双指捏合 | 缩放 |
| 缩小 / 放大 / 复位 | 按钮 |
| 横屏 / 竖屏 | 切换方向 |
| 数值 | 最近 80 条列表，点任意一条跳到该位置 |
| 清空（右上角红色） | 二次确认，「删除」红字、「取消」蓝字 |

- 左侧 10 档双轴刻度：蓝色 = 帧率、橙色 = 温度
- 白线位置有两条水平虚线，分别对齐帧率与温度刻度
- 温度按类型分色：默认 60/80℃，PMIC 类 80/100℃，`sys-therm-9` 95/110℃，电池 45/55℃

### 非温度节点单独显示

内核把不少非温度量也注册成 `thermal_zone`，这些单独列在设置页底部并注明含义，不参与悬浮窗显示：

| 节点 | 含义 |
|---|---|
| `*-bcl-lvl*` | 电池电流限制等级（0 = 未限制） |
| `*-ibat-lvl*` | 输入电流阈值（mA） |
| `vbat` / `usb` | 电池 / USB 电压（mV） |
| `socd` | SoC 裸片（毫度，未校准） |
| `mmw_*` / `sdr*` | 未接传感器（-273 = 无数据） |
| `*trip*` | 降频阈值（不是当前温度） |

## 五、数据文件位置

```
/data/data/com.dsh.fpsmeter/files/
├── fps.txt          帧率（root 脚本写）
├── thermal.txt      全部温度（TSV，root 脚本写）
├── refresh.txt      刷新率
├── foreground.txt   前台包名
├── record.csv       曲线记录（时间,FPS,电池,CPU）
├── record.cfg       记录配置（开关 间隔 熄屏 App 最长 保留）
├── record.start     单次记录开始时间戳
└── screen_state     亮屏状态（App 写）
```

---

## 六、换机型怎么适配

### 1. 帧率节点不同

改 `fps_feed.sh` 顶部：

```sh
NODE=/sys/class/drm/card0-sde-crtc-0/measured_fps
```

先用 `probe.sh` 或下面命令找出真实节点：

```bash
su -c "ls /sys/class/drm/*/measured_fps; ls /sys/kernel/fpsgo/common/ 2>/dev/null"
```

- 联发科常见：`/sys/kernel/fpsgo/common/fps`
- 没有硬件节点时，帧率功能不可用（不要用 `dumpsys SurfaceFlinger --latency` 兜底，新 ROM 已裁剪）

### 2. 温度名称映射

`Names.java` 里是按前缀匹配（`cpu-` / `cpullc-` / `gpuss-` / `qmx-` / `nsphvx` …），其他平台出现新前缀（如 `mtktscpu`、`CPU-therm`）时加一条分支即可。

### 3. 包名不同

改 `mkmanifest.py` 里的 `com.dsh.fpsmeter`，同时改 `fps_feed.sh` 里的 `OUTDIR`，否则脚本写不到应用目录。

---

## 七、故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 帧率一直 `--` | 脚本没跑 / 节点路径不对 | 检查 `files/fps.txt` 是否存在、手动 `cat` 节点 |
| 温度列表为空 | 脚本没跑 / 目录属主不对 | `su -c "chown <应用uid>:<应用uid> /data/data/com.dsh.fpsmeter/files"` |
| 记录不增长 | 记录开关没开 / 熄屏 / 前台 App 不匹配 | 看 `record.cfg` 六段配置 |
| 游戏内不显示 | 「仅在游戏内显示」开着但包名没加进列表 | 设置页输入包名→「加入游戏列表」 |
| 悬浮窗按钮点不到 | 透明度太低或位置贴边 | 调高透明度、拖回屏幕内 |
| 重启后数据全无 | 临时 root 失效 | 重跑 root + 第四节步骤 4 |
| 应用装完闪退 | 设备有调试器/注入框架被壳检测 | 关掉 frida 等调试进程 |

---

## 八、构建

依赖：JDK 8+、Android SDK（`android.jar` + `build-tools` 含 `d8/zipalign/apksigner`）、Python 3。

```bash
export ANDROID_JAR=/path/to/platforms/android-XX/android.jar
export BUILD_TOOLS=/path/to/build-tools/xx.x.x
./build.sh
```

`build.sh` 流程：

1. `mkmanifest.py` 生成二进制 `AndroidManifest.xml`（**不用 aapt**，绕开新版 `resources.arsc` 兼容问题）
2. `javac` → `d8` 得 `classes.dex`
3. 打包 → `zipalign` → `apksigner`（首次自动生成 `ks.jks`）

---

## 九、安全说明

- 所有读取均为**只读**（`cat` / `dumpsys`），不写系统分区、不改 vbmeta、不动内核参数
- 应用**不联网**，不上传任何数据
- 记录文件只存在应用私有目录，可用「清空记录」删除
- 仓库不包含签名密钥

## License

MIT
