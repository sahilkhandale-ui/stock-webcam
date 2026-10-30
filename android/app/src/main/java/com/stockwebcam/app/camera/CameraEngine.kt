package com.stockwebcam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView?,
    private val onFrameCaptured: (ByteArray) -> Unit
) {
    private val TAG = "CameraEngine"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var isBackCamera = true
    private var currentZoomRatio = 1.0f
    private var isTorchOn = false
    private var isHqMode = false
    private var targetResolution = Size(1920, 1080)

    // Reusable byte array output stream for JPEG compression
    private val jpegStream = ByteArrayOutputStream(1920 * 1080)

    fun startCamera(onSuccess: (() -> Unit)? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
            onSuccess?.invoke()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = if (isBackCamera) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        // 1. Preview Use Case (Viewfinder)
        val preview = Preview.Builder()
            .setTargetResolution(targetResolution)
            .build()
            .also {
                previewView?.let { pView ->
                    it.setSurfaceProvider(pView.surfaceProvider)
                }
            }

        // 2. High-speed ImageAnalysis Use Case
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            // Restore zoom and torch state
            setZoomRatio(currentZoomRatio)
            if (isTorchOn && isBackCamera) {
                camera?.cameraControl?.enableTorch(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    private fun processImageProxy(image: ImageProxy) {
        try {
            // CameraX built-in hardware-accelerated converter handles
            // row strides, pixel strides, and device-specific memory alignment flawlessly
            val bitmap = image.toBitmap()
            val rotationDegrees = image.imageInfo.rotationDegrees

            // Rotate bitmap if the sensor orientation requires it
            val finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            jpegStream.reset()
            val quality = if (isHqMode) 90 else 80
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)

            val jpegBytes = jpegStream.toByteArray()
            onFrameCaptured(jpegBytes)

            if (finalBitmap != bitmap) {
                finalBitmap.recycle()
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        } finally {
            image.close()
        }
    }

    fun flipCamera() {
        isBackCamera = !isBackCamera
        bindCameraUseCases()
    }

    fun isUsingBackCamera(): Boolean = isBackCamera

    fun setZoomRatio(ratio: Float) {
        currentZoomRatio = ratio.coerceIn(1.0f, 10.0f)
        camera?.cameraControl?.setZoomRatio(currentZoomRatio)
    }

    fun getZoomRatio(): Float = currentZoomRatio

    fun toggleTorch(): Boolean {
        if (!isBackCamera) return false
        isTorchOn = !isTorchOn
        camera?.cameraControl?.enableTorch(isTorchOn)
        return isTorchOn
    }

    fun isTorchActive(): Boolean = isTorchOn

    fun setHqMode(enabled: Boolean) {
        isHqMode = enabled
    }

    fun isHqEnabled(): Boolean = isHqMode

    fun setResolution(width: Int, height: Int) {
        targetResolution = Size(width, height)
        bindCameraUseCases()
    }

    fun getResolution(): Size = targetResolution

    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
