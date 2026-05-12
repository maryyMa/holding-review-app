package com.example.holdingreview.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用协程友好的 API 封装 ML Kit 中文文本识别。
 */
@Singleton
class OcrTextRecognizer @Inject constructor(
    /** 用于将内容 URI 加载为 ML Kit 图片的应用上下文。 */
    @ApplicationContext private val context: Context
) {
    /** 延迟创建的识别器，确保 ML Kit 客户端只在需要时初始化。 */
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 在 IO 调度器上识别图片 URI 中的文本。
     */
    suspend fun recognize(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image).await().text
        }
    }
}
