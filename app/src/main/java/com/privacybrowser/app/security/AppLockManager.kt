package com.privacybrowser.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages the optional PIN / biometric app lock.
 *
 * Security notes:
 *  - The raw PIN is NEVER stored and NEVER logged. Only a salted SHA-256 hash of it is written to
 *    disk, inside EncryptedSharedPreferences (which itself encrypts the file using a Keystore-backed
 *    master key). This mirrors how a password should be handled: verify by re-hashing the entered
 *    PIN and comparing hashes, never by storing something reversible.
 *  - A per-install random salt is generated once and stored alongside the hash; this defeats
 *    precomputed-hash ("rainbow table") attacks against short numeric PINs.
 *  - Biometric authentication (fingerprint/face, via androidx.biometric BiometricPrompt) is offered
 *    as an alternative unlock method when the device supports it, but the PIN is always required as
 *    the fallback / setup method — biometric alone is not used to derive or replace the stored hash.
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.toBase64())
            .putString(KEY_HASH, hash)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = saltB64.fromBase64()
        return hash(pin, salt) == storedHash
    }

    fun clearPin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val result = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return result.toBase64()
    }

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
    }
}
