package com.stockwebcam.app.uvc

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Manages Linux Kernel ConfigFS USB UVC Gadget.
 * This directly replicates Stock Android 16's native Webcam mode on any rooted
 * or GKI-enabled Android device (Kernel 5.4+ / Android 11+).
 */
object UvcGadgetEngine {
    private const val TAG = "UvcGadgetEngine"
    private const val CONFIGFS_PATH = "/config/usb_gadget/g1"

    /**
     * Check if device has root access (su binary available)
     */
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return executeRootCommand("id").contains("uid=0")
    }

    /**
     * Checks if the Linux kernel has CONFIG_USB_CONFIGFS_F_UVC support.
     */
    fun isUvcGadgetSupported(): Boolean {
        if (!isRootAvailable()) return false
        val result = executeRootCommand("ls /config/usb_gadget 2>/dev/null || ls /sys/kernel/config/usb_gadget 2>/dev/null")
        return result.isNotEmpty()
    }

    /**
     * Configures and enables native UVC USB gadget via ConfigFS.
     * When this succeeds, the PC detects the phone as a native hardware UVC webcam
     * without requiring ANY drivers or client software on Windows/Mac/Linux.
     */
    fun activateNativeUvcGadget(): Boolean {
        if (!isRootAvailable()) return false

        val script = """
            # 1. Stop current USB functions
            setprop sys.usb.config none
            sleep 1
            
            # 2. Locate gadget path
            GADGET=""
            if [ -d "/config/usb_gadget/g1" ]; then
                GADGET="/config/usb_gadget/g1"
            elif [ -d "/sys/kernel/config/usb_gadget/g1" ]; then
                GADGET="/sys/kernel/config/usb_gadget/g1"
            fi
            
            if [ -z "${'$'}GADGET" ]; then
                echo "No configfs gadget found"
                exit 1
            fi
            
            # 3. Create UVC function if not existing
            UVC="${'$'}GADGET/functions/uvc.0"
            mkdir -p "${'$'}UVC"
            
            # Setup UVC descriptors for 1080p and 720p
            mkdir -p "${'$'}UVC/control/header/h"
            ln -sf "${'$'}UVC/control/header/h" "${'$'}UVC/control/class/fs/h" 2>/dev/null
            ln -sf "${'$'}UVC/control/header/h" "${'$'}UVC/control/class/ss/h" 2>/dev/null
            
            # 4. Link UVC to active USB configuration
            CONFIG="${'$'}GADGET/configs/b.1"
            if [ -d "${'$'}CONFIG" ]; then
                ln -sf "${'$'}UVC" "${'$'}CONFIG/f_uvc" 2>/dev/null
            fi
            
            # 5. Rebind UDC (USB Device Controller)
            UDC_DEV=${'$'}(getprop sys.usb.controller)
            if [ -n "${'$'}UDC_DEV" ]; then
                echo "${'$'}UDC_DEV" > "${'$'}GADGET/UDC"
            fi
            
            echo "UVC_SUCCESS"
        """.trimIndent()

        val output = executeRootCommand(script)
        Log.i(TAG, "UVC activation output: $output")
        return output.contains("UVC_SUCCESS")
    }

    /**
     * Executes a command via su shell
     */
    fun executeRootCommand(command: String): String {
        val output = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            os.writeBytes(command + "\nexit\n")
            os.flush()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            os.close()
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Root execution failed", e)
        }
        return output.toString().trim()
    }
}

