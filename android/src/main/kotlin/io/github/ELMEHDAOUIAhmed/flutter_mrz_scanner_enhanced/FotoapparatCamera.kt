package io.github.elmehdaouiahmed.flutter_mrz_scanner_enhanced

import kotlinx.coroutines.*
import android.content.Context
import android.graphics.*
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import io.flutter.plugin.common.MethodChannel
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.configuration.UpdateConfiguration
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.preview.Frame
import io.fotoapparat.selector.off
import io.fotoapparat.selector.torch
import io.fotoapparat.view.CameraView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import io.fotoapparat.selector.*
import io.fotoapparat.parameter.*
import io.fotoapparat.selector.manualJpegQuality
import android.os.Environment
import android.util.Log
import android.util.Base64
import org.opencv.android.OpenCVLoader
class FotoapparatCamera constructor(
    val context: Context,
    var messenger: MethodChannel
) {
    private val DEFAULT_PAGE_SEG_MODE = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
    private var cachedTessData: File? = null
    private var mainExecutor = ContextCompat.getMainExecutor(context)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    val cameraView = CameraView(context)

    val configuration = CameraConfiguration(
        frameProcessor = this::processFrame,
        focusMode = firstAvailable(
            continuousFocusPicture(),
            autoFocus(),
            fixed()
        ),
        pictureResolution = highestResolution(),
        previewResolution = highestResolution(),
        previewFpsRange = highestFps(),
        jpegQuality = manualJpegQuality(90)
    )

    val fotoapparat = Fotoapparat(
        context = context,
        view = cameraView,
        cameraConfiguration = configuration
    )

    init {
        if (cachedTessData == null) {
            cachedTessData = getFileFromAssets(context, fileName = "ocrb.traineddata")
        }
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed")
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully")
        }
    }

    fun flashlightOn() {
        fotoapparat.updateConfiguration(UpdateConfiguration(flashMode = torch()))
    }

    fun flashlightOff() {
        fotoapparat.updateConfiguration(UpdateConfiguration(flashMode = off()))
    }

    fun takePhoto(@NonNull result: MethodChannel.Result, crop: Boolean) {
        val photoResult = fotoapparat.autoFocus().takePicture()
        photoResult.toBitmap().whenAvailable { bitmapPhoto ->
            if (bitmapPhoto != null) {
                val bitmap = bitmapPhoto.bitmap
                val rotated = rotateBitmap(bitmap, rotationAngle(bitmapPhoto.rotationDegrees))
                if (crop) {
                    // Crop the PHOTO 
                    //val cropped = calculateCutoutRect(rotated, false) // use false if you don't want to crop to MRZ area
                    val cropped = calculateCutoutRectCardSize(rotated, false)
                    try {
                        val storageDir = context.cacheDir
                        val fileName = "cropped_image_${System.currentTimeMillis()}.jpg"
                        val file = File(storageDir, fileName)
                        file.outputStream().use { output ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 100, output)
                        }
                        
                        val stream = ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        val array = stream.toByteArray()
                        mainExecutor.execute {
                            result.success(array)
                        }

                    } catch (e: IOException) {
                        e.printStackTrace()
                        mainExecutor.execute {
                            result.error("IO_ERROR", "Failed to save cropped image", null)
                        }
                    }
                } else {
                    val stream = ByteArrayOutputStream()
                    rotated.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    val array = stream.toByteArray()
                    mainExecutor.execute {
                        result.success(array)
                    }
                }
            }
        }
    }
    
    private fun rotationAngle(rotation: Int): Int {
        return when (rotation) {
            90 -> -90
            270 -> 90
            180 -> 180
            else -> rotation
        }
    }

    // Process the full frame: apply pre‑processing and OCR without cropping.
    private fun processFrame(frame: Frame) {
        val bitmap = getImage(frame)
    
        // MRZ 영역 추출
        val cropped = calculateCutoutRectCardSize(bitmap, true)
    
        // ✅ 고해상도 스케일 업 (Tesseract 선호)
        val scaled = Bitmap.createScaledBitmap(cropped, cropped.width * 2, cropped.height * 2, true)
    
        // ✅ 고급 전처리 (흑백 + adaptive threshold 흉내)
        val processed = preprocessImageAdvanced(scaled)
    
        scope.launch {
            val mrzText = scanMRZ(processed)
            val fixedMrz = extractMRZ(mrzText)
    
            withContext(Dispatchers.Main) {
                messenger.invokeMethod("onParsed", fixedMrz)
            }
        }
    }

    private fun preprocessImageAdvanced(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    
        // Otsu threshold 흉내
        val width = grayscale.width
        val height = grayscale.height
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
    
        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            pixels[i] = if (gray < 128) Color.BLACK else Color.WHITE
        }
    
        val thresholded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        thresholded.setPixels(pixels, 0, width, 0, 0, width, height)
        return thresholded
    }

    private fun getImage(frame: Frame): Bitmap {
        val out = ByteArrayOutputStream()
        val yuvImage = YuvImage(frame.image, ImageFormat.NV21, frame.size.width, frame.size.height, null)
        yuvImage.compressToJpeg(Rect(0, 0, frame.size.width, frame.size.height), 100, out)
        val imageBytes = out.toByteArray()
        val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return rotateBitmap(image, -frame.rotation)
    }

    private fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    // Preprocess the image by converting it to grayscale and applying a simple threshold.
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Convert to grayscale.
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Apply thresholding.
        val threshold = 128
        val width = grayscale.width
        val height = grayscale.height
        val pixels = IntArray(width * height)
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])  // For grayscale images, R=G=B.
            pixels[i] = if (gray < threshold) Color.BLACK else Color.WHITE
        }
        val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        processed.setPixels(pixels, 0, width, 0, 0, width, height)
        return processed
    }

    // Run OCR using Tesseract on the provided bitmap.
    private fun scanMRZ(bitmap: Bitmap): String {
        val baseApi = TessBaseAPI()
    
        baseApi.init(context.cacheDir.absolutePath, "ocrb")
    
        // ✅ 세팅 강화
        baseApi.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<")
        baseApi.setVariable("user_defined_dpi", "300")
        baseApi.setVariable("load_system_dawg", "F")
        baseApi.setVariable("load_freq_dawg", "F")
        baseApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
    
        baseApi.setImage(bitmap)
        val mrz = baseApi.utF8Text
        baseApi.stop()
    
        return mrz
    }

    private fun extractMRZ(input: String): String {
        val lines = input.split("\n")
        val mrzLines = lines.filter { it.trim().length in 40..45 && it.contains(Regex("[0-9A-Z<]{10,}")) }
        if (mrzLines.size >= 2) {
            return mrzLines.takeLast(2).joinToString("\n")
        }
        return mrzLines.joinToString("\n")
    }

    @Throws(IOException::class)
    fun getFileFromAssets(context: Context, fileName: String): File {
        val directory = File(context.cacheDir, "tessdata/")
        directory.mkdir()
        return File(directory, fileName).also { file ->
            file.outputStream().use { cache ->
                context.assets.open(fileName).use { stream -> 
                    stream.copyTo(cache)
                }
            }
        }
    }

