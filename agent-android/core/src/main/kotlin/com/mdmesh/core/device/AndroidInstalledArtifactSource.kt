package com.mdmesh.core.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

class AndroidInstalledArtifactSource(private val context: Context) : InstalledArtifactSource {
    @Suppress("DEPRECATION")
    override fun snapshot(pkg: String): InstalledArtifactSnapshot? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageInfo(pkg, flags)
        val app = info.applicationInfo ?: return null
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return null
            // For a rotated single-signer package, Android documents the current
            // signer as the final entry. Past signers are not current identity.
            if (signing.hasMultipleSigners()) signing.apkContentsSigners?.toList()
            else signing.signingCertificateHistory?.lastOrNull()?.let { listOf(it) }
        } else info.signatures?.toList()
        if (signatures.isNullOrEmpty() || signatures.size > 8) return null
        val hashes = signatures.map { signature ->
            val bytes = signature.toByteArray()
            if (bytes.size > 65536) return null
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }.sorted()
        return InstalledArtifactSnapshot(info.packageName,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.lastUpdateTime, app.sourceDir ?: return null, app.splitSourceDirs?.toList()?.sorted() ?: emptyList(), hashes)
    }

    override fun openBaseApk(snapshot: InstalledArtifactSnapshot): InputStream = FileInputStream(snapshot.basePath)
}
