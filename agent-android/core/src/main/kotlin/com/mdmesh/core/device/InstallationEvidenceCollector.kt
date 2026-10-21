package com.mdmesh.core.device

import com.mdmesh.proto.AppInstallationEvidence
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CancellationException

/** Internal paths never leave the device. Snapshot equality detects package updates during hashing. */
data class InstalledArtifactSnapshot(
    val pkg: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val basePath: String,
    val splitPaths: List<String>,
    val currentSignerSha256: List<String>,
)

interface InstalledArtifactSource {
    fun snapshot(pkg: String): InstalledArtifactSnapshot?
    fun openBaseApk(snapshot: InstalledArtifactSnapshot): InputStream
}

/** Bounded, explicitly targeted evidence. Split APKs need a complete signed
 * artifact-set manifest and multiple signers need set matching; neither is
 * silently reduced to a base APK or a single trusted signer. */
class InstallationEvidenceCollector(
    private val source: InstalledArtifactSource,
    private val maxApkBytes: Long = 128L * 1024 * 1024,
    private val maxTotalBytes: Long = 256L * 1024 * 1024,
    private val maxDurationNanos: Long = 10_000_000_000L,
    private val nanoTime: () -> Long = System::nanoTime,
    private val observedAt: () -> String = { Instant.now().toString() },
) {
    fun collect(packages: List<String>, authorized: () -> Boolean = { true }): List<AppInstallationEvidence> {
        require(packages.size in 1..5 && packages.distinct().size == packages.size &&
            packages.all { it.length <= 255 && PACKAGE.matches(it) }) { "Invalid installation evidence request" }
        val started = nanoTime()
        var totalBytes = 0L
        fun withinTime() = nanoTime() - started < maxDurationNanos
        return packages.map { pkg ->
            fun unknown(reason: String) = AppInstallationEvidence(pkg, "unknown", reason)
            if (!withinTime()) return@map unknown("time_limit")
            if (!authorized()) return@map unknown("authority_lost")
            try {
                val initial = source.snapshot(pkg) ?: return@map unknown("unavailable")
                if (initial.pkg != pkg || initial.versionCode < 0) return@map unknown("unavailable")
                if (initial.splitPaths.isNotEmpty()) return@map unknown("split_artifacts_unsupported")
                if (initial.currentSignerSha256.size != 1 || !SHA256.matches(initial.currentSignerSha256.single()))
                    return@map unknown("signer_set_unsupported")
                val digest = MessageDigest.getInstance("SHA-256")
                var apkBytes = 0L
                var refusal: String? = null
                source.openBaseApk(initial).use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (!withinTime()) { refusal = "time_limit"; break }
                        if (!authorized()) { refusal = "authority_lost"; break }
                        // At most one byte beyond either cap is read, solely to detect an oversize file.
                        val remaining = minOf(maxApkBytes - apkBytes, maxTotalBytes - totalBytes)
                        if (remaining < 0) { refusal = "size_limit"; break }
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining + 1).toInt())
                        if (read == -1) break
                        if (read == 0) { refusal = "read_unavailable"; break }
                        apkBytes += read; totalBytes += read
                        if (apkBytes > maxApkBytes || totalBytes > maxTotalBytes) { refusal = "size_limit"; break }
                        digest.update(buffer, 0, read)
                    }
                }
                if (refusal != null) return@map unknown(refusal!!)
                if (!withinTime()) return@map unknown("time_limit")
                val finalSnapshot = source.snapshot(pkg)
                if (!withinTime()) return@map unknown("time_limit")
                if (!authorized()) return@map unknown("authority_lost")
                if (apkBytes == 0L || finalSnapshot != initial) return@map unknown("installation_changed")
                AppInstallationEvidence(pkg, "observed", versionCode = initial.versionCode,
                    apkSha256 = digest.digest().joinToString("") { "%02x".format(it) },
                    signerSha256 = initial.currentSignerSha256.single(), observedAt = observedAt())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unknown("unavailable") }
        }
    }

    private companion object {
        val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
