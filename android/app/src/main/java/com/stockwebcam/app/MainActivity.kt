package com.stockwebcam.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.stockwebcam.app.camera.CameraEngine
import com.stockwebcam.app.databinding.ActivityMainBinding
import com.stockwebcam.app.receiver.UsbStateReceiver
import com.stockwebcam.app.service.WebcamService
import com.stockwebcam.app.utils.NetworkUtils
import com.stockwebcam.app.uvc.UvcGadgetEngine

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var cameraEngine: CameraEngine? = null
    private var webcamService: WebcamService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WebcamService.LocalBinder
            webcamService = binder.getService()
            isBound = true

            // Initialize server with remote control handler
            webcamService?.initServer { command, value ->
                runOnUiThread {
                    handleRemoteControlCommand(command, value)
                }
            }

            // Start Camera
            setupCamera()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            webcamService = null
        }
    }

    private val usbStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbStateReceiver.ACTION_USB_STATUS_CHANGED) {
                val connected = intent.getBooleanExtra("connected", false)
                updateUsbStatusBadge(connected)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            startWebcamForegroundService()
        } else {
            Toast.makeText(this, "Camera permission is required for Webcam operation", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on while app is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissionsAndStart()
        checkNativeUvcSupport()

        val filter = IntentFilter(UsbStateReceiver.ACTION_USB_STATUS_CHANGED)
        ContextCompat.registerReceiver(
            this,
            usbStatusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startWebcamForegroundService()
        } else {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startWebcamForegroundService() {
        val serviceIntent = Intent(this, WebcamService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupCamera() {
        cameraEngine = CameraEngine(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView
        ) { jpegBytes ->
            webcamService?.pushFrame(jpegBytes)
        }

        cameraEngine?.startCamera {
            updateStreamUrlUI()
        }
    }

    private fun setupUI() {
        // Zoom Buttons
        binding.btnZoom1x.setOnClickListener {
            cameraEngine?.setZoomRatio(1.0f)
            highlightZoomButton(binding.btnZoom1x)
        }
        binding.btnZoom2x.setOnClickListener {
            cameraEngine?.setZoomRatio(2.0f)
            highlightZoomButton(binding.btnZoom2x)
        }
        binding.btnZoom5x.setOnClickListener {
            cameraEngine?.setZoomRatio(5.0f)
            highlightZoomButton(binding.btnZoom5x)
        }

        // Camera Flip
        binding.btnFlipCamera.setOnClickListener {
            cameraEngine?.flipCamera()
            val isBack = cameraEngine?.isUsingBackCamera() == true
            binding.btnTorch.visibility = if (isBack) View.VISIBLE else View.GONE
        }

        // Flashlight Torch
        binding.btnTorch.setOnClickListener {
            val isOn = cameraEngine?.toggleTorch() ?: false
            val tint = if (isOn) getColor(R.color.primary) else getColor(R.color.text_primary)
            binding.btnTorch.setColorFilter(tint)
        }

        // HQ Mode Toggle
        binding.btnHqMode.setOnClickListener {
            val current = cameraEngine?.isHqEnabled() ?: false
            val newHq = !current
            cameraEngine?.setHqMode(newHq)
            val tint = if (newHq) getColor(R.color.primary) else getColor(R.color.text_primary)
            binding.btnHqMode.setColorFilter(tint)
            Toast.makeText(this, if (newHq) "HQ 1080p mode active" else "Standard mode", Toast.LENGTH_SHORT).show()
        }

        // AMOLED Black Screen Mode
        binding.btnScreenOff.setOnClickListener {
            binding.overlayScreenOff.visibility = View.VISIBLE
        }

        // Double tap detector to wake AMOLED black screen
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                binding.overlayScreenOff.visibility = View.GONE
                return true
            }
        })
        binding.overlayScreenOff.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Copy Stream URL
        binding.btnCopyUrl.setOnClickListener {
            val url = binding.tvStreamUrl.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Stock Webcam URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Stream URL copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        updateStreamUrlUI()
    }

    private fun highlightZoomButton(selectedView: View) {
        val views = listOf(binding.btnZoom1x, binding.btnZoom2x, binding.btnZoom5x)
        for (v in views) {
            if (v == selectedView) {
                v.setTextColor(getColor(R.color.primary))
            } else {
                v.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }

    private fun updateStreamUrlUI() {
        val ipInfo = NetworkUtils.getStreamIpAddress(this)
        // Show USB ADB url as primary since it works on 100% of PCs without USB tethering
        binding.tvStreamUrl.text = "http://localhost:8080"

        val connText = when (ipInfo.type) {
            NetworkUtils.ConnectionType.ADB_USB_CABLE -> "Direct USB Cable (ADB)"
            NetworkUtils.ConnectionType.WIFI -> "Wi-Fi: ${ipInfo.getStreamUrl(8080)}"
            NetworkUtils.ConnectionType.LOCAL_NETWORK -> "Local IP: ${ipInfo.getStreamUrl(8080)}"
        }
        binding.tvStatusBadge.text = connText
    }

    private fun updateUsbStatusBadge(connected: Boolean) {
        if (connected) {
            binding.tvStatusBadge.text = "Connected via USB"
            binding.tvStatusBadge.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvStatusBadge.text = "Ready"
            binding.tvStatusBadge.setTextColor(getColor(R.color.text_secondary))
        }
        updateStreamUrlUI()
    }

    private fun checkNativeUvcSupport() {
        Thread {
            val hasRoot = UvcGadgetEngine.isRootAvailable()
            val hasUvc = hasRoot && UvcGadgetEngine.isUvcGadgetSupported()

            runOnUiThread {
                if (hasUvc) {
                    binding.tvUsbModeTitle.text = "Native UVC Gadget: Available (Root)"
                    binding.tvHelperHint.text = "🎉 Device supports Stock Android 16 Native UVC Gadget! Host PC detects as native webcam."
                } else {
                    binding.tvUsbModeTitle.text = "Direct USB Cable Stream (No Tethering)"
                    binding.tvHelperHint.text = "⚡ Plug USB cable into PC -> Run StockWebcam-Windows.bat -> Video appears in browser, OBS, or Zoom!"
                }
            }
        }.start()
    }

    private fun handleRemoteControlCommand(command: String, value: String?) {
        when (command) {
            "flip" -> binding.btnFlipCamera.performClick()
            "torch" -> binding.btnTorch.performClick()
            "zoom_in" -> {
                val current = cameraEngine?.getZoomRatio() ?: 1.0f
                cameraEngine?.setZoomRatio((current + 0.5f).coerceAtMost(10.0f))
            }
            "zoom_out" -> {
                val current = cameraEngine?.getZoomRatio() ?: 1.0f
                cameraEngine?.setZoomRatio((current - 0.5f).coerceAtLeast(1.0f))
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        cameraEngine?.release()
        try {
            unregisterReceiver(usbStatusReceiver)
        } catch (ignored: Exception) {}
        super.onDestroy()
    }
}

