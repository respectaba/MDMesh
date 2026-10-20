package com.mdmesh.core.command

import com.mdmesh.core.command.handlers.DisplayPolicyHandler
import com.mdmesh.policy.DisplayDevice
import com.mdmesh.policy.DisplayPolicyControl
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class DisplayPolicyHandlersTest {
    private class Device : DisplayDevice {
        var owner = true; var calls = 0; var light = 0; var auto = false; var timeout = 0; var volume = 0
        override val sdkInt = 35
        override fun isDeviceOwner() = owner
        override fun setBrightnessMode(automatic: Boolean) { calls++; auto = automatic }
        override fun brightnessMode() = auto
        override fun setBrightness(value: Int) { calls++; light = value }
        override fun brightness() = light
        override fun maximumLockMillis() = 0L
        override fun setTimeoutMillis(value: Int) { calls++; timeout = value }
        override fun timeoutMillis() = timeout
        override fun mediaVolumeFixed() = false
        override fun mediaVolumeMin() = 0
        override fun mediaVolumeMax() = 10
        override fun setMediaVolume(index: Int) { calls++; volume = index }
        override fun mediaVolume() = volume
    }
    private fun cmd(key: String, payload: String) = CommandEnvelope(commandId = "synthetic-display", type = "policy.$key", requiresCapability = "policy.$key", issuedAt = "2026-10-20T00:00:00Z", payload = ProtocolJson.json.parseToJsonElement(payload).jsonObject)
    @Test fun `strict payloads reject arbitrary settings streams wipe and malformed numbers before mutation`() = runTest {
        val d = Device(); val control = DisplayPolicyControl(d)
        for ((key, json) in listOf("brightness" to """{"automatic":false,"value":120,"setting":"adb_enabled"}""", "screenTimeout" to """{"seconds":1.5}""", "screenTimeout" to """{"seconds":2147483648}""", "mediaVolume" to """{"percent":50,"stream":"alarm"}""", "mediaVolume" to """{"percent":20,"wipe":true}""", "brightness" to "{}")) {
            assertEquals(CommandStatus.FAILED, DisplayPolicyHandler(control, key).handle(cmd(key, json)).status)
        }
        assertEquals(0, d.calls)
    }
    @Test fun `requires exact capability and current ownership`() = runTest {
        val d = Device(); val h = DisplayPolicyHandler(DisplayPolicyControl(d), "screenTimeout"); val c = cmd("screenTimeout", """{"seconds":30}""")
        assertEquals(CommandStatus.UNSUPPORTED, h.handle(c.copy(requiresCapability = "policy.brightness")).status)
        d.owner = false; assertEquals(CommandStatus.UNSUPPORTED, h.handle(c).status); assertEquals(0, d.calls)
    }
    @Test fun `confirmed media change reports its real limited scope`() = runTest {
        val d = Device(); val h = DisplayPolicyHandler(DisplayPolicyControl(d), "mediaVolume")
        val result = h.handle(cmd("mediaVolume", """{"percent":50}"""))
        assertEquals(CommandStatus.DONE, result.status); assertEquals(5, d.volume)
    }
}
