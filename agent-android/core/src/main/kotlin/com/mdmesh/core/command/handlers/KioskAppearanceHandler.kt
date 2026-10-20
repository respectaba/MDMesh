package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.core.kiosk.KioskAppearancePatch
import com.mdmesh.core.kiosk.KioskApplier
import com.mdmesh.kiosk.KioskResult
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.KioskThemeDto
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.CancellationException

class KioskAppearanceHandler(private val applier: KioskApplier, private val authorized: () -> Boolean) : CommandHandler {
    override val type = "kiosk.appearance"
    override suspend fun handle(command: CommandEnvelope): CommandResult {
        if (command.requiresCapability != "device.kioskAppearance") return CommandResults.unsupported(command)
        val raw = command.payload ?: return CommandResults.failed(command, "Launcher appearance required")
        if (raw.keys.any { it !in setOf("backgroundColor", "textColor", "iconSize", "headerText") }) return CommandResults.failed(command, "Unsupported launcher setting")
        val theme = runCatching { ProtocolJson.json.decodeFromJsonElement(KioskThemeDto.serializer(), raw) }.getOrNull()
            ?: return CommandResults.failed(command, "Invalid launcher appearance")
        val patch = KioskAppearancePatch(raw.keys, theme)
        if (!patch.valid()) return CommandResults.failed(command, "Invalid launcher appearance")
        return try {
            when (val result = applier.updateAppearance(patch, authorized)) {
                KioskResult.Ok -> CommandResults.done(command, "Appearance saved for the active launcher; lock-task and application access settings unchanged")
                KioskResult.Unsupported -> CommandResults.unsupported(command, "An active managed launcher is required")
                is KioskResult.Failed -> CommandResults.failed(command, result.reason)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { CommandResults.failed(command, "Launcher appearance could not be saved") }
    }
}
