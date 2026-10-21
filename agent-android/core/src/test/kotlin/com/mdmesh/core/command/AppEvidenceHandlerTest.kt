package com.mdmesh.core.command

import com.mdmesh.core.command.handlers.AppEvidenceHandler
import com.mdmesh.core.device.InstallationEvidenceCollector
import com.mdmesh.core.device.InstalledArtifactSnapshot
import com.mdmesh.core.device.InstalledArtifactSource
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class AppEvidenceHandlerTest {
    private class Source : InstalledArtifactSource {
        var snapshots = 0
        var afterRead: () -> Unit = {}
        var failure: Exception? = null
        override fun snapshot(pkg: String): InstalledArtifactSnapshot {
            snapshots++; failure?.let { throw it }
            return InstalledArtifactSnapshot(pkg, 7, 123, "/private/base.apk", emptyList(), listOf("a".repeat(64)))
        }
        override fun openBaseApk(snapshot: InstalledArtifactSnapshot): InputStream = object : ByteArrayInputStream("abc".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int { val n = super.read(b, off, len); afterRead(); return n }
        }
    }
    private fun command() = CommandEnvelope(commandId = "synthetic-evidence", issuedAt = "2026-10-20T00:00:00Z", type = "apps.evidence", requiresCapability = "app.installationEvidence", payload = buildJsonObject { put("packages", JsonArray(listOf(JsonPrimitive("com.example.app")))) })
    @Test fun `missing or wrong capability and absent live owner prevent all package reads`() = runTest {
        val source = Source(); val handler = AppEvidenceHandler(InstallationEvidenceCollector(source)) { true }
        for (token in listOf(null, "app.silentInstall")) assertEquals(CommandStatus.UNSUPPORTED, handler.handle(command().copy(requiresCapability = token)).status)
        assertEquals(CommandStatus.UNSUPPORTED, AppEvidenceHandler(InstallationEvidenceCollector(source)) { false }.handle(command()).status)
        assertEquals(0, source.snapshots)
    }
    @Test fun `authorized exact capability returns observation without internal paths`() = runTest {
        val result = AppEvidenceHandler(InstallationEvidenceCollector(Source())) { true }.handle(command())
        assertEquals(CommandStatus.DONE, result.status); assertTrue(result.detail!!.contains("observed")); assertFalse(result.detail!!.contains("private"))
    }
    @Test fun `losing owner authority during hashing discards all observations`() = runTest {
        var owner = true
        val source = Source(); source.afterRead = { owner = false }
        val result = AppEvidenceHandler(InstallationEvidenceCollector(source)) { owner }.handle(command())
        assertEquals(CommandStatus.UNSUPPORTED, result.status); assertNull(result.detail)
    }
    @Test fun `read errors are unknown and cancellation propagates`() = runTest {
        val source = Source(); source.failure = SecurityException("private path")
        val result = AppEvidenceHandler(InstallationEvidenceCollector(source)) { true }.handle(command())
        assertEquals(CommandStatus.DONE, result.status); assertTrue(result.detail!!.contains("unknown")); assertFalse(result.detail!!.contains("private"))
        source.failure = CancellationException("cancel")
        try { AppEvidenceHandler(InstallationEvidenceCollector(source)) { true }.handle(command()); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
}
