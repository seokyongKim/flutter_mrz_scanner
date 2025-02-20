package: io.github.elmehdaouiahmed.flutter_mrz_scanner_enhanced

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

class FotoapparatCamera constructor(
    val context: Context,
    var messenger: MethodChannel
) {
    private val DEFAULT_PAGE_SEG_MODE = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
    private var cachedTessData: File? = null
    private var mainExecutor = ContextCompat.getMainExecutor(context)

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
        jpegQuality = manualJpegQuality(100)
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
                    val cropped = calculateCutoutRect(rotated, true)
                    try {
                        val storageDir: File? =
                            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        val fileName = "cropped_image_${System.currentTimeMillis()}.jpg"
                        val file = File(storageDir, fileName)
                        file.outputStream().use { output ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 100, output)
                        }
                        // Log and send the file path via the method channel.
                        Log.i("FotoapparatCamera", "Cropped image saved at: ${file.absolutePath}")
                        // Instead of waiting for result.success here, immediately notify Flutter.
                        mainExecutor.execute {
                            messenger.invokeMethod("onCroppedImagePath", file)
                        }
                        // And then immediately return success (or a placeholder) so that the original method call doesn't block.
                        mainExecutor.execute {
                            result.success("CROPPED_IMAGE_SAVED")
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
        // Preprocess the full image (convert to grayscale and apply thresholding) to improve OCR.
        val processedBitmap = preprocessImage(bitmap)
        val mrzText = scanMRZ(processedBitmap)
        val fixedMrz = extractMRZ(mrzText)
        mainExecutor.execute {
            messenger.invokeMethod("onParsed", fixedMrz)
        }
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
        // Set Tesseract to recognize only MRZ-valid characters.
        baseApi.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<")
        baseApi.pageSegMode = DEFAULT_PAGE_SEG_MODE
        baseApi.setImage(bitmap)
        val mrz = baseApi.utF8Text
        baseApi.stop()
        return mrz
    }

    private fun extractMRZ(input: String): String {
        val lines = input.split("\n")
        val mrzLength = lines.last().length
        val mrzLines = lines.takeLastWhile { it.length == mrzLength }
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
 * It uses the aspect ratio 82:52 (≈1.577) and the same width/height percentages,
 * then expands the region by a given margin to allow for misalignment.
 */
private fun calculateCutoutRect(bitmap: Bitmap, cropToMRZ: Boolean): Bitmap {
    // Use the same document ratio as in your Flutter overlay.
    val documentFrameRatio = 82.0 / 52.0  // ≈1.577

    val width: Double
    val height: Double

    // Use the same percentages as your overlay:
    if (bitmap.height > bitmap.width) {
        width = bitmap.width * 0.9   // 90% of available width
        height = width / documentFrameRatio
    } else {
        height = bitmap.height * 0.75  // 75% of available height
        width = height * documentFrameRatio
    }

    // Center the "document" region within the image.
    val leftOffset = (bitmap.width - width) / 2.0
    val topOffset = (bitmap.height - height) / 2.0

    // Define a margin percentage to expand the crop region (1% extra on each side)
    val marginPercentage = 0.01
    val marginX = width * marginPercentage
    val marginY = height * marginPercentage

    // Compute the new coordinates, ensuring they are within bitmap bounds.
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

    return Bitmap.createBitmap(bitmap, newLeft.toInt(), newTop.toInt(), newWidth.toInt(), newHeight.toInt())
}

}

