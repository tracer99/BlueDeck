package com.bluedeck.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class SavedCredentials(
    val username: String,
    val password: String,
    val servicePin: String
)

@Singleton
class SecureCredentialsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSavedCredentials(): Boolean = prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)

    fun saveCredentials(username: String, password: String, servicePin: String) {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("servicePin", servicePin)
            .toString()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun getSavedCredentials(): SavedCredentials? {
        val ivText = prefs.getString(KEY_IV, null) ?: return null
        val ciphertextText = prefs.getString(KEY_CIPHERTEXT, null) ?: return null

        return runCatching {
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextText, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plain = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            val json = JSONObject(plain)
            SavedCredentials(
                username = json.optString("username"),
                password = json.optString("password"),
                servicePin = json.optString("servicePin")
            )
        }.getOrNull()?.takeIf { it.username.isNotBlank() && it.password.isNotBlank() }
    }

    fun clearSavedCredentials() {
        prefs.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "bluedeck_secure_credentials"
        const val KEY_ALIAS = "bluedeck_saved_login_key"
        const val KEY_IV = "saved_login_iv"
        const val KEY_CIPHERTEXT = "saved_login_ciphertext"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
