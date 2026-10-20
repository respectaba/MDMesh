package com.mdmesh.core.command

import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.CommandStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommandDispatcherTest {

    private fun envelope(
        type: String,
        ttlSeconds: Int? = null,
        issuedAt: String = "2026-01-01T00:00:00Z",
    ) = CommandEnvelope(
        commandId = "cmd-1",
        issuedAt = issuedAt,
        ttlSeconds = ttlSeconds,
        type = type,
    )

    private class EchoHandler(override val type: String) : CommandHandler {
        override suspend fun handle(command: CommandEnvelope): CommandResult =
            CommandResults.done(command)
    }

    @Test
    fun `unknown command type returns unsupported`() = runTest {
        val dispatcher = CommandDispatcher(handlers = emptyList())

        val result = dispatcher.dispatch(envelope("totally.unknown"))

        assertEquals(CommandStatus.UNSUPPORTED, result.status)
        assertEquals("cmd-1", result.commandId)
    }

    @Test
    fun `known command type is routed to its handler`() = runTest {
        val dispatcher = CommandDispatcher(handlers = listOf(EchoHandler("config.sync")))

        val result = dispatcher.dispatch(envelope("config.sync"))

        assertEquals(CommandStatus.DONE, result.status)
    }

    @Test
    fun `expired command returns expired`() = runTest {
        // issued at epoch 0, ttl 60s, but "now" is far in the future.
        val dispatcher = CommandDispatcher(
            handlers = listOf(EchoHandler("config.sync")),
            nowEpochSeconds = { 10_000_000_000L },
        )

        val result = dispatcher.dispatch(
            envelope("config.sync", ttlSeconds = 60, issuedAt = "1970-01-01T00:00:00Z"),
        )

        assertEquals(CommandStatus.EXPIRED, result.status)
    }

    @Test
    fun `handler exception becomes failed`() = runTest {
        val throwing = object : CommandHandler {
            override val type = "boom"
            override suspend fun handle(command: CommandEnvelope): CommandResult =
                throw IllegalStateException("kaboom")
        }
        val dispatcher = CommandDispatcher(handlers = listOf(throwing))

        val result = dispatcher.dispatch(envelope("boom"))

        assertEquals(CommandStatus.FAILED, result.status)
        assertEquals("kaboom", result.detail)
    }

    @Test
    fun `duplicate failure keeps the original failed outcome`() = runTest {
        var executions = 0
        val handler = object : CommandHandler {
            override val type = "failing"
            override suspend fun handle(command: CommandEnvelope): CommandResult {
                executions++
                return CommandResults.failed(command, "synthetic refusal")
            }
        }
        val dispatcher = CommandDispatcher(listOf(handler))
        val first = dispatcher.dispatch(envelope("failing"))
        assertEquals(first, dispatcher.dispatch(envelope("failing")))
        assertEquals(CommandStatus.FAILED, first.status)
        assertEquals(1, executions)
    }

    @Test
    fun `duplicate unsupported stays unsupported`() = runTest {
        val dispatcher = CommandDispatcher(emptyList())
        assertEquals(dispatcher.dispatch(envelope("unknown")), dispatcher.dispatch(envelope("unknown")))
    }

    @Test
    fun `concurrent duplicate waits for the real outcome`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var executions = 0
        val handler = object : CommandHandler {
            override val type = "waiting"
            override suspend fun handle(command: CommandEnvelope): CommandResult {
                executions++
                gate.await()
                return CommandResults.failed(command, "actual result")
            }
        }
        val dispatcher = CommandDispatcher(listOf(handler))
        val first = async { dispatcher.dispatch(envelope("waiting")) }
        runCurrent()
        val second = async { dispatcher.dispatch(envelope("waiting")) }
        runCurrent()
        val premature = second.isCompleted
        gate.complete(Unit)
        assertEquals(first.await(), second.await())
        org.junit.Assert.assertFalse(premature)
        assertEquals(1, executions)
    }

    @Test
    fun `reused id cannot change command content`() = runTest {
        val dispatcher = CommandDispatcher(listOf(EchoHandler("config.sync")))
        assertEquals(CommandStatus.DONE, dispatcher.dispatch(envelope("config.sync")).status)
        assertEquals(CommandStatus.FAILED, dispatcher.dispatch(envelope("device.wipe")).status)
    }

    @Test
    fun `malformed expiring timestamp never reaches a handler`() = runTest {
        val dispatcher = CommandDispatcher(listOf(EchoHandler("config.sync")))
        assertEquals(CommandStatus.FAILED, dispatcher.dispatch(envelope("config.sync", ttlSeconds = 60, issuedAt = "invalid")).status)
    }

    @Test
    fun `full replay cache refuses new ids while retaining real outcomes`() = runTest {
        val dispatcher = CommandDispatcher(listOf(EchoHandler("config.sync")), maxCachedCommands = 1)
        val first = dispatcher.dispatch(envelope("config.sync"))
        assertEquals(CommandStatus.FAILED, dispatcher.dispatch(envelope("config.sync").copy(commandId = "other")).status)
        assertEquals(first, dispatcher.dispatch(envelope("config.sync")))
    }

    @Test
    fun `reordered payload keys still replay the real result`() = runTest {
        val dispatcher = CommandDispatcher(listOf(EchoHandler("config.sync")))
        val a = envelope("config.sync").copy(payload = kotlinx.serialization.json.JsonObject(linkedMapOf("a" to kotlinx.serialization.json.JsonPrimitive(1), "b" to kotlinx.serialization.json.JsonPrimitive(2))))
        val b = a.copy(payload = kotlinx.serialization.json.JsonObject(linkedMapOf("b" to kotlinx.serialization.json.JsonPrimitive(2), "a" to kotlinx.serialization.json.JsonPrimitive(1))))
        assertEquals(dispatcher.dispatch(a), dispatcher.dispatch(b))
    }

    @Test
    fun `large result keeps status without retaining unbounded replay data`() = runTest {
        val handler = object : CommandHandler {
            override val type = "large"
            override suspend fun handle(command: CommandEnvelope) = CommandResults.done(command, "x".repeat(9000))
        }
        val dispatcher = CommandDispatcher(listOf(handler))
        val first = dispatcher.dispatch(envelope("large"))
        val duplicate = dispatcher.dispatch(envelope("large"))
        assertEquals(first.status, duplicate.status)
        assertEquals(first.completedAt, duplicate.completedAt)
        org.junit.Assert.assertTrue(duplicate.detail!!.contains("unavailable for replay"))
    }

    @Test
    fun `cancelled execution cannot be repeated or leave duplicates waiting forever`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var executions = 0
        val handler = object : CommandHandler {
            override val type = "cancelled"
            override suspend fun handle(command: CommandEnvelope): CommandResult {
                executions++
                gate.await()
                return CommandResults.done(command)
            }
        }
        val dispatcher = CommandDispatcher(listOf(handler))
        val first = async { dispatcher.dispatch(envelope("cancelled")) }
        runCurrent()
        first.cancelAndJoin()
        val duplicate = kotlinx.coroutines.withTimeout(1000) { dispatcher.dispatch(envelope("cancelled")) }
        assertEquals(CommandStatus.FAILED, duplicate.status)
        assertEquals(1, executions)
    }
}
