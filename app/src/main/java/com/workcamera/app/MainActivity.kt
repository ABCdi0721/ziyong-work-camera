package com.workcamera.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var gridOverlay: GridOverlayView
    private lateinit var flashGlyph: GlyphView
    private lateinit var gridGlyph: GlyphView
    private lateinit var switchGlyph: GlyphView
    private lateinit var galleryGlyph: GlyphView
    private lateinit var locationGlyph: GlyphView
    private lateinit var galleryThumb: ShapeableImageView
    private lateinit var shutterBtn: FrameLayout
    private lateinit var shutterInner: View
    private lateinit var flashOverlay: View
    private lateinit var focusRing: View
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var zoomLabel: TextView
    private lateinit var hintLabel: TextView
    private lateinit var locationText: TextView
    private lateinit var locationChip: View

    private lateinit var locationHelper: LocationHelper
    private val thumbLoader: ThumbLoader by lazy { ThumbLoader(this) }
    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var zoomRatio = 1f
    private var maxZoomRatio = 1f
    private var gridVisible = true
    private var isCapturing = false
    private var previewStreaming = false
    private var captureAttempts = 0
    private var pendingSystemPhoto: File? = null
    private var pendingSystemTakenAt = 0L

    /** 小米/红米/POCO 的相机 HAL 对采集模式较敏感，单独走更保守的配置 */
    private val isXiaomiDevice: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", true) ||
            Build.BRAND.equals("Redmi", true) ||
            Build.BRAND.equals("POCO", true) ||
            Build.BRAND.equals("Xiaomi", true)

    private var topBasePadding = 0
    private var bottomBasePadding = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == true) {
            startCamera()
            startLocationWarmUp()
        } else {
            Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val scaleDetector: ScaleGestureDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (zoomRatio * detector.scaleFactor).coerceIn(1f, maxZoomRatio)
                zoomRatio = target
                camera?.cameraControl?.setZoomRatio(target)
                showZoomLabel(String.format(Locale.US, "%.1fx", target))
                return true
            }
        })
    }

    private val tapDetector: GestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!scaleDetector.isInProgress) {
                    focusAt(e.x, e.y)
                }
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        bindViews()
        setupInsets()
        setupGlyphs()
        setupListeners()
        setupGestures()

        // 只有取景画面真正出帧后才允许拍照，避免第一张拍成黑图
        previewView.previewStreamState.observe(this) { state ->
            previewStreaming = state == PreviewView.StreamState.STREAMING
            Log.d("WorkCamera", "preview stream state=$state")
        }

        locationHelper = LocationHelper(this)

        if (hasCameraPermission()) {
            startCamera()
            startLocationWarmUp()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        if (isXiaomiDevice) {
            // 小米部分机型 SurfaceView 与相机 HAL 配合会出现取景/成像异常，改用兼容模式（TextureView）
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        gridOverlay = findViewById(R.id.gridOverlay)
        flashGlyph = findViewById(R.id.flashGlyph)
        gridGlyph = findViewById(R.id.gridGlyph)
        switchGlyph = findViewById(R.id.switchGlyph)
        galleryGlyph = findViewById(R.id.galleryGlyph)
        locationGlyph = findViewById(R.id.locationGlyph)
        galleryThumb = findViewById(R.id.galleryThumb)
        shutterBtn = findViewById(R.id.shutterBtn)
        shutterInner = findViewById(R.id.shutterInner)
        flashOverlay = findViewById(R.id.flashOverlay)
        focusRing = findViewById(R.id.focusRing)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        zoomLabel = findViewById(R.id.zoomLabel)
        hintLabel = findViewById(R.id.hintLabel)
        locationText = findViewById(R.id.locationText)
        locationChip = findViewById(R.id.locationChip)
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.rootCamera)
        topBasePadding = topBar.paddingTop
        bottomBasePadding = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(
                topBar.paddingLeft,
                topBasePadding + bars.top,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            bottomBar.setPadding(
                bottomBar.paddingLeft,
                bottomBar.paddingTop,
                bottomBar.paddingRight,
                bottomBasePadding + bars.bottom
            )
            insets
        }
    }

    private fun setupGlyphs() {
        flashGlyph.style = GlyphView.Style.BOLT_AUTO
        gridGlyph.style = GlyphView.Style.GRID
        switchGlyph.style = GlyphView.Style.SWITCH
        galleryGlyph.style = GlyphView.Style.GALLERY
        locationGlyph.style = GlyphView.Style.LOCATION
        findViewById<GlyphView>(R.id.settingsGlyph).style = GlyphView.Style.SETTINGS
        galleryGlyph.tint = Color.argb(220, 255, 255, 255)
        locationGlyph.tint = Color.argb(170, 255, 255, 255)
        updateFlashIcon()
    }

    private fun setupListeners() {
        findViewById<FrameLayout>(R.id.flashBtn).setOnClickListener { cycleFlashMode() }
        findViewById<FrameLayout>(R.id.gridBtn).setOnClickListener { toggleGrid() }
        findViewById<FrameLayout>(R.id.settingsBtn).setOnClickListener { showSettingsDialog() }
        findViewById<FrameLayout>(R.id.switchBtn).setOnClickListener { switchLens() }
        findViewById<FrameLayout>(R.id.galleryBtn).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        shutterBtn.setOnClickListener { takePhoto() }
        shutterBtn.setOnLongClickListener {
            if (isCapturing) return@setOnLongClickListener true
            isCapturing = true
            shutterBtn.isEnabled = false
            launchSystemCamera()
            true
        }
        locationChip.isClickable = true
        locationChip.isLongClickable = true
        locationChip.setOnLongClickListener {
            showManualPlaceDialog()
            true
        }
    }

    /** 长按定位胶囊：把水印地点固定成某个地址（系统地址库取不到小区名时用） */
    private fun showManualPlaceDialog() {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(locationHelper.manualPlace().orEmpty())
            hint = getString(R.string.place_hint)
        }
        val container = FrameLayout(this).apply {
            val horizontal = (20 * density).toInt()
            val top = (8 * density).toInt()
            setPadding(horizontal, top, horizontal, 0)
            addView(input, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.place_dialog_title)
            .setMessage(R.string.place_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.place_save) { _, _ ->
                val text = input.text.toString().trim()
                locationHelper.setManualPlace(text)
                if (text.isEmpty()) {
                    Toast.makeText(this, getString(R.string.place_auto_toast), Toast.LENGTH_SHORT).show()
                    refreshLocation()
                } else {
                    locationText.text = text
                    Toast.makeText(this, getString(R.string.place_saved, text), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (locationHelper.manualPlace() != null) {
            builder.setNeutralButton(R.string.place_auto) { _, _ ->
                locationHelper.setManualPlace(null)
                Toast.makeText(this, getString(R.string.place_auto_toast), Toast.LENGTH_SHORT).show()
                refreshLocation()
            }
        }
        builder.show()
    }

    private fun refreshLocation() {
        if (!locationHelper.hasPermission()) {
            locationText.text = getString(R.string.location_denied)
            return
        }
        locationText.text = getString(R.string.locating)
        locationHelper.locateAsync { text -> locationText.text = text }
    }

    private fun setupGestures() {
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            true
        }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = ArrayList<String>()
        permissions.add(Manifest.permission.CAMERA)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startLocationWarmUp() {
        if (!locationHelper.hasPermission()) {
            locationText.text = getString(R.string.location_denied)
            return
        }
        locationText.text = getString(R.string.locating)
        locationHelper.locateAsync { text ->
            locationText.text = text
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
            } catch (t: Throwable) {
                Toast.makeText(this, "相机启动失败：${t.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val captureBuilder = ImageCapture.Builder()
            // 不用 MINIMIZE_LATENCY（零快门/低延迟模式）：小米等机型上可能出现全黑帧
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)

        if (isXiaomiDevice) {
            // 显式限定 4:3，避免 HAL 拿到不支持的采集尺寸返回黑图
            captureBuilder.setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
        }
        val capture = captureBuilder.build()

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, capture)
            imageCapture = capture
            updateZoomBounds()
            zoomRatio = 1f
            camera?.cameraControl?.setZoomRatio(1f)
            refreshGalleryThumb()
        } catch (e: Exception) {
            Toast.makeText(this, "切换镜头失败", Toast.LENGTH_SHORT).show()
            if (lensFacing != CameraSelector.LENS_FACING_BACK) {
                lensFacing = CameraSelector.LENS_FACING_BACK
                bindCamera()
            }
        }
    }

    private fun updateZoomBounds() {
        val state = camera?.cameraInfo?.zoomState?.value
        maxZoomRatio = state?.maxZoomRatio ?: 1f
        if (maxZoomRatio < 1f) maxZoomRatio = 1f
    }

    private fun cycleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        imageCapture?.flashMode = flashMode
        updateFlashIcon()
    }

    private fun updateFlashIcon() {
        flashGlyph.style = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> GlyphView.Style.BOLT
            ImageCapture.FLASH_MODE_OFF -> GlyphView.Style.BOLT_OFF
            else -> GlyphView.Style.BOLT_AUTO
        }
        flashGlyph.tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            Color.argb(150, 255, 255, 255)
        } else {
            Color.WHITE
        }
    }

    private fun toggleGrid() {
        gridVisible = !gridVisible
        gridOverlay.visibility = if (gridVisible) View.VISIBLE else View.GONE
        gridGlyph.tint = if (gridVisible) Color.WHITE else Color.argb(140, 255, 255, 255)
        if (gridVisible) {
            gridOverlay.invalidate()
        }
    }

    private fun switchLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        zoomRatio = 1f
        zoomLabel.visibility = View.GONE
        bindCamera()
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            cam.cameraControl.startFocusAndMetering(action)
            showFocusRing(x, y)
        } catch (e: Exception) {
        }
    }

    private fun showFocusRing(x: Float, y: Float) {
        val half = dpF(28f)
        focusRing.visibility = View.VISIBLE
        focusRing.translationX = x - half
        focusRing.translationY = y - half
        focusRing.alpha = 1f
        focusRing.scaleX = 1.5f
        focusRing.scaleY = 1.5f
        focusRing.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(0f)
            .setDuration(900)
            .withEndAction { focusRing.visibility = View.GONE }
            .start()
    }

    private fun showZoomLabel(text: String) {
        zoomLabel.text = text
        zoomLabel.visibility = View.VISIBLE
        zoomLabel.animate().alpha(1f).setDuration(120).start()
        mainHandler.removeCallbacks(hideZoomRunnable)
        mainHandler.postDelayed(hideZoomRunnable, 1600)
    }

    private val hideZoomRunnable = Runnable {
        zoomLabel.animate().alpha(0f).setDuration(250).withEndAction {
            zoomLabel.visibility = View.GONE
            zoomLabel.alpha = 1f
        }.start()
    }

    private fun playShutterFeedback() {
        flashOverlay.alpha = 0f
        flashOverlay.animate().alpha(0.6f).setDuration(70).withEndAction {
            flashOverlay.animate().alpha(0f).setDuration(230).start()
        }.start()
        shutterInner.animate().scaleX(0.86f).scaleY(0.86f).setDuration(70).withEndAction {
            shutterInner.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }.start()
    }

    private fun showHint(message: String) {
        hintLabel.text = message
        hintLabel.visibility = View.VISIBLE
        hintLabel.animate().alpha(1f).setDuration(160).start()
        mainHandler.removeCallbacks(hideHintRunnable)
        mainHandler.postDelayed(hideHintRunnable, 2200)
    }

    private val hideHintRunnable = Runnable {
        hintLabel.animate().alpha(0f).setDuration(260).withEndAction {
            hintLabel.visibility = View.GONE
            hintLabel.alpha = 1f
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun takePhoto() {
        if (isCapturing) return
        isCapturing = true
        captureAttempts = 0
        shutterBtn.isEnabled = false
        playShutterFeedback()

        if (previewStreaming) {
            captureNow()
        } else {
            showHint(getString(R.string.camera_not_ready))
            waitForStreamingThenCapture(SystemClock.uptimeMillis() + 2500)
        }
    }

    /** 等取景画面出帧再拍，最多等 2.5 秒 */
    private fun waitForStreamingThenCapture(deadline: Long) {
        if (previewStreaming || SystemClock.uptimeMillis() > deadline) {
            captureNow()
            return
        }
        mainHandler.postDelayed({ waitForStreamingThenCapture(deadline) }, 120)
    }

    @SuppressLint("MissingPermission")
    private fun captureNow() {
        val capture = imageCapture
        if (capture == null) {
            finishCapture()
            Toast.makeText(this, getString(R.string.camera_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        val takenAt = System.currentTimeMillis()
        val rawFile = File(cacheDir, "wc_raw_$takenAt.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(rawFile).build()

        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                showHint(getString(R.string.adding_watermark))
                io.execute { processPhoto(rawFile, takenAt, false) }
            }

            override fun onError(exception: ImageCaptureException) {
                finishCapture()
                rawFile.delete()
                Toast.makeText(this@MainActivity, "拍照失败：${exception.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun finishCapture() {
        isCapturing = false
        shutterBtn.isEnabled = true
        captureAttempts = 0
    }

    /** 黑图兜底：改用系统相机拍，照片回来后照常加水印、存到「下载/工作相机」 */
    private val systemCameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingSystemPhoto
            pendingSystemPhoto = null
            if (success && file != null && file.exists()) {
                isCapturing = true
                shutterBtn.isEnabled = false
                showHint(getString(R.string.adding_watermark))
                io.execute { processPhoto(file, pendingSystemTakenAt, true) }
            } else {
                file?.delete()
                finishCapture()
                showHint(getString(R.string.system_camera_canceled))
            }
        }

    private fun launchSystemCamera() {
        try {
            val file = File(cacheDir, "wc_sys_${System.currentTimeMillis()}.jpg")
            pendingSystemPhoto = file
            pendingSystemTakenAt = System.currentTimeMillis()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            systemCameraLauncher.launch(uri)
        } catch (e: Exception) {
            pendingSystemPhoto?.delete()
            pendingSystemPhoto = null
            finishCapture()
            Toast.makeText(this, "打不开系统相机：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBlackPhotoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.black_photo_title)
            .setMessage(R.string.black_photo_message)
            .setPositiveButton(R.string.use_system_camera) { _, _ ->
                isCapturing = true
                shutterBtn.isEnabled = false
                launchSystemCamera()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun processPhoto(rawFile: File, takenAt: Long, fromSystemCamera: Boolean) {
        // 黑图检测：第一次自动重拍，仍然全黑就提示改用系统相机，避免把黑照片存进相册
        if (!fromSystemCamera && ImageUtils.isNearlyBlack(rawFile)) {
            rawFile.delete()
            if (captureAttempts == 0) {
                captureAttempts = 1
                mainHandler.post {
                    showHint(getString(R.string.retrying_capture))
                    mainHandler.postDelayed({ captureNow() }, 900)
                }
            } else {
                mainHandler.post {
                    finishCapture()
                    showBlackPhotoDialog()
                }
            }
            return
        }

        var decoded: Bitmap? = null
        var composed: Bitmap? = null
        var thumb: Bitmap? = null
        try {
            val bitmap = ImageUtils.decodeForEdit(rawFile)
            if (bitmap == null) {
                mainHandler.post {
                    finishCapture()
                    Toast.makeText(this, "照片解析失败", Toast.LENGTH_SHORT).show()
                }
                return
            }
            decoded = bitmap

            val place = locationHelper.currentPlaceBlocking(6000)
            val finalImage = WatermarkRenderer.render(bitmap, takenAt, place)
            composed = finalImage
            Log.d("WorkCamera", "watermark done ${finalImage.width}x${finalImage.height} mean=${String.format("%.1f", ImageUtils.meanLuminance(finalImage))} mutable=${finalImage.isMutable} config=${finalImage.config}")

            val uri = PhotoSaver.save(this, finalImage, takenAt, place)
            val scaled = ImageUtils.scaleDown(finalImage, 320)
            if (scaled !== finalImage) {
                thumb = scaled
            }

            mainHandler.post {
                isCapturing = false
                shutterBtn.isEnabled = true
                captureAttempts = 0
                locationText.text = place
                if (uri != null) {
                    showHint(getString(R.string.photo_saved))
                    val bitmapThumb = thumb
                    if (bitmapThumb != null && !bitmapThumb.isRecycled) {
                        galleryGlyph.visibility = View.GONE
                        galleryThumb.visibility = View.VISIBLE
                        galleryThumb.setImageBitmap(bitmapThumb)
                    } else {
                        refreshGalleryThumb()
                    }
                } else {
                    Toast.makeText(this, "保存失败，请检查存储权限或空间", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            mainHandler.post {
                finishCapture()
                Toast.makeText(this, "处理照片失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            rawFile.delete()
            if (decoded !== composed) {
                ImageUtils.recycleQuietly(decoded)
            }
            // 成品位图已压缩入库，缩略图是独立副本，可安全回收
            ImageUtils.recycleQuietly(composed)
        }
    }

    /** 设置：查看并一键清理缓存（只清 App 缓存，不动「下载/工作相机」里的照片） */
    private fun showSettingsDialog() {
        io.execute {
            val cacheBytes = CacheCleaner.totalBytes(this)
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            } catch (e: Exception) {
                ""
            }
            mainHandler.post {
                val message = getString(
                    R.string.cache_clean_message,
                    CacheCleaner.format(this, cacheBytes)
                ) + "\n\n" + getString(R.string.settings_about, versionName)
                AlertDialog.Builder(this)
                    .setTitle(R.string.settings)
                    .setMessage(message)
                    .setPositiveButton(R.string.cache_clean_action) { _, _ -> clearCache() }
                    .setNegativeButton(R.string.settings_close, null)
                    .show()
            }
        }
    }

    private fun clearCache() {
        if (isCapturing) {
            Toast.makeText(this, R.string.cache_clean_busy, Toast.LENGTH_SHORT).show()
            return
        }
        io.execute {
            val freed = CacheCleaner.clear(this)
            mainHandler.post {
                refreshGalleryThumb()
                if (freed <= 0L) {
                    Toast.makeText(this, R.string.cache_clean_empty, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.cache_clean_done, CacheCleaner.format(this, freed)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun refreshGalleryThumb() {
        io.execute {
            val photos = try {
                PhotoRepository(this).loadAll()
            } catch (e: Exception) {
                emptyList<WorkPhoto>()
            }
            val latest = photos.firstOrNull()
            mainHandler.post {
                if (latest == null) {
                    galleryThumb.visibility = View.GONE
                    galleryGlyph.visibility = View.VISIBLE
                } else {
                    galleryGlyph.visibility = View.GONE
                    galleryThumb.visibility = View.VISIBLE
                    thumbLoader.loadThumb(latest, 320, galleryThumb)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) {
            if (cameraProvider == null) startCamera()
            refreshGalleryThumb()
            if (locationHelper.hasPermission()) {
                locationHelper.locateAsync { text -> locationText.text = text }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        io.shutdownNow()
        thumbLoader.shutdown()
    }
}
