package com.mdmesh.core.command

import com.mdmesh.core.command.handlers.PasswordPolicyHandler
import com.mdmesh.core.command.handlers.SystemUpdatePolicyHandler
import com.mdmesh.policy.ManagedPolicyControl
import com.mdmesh.policy.PolicyOutcome
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ManagedPolicyHandlersTest {
    private class Control : ManagedPolicyControl {
        var keys = setOf("systemUpdatePolicy", "passwordRequirements")
        var result: PolicyOutcome = PolicyOutcome.Applied
        val updates = mutableListOf<Triple<Int, Int?, Int?>>()
        val passwords = mutableListOf<Pair<String, Int>>()
        override fun supportedKeys() = keys
        override fun systemUpdate(mode: Int, startMinutes: Int?, endMinutes: Int?): PolicyOutcome {
            updates.add(Triple(mode, startMinutes, endMinutes)); return result
        }
        override fun password(quality: String, minimumLength: Int): PolicyOutcome {
            passwords.add(quality to minimumLength); return result
        }
    }
    private fun command(type: String, json: String) = CommandEnvelope(commandId = "synthetic", issuedAt = "2026-01-01T00:00:00Z", type = type,
        requiresCapability = type, payload = ProtocolJson.json.parseToJsonElement(json).jsonObject)

    @Test fun `overnight update window uses exact device local minutes`() = runTest {
        val c = Control(); val result = SystemUpdatePolicyHandler(c).handle(command("policy.systemUpdatePolicy", """{"mode":2,"from":"23:00","to":"02:15"}"""))
        assertEquals(CommandStatus.DONE, result.status); assertEquals(listOf(Triple(2, 1380, 135)), c.updates)
    }
    @Test fun `invalid update windows never reach the controller`() = runTest {
        for (json in listOf("""{"mode":2,"from":"24:00","to":"02:00"}""", """{"mode":2,"from":"02:00","to":"02:00"}""", """{"mode":2}""", """{"mode":9}""", """{"mode":1,"from":"02:00"}""", """{"mode":1,"wipe":true}""")) {
            val c = Control(); assertEquals(CommandStatus.FAILED, SystemUpdatePolicyHandler(c).handle(command("policy.systemUpdatePolicy", json)).status); assertTrue(c.updates.isEmpty())
        }
    }
    @Test fun `default automatic and postponement modes remain distinct`() = runTest {
        val c = Control(); for (mode in listOf(0, 1, 3)) assertEquals(CommandStatus.DONE, SystemUpdatePolicyHandler(c).handle(command("policy.systemUpdatePolicy", "{\"mode\":$mode}")).status)
        assertEquals(listOf(0, 1, 3), c.updates.map { it.first })
    }
    @Test fun `password requirements never reset a credential or accept automatic wipe`() = runTest {
        val c = Control(); val handler = PasswordPolicyHandler(c)
        assertEquals(CommandStatus.DONE, handler.handle(command("policy.passwordRequirements", """{"quality":"NUMERIC_COMPLEX","minimumLength":6}""")).status)
        assertEquals(listOf("NUMERIC_COMPLEX" to 6), c.passwords)
        for (json in listOf("""{"quality":"NUMERIC","wipeAfterAttempts":5}""", """{"quality":"NUMERIC","newPassword":"secret"}""", """{"quality":"NONE","minimumLength":6}""", """{"quality":"UNKNOWN"}""", """{"quality":"COMPLEX","minimumLength":1000}""")) assertEquals(CommandStatus.FAILED, handler.handle(command("policy.passwordRequirements", json)).status)
        assertEquals(1, c.passwords.size)
    }
    @Test fun `loss of ownership capability blocks immediately and wrong wire capability refuses`() = runTest {
        val c = Control(); val handler = PasswordPolicyHandler(c); val cmd = command("policy.passwordRequirements", """{"quality":"ALPHANUMERIC","minimumLength":8}""")
        assertEquals(CommandStatus.UNSUPPORTED, handler.handle(cmd.copy(requiresCapability = null)).status)
        c.keys = emptySet(); assertEquals(CommandStatus.UNSUPPORTED, handler.handle(cmd).status); assertTrue(c.passwords.isEmpty())
    }
    @Test fun `platform refusals never become applied`() = runTest {
        for ((outcome, status) in listOf(PolicyOutcome.Unsupported to CommandStatus.UNSUPPORTED, PolicyOutcome.Failed("synthetic refusal") to CommandStatus.FAILED, PolicyOutcome.Deferred("synthetic hold") to CommandStatus.FAILED)) {
            val c = Control(); c.result = outcome
            assertEquals(status, PasswordPolicyHandler(c).handle(command("policy.passwordRequirements", """{"quality":"NONE"}""")).status)
        }
    }
}
