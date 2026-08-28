#!/usr/bin/env bash
# ============================================================
# 「自己」Release 打包脚本 v1.0
# 由于 AGP 9.0 在 ARM64/proot 环境下 packageRelease 存在
# "资源包(ap_)未合并进 APK" 的兼容性 bug（任务谎报成功），
# 本脚本在 assembleRelease 之后手动合并资源并重新签名。
#
# 用法: ./build_release.sh [versionName]   （默认 1.0）
# 输出: /sdcard/Download/自己_v{version}.apk
# ============================================================
set -e

cd "$(dirname "$0")"
VERSION="${1:-1.0}"
OUT_NAME="自己_v${VERSION}.apk"
RELEASE_DIR="app/build/outputs/apk/release"
KEYSTORE="keystore/release.jks"
KS_PASS="mgxd2026"
AAPT2="/root/Android/build-tools/35.0.0/aapt2"
APKSIGNER="/root/Android/build-tools/35.0.0/apksigner"

echo "▶ 1/4 构建 assembleRelease ..."
export GRADLE_USER_HOME=/root/.gradle
./gradlew assembleRelease --no-daemon

echo "▶ 2/4 检查资源是否已打包 ..."
python3 - <<'PY'
import zipfile, sys
p = "app/build/outputs/apk/release/app-release.apk"
z = zipfile.ZipFile(p)
names = z.namelist()
if "resources.arsc" in names and "AndroidManifest.xml" in names:
    print("   资源已完整，无需修复 ✓")
    sys.exit(0)
print("   发现资源缺失，进入手动修复流程")
sys.exit(1)
PY

FIX_NEEDED=$?

if [ "$FIX_NEEDED" = "0" ]; then
    FIXED="$RELEASE_DIR/app-release.apk"
else
    echo "▶ 3/4 合并资源并重新签名 ..."
    python3 - <<'PY'
import zipfile, shutil
apk  = "app/build/outputs/apk/release/app-release.apk"
res  = "app/build/intermediates/linked_resources_binary_format/release/processReleaseResources/linked-resources-binary-format-release.ap_"
fix  = "app/build/outputs/apk/release/app-release-fixed.apk"
shutil.copy(apk, fix)
zin  = zipfile.ZipFile(res)
zout = zipfile.ZipFile(fix, "a", zipfile.ZIP_DEFLATED)
existing = set(zout.namelist())
added = 0
for info in zin.infolist():
    if info.filename not in existing and not info.filename.startswith("META-INF/"):
        zout.writestr(info, zin.read(info.filename))
        added += 1
zout.close(); zin.close()
print(f"   已合并 {added} 个资源条目")
PY
    "$APKSIGNER" sign \
        --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
        --out "$RELEASE_DIR/app-release-final.apk" "$RELEASE_DIR/app-release-fixed.apk"
    FIXED="$RELEASE_DIR/app-release-final.apk"
fi

echo "▶ 4/4 输出到 Download ..."
cp "$FIXED" "/sdcard/Download/$OUT_NAME"

echo ""
echo "=========================================="
echo " ✅ 打包完成: /sdcard/Download/$OUT_NAME"
echo "    版本: v$VERSION | 包名: com.mgxd.mirror"
echo "    签名: MirrorSelf (release.jks)"
echo "=========================================="
