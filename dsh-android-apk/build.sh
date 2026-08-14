#!/bin/bash
# 构建 DSH Installer APK
set -e

SDK=/opt/android-sdk
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
PROJ=/workspace/dsh-android-apk
BUILD="$PROJ/build"
TERMUX_APK=/opt/termux-dl/termux-app.apk

mkdir -p "$BUILD/gen" "$BUILD/obj"

echo "==> 1/5 编译资源"
"$BT/aapt2" compile --dir "$PROJ/app/res" -o "$BUILD/gen/res.zip"

echo "==> 2/5 链接资源与 Manifest"
"$BT/aapt2" link \
  -o "$BUILD/base.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/app/AndroidManifest.xml" \
  -A "$PROJ/app/assets" \
  "$BUILD/gen/res.zip"

echo "==> 3/5 编译 Java"
mkdir -p "$BUILD/obj-v2"
javac --release 8 \
  -classpath "$PLATFORM" \
  -d "$BUILD/obj-v2" \
  "$PROJ/app/src/com/dsh/installer/MainActivity.java"

echo "==> 4/5 打包 dex 与资源"
"$BT/d8" --release --lib "$PLATFORM" --output "$BUILD" \
  $(find "$BUILD/obj-v2" -name '*.class')

# 将 classes.dex 与 Termux APK 加入 base.apk
cd "$BUILD"
python3 - <<'PY'
import zipfile
apk = zipfile.ZipFile('/workspace/dsh-android-apk/build/base.apk', 'a')
apk.write('/workspace/dsh-android-apk/build/classes.dex', 'classes.dex')
apk.write('/opt/termux-dl/termux-app.apk', 'assets/termux.apk')
apk.close()
PY

echo "==> 5/5 对齐并签名"
# 生成签名密钥
if [ ! -f "$PROJ/dsh.keystore" ]; then
  keytool -genkeypair -v \
    -keystore "$PROJ/dsh.keystore" \
    -alias dsh -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass dshinstall -keypass dshinstall \
    -dname "CN=DSH Installer, O=DSH, C=CN" >/dev/null 2>&1
fi

"$BT/zipalign" -f 4 "$BUILD/base.apk" "$BUILD/base-aligned.apk"
"$BT/apksigner" sign \
  --ks "$PROJ/dsh.keystore" \
  --ks-pass pass:dshinstall \
  --key-pass pass:dshinstall \
  --out "/workspace/dsh-android-apk/DSH-Installer-v1.0.apk" \
  "$BUILD/base-aligned.apk"

echo "==> 完成"
ls -la /workspace/dsh-android-apk/DSH-Installer-v1.0.apk
