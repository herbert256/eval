package com.eval.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Trust-on-first-use pinning of the apps Eval hands work to. Stockfish's binary runs as a
 * child process with Eval's own permissions and files, and the AI app receives games and
 * instructions, so an app that merely reuses one of these package names must not be used
 * silently. The first signer seen is remembered; a later install signed by someone else is
 * reported as [Status.CHANGED] until the user trusts it. Key rotation keeps trust.
 */
class AppSignerTrust(private val context: Context, private val prefs: SharedPreferences) {

    enum class Status { TRUSTED, CHANGED, NOT_INSTALLED }

    fun check(packageName: String): Status {
        val signers = signers(packageName) ?: return Status.NOT_INSTALLED
        val key = KEY_PREFIX + packageName
        val trusted = prefs.getStringSet(key, null).orEmpty()
        return when {
            trusted.isEmpty() -> { remember(key, signers.current); Status.TRUSTED }
            signers.current == trusted -> Status.TRUSTED
            // A rotated key proves its lineage through the certificate history.
            signers.history.any { it in trusted } -> { remember(key, signers.current); Status.TRUSTED }
            else -> Status.CHANGED
        }
    }

    /** Accept the currently installed signer after the user confirmed it. */
    fun trustCurrent(packageName: String) {
        signers(packageName)?.let { remember(KEY_PREFIX + packageName, it.current) }
    }

    private fun remember(key: String, digests: Set<String>) {
        prefs.edit().putStringSet(key, digests).apply()
    }

    private class Signers(val current: Set<String>, val history: Set<String>)

    private fun signers(packageName: String): Signers? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                    ?: return null
                if (info.hasMultipleSigners()) {
                    val all = info.apkContentsSigners.map { digest(it.toByteArray()) }.toSet()
                    Signers(all, all)
                } else {
                    // Ordered from the original certificate to the current one.
                    val history = info.signingCertificateHistory.map { digest(it.toByteArray()) }
                    if (history.isEmpty()) null else Signers(setOf(history.last()), history.toSet())
                }
            } else {
                @Suppress("DEPRECATION")
                val all = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                    ?.map { digest(it.toByteArray()) }?.toSet().orEmpty()
                if (all.isEmpty()) null else Signers(all, all)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val PREFS_NAME = "eval_trust"
        const val STOCKFISH_PACKAGE = "com.stockfish141"
        const val AI_PACKAGE = "com.ai"
        private const val KEY_PREFIX = "signer_"

        fun create(context: Context) =
            AppSignerTrust(context, context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
