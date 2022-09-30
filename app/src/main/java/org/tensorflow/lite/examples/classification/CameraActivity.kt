/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.classification

import android.Manifest
import android.app.Fragment
import android.media.ImageReader.OnImageAvailableListener
import android.hardware.Camera.PreviewCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.tensorflow.lite.examples.classification.CameraActivity
import android.view.WindowManager
import org.tensorflow.lite.examples.classification.R
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import org.tensorflow.lite.examples.classification.env.ImageUtils
import android.media.Image.Plane
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.hardware.camera2.CameraAccessException
import org.tensorflow.lite.examples.classification.CameraConnectionFragment
import org.tensorflow.lite.examples.classification.LegacyCameraConnectionFragment
import android.content.Intent
import android.hardware.Camera
import android.media.ImageReader
import android.os.*
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.BuildConfig
import org.tensorflow.lite.examples.classification.databinding.TfeIcActivityCameraBinding
import org.tensorflow.lite.examples.classification.env.Logger
import org.tensorflow.lite.examples.classification.tflite.Classifier
import timber.log.Timber
import java.lang.Exception
import java.util.*

abstract class CameraActivity : AppCompatActivity(), OnImageAvailableListener, PreviewCallback,
    View.OnClickListener, AdapterView.OnItemSelectedListener {
    @JvmField
    protected var previewWidth = 0
    @JvmField
    protected var previewHeight = 0
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var useCamera2API = false
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    protected var luminanceStride = 0
        private set
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var bottomSheetLayout: LinearLayout? = null
    private var gestureLayout: LinearLayout? = null
    private var sheetBehavior: BottomSheetBehavior<LinearLayout?>? = null
    protected var recognitionTextView: TextView? = null
    protected var recognition1TextView: TextView? = null
    protected var recognition2TextView: TextView? = null
    protected var recognitionValueTextView: TextView? = null
    protected var recognition1ValueTextView: TextView? = null
    protected var recognition2ValueTextView: TextView? = null
    protected var frameValueTextView: TextView? = null
    protected var cropValueTextView: TextView? = null
    protected var cameraResolutionTextView: TextView? = null
    protected var rotationTextView: TextView? = null
    protected var inferenceTimeTextView: TextView? = null
    protected var bottomSheetArrowImageView: ImageView? = null
    private var plusImageView: ImageView? = null
    private var minusImageView: ImageView? = null
    private var modelSpinner: Spinner? = null
    private var deviceSpinner: Spinner? = null
    private var threadsTextView: TextView? = null
    private var model = Classifier.Model.RECYCLE_VISION
    private var device = Classifier.Device.CPU
    private var numThreads = -1

    private lateinit var binding: TfeIcActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        LOGGER.d("onCreate $this")
        super.onCreate(null)

        binding = TfeIcActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }
        threadsTextView = findViewById(R.id.threads)
        plusImageView = findViewById(R.id.plus)
        minusImageView = findViewById(R.id.minus)
        modelSpinner = findViewById(R.id.model_spinner)
        deviceSpinner = findViewById(R.id.device_spinner)
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout)
        gestureLayout = findViewById(R.id.gesture_layout)
        bottomSheetLayout?.let {
            sheetBehavior = BottomSheetBehavior.from(it)
        }

        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow)
        gestureLayout?.let { it ->
            val vto = it.viewTreeObserver

            vto.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        it.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val width = it.getMeasuredWidth();
                        gestureLayout?.let { gesture ->
                            val height = gesture.measuredHeight
                            sheetBehavior!!.peekHeight = height
                        }
                    }
                })
        }


        sheetBehavior!!.isHideable = false
        sheetBehavior!!.setBottomSheetCallback(
            object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {}
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            bottomSheetArrowImageView?.let {
                                it.setImageResource(R.drawable.icn_chevron_down)
                            }

                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            bottomSheetArrowImageView?.let {
                                it.setImageResource(R.drawable.icn_chevron_up)
                            }

                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {}
                        BottomSheetBehavior.STATE_SETTLING -> {
                            bottomSheetArrowImageView?.let {
                                it.setImageResource(R.drawable.icn_chevron_up)
                            }

                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        recognitionTextView = findViewById(R.id.detected_item)
        recognitionValueTextView = findViewById(R.id.detected_item_value)
        recognition1TextView = findViewById(R.id.detected_item1)
        recognition1ValueTextView = findViewById(R.id.detected_item1_value)
        recognition2TextView = findViewById(R.id.detected_item2)
        recognition2ValueTextView = findViewById(R.id.detected_item2_value)
        frameValueTextView = findViewById(R.id.frame_info)
        cropValueTextView = findViewById(R.id.crop_info)
        cameraResolutionTextView = findViewById(R.id.view_info)
        rotationTextView = findViewById(R.id.rotation_info)
        inferenceTimeTextView = findViewById(R.id.inference_info)
        modelSpinner?.let {
            it.onItemSelectedListener = this
        }
        deviceSpinner?.let {
            it.onItemSelectedListener = this
        }
        plusImageView?.let {
            it.setOnClickListener(this)
        }
        minusImageView?.let {
            it.setOnClickListener(this)
        }
        modelSpinner?.let {
            model = Classifier.Model.valueOf(it.selectedItem.toString()
                .uppercase(Locale.getDefault()))
        }
        deviceSpinner?.let {
            device = Classifier.Device.valueOf(it.selectedItem.toString())
        }
        threadsTextView?.let { threads ->
            numThreads = threads.text.toString().trim { it <= ' ' }.toInt()
        }

    }

    protected fun getRgbBytes(): IntArray? {
        imageConverter!!.run()
        return rgbBytes
    }

    protected val luminance: ByteArray?
        protected get() = yuvBytes[0]

    /** Callback for android.hardware.Camera API  */
    override fun onPreviewFrame(bytes: ByteArray, camera: Camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!")
            return
        }
        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                val previewSize = camera.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            LOGGER.e(e, "Exception!")
            return
        }
        isProcessingFrame = true
        yuvBytes[0] = bytes
        luminanceStride = previewWidth
        imageConverter = Runnable {
            ImageUtils.convertYUV420SPToARGB8888(
                bytes,
                previewWidth,
                previewHeight,
                rgbBytes
            )
        }
        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
            isProcessingFrame = false
        }
        processImage()
    }

    /** Callback for Camera2 API  */
    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            luminanceStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = object : Runnable {
                override fun run() {
                    ImageUtils.convertYUV420ToARGB8888(
                        yuvBytes[0],
                        yuvBytes[1],
                        yuvBytes[2],
                        previewWidth,
                        previewHeight,
                        luminanceStride,
                        uvRowStride,
                        uvPixelStride,
                        rgbBytes
                    )
                }
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            LOGGER.e(e, "Exception!")
            Trace.endSection()
            return
        }
        Trace.endSection()
    }

    @Synchronized
    public override fun onStart() {
        LOGGER.d("onStart $this")
        super.onStart()
    }

    @Synchronized
    public override fun onResume() {
        LOGGER.d("onResume $this")
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread!!.start()
        handler = Handler(handlerThread!!.looper)
    }

    @Synchronized
    public override fun onPause() {
        LOGGER.d("onPause $this")
        handlerThread!!.quitSafely()
        try {
            handlerThread!!.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            LOGGER.e(e, "Exception!")
        }
        super.onPause()
    }

    @Synchronized
    public override fun onStop() {
        LOGGER.d("onStop $this")
        super.onStop()
    }

    @Synchronized
    public override fun onDestroy() {
        LOGGER.d("onDestroy $this")
        super.onDestroy()
    }

    @Synchronized
    protected fun runInBackground(r: Runnable?) {
        if (handler != null) {
            handler!!.post(r!!)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                setFragment()
            } else {
                requestPermission()
            }
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    this@CameraActivity,
                    "Camera permission is required for this demo",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else requiredLevel <= deviceLevel
        // deviceLevel is not LEGACY, can use numerical sort
    }

    private fun chooseCamera(): String? {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL
                        || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                ))
                LOGGER.i("Camera API lv2?: %s", useCamera2API)
                return cameraId
            }
        } catch (e: CameraAccessException) {
            LOGGER.e(e, "Not allowed to access camera")
        }
        return null
    }

    protected fun setFragment() {
        val cameraId = chooseCamera()
        val fragment: Fragment
        if (useCamera2API) {
            val camera2Fragment = CameraConnectionFragment.newInstance(
                { size, rotation ->
                    previewHeight = size.height
                    previewWidth = size.width
                    onPreviewSizeChosen(size, rotation)
                },
                this,
                layoutId,
                desiredPreviewFrameSize
            )
            camera2Fragment.setCamera(cameraId)
            fragment = camera2Fragment
        } else {
            fragment = LegacyCameraConnectionFragment(this, layoutId, desiredPreviewFrameSize)
        }
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    protected fun fillBytes(planes: Array<Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity())
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

    protected fun readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback!!.run()
        }
    }

    protected val screenOrientation: Int
        protected get() = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }

    @UiThread
    protected fun showResultsInBottomSheet(results: List<Classifier.Recognition?>?) {
        if (results != null && results.size >= 3) {
            val recognition = results[0]
            if (recognition != null) {
                if (recognition.title != null) recognitionTextView!!.text = recognition.title
                if (recognition.confidence != null) {
                    (String.format("%.2f", 100 * recognition.confidence) + "%")
                        .also { recognitionValueTextView!!.text = it }
                    if (recognition.confidence > 0.5) {
                        if (recognition.title == "pp" || recognition.title == "pvc" || recognition.title == "pet" || recognition.title == "other" || recognition.title == "ps" || recognition.title == "peld" || recognition.title == "pehd") {
//       Log.e(TAG, ImageClassifier.plasticType);
                            val intent = Intent(this@CameraActivity, PlasticTypeActivity::class.java)
                            val s = recognition.title
                            intent.putExtra("PLASTIC_TYPE", s)
                            startActivity(intent)
                        }
                    }
                }
            }
            val recognition1 = results[1]
            if (recognition1 != null) {
                if (recognition1.title != null) recognition1TextView!!.text = recognition1.title
                if (recognition1.confidence != null) (String.format("%.2f", 100 * recognition1.confidence) + "%")
                    .also { recognition1ValueTextView!!.text = it }
            }
            val recognition2 = results[2]
            if (recognition2 != null) {
                if (recognition2.title != null) recognition2TextView!!.text = recognition2.title
                if (recognition2.confidence != null) (String.format("%.2f", 100 * recognition2.confidence) + "%")
                    .also { recognition2ValueTextView!!.text = it }
            }
        }
    }

    protected fun showFrameInfo(frameInfo: String?) {
        frameValueTextView!!.text = frameInfo
    }

    protected fun showCropInfo(cropInfo: String?) {
        cropValueTextView!!.text = cropInfo
    }

    protected fun showCameraResolution(cameraInfo: String?) {
        cameraResolutionTextView!!.text = cameraInfo
    }

    protected fun showRotationInfo(rotation: String?) {
        rotationTextView!!.text = rotation
    }

    protected fun showInference(inferenceTime: String?) {
        inferenceTimeTextView!!.text = inferenceTime
    }

    protected fun getModel(): Classifier.Model {
        return model
    }

    private fun setModel(model: Classifier.Model) {
        if (this.model != model) {
            LOGGER.d("Updating  model: $model")
            this.model = model
            onInferenceConfigurationChanged()
        }
    }

    protected fun getDevice(): Classifier.Device {
        return device
    }

    private fun setDevice(device: Classifier.Device) {
        if (this.device != device) {
            LOGGER.d("Updating  device: $device")
            this.device = device
            val threadsEnabled = device == Classifier.Device.CPU
            plusImageView!!.isEnabled = threadsEnabled
            minusImageView!!.isEnabled = threadsEnabled
            threadsTextView!!.text = if (threadsEnabled) numThreads.toString() else "N/A"
            onInferenceConfigurationChanged()
        }
    }

    protected fun getNumThreads(): Int {
        return numThreads
    }

    private fun setNumThreads(numThreads: Int) {
        if (this.numThreads != numThreads) {
            LOGGER.d("Updating  numThreads: $numThreads")
            this.numThreads = numThreads
            onInferenceConfigurationChanged()
        }
    }

    protected abstract fun processImage()
    protected abstract fun onPreviewSizeChosen(size: Size?, rotation: Int)
    protected abstract val layoutId: Int
    protected abstract val desiredPreviewFrameSize: Size?
    protected abstract fun onInferenceConfigurationChanged()
    override fun onClick(v: View) {
        if (v.id == R.id.plus) {
            val threads = threadsTextView!!.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads >= 9) return
            setNumThreads(++numThreads)
            threadsTextView!!.text = numThreads.toString()
        } else if (v.id == R.id.minus) {
            val threads = threadsTextView!!.text.toString().trim { it <= ' ' }
            var numThreads = threads.toInt()
            if (numThreads == 1) {
                return
            }
            setNumThreads(--numThreads)
            threadsTextView!!.text = numThreads.toString()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        if (parent === modelSpinner) {
            setModel(
                Classifier.Model.valueOf(
                    parent.getItemAtPosition(pos).toString().toUpperCase()
                )
            )
        } else if (parent === deviceSpinner) {
            setDevice(Classifier.Device.valueOf(parent.getItemAtPosition(pos).toString()))
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing.
    }

    companion object {
        private val LOGGER = Logger()
        private const val PERMISSIONS_REQUEST = 1
        private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
        const val PLASTIC_TYPE = 88
        private fun allPermissionsGranted(grantResults: IntArray): Boolean {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        }
    }
}