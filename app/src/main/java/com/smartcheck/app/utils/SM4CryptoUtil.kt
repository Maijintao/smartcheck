package com.smartcheck.app.utils

import timber.log.Timber
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SM4 国密加解密工具类
 *
 * 算法规范：SM4-CBC，零字节填充（末尾补 \x00）
 * 编码格式：明文 → UTF-8 字节 → 零填充 → SM4-CBC 加密 → 16 进制字符串
 *
 * 注意：Android 系统自带旧版 BouncyCastle Provider，使用前需先调用：
 *   Security.removeProvider("BC")
 *   Security.insertProviderAt(BouncyCastleProvider(), 1)
 */
object SM4CryptoUtil {

    private const val ALGORITHM = "SM4"
    private const val TRANSFORMATION = "SM4/CBC/NoPadding"

    /**
     * SM4 加密
     *
     * @param plaintext 明文字符串（通常为 JSON）
     * @param key 16 字节密钥
     * @param iv 16 字节初始向量
     * @return 16 进制密文字符串
     */
    fun encrypt(plaintext: String, key: ByteArray, iv: ByteArray): String {
        require(key.size == 16) { "SM4 key must be 16 bytes, got ${key.size}" }
        require(iv.size == 16) { "SM4 IV must be 16 bytes, got ${iv.size}" }

        // 1. UTF-8 编码
        val plainBytes = plaintext.toByteArray(Charset.forName("UTF-8"))
        // 2. 零填充至 16 的整数倍
        val paddedBytes = zeroPadding(plainBytes)
        // 3. SM4-CBC 加密
        val cipher = Cipher.getInstance(TRANSFORMATION, "BC")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, ALGORITHM),
            IvParameterSpec(iv)
        )
        val encrypted = cipher.doFinal(paddedBytes)
        // 4. 转 16 进制字符串
        return bytesToHex(encrypted)
    }

    /**
     * SM4 解密
     *
     * @param ciphertext 16 进制密文字符串
     * @param key 16 字节密钥
     * @param iv 16 字节初始向量
     * @return 明文字符串
     */
    fun decrypt(ciphertext: String, key: ByteArray, iv: ByteArray): String {
        require(key.size == 16) { "SM4 key must be 16 bytes, got ${key.size}" }
        require(iv.size == 16) { "SM4 IV must be 16 bytes, got ${iv.size}" }

        // 1. 16 进制字符串转字节
        val encryptedBytes = hexToBytes(ciphertext)
        // 2. SM4-CBC 解密
        val cipher = Cipher.getInstance(TRANSFORMATION, "BC")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, ALGORITHM),
            IvParameterSpec(iv)
        )
        val decrypted = cipher.doFinal(encryptedBytes)
        // 3. 去除末尾零填充
        val plainBytes = removeZeroPadding(decrypted)
        // 4. UTF-8 解码
        return String(plainBytes, Charset.forName("UTF-8"))
    }

    /**
     * 零填充：将数据填充至 16 字节的整数倍，末尾补 \x00
     */
    private fun zeroPadding(data: ByteArray): ByteArray {
        val blockSize = 16
        val remainder = data.size % blockSize
        if (remainder == 0) return data
        val paddingLen = blockSize - remainder
        return data + ByteArray(paddingLen) { 0 }
    }

    /**
     * 去除末尾零填充
     */
    private fun removeZeroPadding(data: ByteArray): ByteArray {
        var len = data.size
        while (len > 0 && data[len - 1] == 0.toByte()) {
            len--
        }
        return data.copyOf(len)
    }

    /**
     * 字节数组转 16 进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * 16 进制字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Hex string length must be even" }
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = (
                (Character.digit(hex[i], 16) shl 4) +
                Character.digit(hex[i + 1], 16)
            ).toByte()
            i += 2
        }
        return data
    }

    /**
     * 16 进制字符串转字节数组（公开方法，供外部使用）
     */
    fun hexStringToBytes(hex: String): ByteArray = hexToBytes(hex)
}
