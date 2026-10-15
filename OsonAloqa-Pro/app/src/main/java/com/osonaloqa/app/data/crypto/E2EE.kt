package com.osonaloqa.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * E2EE — рамзгузории аз-қатор ба охир (end-to-end encryption).
 *
 * Нақша:
 *  1. Ҳар корбар ҷуфти калидҳои X25519-монанд (EC P-256) дар Android Keystore дорад.
 *  2. Калиди оммавӣ (public) ба сервер тавассути PUT /keys дода мешавад.
 *  3. Ҳангоми фиристодан: ECDH(калиди ман, калиди қабулкунанда) → AES-256-GCM калид.
 *  4. Сервер ТАНҲО матни рамзшударо нигоҳ медорад — матни аслӣ ҳеҷ гоҳ ба сервер намерасад.
 *
 * Мутобиқи протоколи Signal (соддашуда): X25519 + AES-256-GCM + IV-и тасодуфӣ 96-bit.
 */
object E2EE {

    private const val EC_ALIAS = "osonaloqa_e2ee"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /** Ҷуфти калидҳо — як маротиба сохта мешавад ва дар Keystore мемонад */
    fun ensureKeyPair(): KeyPair {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(EC_ALIAS)
        if (cert != null) {
            val priv = ks.getKey(EC_ALIAS, null) as java.security.PrivateKey
            return KeyPair(cert.publicKey, priv)
        }
        val gen = java.security.KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        gen.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return gen.generateKeyPair()
    }

    /** Калиди оммавӣ — Base64 (X509) */
    fun publicKeyBase64(): String = try {
        Base64.encodeToString(ensureKeyPair().public.encoded, Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }

    /** Калиди аудитивӣ (приватный) */
    private fun privateKey(): PrivateKey = ensureKeyPair().private

    /** ECDH → калиди AES-256 вақти якҷояшавии калидҳо */
    private fun deriveSharedKey(peerPubB64: String): SecretKey {
        val kf = KeyFactory.getInstance("EC")
        val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(peerPubB64, Base64.NO_WRAP)))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey())
        agreement.doPhase(pub, true)
        val secret = agreement.generateSecret()
        val keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(secret)
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Рамзгузорӣ барои қабулкунанда */
    fun encryptFor(peerPubB64: String, plain: String): String = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveSharedKey(peerPubB64))
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(out, Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }

    /** Кушодани рамз */
    fun decryptFor(peerPubB64: String, encoded: String): String = try {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveSharedKey(peerPubB64), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }
}
