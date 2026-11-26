#!/usr/bin/env bash
# 构建 FPS 悬浮窗 APK
# 依赖：JDK8+、Android SDK(build-tools 含 d8/zipalign/apksigner)、Python3
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_JAR:?请先 export ANDROID_JAR=/path/to/android.jar}"
: "${BUILD_TOOLS:?请先 export BUILD_TOOLS=/path/to/build-tools/xx.x.x}"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

echo "== 1/6 生成 AndroidManifest.xml (binary)"
python3 mkmanifest.py

echo "== 2/6 编译 java"
rm -rf out dex && mkdir -p out dex
javac -source 8 -target 8 -nowarn -classpath "$ANDROID_JAR" -d out src/com/dsh/fpsmeter/*.java

echo "== 3/6 d8 -> classes.dex"
"$D8" --lib "$ANDROID_JAR" --release --min-api 26 --output dex out/com/dsh/fpsmeter/*.class

echo "== 4/6 打包"
python3 - <<'PY'
import zipfile
with zipfile.ZipFile("app-unsigned.apk", "w") as z:
    z.write("AndroidManifest.bin", "AndroidManifest.xml")
    z.write("dex/classes.dex", "classes.dex")
PY

echo "== 5/6 zipalign"
"$ZIPALIGN" -f -p 4 app-unsigned.apk app-aligned.apk

echo "== 6/6 签名"
if [ ! -f ks.jks ]; then
  echo "生成签名密钥 ks.jks（密码 123456，仅供本地测试）"
  keytool -genkeypair -keystore ks.jks -alias app -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass 123456 -keypass 123456 -dname "CN=FPS Overlay, O=Local, C=CN"
fi
"$APKSIGNER" sign --ks ks.jks --ks-pass pass:123456 --key-pass pass:123456 \
  --out FPSOverlay.apk app-aligned.apk

echo "完成：$(pwd)/FPSOverlay.apk"
