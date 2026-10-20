package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.core.config.ConfigApplier
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ConfigApplyPayload
import com.mdmesh.proto.ConfigApplyResult
import com.mdmesh.proto.DeviceAction
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.json.JsonObject

/**
 * `config.apply` — desired-state push. Decodes the document, delegates to [ConfigApplier], and reports the
 * per-key outcomes as JSON in `detail`. `done` = every key applied, unsupported or deferred (protocol 1.4: not
 * applied now on purpose, with the reason; revision now persisted and reported in check-ins); `failed` = at least
 * one key failed (server retries after a backoff).
 */
class ConfigApplyHandler(private val applier: ConfigApplier) : CommandHandler {
    override val type: String = DeviceAction.CONFIG_APPLY

    override suspend fun handle(command: CommandEnvelope): CommandResult {
        val payload = command.payload ?: return CommandResults.failed(command, "config.apply requires a payload")
        if (payload["managedDisplay"] != null) {
            if (command.requiresCapability != "device.configDisplay") return CommandResults.unsupported(command)
            val display = payload["managedDisplay"] as? JsonObject ?: return CommandResults.failed(command, "Invalid display settings")
            if (display.keys.any { it !in setOf("brightness", "screenTimeoutSeconds", "mediaVolumePercent") }) return CommandResults.failed(command, "Unsupported display setting")
            display["brightness"]?.let {
                val brightness = it as? JsonObject ?: return CommandResults.failed(command, "Invalid brightness settings")
                if (brightness.keys.any { key -> key !in setOf("automatic", "value") }) return CommandResults.failed(command, "Unsupported brightness setting")
            }
        }
        val doc = runCatching { ProtocolJson.json.decodeFromJsonElement(ConfigApplyPayload.serializer(), payload) }
            .getOrElse { return CommandResults.failed(command, "Invalid configuration payload") }
        if (doc.revision.isBlank()) return CommandResults.failed(command, "config.apply requires a revision")
        val result = applier.apply(doc)
        val detail = ProtocolJson.json.encodeToString(ConfigApplyResult.serializer(), result)
        return if (ConfigApplier.succeeded(result)) CommandResults.done(command, detail) else CommandResults.failed(command, detail)
    }
}
