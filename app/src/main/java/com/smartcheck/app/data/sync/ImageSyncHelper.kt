package com.smartcheck.app.data.sync

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.smartcheck.app.utils.FileUtil
import com.smartcheck.sdk.face.FaceSdk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片同步工具
 * 处理图片的上传编码、下载校验、本地存储和特征提取
 */
@Singleton
class ImageSyncHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ImageSyncHelper"
    }

    /**
     * 计算本地图片文件的 SHA-256（小写十六进制）
     * @return SHA-256 字符串，文件不存在时返回 null
     */
    fun sha256OfFile(imageFileName: String): String? {
        val file = FileUtil.getRecordImageFile(context, imageFileName) ?: return null
        if (!file.exists()) return null
        return sha256OfBytes(file.readBytes())
    }

    /**
     * 本地图片 → Base64（NO_WRAP，无 data: 前缀，无换行）
     * @return Base64 字符串，文件不存在时返回 null
     */
    fun fileToBase64(imageFileName: String): String? {
        val file = FileUtil.getRecordImageFile(context, imageFileName) ?: return null
        if (!file.exists()) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    /**
     * 下载图片 → 校验 SHA-256 → 存本地
     * @return 本地文件名
     */
    suspend fun downloadVerifySave(
        api: EmployeeSyncApi,
        fileId: String,
        expectedSha256: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytesResult = api.downloadImage(fileId)
            if (bytesResult.isFailure) {
                return@withContext Result.failure(bytesResult.exceptionOrNull()!!)
            }
            val bytes = bytesResult.getOrThrow()

            // 校验 SHA-256
            val actualSha256 = sha256OfBytes(bytes)
            if (actualSha256 != expectedSha256) {
                Timber.w("$TAG: SHA-256 mismatch for $fileId: expected=$expectedSha256, actual=$actualSha256")
                return@withContext Result.failure(
                    ImageHashMismatchException(expectedSha256, actualSha256)
                )
            }

            // 保存文件
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("图片解码失败: $fileId"))

            val filename = "sync_${fileId}_${System.currentTimeMillis()}.jpg"
            FileUtil.saveBitmapToInternal(context, bitmap, filename)
            bitmap.recycle()

            Timber.d("$TAG: downloadVerifySave 成功: $fileId → $filename")
            Result.success(filename)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: downloadVerifySave failed for $fileId")
            Result.failure(e)
        }
    }

    /**
     * 提取人脸特征（synchronized FaceSdk，线程安全）
     * @return 特征向量 ByteArray
     */
    suspend fun extractFaceFeature(imageFileName: String): Result<ByteArray> =
        withContext(Dispatchers.Default) {
            synchronized(FaceSdk) {
                try {
                    if (!FaceSdk.isInitialized()) {
                        val initRet = FaceSdk.init(context)
                        if (initRet != 0) {
                            return@synchronized Result.failure(
                                Exception("FaceSdk 初始化失败: ${FaceSdk.getLastInitError()}")
                            )
                        }
                    }

                    val bitmap = FileUtil.loadBitmapFromInternal(context, imageFileName)
                        ?: return@synchronized Result.failure(Exception("图片加载失败: $imageFileName"))

                    val feature = FaceSdk.extractFeature(bitmap)
                    bitmap.recycle()

                    if (feature == null) {
                        Timber.w("$TAG: 无法提取人脸特征: $imageFileName（可能图片中无人脸）")
                        return@synchronized Result.failure(Exception("无法提取人脸特征: $imageFileName"))
                    }

                    Result.success(floatArrayToByteArray(feature))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: extractFaceFeature failed for $imageFileName")
                    Result.failure(e)
                }
            }
        }

    // ==================== 内部工具 ====================

    private fun sha256OfBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floatArray.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floatArray)
        return buffer.array()
    }
}

/** SHA-256 校验失败异常 */
class ImageHashMismatchException(val expected: String, val actual: String) :
    Exception("图片 SHA-256 校验失败: expected=$expected, actual=$actual")
