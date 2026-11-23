package com.alok.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.View
import android.view.WindowManager
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encryption at rest: AES-256-GCM with a key that lives inside the phone's secure hardware
 * (Android Keystore). The key can never be exported, so the data files are useless if copied off the phone.
 */
object Crypto {
    private const val ALIAS = "jarvis_master_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    @Volatile private var cached: SecretKey? = null

    @Synchronized
    private fun key(): SecretKey {
        cached?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        val k = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
        cached = k
        return k
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }

    fun encryptString(s: String): String = Base64.encodeToString(encrypt(s.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    fun decryptString(s: String): String? = try {
        String(decrypt(Base64.decode(s, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) { null }

    /** Encrypted file read/write (notes, to-dos) */
    fun writeFile(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(encrypt(text.toByteArray(Charsets.UTF_8)))
        tmp.renameTo(f)
    }
    fun readFile(f: File): String? = try { String(decrypt(f.readBytes()), Charsets.UTF_8) } catch (_: Exception) { null }
}

/** SharedPreferences where every value is encrypted. Old plain-text settings are migrated once and wiped. */
class SecurePrefs private constructor(context: Context) {
    companion object {
        @Volatile private var inst: SecurePrefs? = null
        fun get(c: Context) = inst ?: synchronized(this) { inst ?: SecurePrefs(c.applicationContext).also { inst = it } }
    }

    private val p = context.getSharedPreferences("jarvis_secure", Context.MODE_PRIVATE)
    private val cache = ConcurrentHashMap<String, String>()

    init {
        val old = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
        if (old.all.isNotEmpty()) {
            val e = p.edit()
            for ((k, v) in old.all) if (v != null) e.putString(k, Crypto.encryptString(v.toString()))
            e.commit()
            old.edit().clear().commit()
            try { context.deleteSharedPreferences("jarvis_settings") } catch (_: Exception) {}
        }
    }

    fun getString(key: String, def: String): String {
        cache[key]?.let { return it }
        val raw = p.getString(key, null) ?: return def
        val v = Crypto.decryptString(raw) ?: return def
        cache[key] = v
        return v
    }

    fun putString(key: String, value: String) {
        cache[key] = value
        p.edit().putString(key, Crypto.encryptString(value)).apply()
    }

    fun getFloat(key: String, def: Float) = getString(key, "").toFloatOrNull() ?: def
    fun putFloat(key: String, value: Float) = putString(key, value.toString())
    fun has(key: String) = p.contains(key)
}

/**
 * App lock: fingerprint / face / PIN required to open Jarvis.
 * Locks again 30 seconds after Jarvis leaves the screen, and always after a restart.
 */
object AppLock {
    private const val RELOCK_MS = 30_000L
    private var unlocked = false
    private var visible = 0
    private var hiddenAt = 0L
    private var prompting = false

    fun onStart() {
        if (visible == 0 && hiddenAt != 0L && SystemClock.elapsedRealtime() - hiddenAt > RELOCK_MS) unlocked = false
        visible++
    }

    fun onStop() {
        visible = (visible - 1).coerceAtLeast(0)
        if (visible == 0) hiddenAt = SystemClock.elapsedRealtime()
    }

    fun lockNow() { unlocked = false }
    val isUnlocked get() = unlocked

    private fun hasScreenLock(a: Activity) = a.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun require(a: Activity, onOk: () -> Unit) {
        if (unlocked) { onOk(); return }
        if (prompting) return
        if (!hasScreenLock(a)) {
            AlertDialog.Builder(a)
                .setTitle("Screen lock needed")
                .setMessage("Jarvis can read and send messages, so it only opens on a phone protected by a fingerprint, face or PIN.\n\nSet a screen lock, then open Jarvis again.")
                .setCancelable(false)
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    a.startActivity(Intent(AndroidSettings.ACTION_SECURITY_SETTINGS)); a.finishAffinity()
                }
                .setNegativeButton("CLOSE") { _, _ -> a.finishAffinity() }
                .show()
            return
        }
        prompting = true
        val b = BiometricPrompt.Builder(a)
            .setTitle("Unlock Jarvis")
            .setSubtitle("Fingerprint, face or phone PIN")
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        }
        b.build().authenticate(CancellationSignal(), a.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                prompting = false; unlocked = true; onOk()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                prompting = false
                a.finishAffinity()   // cancelled / too many attempts → close the app
            }
        })
    }

    /** True while the phone itself is locked — personal actions are refused then. */
    fun phoneLocked(c: Context) = c.getSystemService(KeyguardManager::class.java).isDeviceLocked
}

/** Every Jarvis screen: no screenshots / screen recording / recent-apps preview, and behind the app lock. */
abstract class SecureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
    }

    override fun onStart() { super.onStart(); AppLock.onStart() }
    override fun onStop() { super.onStop(); AppLock.onStop() }

    override fun onResume() {
        super.onResume()
        if (!AppLock.isUnlocked) {
            window.decorView.visibility = View.INVISIBLE
            AppLock.require(this) { window.decorView.visibility = View.VISIBLE; onUnlocked() }
        } else {
            window.decorView.visibility = View.VISIBLE
            onUnlocked()
        }
    }

    /** Called on every resume once the user is verified */
    open fun onUnlocked() {}
}

/** Checks a downloaded update was signed with the same key as the installed app. */
object UpdateCheck {
    private fun certs(pm: PackageManager, info: android.content.pm.PackageInfo?): Set<String> {
        val si = info?.signingInfo ?: return emptySet()
        val sigs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return sigs.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    fun sameSigner(c: Context, apk: File): Boolean {
        val pm = c.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val mine = certs(pm, pm.getPackageInfo(c.packageName, flags))
        val other = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (other.packageName != c.packageName) return false
        val theirs = certs(pm, other)
        return mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isNotEmpty()
    }
}
