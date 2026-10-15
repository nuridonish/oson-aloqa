package com.osonaloqa.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private const val KEY_ALIAS = "osonaloqa_master"
    private const val EC_ALIAS = "osonaloqa_ec"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + data.size)
        iv.copyInto(out)
        data.copyInto(out, iv.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val data = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    fun publicKeyBase64(): String = try {
        Base64.encodeToString(ensureEcKeyPair().public.encoded, Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }

    private fun ensureEcKeyPair(): java.security.KeyPair {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(EC_ALIAS)
        if (cert != null) {
            val priv = ks.getKey(EC_ALIAS, null) as java.security.PrivateKey
            return java.security.KeyPair(cert.publicKey, priv)
        }
        val gen = java.security.KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        gen.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    private fun sharedAesKey(peerPubB64: String): SecretKey? {
        val kf = java.security.KeyFactory.getInstance("EC")
        val pub = kf.generatePublic(java.security.spec.X509EncodedKeySpec(Base64.decode(peerPubB64, Base64.NO_WRAP)))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(ensureEcKeyPair().private)
        agreement.doPhase(pub, true)
        val keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptFor(peerPubB64: String, plain: String): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedAesKey(peerPubB64))
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(out, Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }

    fun decryptFor(peerPubB64: String, enc: String): String = try {
        val all = Base64.decode(enc, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, sharedAesKey(peerPubB64), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }
}
