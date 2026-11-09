#!/usr/bin/env bash
# Bol Kisan - Offline Android Bundle Installer (Bash)
# Automates APK installation and model sideloading for field officers

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="${SCRIPT_DIR}/app-debug.apk"
MODEL_PATH="${SCRIPT_DIR}/qwen3_0_6b.litertlm"

echo "=========================================================="
echo "       Bol Kisan Offline Field Bundle Deployment          "
echo "=========================================================="

# Check ADB
if ! command -v adb &> /dev/null; then
    echo "[ERROR] 'adb' tool not found on PATH. Please install Android Platform Tools."
    exit 1
fi

# Check Device
DEVICE=$(adb devices | grep -w "device" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "[ERROR] No connected Android device found. Connect device via USB and enable USB Debugging."
    exit 1
fi
echo "[OK] Connected device detected: $DEVICE"

# Install APK
if [ -f "$APK_PATH" ]; then
    echo "Installing Bol Kisan APK ($APK_PATH)..."
    adb install -r -d "$APK_PATH"
    echo "[OK] App successfully installed."
else
    echo "[WARN] app-debug.apk not found in bundle directory."
fi

# Sideload Model Artifact
if [ -f "$MODEL_PATH" ]; then
    echo "Sideloading LiteRT-LM model to device storage..."
    adb push "$MODEL_PATH" /sdcard/Download/qwen3_0_6b.litertlm
    echo "[OK] Model successfully pushed to /sdcard/Download/qwen3_0_6b.litertlm."
else
    echo "[INFO] qwen3_0_6b.litertlm not found in bundle. Run download_model.py first to include SLM weights."
fi

# Launch App
echo "Launching Bol Kisan..."
adb shell am start -n com.bolkisan.farmer/.MainActivity

echo "=========================================================="
echo " Deployment Complete! App is ready for offline operation. "
echo "=========================================================="
