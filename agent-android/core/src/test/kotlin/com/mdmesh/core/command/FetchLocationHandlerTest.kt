package com.mdmesh.core.command

import com.mdmesh.core.command.handlers.FetchLocationHandler
import com.mdmesh.core.location.FreshLocationSource
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import com.mdmesh.proto.LocationDto
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class FetchLocationHandlerTest {
    private val time = 1792530000000L
    private fun fix() = LocationDto(0.0, 0.0, 12f, "synthetic", time - 700)
    private inner class Source : FreshLocationSource {
        var ready = true; var captures = 0; var offDuringCapture = false; var result: LocationDto? = fix(); var failure: Exception? = null
        override fun supported() = ready
        override suspend fun capture(): LocationDto? {
            captures++; failure?.let { throw it }; if (offDuringCapture) ready = false; return result
        }
    }
    private fun command() = CommandEnvelope(commandId = "synthetic-location", issuedAt = "2026-10-20T00:00:00Z", type = "device.fetchLocation", requiresCapability = "device.fetchLocation")
    @Test fun `fresh result preserves its actual time and equator coordinates`() = runTest {
        val s = Source(); val result = FetchLocationHandler(s) { time }.handle(command())
        assertEquals(CommandStatus.DONE, result.status)
        assertEquals(fix(), ProtocolJson.json.decodeFromString(LocationDto.serializer(), result.detail!!)); assertEquals(1, s.captures)
    }
    @Test fun `off or ungranted capability prevents capture and cannot be overridden in payload`() = runTest {
        val s = Source(); val h = FetchLocationHandler(s) { time }
        assertEquals(CommandStatus.UNSUPPORTED, h.handle(command().copy(requiresCapability = null)).status)
        assertEquals(CommandStatus.FAILED, h.handle(command().copy(payload = buildJsonObject { put("ignorePrivacy", true) })).status)
        s.ready = false; assertEquals(CommandStatus.UNSUPPORTED, h.handle(command()).status); assertEquals(0, s.captures)
    }
    @Test fun `privacy or authority loss while waiting discards coordinates`() = runTest {
        val s = Source(); s.offDuringCapture = true
        val result = FetchLocationHandler(s) { time }.handle(command())
        assertEquals(CommandStatus.UNSUPPORTED, result.status); assertNull(result.detail)
    }
    @Test fun `cached missing malformed and future fixes never become fresh success`() = runTest {
        for (invalid in listOf(null, fix().copy(capturedAt = time - 30001), fix().copy(capturedAt = time + 1), fix().copy(capturedAt = 0), fix().copy(lat = Double.NaN), fix().copy(lon = 181.0), fix().copy(accuracyM = -1f), fix().copy(accuracyM = Float.POSITIVE_INFINITY))) {
            val s = Source(); s.result = invalid
            val result = FetchLocationHandler(s) { time }.handle(command())
            assertEquals(CommandStatus.FAILED, result.status); assertFalse(result.detail!!.contains("synthetic"))
        }
    }
    @Test fun `provider errors are redacted and coroutine cancellation propagates`() = runTest {
        val s = Source(); s.failure = SecurityException("private permission details")
        val result = FetchLocationHandler(s) { time }.handle(command())
        assertEquals(CommandStatus.FAILED, result.status); assertFalse(result.detail!!.contains("private"))
        s.failure = CancellationException("cancel synthetic")
        try { FetchLocationHandler(s) { time }.handle(command()); fail("cancellation swallowed") } catch (_: CancellationException) { }
    }
}
