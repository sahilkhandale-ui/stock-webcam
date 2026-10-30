#!/system/bin/sh
# ==============================================================================
# Stock Android 16 UVC Gadget Activation Script
# Enables native USB Video Class (UVC) plug-and-play webcam on any rooted
# or GKI Android device without needing PC software or drivers.
# ==============================================================================

echo "[*] Initializing Native Stock Android UVC Gadget..."

# 1. Reset current USB state
setprop sys.usb.config none
sleep 1

# 2. Locate Linux ConfigFS gadget path
GADGET_PATH=""
if [ -d "/config/usb_gadget/g1" ]; then
    GADGET_PATH="/config/usb_gadget/g1"
elif [ -d "/sys/kernel/config/usb_gadget/g1" ]; then
    GADGET_PATH="/sys/kernel/config/usb_gadget/g1"
fi

if [ -z "$GADGET_PATH" ]; then
    echo "[-] Error: ConfigFS USB gadget path not found on this kernel."
    exit 1
fi

echo "[+] Located USB Gadget at: $GADGET_PATH"

# 3. Create UVC Function
UVC_FUNCTION="$GADGET_PATH/functions/uvc.0"
if [ ! -d "$UVC_FUNCTION" ]; then
    mkdir -p "$UVC_FUNCTION"
    echo "[+] Created UVC function node: uvc.0"
fi

# 4. Setup Control Descriptors
mkdir -p "$UVC_FUNCTION/control/header/h"
ln -sf "$UVC_FUNCTION/control/header/h" "$UVC_FUNCTION/control/class/fs/h" 2>/dev/null
ln -sf "$UVC_FUNCTION/control/header/h" "$UVC_FUNCTION/control/class/ss/h" 2>/dev/null

# 5. Link to active configuration
CONFIG_PATH="$GADGET_PATH/configs/b.1"
if [ -d "$CONFIG_PATH" ]; then
    ln -sf "$UVC_FUNCTION" "$CONFIG_PATH/f_uvc" 2>/dev/null
    echo "[+] Linked UVC function to USB configuration b.1"
fi

# 6. Rebind to USB Device Controller
UDC_CONTROLLER=$(getprop sys.usb.controller)
if [ -n "$UDC_CONTROLLER" ]; then
    echo "$UDC_CONTROLLER" > "$GADGET_PATH/UDC"
    echo "[+] Rebound UDC controller: $UDC_CONTROLLER"
fi

echo "[✓] Native UVC Webcam gadget active! Check host PC device manager."

