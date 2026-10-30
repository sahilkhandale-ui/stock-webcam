package com.stockwebcam.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UsbStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "android.hardware.usb.action.USB_STATE") {
            val connected = intent.extras?.getBoolean("connected") ?: false
            val configured = intent.extras?.getBoolean("configured") ?: false
            val uvcActive = intent.extras?.getBoolean("uvc") ?: false

            Log.i("UsbStateReceiver", "USB state changed: connected=$connected, configured=$configured, uvc=$uvcActive")
            
            // Notify active listeners or MainActivity
            val stateIntent = Intent(ACTION_USB_STATUS_CHANGED).apply {
                putExtra("connected", connected)
                putExtra("configured", configured)
                putExtra("uvc", uvcActive)
                setPackage(context.packageName)
            }
            context.sendBroadcast(stateIntent)
        }
    }

    companion object {
        const val ACTION_USB_STATUS_CHANGED = "com.stockwebcam.app.USB_STATUS_CHANGED"
    }
}

