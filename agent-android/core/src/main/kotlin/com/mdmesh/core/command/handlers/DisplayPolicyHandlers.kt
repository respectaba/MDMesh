package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.policy.DisplayPolicyControl
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.Serializable

/** Strict typed payloads; no caller-selected Settings key, audio stream or permission bypass. */
class DisplayPolicyHandler(private val control: DisplayPolicyControl, private val key: String) : CommandHandler {
    init { require(key in setOf("brightness", "screenTimeout", "mediaVolume")) }
    override val type = "policy.$key"
    @Serializable private data class Brightness(val automatic: Boolean, val value: Int? = null)
    @Serializable private data class Timeout(val seconds: Int)
    @Serializable private data class Volume(val percent: Int)
    override suspend fun handle(command: CommandEnvelope): CommandResult {
        if (command.requiresCapability != type || key !in control.supportedKeys()) return CommandResults.unsupported(command)
        val raw = command.payload ?: return CommandResults.failed(command, "Settings required")
        val allowed = when (key) { "brightness" -> setOf("automatic", "value"); "screenTimeout" -> setOf("seconds"); else -> setOf("percent") }
        if (raw.keys.any { it !in allowed }) return CommandResults.failed(command, "Unsupported setting")
        val result = try {
            when (key) {
                "brightness" -> ProtocolJson.json.decodeFromJsonElement(Brightness.serializer(), raw).let { control.brightness(it.automatic, it.value) }
                "screenTimeout" -> ProtocolJson.json.decodeFromJsonElement(Timeout.serializer(), raw).let { control.timeout(it.seconds) }
                else -> ProtocolJson.json.decodeFromJsonElement(Volume.serializer(), raw).let { control.mediaVolume(it.percent) }
            }
        } catch (_: Exception) { return CommandResults.failed(command, "Invalid settings") }
        return when (result) {
            PolicyOutcome.Applied -> CommandResults.done(command, if (key == "mediaVolume") "Media stream volume confirmed at the nearest device step; other streams and mute restrictions unchanged" else "Requested Android setting confirmed; other lock/power policies still apply")
            PolicyOutcome.Unsupported -> CommandResults.unsupported(command)
            is PolicyOutcome.Failed -> CommandResults.failed(command, result.reason)
            is PolicyOutcome.Deferred -> CommandResults.failed(command, "Setting deferred; not applied")
        }
    }
}