/**
 * Calculates a crop region based on the same document size used in your Flutter overlay.
 * It uses the aspect ratio 86:55 and the same width/height percentages,
 * then expands the region by a given margin to allow for misalignment.
 */
    private fun calculateCutoutRect(bitmap: Bitmap, cropToMRZ: Boolean): Bitmap {
    // Use the same document ratio as in your Flutter overlay.
    val documentFrameRatio = 86.0 / 55.0

    val width: Double
    val height: Double

    // Calculate document frame dimensions based on bitmap size.
    if (bitmap.height > bitmap.width) {
        width = bitmap.width * 0.9  // 90% of available width
        height = width / documentFrameRatio
    } else {
        height = bitmap.height * 0.75  // 75% of available height
        width = height * documentFrameRatio
    }

    // Center the document region within the image.
    val leftOffset = (bitmap.width - width) / 2.0
    val topOffset = (bitmap.height - height) / 2.0

    return if (!cropToMRZ) {
        // Normal cropping: Expand the region with a margin (10% extra on each side)
        val marginPercentage = 0.1
        val marginX = width * marginPercentage
        val marginY = height * marginPercentage

        val newLeft = (leftOffset - marginX).coerceAtLeast(0.0)
        val newTop = (topOffset - marginY).coerceAtLeast(0.0)
        var newWidth = width * (1 + 2 * marginPercentage)
        var newHeight = height * (1 + 2 * marginPercentage)

        if (newLeft + newWidth > bitmap.width) {
            newWidth = bitmap.width - newLeft
        }
        if (newTop + newHeight > bitmap.height) {
            newHeight = bitmap.height - newTop
        }

        Bitmap.createBitmap(bitmap, newLeft.toInt(), newTop.toInt(), newWidth.toInt(), newHeight.toInt())
    } else {
        // Crop to MRZ area only: 35% of the document frame height at the bottom.
        val mrzHeight = height * 0.20
        val mrzLeft = leftOffset
        val mrzTop = topOffset + height - mrzHeight
        val mrzWidth = width

        val cropLeft = mrzLeft.coerceAtLeast(0.0)
        val cropTop = mrzTop.coerceAtLeast(0.0)
        val cropWidth = if (cropLeft + mrzWidth > bitmap.width) bitmap.width - cropLeft else mrzWidth
        val cropHeight = if (cropTop + mrzHeight > bitmap.height) bitmap.height - cropTop else mrzHeight

        Bitmap.createBitmap(bitmap, cropLeft.toInt(), cropTop.toInt(), cropWidth.toInt(), cropHeight.toInt())
    }
    }

    private fun calculateCutoutRectCardSize(bitmap: Bitmap, cropToMRZ: Boolean): Bitmap {
        // 여권 비율: 125mm × 88mm = 1.42:1
        val documentFrameRatio = 1.42
        val width: Double
        val height: Double
    
        if (bitmap.height > bitmap.width) {
            width = bitmap.width * 0.9 // 화면 너비의 90%
            height = width / documentFrameRatio
        } else {
            height = bitmap.height * 0.75 // 화면 높이의 75%
            width = height * documentFrameRatio
        }
    
        // MRZ는 여권 하단 44mm 영역 (전체 높이의 약 50%)
        val mrzZoneOffset = if (cropToMRZ) height * 0.2  else 0.0
        val topOffset = ((bitmap.height - height) / 2 + mrzZoneOffset).coerceAtLeast(0.0)
        val leftOffset = ((bitmap.width - width) / 2).coerceAtLeast(0.0)
    
        val cropWidth = width.coerceAtMost(bitmap.width - leftOffset)
        val cropHeight = (height - mrzZoneOffset).coerceAtMost(bitmap.height - topOffset)
    
        // Validate crop dimensions
        if (cropWidth <= 0 || cropHeight <= 0) {
            throw IllegalArgumentException("Invalid crop dimensions: width=$cropWidth, height=$cropHeight")
        }
    
        return Bitmap.createBitmap(
            bitmap,
            leftOffset.toInt(),
            topOffset.toInt(),
            cropWidth.toInt(),
            cropHeight.toInt()
        )
    }

    fun dispose() {
    job.cancel()
    }
}

