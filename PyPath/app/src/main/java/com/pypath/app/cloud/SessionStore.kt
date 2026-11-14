package com.pypath.app.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.cloudSessionStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_cloud_session")

/** Where the Supabase session is kept between app starts. */
interface SessionStore {
    suspend fun load(): SupabaseSession?
    suspend fun save(session: SupabaseSession)
    suspend fun clear()
}

/**
 * Keeps the Supabase session encrypted with AES-256-GCM. The key lives in the Android Keystore and
 * never leaves it, so the session cannot be read from a copied or backed-up data folder: a restored
 * backup simply fails to decrypt and the learner is asked to sign in again.
 */
class KeystoreSessionStore(private val context: Context) : SessionStore {
    private val key = stringPreferencesKey("session_v1")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): SupabaseSession? {
        val blob = runCatching { context.cloudSessionStore.data.first()[key] }.getOrNull() ?: return null
        val session = runCatching { json.decodeFromString(SupabaseSession.serializer(), decrypt(blob)) }.getOrNull()
        if (session == null) clear()
        return session
    }

    override suspend fun save(session: SupabaseSession) {
        val blob = encrypt(json.encodeToString(SupabaseSession.serializer(), session))
        context.cloudSessionStore.edit { it[key] = blob }
    }

    override suspend fun clear() {
        runCatching { context.cloudSessionStore.edit { it.clear() } }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "pypath_supabase_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** In-memory version for tests. */
class MemorySessionStore(var session: SupabaseSession? = null) : SessionStore {
    override suspend fun load() = session
    override suspend fun save(session: SupabaseSession) { this.session = session }
    override suspend fun clear() { session = null }
}
