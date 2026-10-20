package com.mdmesh.core.command.handlers

import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.policy.ManagedPolicyControl
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.Serializable

private fun outcome(command: CommandEnvelope, result: PolicyOutcome, detail: String): CommandResult = when (result) {
    PolicyOutcome.Applied -> CommandResults.done(command, detail)
    PolicyOutcome.Unsupported -> CommandResults.unsupported(command)
    is PolicyOutcome.Failed -> CommandResults.failed(command, result.reason)
    is PolicyOutcome.Deferred -> CommandResults.failed(command, "Policy deferred; not applied: " + result.reason)
}

class SystemUpdatePolicyHandler(private val control: ManagedPolicyControl) : CommandHandler {
    override val type = "policy.systemUpdatePolicy"
    @Serializable private data class Payload(val mode: Int, val from: String? = null, val to: String? = null)
    private fun minute(value: String?): Int? {
        if (value == null || !Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$").matches(value)) return null
        return value.take(2).toInt() * 60 + value.takeLast(2).toInt()
    }
    override suspend fun handle(command: CommandEnvelope): CommandResult {
        if (command.requiresCapability != type || "systemUpdatePolicy" !in control.supportedKeys()) return CommandResults.unsupported(command)
        val raw = command.payload ?: return CommandResults.failed(command, "System update settings required")
        if (raw.keys.any { it !in setOf("mode", "from", "to") }) return CommandResults.failed(command, "Unsupported system update setting")
        val p = runCatching { ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), raw) }.getOrNull()
            ?: return CommandResults.failed(command, "Invalid system update settings")
        val start = minute(p.from); val end = minute(p.to)
        if (p.mode !in 0..3 || (p.mode == 2 && (start == null || end == null || start == end)) ||
            (p.mode != 2 && (p.from != null || p.to != null))) return CommandResults.failed(command, "Invalid system update window")
        return outcome(command, control.systemUpdate(p.mode, start, end), "Android update policy configured in device-local time; OEM availability and Android postponement limits still apply")
    }
}

class PasswordPolicyHandler(private val control: ManagedPolicyControl) : CommandHandler {
    override val type = "policy.passwordRequirements"
    @Serializable private data class Payload(val quality: String, val minimumLength: Int = 0)
    override suspend fun handle(command: CommandEnvelope): CommandResult {
        if (command.requiresCapability != type || "passwordRequirements" !in control.supportedKeys()) return CommandResults.unsupported(command)
        val raw = command.payload ?: return CommandResults.failed(command, "Password requirements required")
        // In particular, never accept a failed-attempt wipe threshold or a new passcode here.
        if (raw.keys.any { it !in setOf("quality", "minimumLength") }) return CommandResults.failed(command, "Unsupported password setting")
        val p = runCatching { ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), raw) }.getOrNull()
            ?: return CommandResults.failed(command, "Invalid password requirements")
        if (p.quality !in setOf("NONE", "NUMERIC", "NUMERIC_COMPLEX", "ALPHANUMERIC", "COMPLEX") ||
            p.minimumLength !in 0..16 || (p.quality == "NONE" && p.minimumLength != 0)) return CommandResults.failed(command, "Invalid password requirements")
        return outcome(command, control.password(p.quality, p.minimumLength), "Password requirements configured; the current credential may still require an on-device change")
    }
}
