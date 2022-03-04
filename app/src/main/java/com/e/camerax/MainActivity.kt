package com.e.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera as Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.e.camerax.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private val PERM_STORAGE = 99
    private val PERM_CAMERA = 100

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var camera: Camera? = null
    private var cameraController: CameraControl?=null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraInfo: CameraInfo?=null
//    private var slider: Slider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requirePermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),PERM_STORAGE)
    }

    override fun permissionGranted(requestCode: Int) {
        when(requestCode) {
            PERM_STORAGE -> setView()
            PERM_CAMERA -> openCamera()
        }
    }

    override fun permissionDenied(requestCode: Int) {
        when (requestCode){
            PERM_STORAGE -> {
                Toast.makeText(baseContext,
                    "외부 저장소 권한을 승인해야 앱을 사용할 수 있습니다.",
                    Toast.LENGTH_LONG).show()
                finish()
            }
            PERM_CAMERA -> {
                Toast.makeText(baseContext,
                    "카메라 권한을 승인해야 카메라를 사용할 수 있습니다.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setView() {
        setUpCamera()
        binding.btnTakePicture.setOnClickListener{
            requirePermissions(arrayOf(Manifest.permission.CAMERA), PERM_CAMERA)
        }
    }

    private fun openCamera() {
        binding.btnTakePicture.setOnClickListener {
            takePhoto()
        }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnTorch.setOnClickListener{
            Log.d(TAG,cameraInfo?.torchState?.value.toString())
            when(cameraInfo?.torchState?.value){
                TorchState.ON -> {
                    cameraController?.enableTorch(false)
                    binding.btnTorch.text="Torch ON"
                }
                TorchState.OFF -> {
                    cameraController?.enableTorch(true)
                    binding.btnTorch.text="Torch OFF"
                }
            }
        }

        binding.cameraSwitchButton.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing){
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            // Re-bind use cases to update selected camera
            bindCameraUseCases()
        }
        //TODO: change btn into bar style
        binding.btn1X.setOnClickListener {
            cameraController?.setZoomRatio(1F)
        }
        binding.btn2X.setOnClickListener {
            cameraController?.setZoomRatio(2F)
        }
        binding.btn5X.setOnClickListener {
            cameraController?.setZoomRatio(5F)
        }
        binding.btn10X.setOnClickListener {
            cameraController?.setZoomRatio(8F)
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture?: return

        val photoFile = File(
            outputDirectory,
            newJpgFileName())

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        //Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object: ImageCapture.OnImageSavedCallback{
                override fun onError(exc: ImageCaptureException) {
                    Log.d(TAG,"Photo capture failed: ${exc.message}",exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "photo capture succeeded:$savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG,msg)

                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also{
                        it.data = savedUri
                        sendBroadcast(it)
                    }
                }
            })
    }

    // viewFinder Setting: Preview
    private fun setUpCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            //Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                cameraProvider.hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                cameraProvider.hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()

        }, ContextCompat.getMainExecutor(this))
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        val switchCamerasButton = binding.cameraSwitchButton
        try {
            switchCamerasButton.isEnabled =
                cameraProvider.hasBackCamera() && cameraProvider.hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            switchCamerasButton.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun ProcessCameraProvider?.hasBackCamera(): Boolean {
        return this?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun ProcessCameraProvider?.hasFrontCamera(): Boolean {
        return this?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun newJpgFileName(): String{
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName = sdf.format(System.currentTimeMillis())
        return "${fileName}.jpg"
    }
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir
        else filesDir
    }

    /** Declare and bind preview and capture use cases */
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")
        var screenAspectRatio: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            val widthPixels = windowMetrics.bounds.width() - insets.left - insets.right
            val heightPixels = windowMetrics.bounds.height() - insets.bottom - insets.top
            screenAspectRatio = aspectRatio(widthPixels, heightPixels)
            Log.d(TAG, "Screen metrics : $widthPixels x $heightPixels")
        }else{
            // Get screen metrics used to setup camera for full screen resolution
            val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
            Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
            screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        }
        Log.d(TAG, "screenAspectRatio: $screenAspectRatio")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
        //ImageCapture
        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .build()
        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try{
            //Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture)

            // Update the EV slider UI according to the CameraInfo
            binding.slider.setup(camera!!)

            cameraController = camera!!.cameraControl
            cameraInfo = camera!!.cameraInfo

            cameraInfo!!.zoomState.observe(this,androidx.lifecycle.Observer {
                val currentZoomRatio = it.zoomRatio
                Log.d(TAG,currentZoomRatio.toString())
                binding.txtZoomState.text=currentZoomRatio.toString()
                binding.txtMaxZoom.text=it.maxZoomRatio.toString()
                binding.txtMinZoom.text=it.minZoomRatio.toString()
            })
        }catch(exc: Exception){
            Log.d(TAG,"Use case binding failed",exc)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun Slider.setup(camera: Camera) {
        camera.cameraInfo.exposureState.let {
            value = it.exposureCompensationIndex.toFloat()
            valueFrom = it.exposureCompensationRange.lower.toFloat()
            valueTo = it.exposureCompensationRange.upper.toFloat()
            addOnChangeListener { _, value, _ ->
                camera.cameraControl.setExposureCompensationIndex(value.roundToInt())
            }
            setLabelFormatter { value: Float ->
                "%.2f".format((value * it.exposureCompensationStep.toFloat()))
            }
        }
    }

    /**
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraX-Debug"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

}