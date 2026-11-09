package com.gammac3.ddlapp.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 本机 OCR：ML Kit 中文模型，中英混排。
 *
 * 传入的 bitmap 所有权归本类：任务完成 / 失败 / 取消时统一 recycle。
 * 不落盘、不打日志、不上传。
 */
class CaptureOcr {

    private var recognizer: TextRecognizer? = null

    private fun client(): TextRecognizer =
        recognizer ?: TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build(),
        ).also { recognizer = it }

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val task = try {
            client().process(InputImage.fromBitmap(bitmap, 0))
        } catch (t: Throwable) {
            bitmap.recycle()
            cont.resume("")
            return@suspendCancellableCoroutine
        }
        // 任务真正结束时才回收：8 秒超时提前返回后，ML Kit 可能仍在读这块内存
        task.addOnCompleteListener { bitmap.recycle() }
        task.addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result.text)
        }
        task.addOnFailureListener {
            if (cont.isActive) cont.resume("")
        }
        task.addOnCanceledListener {
            if (cont.isActive) cont.resume("")
        }
    }

    /** 幂等；服务销毁时调用。 */
    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
