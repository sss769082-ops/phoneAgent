package com.example.agentapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureActivity : Activity() {

    private val TAG = "ScreenCapture"
    private val SCREEN_CAPTURE_REQUEST = 100
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout — this activity is transparent/invisible

        when (intent.getStringExtra("mode")) {
            "camera" -> takeSilentPhoto()
            "screen" -> requestScreenPermission()
            else -> finish()
        }
    }

    // ─── Camera capture ───────────────────────────────────────────────

    private fun takeSilentPhoto() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList.firstOrNull() ?: run {
                Log.e(TAG, "No cameras found")
                finish()
                return
            }

            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(
                android.hardware.camera2.params.StreamConfigurationMap::class.java.let {
                    android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                }
            )

            imageReader = ImageReader.newInstance(1280, 720, android.graphics.ImageFormat.JPEG, 2)

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val file = File(filesDir, "last_photo.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    Log.d(TAG, "Photo saved: ${file.length()} bytes")
                } finally {
                    image.close()
                    cameraDevice?.close()
                    finish()
                }
            }, Handler(Looper.getMainLooper()))

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surface = imageReader!!.surface
                    val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureBuilder.addTarget(surface)

                    camera.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                session.capture(captureBuilder.build(), null, null)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "Camera session config failed")
                                finish()
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); finish() }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close(); finish()
                }
            }, Handler(Looper.getMainLooper()))

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied: ${e.message}")
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Camera error: ${e.message}")
            finish()
        }
    }

    // ─── Screen capture ───────────────────────────────────────────────

    private fun requestScreenPermission() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SCREEN_CAPTURE_REQUEST && resultCode == RESULT_OK && data != null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, data)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            projection.createVirtualDisplay(
                "AgentScreen", width, height, density,
                0, reader.surface, null, null
            )

            // Wait a bit for the frame to be ready
            Handler(Looper.getMainLooper()).postDelayed({
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)

                        // Crop to exact screen size
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                        val file = File(filesDir, "last_screen.jpg")
                        FileOutputStream(file).use { out ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        Log.d(TAG, "Screenshot saved: ${file.length()} bytes")
                        bitmap.recycle()
                        cropped.recycle()
                    } finally {
                        image.close()
                        projection.stop()
                        reader.close()
                        finish()
                    }
                } else {
                    Log.e(TAG, "No image available")
                    projection.stop()
                    reader.close()
                    finish()
                }
            }, 800)
        } else {
            Log.d(TAG, "Screen capture cancelled or denied")
            finish()
        }
    }

    override fun onDestroy() {
        try { captureSession?.close() } catch (e: Exception) {}
        try { cameraDevice?.close() } catch (e: Exception) {}
        try { imageReader?.close() } catch (e: Exception) {}
        super.onDestroy()
    }
}
