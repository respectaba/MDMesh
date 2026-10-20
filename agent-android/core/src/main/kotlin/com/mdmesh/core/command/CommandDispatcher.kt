package com.mdmesh.core.command

import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import java.security.MessageDigest
import java.time.Instant

/** Process-local deduplication. A duplicate awaits/replays the actual outcome;
 * it never changes a failure, unsupported action or pending operation to DONE.
 * The provider's durable delivered/result protocol still owns restart recovery.
 */
class CommandDispatcher(
    handlers: List<CommandHandler>,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val maxCachedCommands: Int = 10_000,
) {
    private data class Entry(val fingerprint: String, val result: CompletableDeferred<CommandResult>)
    private val handlersByType = handlers.associateBy { it.type }
    private val outcomes = HashMap<String, Entry>()
    private var cachedDetailBytes = 0

    init { require(maxCachedCommands in 1..10_000) }

    private fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map { canonical(it) })
        else -> value
    }

    suspend fun dispatch(command: CommandEnvelope): CommandResult {
        // Keep only a digest, not a second long-lived copy of a passcode payload.
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(
            canonical(ProtocolJson.json.encodeToJsonElement(CommandEnvelope.serializer(), command)).toString().toByteArray(),
        ).joinToString("") { "%02x".format(it) }
        val (entry, execute) = synchronized(outcomes) {
            val prior = outcomes[command.commandId]
            if (prior != null) {
                if (prior.fingerprint != fingerprint) {
                    return CommandResults.failed(command, "command id reused with different content; no action taken")
                }
                Pair(prior, false)
            } else {
                val ttl = command.ttlSeconds
                if (ttl != null && ttl < 0) return CommandResults.failed(command, "invalid command ttl")
                if (ttl != null && ttl > 0) {
                    val issued = runCatching { Instant.parse(command.issuedAt).epochSecond }.getOrNull()
                        ?: return CommandResults.failed(command, "invalid command timestamp")
                    if (issued > nowEpochSeconds() + 300) return CommandResults.failed(command, "command timestamp is in the future")
                    if (nowEpochSeconds() - issued > ttl) return CommandResults.expired(command, "command past ttl")
                }
                // Do not evict an id and accidentally execute it again. Memory
                // exhaustion refuses new work, while known ids remain replayable.
                if (outcomes.size >= maxCachedCommands) {
                    return CommandResults.failed(command, "command replay cache full; no action taken")
                }
                Entry(fingerprint, CompletableDeferred()).also { outcomes[command.commandId] = it } to true
            }
        }
        if (!execute) return entry.result.await()

        val result = try {
            val handler = handlersByType[command.type]
            if (handler == null) CommandResults.unsupported(command, "unknown command type: ${command.type}")
            else handler.handle(command).let { outcome ->
                if (outcome.commandId == command.commandId) outcome
                else CommandResults.failed(command, "handler returned a different command identity")
            }
        } catch (cancelled: CancellationException) {
            // A cancelled handler may already have acted: cache a refusal to
            // replay, then preserve coroutine cancellation for the caller.
            entry.result.complete(CommandResults.failed(command, "command interrupted; action will not be repeated"))
            throw cancelled
        } catch (error: Throwable) {
            CommandResults.failed(command, error.message ?: "handler threw")
        }
        val cached = synchronized(outcomes) {
            val bytes = result.detail?.toByteArray()?.size ?: 0
            if (bytes > 8192 || cachedDetailBytes + bytes > 4 * 1024 * 1024) {
                // Large app/icon data must not fill the agent heap. Preserve
                // actual status/time and explicitly report missing replay data.
                result.copy(detail = "Original result data unavailable for replay; action was not repeated")
            } else { cachedDetailBytes += bytes; result }
        }
        entry.result.complete(cached)
        return result
    }
}
