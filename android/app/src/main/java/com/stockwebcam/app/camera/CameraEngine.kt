package com.stockwebcam.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
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
    private var nv21Buffer: ByteArray? = null

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
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
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
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val totalSize = ySize + uSize + vSize
            if (nv21Buffer == null || nv21Buffer!!.size != totalSize) {
                nv21Buffer = ByteArray(totalSize)
            }
            val nv21 = nv21Buffer!!

            yBuffer.get(nv21, 0, ySize)

            // In YUV_420_888 to NV21: interleaved V and U
            val pixelStride = image.planes[2].pixelStride
            val rowStride = image.planes[2].rowStride

            val width = image.width
            val height = image.height

            if (pixelStride == 2) {
                // Already semi-planar NV21
                vBuffer.get(nv21, ySize, vSize)
            } else {
                // Pack UV into NV21 (VU order)
                var pos = ySize
                val uBytes = ByteArray(uSize)
                val vBytes = ByteArray(vSize)
                uBuffer.get(uBytes)
                vBuffer.get(vBytes)

                for (i in 0 until uSize) {
                    if (pos + 1 < nv21.size) {
                        nv21[pos++] = vBytes[i]
                        nv21[pos++] = uBytes[i]
                    }
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            jpegStream.reset()
            val quality = if (isHqMode) 90 else 75
            yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, jpegStream)

            val jpegBytes = jpegStream.toByteArray()
            onFrameCaptured(jpegBytes)
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

