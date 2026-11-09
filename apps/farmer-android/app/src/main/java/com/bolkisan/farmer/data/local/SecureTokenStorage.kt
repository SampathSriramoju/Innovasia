package com.bolkisan.farmer.data.local

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureTokenStorage {
    private const val KEY_ALIAS = "bolkisan_auth_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "bolkisan_secure_prefs"
    private const val KEY_ENCRYPTED_TOKEN = "enc_jwt_token"
    private const val KEY_IV = "enc_jwt_iv"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun saveToken(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (token == null) {
            prefs.edit().remove(KEY_ENCRYPTED_TOKEN).remove(KEY_IV).apply()
            return
        }

        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))

            prefs.edit()
                .putString(KEY_ENCRYPTED_TOKEN, Base64.encodeToString(cipherText, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            prefs.edit().putString(KEY_ENCRYPTED_TOKEN, Base64.encodeToString(token.toByteArray(), Base64.NO_WRAP)).apply()
        }
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encTokenStr = prefs.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        val ivStr = prefs.getString(KEY_IV, null)

        if (ivStr == null) {
            return try {
                String(Base64.decode(encTokenStr, Base64.NO_WRAP), StandardCharsets.UTF_8)
            } catch (e: Exception) { null }
        }

        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(Base64.decode(encTokenStr, Base64.NO_WRAP))
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
