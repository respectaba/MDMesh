package com.mdmesh.proto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolJsonTest {

    private val json = ProtocolJson.json

    @Test
    fun `ignores unknown keys for forward compatibility`() {
        val payload = """
            {
              "protocolVersion": "1.0",
              "commandId": "abc",
              "issuedAt": "2026-01-01T00:00:00Z",
              "type": "device.lock",
              "futureField": { "added": "by a newer server" }
            }
        """.trimIndent()

        val command = json.decodeFromString(CommandEnvelope.serializer(), payload)

        assertEquals("device.lock", command.type)
        assertEquals("abc", command.commandId)
    }

    @Test
    fun `omits null and default fields when encoding`() {
        val command = CommandEnvelope(
            commandId = "id-1",
            issuedAt = "2026-01-01T00:00:00Z",
            type = "config.sync",
        )

        val encoded = json.encodeToString(CommandEnvelope.serializer(), command)

        // explicitNulls=false + encodeDefaults=false -> these are absent.
        assertTrue("ttlSeconds" !in encoded)
        assertTrue("requiresCapability" !in encoded)
        assertTrue("payload" !in encoded)
    }

    @Test
    fun `command status serializes with registry names`() {
        val result = CommandResult(
            commandId = "id-1",
            status = CommandStatus.UNSUPPORTED,
            completedAt = "2026-01-01T00:00:00Z",
        )

        val encoded = json.encodeToString(CommandResult.serializer(), result)

        assertTrue("\"status\":\"unsupported\"" in encoded)
    }

    @Test
    fun `payload survives round trip as json object`() {
        val command = CommandEnvelope(
            commandId = "id-1",
            issuedAt = "2026-01-01T00:00:00Z",
            type = "policy.apply",
            payload = buildJsonObject {
                put("policy", "wifi")
                put("value", false)
            },
        )

        val encoded = json.encodeToString(CommandEnvelope.serializer(), command)
        val decoded = json.decodeFromString(CommandEnvelope.serializer(), encoded)

        assertEquals(command, decoded)
        assertNull(decoded.ttlSeconds)
    }
    @Test
    fun `wire frames always advertise their protocol version while other defaults stay lean`() {
        val agent = AgentInfo("synthetic", "com.example.synthetic")
        val device = DeviceInfo("synthetic", 30, isDeviceOwner = false)
        val cap = Capabilities()
        val frames = listOf(
            json.encodeToString(AgentCheckInRequest.serializer(), AgentCheckInRequest(deviceId = "synthetic", capabilities = cap)),
            json.encodeToString(AgentCheckInResponse.serializer(), AgentCheckInResponse()),
            json.encodeToString(AgentEnrollRequest.serializer(), AgentEnrollRequest(enrollToken = "synthetic", agent = agent, device = device, capabilities = cap)),
            json.encodeToString(AgentEnrollResponse.serializer(), AgentEnrollResponse(deviceId = "synthetic")),
            json.encodeToString(CapabilityMatrix.serializer(), CapabilityMatrix(agent = agent, device = device, capabilities = cap)),
            json.encodeToString(CommandEnvelope.serializer(), CommandEnvelope(commandId = "synthetic", issuedAt = "2026-10-21T00:00:00Z", type = "apps.evidence")),
            json.encodeToString(CommandResult.serializer(), CommandResult(commandId = "synthetic", status = CommandStatus.DONE, completedAt = "2026-10-21T00:00:00Z")),
        )
        frames.forEach { assertEquals("1.6", json.parseToJsonElement(it).jsonObject["protocolVersion"]!!.jsonPrimitive.content) }
        assertTrue("results" !in frames.first())
    }

}
