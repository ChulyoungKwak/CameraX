package com.e.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.RadioGroup
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

class MainActivity : BaseActivity(), SensorEventListener {
    private lateinit var binding: ActivityMainBinding

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraResolution: Int = AspectRatio.RATIO_4_3
    private var cameraSizeWidth: Int = 1280
    private var cameraSizeHeight: Int = 7

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

    private lateinit var sensorManager: SensorManager
    private var mLight: Sensor? = null
    private var lightValue: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requirePermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),PERM_STORAGE)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun onResume() {
        super.onResume()
        mLight?.also { light ->
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
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
        requirePermissions(arrayOf(Manifest.permission.CAMERA), PERM_CAMERA)
    }

    private fun openCamera() {
        binding.btnTakePicture.setOnClickListener {
            takePhoto()
        }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.cameraSwitchButton.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing){
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            // Re-bind use cases to update selected camera
            bindCameraUseCases()
        }

        binding.btnPreference.setOnClickListener {
            AlertDialog.Builder(this)
                .setView(R.layout.fragment_list)
                .show()
                .also { alertDialog ->
                    if(alertDialog==null){
                        return@also
                    }

                    val brightRadioGroup = alertDialog.findViewById<RadioGroup>(R.id.radioGroupBright)
                    val resolutionRadioGroup = alertDialog.findViewById<RadioGroup>(R.id.radioGroupResolution)
                    val zoomRadioGroup = alertDialog.findViewById<RadioGroup>(R.id.radioGroupZoom)

                    brightRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                        when (checkedId) {
                            R.id.radioBtnBright1 -> {
                                cameraInfo?.let{cameraController?.setExposureCompensationIndex(-1)}
                                Log.d(TAG, "Set exposure compensation index -1")
                            }
                            R.id.radioBtnBright2 -> {
                                cameraInfo?.let{cameraController?.setExposureCompensationIndex(0)}
                                Log.d(TAG, "Set exposure compensation index to 0")
                            }
                            R.id.radioBtnBright3 -> {
                                cameraInfo?.let{cameraController?.setExposureCompensationIndex(1)}
                                Log.d(TAG, "Set exposure compensation index to +1")
                            }
                        }
                    }

                    resolutionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                        when (checkedId) {
                            R.id.radioBtnResolution1 -> {
                                cameraSizeWidth = 1280
                                cameraSizeHeight = 720
//                                cameraResolution = AspectRatio.RATIO_16_9
                                bindCameraUseCases()
                                Log.d(TAG, "Set aspect ratio 16:9")
                            }

                            R.id.radioBtnResolution2 -> {
//                                cameraResolution = AspectRatio.RATIO_4_3
                                cameraSizeWidth = 640
                                cameraSizeHeight = 480
                                bindCameraUseCases()
                                Log.d(TAG, "Set aspect ratio 4:3")
                            }
                        }
                    }

                    zoomRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                        when (checkedId) {
                            R.id.radioBtnZoom1 -> {
                                cameraController?.setZoomRatio(1F)
                                Log.d(TAG, "Set zoom ratio x1")
                            }
                            R.id.radioBtnZoom2 -> {
                                cameraController?.setZoomRatio(2F)
                                Log.d(TAG, "Set zoom ratio x2")
                            }
                            R.id.radioBtnZoom3 -> {
                                cameraController?.setZoomRatio(5F)
                                Log.d(TAG, "Set zoom ratio x5")
                            }
                        }
                    }
                }
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
        val sdf = SimpleDateFormat("MMdd_HHmmss")
        val fileName = sdf.format(System.currentTimeMillis())
        return "${fileName}_${lightValue}.jpg"
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

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
//            .setTargetAspectRatio(cameraResolution)
            .setTargetResolution(Size(cameraSizeWidth, cameraSizeHeight))
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
        //ImageCapture
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(cameraSizeWidth, cameraSizeHeight))
//            .setTargetAspectRatio(cameraResolution)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraX-Debug"
    }

    override fun onSensorChanged(event: SensorEvent) {
        lightValue = event.values[0]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

}