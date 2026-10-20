package com.tawasol.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface SecureKeyStorage {
    fun saveUserPrivateKey(userId: String, privateKeyBase64: String)
    fun getUserPrivateKey(userId: String): String?
    fun saveUserPublicKey(userId: String, publicKeyBase64: String)
    fun getUserPublicKey(userId: String): String?
    fun savePeerPublicKey(peerUserId: String, publicKeyBase64: String)
    fun getPeerPublicKey(peerUserId: String): String?
    fun saveSafetyNumberVerified(peerUserId: String, isVerified: Boolean)
    fun isSafetyNumberVerified(peerUserId: String): Boolean
}

/**
 * Manages sensitive local key storage backed by Android KeyStore.
 * Securely stores auth session tokens and cryptographic key pairs for E2EE.
 */
class KeystoreManager(context: Context) : SecureKeyStorage {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "tawasol_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuthToken(token: String) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearAuthToken() {
        sharedPreferences.edit().remove(KEY_AUTH_TOKEN).apply()
    }

    fun saveCurrentUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getCurrentUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    override fun saveUserPrivateKey(userId: String, privateKeyBase64: String) {
        sharedPreferences.edit().putString(KEY_E2EE_PRIVATE_PREFIX + userId, privateKeyBase64).apply()
    }

    override fun getUserPrivateKey(userId: String): String? {
        return sharedPreferences.getString(KEY_E2EE_PRIVATE_PREFIX + userId, null)
    }

    override fun saveUserPublicKey(userId: String, publicKeyBase64: String) {
        sharedPreferences.edit().putString(KEY_E2EE_PUBLIC_PREFIX + userId, publicKeyBase64).apply()
    }

    override fun getUserPublicKey(userId: String): String? {
        return sharedPreferences.getString(KEY_E2EE_PUBLIC_PREFIX + userId, null)
    }

    override fun savePeerPublicKey(peerUserId: String, publicKeyBase64: String) {
        sharedPreferences.edit().putString(KEY_PEER_PUBLIC_PREFIX + peerUserId, publicKeyBase64).apply()
    }

    override fun getPeerPublicKey(peerUserId: String): String? {
        return sharedPreferences.getString(KEY_PEER_PUBLIC_PREFIX + peerUserId, null)
    }

    override fun saveSafetyNumberVerified(peerUserId: String, isVerified: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SAFETY_VERIFIED_PREFIX + peerUserId, isVerified).apply()
    }

    override fun isSafetyNumberVerified(peerUserId: String): Boolean {
        return sharedPreferences.getBoolean(KEY_SAFETY_VERIFIED_PREFIX + peerUserId, false)
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_AUTH_TOKEN = "tawasol_jwt_token"
        private const val KEY_USER_ID = "tawasol_current_user_id"
        private const val KEY_E2EE_PRIVATE_PREFIX = "tawasol_e2ee_priv_"
        private const val KEY_E2EE_PUBLIC_PREFIX = "tawasol_e2ee_pub_"
        private const val KEY_PEER_PUBLIC_PREFIX = "tawasol_e2ee_peer_pub_"
        private const val KEY_SAFETY_VERIFIED_PREFIX = "tawasol_safety_verified_"
    }
}
