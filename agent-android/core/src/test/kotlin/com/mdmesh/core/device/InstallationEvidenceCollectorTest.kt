package com.mdmesh.core.device

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class InstallationEvidenceCollectorTest {
    private val snapshot = InstalledArtifactSnapshot("com.example.app", 7, 1234, "/private/base.apk", emptyList(), listOf("a".repeat(64)))
    private class Source(var value: InstalledArtifactSnapshot?, val bytes: ByteArray = "abc".toByteArray()) : InstalledArtifactSource {
        var reads = 0
        var changed: InstalledArtifactSnapshot? = null
        var opened = 0
        var closed = false
        var failRead = false
        override fun snapshot(pkg: String): InstalledArtifactSnapshot? { reads++; return if (reads > 1 && changed != null) changed else value?.copy(pkg = pkg) }
        override fun openBaseApk(snapshot: InstalledArtifactSnapshot): InputStream {
            opened++
            return object : ByteArrayInputStream(bytes) {
                override fun read(b: ByteArray, off: Int, len: Int): Int { if (failRead) throw IOException("private path"); return super.read(b, off, len) }
                override fun close() { closed = true; super.close() }
            }
        }
    }
    @Test fun `records exact APK current signer version and time without internal paths`() {
        val source = Source(snapshot)
        val evidence = InstallationEvidenceCollector(source, observedAt = { "2026-10-20T12:00:00Z" }).collect(listOf(snapshot.pkg)).single()
        assertEquals("observed", evidence.status); assertEquals(7L, evidence.versionCode)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", evidence.apkSha256)
        assertEquals("a".repeat(64), evidence.signerSha256); assertEquals("2026-10-20T12:00:00Z", evidence.observedAt)
        assertFalse(evidence.toString().contains("private")); assertTrue(source.closed)
    }
    @Test fun `split and multiple signer packages never produce partial evidence`() {
        for (value in listOf(snapshot.copy(splitPaths = listOf("/private/split.apk")), snapshot.copy(currentSignerSha256 = listOf("a".repeat(64), "b".repeat(64))))) {
            val source = Source(value); val evidence = InstallationEvidenceCollector(source).collect(listOf(snapshot.pkg)).single()
            assertEquals("unknown", evidence.status); assertNull(evidence.apkSha256); assertEquals(0, source.opened)
        }
    }
    @Test fun `oversize APK and aggregate budget refuse rather than truncate hashes`() {
        val source = Source(snapshot)
        val result = InstallationEvidenceCollector(source, maxApkBytes = 2).collect(listOf(snapshot.pkg)).single()
        assertEquals("size_limit", result.reason); assertNull(result.apkSha256); assertTrue(source.closed)
        val results = InstallationEvidenceCollector(Source(snapshot), maxTotalBytes = 4).collect(listOf("com.example.one", "com.example.two"))
        assertEquals("observed", results[0].status); assertEquals("size_limit", results[1].reason); assertNull(results[1].apkSha256)
    }
    @Test fun `package update during hashing discards observed identity`() {
        val source = Source(snapshot); source.changed = snapshot.copy(lastUpdateTime = 1235)
        val result = InstallationEvidenceCollector(source).collect(listOf(snapshot.pkg)).single()
        assertEquals("installation_changed", result.reason); assertNull(result.signerSha256); assertNull(result.observedAt)
    }
    @Test fun `unavailable read and elapsed deadline stay unknown without raw error details`() {
        val source = Source(snapshot); source.failRead = true
        val result = InstallationEvidenceCollector(source).collect(listOf(snapshot.pkg)).single()
        assertEquals("unavailable", result.reason); assertFalse(result.toString().contains("private")); assertTrue(source.closed)
        var time = 0L
        val elapsed = InstallationEvidenceCollector(Source(snapshot), maxDurationNanos = 2, nanoTime = { time++ }).collect(listOf(snapshot.pkg)).single()
        assertEquals("time_limit", elapsed.reason); assertNull(elapsed.apkSha256)
    }
    @Test fun `invalid duplicate empty or broad requests are refused before package reads`() {
        for (packages in listOf(emptyList(), listOf("../base.apk"), listOf(snapshot.pkg, snapshot.pkg), (1..6).map { "com.example.app$it" })) {
            val source = Source(snapshot)
            try { InstallationEvidenceCollector(source).collect(packages); fail("Expected invalid request") } catch (_: IllegalArgumentException) { }
            assertEquals(0, source.reads); assertEquals(0, source.opened)
        }
    }
}
