package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.core.location.FreshLocationSource
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.LocationDto
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.CancellationException

/** One fresh fix. Does not change collection mode, grant permissions, or substitute last-known data. */
class FetchLocationHandler(private val source: FreshLocationSource, private val now: () -> Long = System::currentTimeMillis) : CommandHandler {
    override val type = "device.fetchLocation"
    override suspend fun handle(command: CommandEnvelope): CommandResult {
        if (command.requiresCapability != type) return CommandResults.unsupported(command)
        if (command.payload?.isNotEmpty() == true) return CommandResults.failed(command, "Fresh location accepts no settings")
        return try {
            if (!source.supported()) return CommandResults.unsupported(command)
            val fix = source.capture()
            // Turning collection off or losing authority while awaiting a fix discards it.
            if (!source.supported()) return CommandResults.unsupported(command)
            if (fix == null || !valid(fix, now())) return CommandResults.failed(command, "No valid fresh location fix was available; no cached location substituted")
            CommandResults.done(command, ProtocolJson.json.encodeToString(LocationDto.serializer(), fix))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CommandResults.failed(command, "Fresh location could not be collected")
        }
    }
    private fun valid(fix: LocationDto, time: Long): Boolean =
        fix.lat.isFinite() && fix.lat in -90.0..90.0 && fix.lon.isFinite() && fix.lon in -180.0..180.0 &&
        (fix.accuracyM?.let { it.isFinite() && it >= 0 } ?: true) &&
        fix.capturedAt > 0 && fix.capturedAt <= time && time - fix.capturedAt <= 30000
}
