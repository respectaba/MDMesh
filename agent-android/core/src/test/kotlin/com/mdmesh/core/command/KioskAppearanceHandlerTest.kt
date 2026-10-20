package com.mdmesh.core.command

import android.content.ComponentName
import com.mdmesh.core.command.handlers.KioskAppearanceHandler
import com.mdmesh.core.kiosk.KioskApplier
import com.mdmesh.core.kiosk.KioskHomeSwitch
import com.mdmesh.core.store.InMemoryKioskStateStore
import com.mdmesh.kiosk.StubKioskController
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandStatus
import com.mdmesh.proto.KioskApplyPayload
import com.mdmesh.proto.KioskThemeDto
import com.mdmesh.proto.ProtocolJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class KioskAppearanceHandlerTest {
    private val original = KioskApplyPayload(allowedPackages = listOf("com.synthetic.app"), password = "synthetic-exit-secret", theme = KioskThemeDto("#112233", "#ffffff", "LARGE", "Original"))
    private object NoHome : KioskHomeSwitch {
        override fun setClaimEnabled(enabled: Boolean) { error("Appearance must not claim HOME") }
        override fun showLauncher() { error("Appearance must not launch a new task") }
        override fun showOemHome() { error("Appearance must not exit kiosk") }
    }
    private fun handler(store: InMemoryKioskStateStore, authorized: () -> Boolean = { true }) = KioskAppearanceHandler(KioskApplier(StubKioskController(), store, NoHome, ComponentName("a", "b")) { true }, authorized)
    private fun command(json: String) = CommandEnvelope(commandId = "synthetic-appearance", issuedAt = "2026-10-20T00:00:00Z", type = "kiosk.appearance", requiresCapability = "device.kioskAppearance", payload = ProtocolJson.json.parseToJsonElement(json).jsonObject)
    @Test fun `wire appearance cannot smuggle app access passwords or features`() = runTest {
        for (raw in listOf("""{"headerText":"Safe","allowedPackages":["com.other.app"]}""", """{"password":"new-secret"}""", """{"features":{"lockVolume":false}}""", """{"headerText":123}""", "{}")) {
            val store = InMemoryKioskStateStore(original)
            assertEquals(CommandStatus.FAILED, handler(store).handle(command(raw)).status); assertEquals(original, store.load())
        }
    }
    @Test fun `header hide reset and omitted appearance fields remain distinct across serialization`() = runTest {
        val store = InMemoryKioskStateStore(original); val h = handler(store)
        assertEquals(CommandStatus.DONE, h.handle(command("""{"headerText":""}""")).status)
        assertEquals(original.copy(theme = original.theme.copy(headerText = "")), store.load())
        assertEquals(CommandStatus.DONE, h.handle(command("""{"headerText":null}""")).status)
        val current = store.load()!!; assertNull(current.theme.headerText); assertEquals("LARGE", current.theme.iconSize)
        assertEquals(current, ProtocolJson.json.decodeFromString(KioskApplyPayload.serializer(), ProtocolJson.json.encodeToString(KioskApplyPayload.serializer(), current)))
    }
    @Test fun `capability mismatch and authority loss never save a change`() = runTest {
        val store = InMemoryKioskStateStore(original)
        assertEquals(CommandStatus.UNSUPPORTED, handler(store).handle(command("""{"iconSize":"SMALL"}""").copy(requiresCapability = null)).status)
        assertEquals(CommandStatus.UNSUPPORTED, handler(store) { false }.handle(command("""{"iconSize":"SMALL"}""")).status)
        assertEquals(original, store.load())
    }
}
