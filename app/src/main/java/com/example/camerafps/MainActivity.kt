package com.example.camerafps

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fpsTextView: TextView
    private lateinit var errorTextView: TextView
    private var lastTime = 0L
    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI SETUP
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fpsTextView = TextView(this).apply {
            text = "Loading..."
            textSize = 24f
            setTextColor(Color.GREEN)
            setPadding(40, 60, 40, 40)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
        }

        errorTextView = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.RED)
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(20, 20, 20, 20)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
        }

        root.addView(previewView)
        root.addView(fpsTextView)
        root.addView(errorTextView)
        setContentView(root)

        // LOGIC START
        try {
            cameraExecutor = Executors.newSingleThreadExecutor()
            checkPermissionsAndStart()
        } catch (e: Exception) {
            showError("Init Error: " + e.message)
        }
    }

    private fun checkPermissionsAndStart() {
        if (allPermissionsGranted()) {
            startCameraSafe()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCameraSafe() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            } catch (e: Exception) {
                showError("Camera Provider Failed: " + e.message)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        try {
            val previewView = (window.decorView as ViewGroup).getChildAt(0) as ViewGroup
            val pv = previewView.getChildAt(0) as PreviewView

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(pv.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            calculateFPS()
                        } catch(e: Exception) {
                            Log.e("FPS", "Calc failed")
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()

            if (cameraProvider.hasCamera(cameraSelector)) {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                runOnUiThread { errorTextView.text = "" } 
            } else {
                showError("No Back Camera Found on Device!")
            }
        } catch (e: Exception) {
            showError("Bind Failed: " + e.message)
        }
    }

    private fun calculateFPS() {
        val currentTime = System.nanoTime()
        frameCount++
        if (currentTime - lastTime >= 1_000_000_000) {
            val fps = frameCount
            runOnUiThread { fpsTextView.text = "FPS: $fps" }
            frameCount = 0
            lastTime = currentTime
        }
    }

    private fun showError(msg: String) {
        Log.e("CameraSafe", msg)
        runOnUiThread {
            errorTextView.text = "ERROR: " + msg
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCameraSafe()
            } else {
                showError("Permission Denied.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}