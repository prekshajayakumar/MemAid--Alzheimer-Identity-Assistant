package com.example.myapplication.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreHelper {
    private const val ALIAS = "db_key_alias"

    private fun ensureKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(spec)
            kg.generateKey()
        }
    }

    fun getOrCreateSqlcipherPass(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
        val ivB64 = prefs.getString("db_iv", null)
        val wrappedB64 = prefs.getString("db_wrapped", null)
        return if (ivB64 != null && wrappedB64 != null) {
            unwrap(Base64.decode(ivB64, 0), Base64.decode(wrappedB64, 0))
        } else {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val (iv, wrapped) = wrap(raw)
            prefs.edit()
                .putString("db_iv", Base64.encodeToString(iv, 0))
                .putString("db_wrapped", Base64.encodeToString(wrapped, 0))
                .apply()
            raw
        }
    }

    private fun wrap(raw: ByteArray): Pair<ByteArray, ByteArray> {
        ensureKey()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = ks.getKey(ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret) }
        val iv = cipher.iv
        val wrapped = cipher.doFinal(raw)
        return iv to wrapped
    }

    private fun unwrap(iv: ByteArray, wrapped: ByteArray): ByteArray {
        ensureKey()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = ks.getKey(ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(wrapped)
    }
}
