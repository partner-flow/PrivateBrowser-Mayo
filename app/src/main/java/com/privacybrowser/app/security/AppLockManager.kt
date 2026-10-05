package com.privacybrowser.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages the optional PIN app lock.
 *
 * Security notes:
 *  - The raw PIN is never stored and never logged. Only a salted PBKDF2-HMAC-SHA256 hash
 *    (120,000 iterations, random 16-byte salt) is written to app-private storage, which is
 *    excluded from backups (see data_extraction_rules.xml and allowBackup="false").
 *  - Verification re-derives the hash from the entered PIN and compares it in constant time.
 *  - Biometric unlock is handled separately by BiometricPrompt in MainActivity and never
 *    replaces the PIN; the PIN is always the fallback.
 *  - A short numeric PIN has limited entropy no matter how it is hashed, so this protects
 *    against casual access to the app, not against an attacker who can extract app data
 *    from a rooted device. The Privacy screen says so.
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let { decode(it) } ?: return false
        val stored = prefs.getString(KEY_HASH, null)?.let { decode(it) } ?: return false
        return MessageDigest.isEqual(derive(pin, salt), stored)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
    }

    private fun decode(value: String): ByteArray? =
        runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
    }
}
