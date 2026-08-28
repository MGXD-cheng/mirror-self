package com.mgxd.mirror.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES-GCM 加解密工具。
 * 用于加密存储 API Key 等敏感配置，私钥永远不出硬件安全区。
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mirror_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    /** 加密明文，返回 Base64(iv + ciphertext) */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            val combined = iv + encrypted
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Keystore 异常时退化为 Base64（仅混淆，不落盘明文）
            "b64:" + Base64.encodeToString(plainText.toByteArray(), Base64.NO_WRAP)
        }
    }

    /** 解密 Base64(iv + ciphertext)，失败返回空串 */
    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            if (encoded.startsWith("b64:")) {
                String(Base64.decode(encoded.substring(4), Base64.NO_WRAP))
            } else {
                val combined = Base64.decode(encoded, Base64.NO_WRAP)
                val iv = combined.copyOfRange(0, 12)
                val cipherText = combined.copyOfRange(12, combined.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(cipherText), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }
}