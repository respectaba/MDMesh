package com.mdmesh.proto

import org.junit.Assert.*
import org.junit.Test

class SecurityObservationTest {
    @Test fun `fresh security source time travels with ownership in the same report`() {
        val at = "2026-10-20T12:00:00.123456789Z"
        val posture = SecurityPosture(true, true, false, false, false, 10, true, at)
        val encoded = ProtocolJson.json.encodeToString(SecurityPosture.serializer(), posture)
        val decoded = ProtocolJson.json.decodeFromString(SecurityPosture.serializer(), encoded)
        assertEquals(at, decoded.observedAt)
        assertTrue(decoded.isDeviceOwner)
    }
    @Test fun `self identity travels in the ownership snapshot without file paths`() {
        val identity = AppInstallationEvidence("com.mdmesh.agent", "observed", versionCode = 309,
            apkSha256 = "a".repeat(64), signerSha256 = "b".repeat(64), observedAt = "2026-10-21T00:00:00Z")
        val posture = SecurityPosture(true, true, false, false, false, 10, true,
            "2026-10-21T00:00:00Z", identity)
        val raw = ProtocolJson.json.encodeToString(SecurityPosture.serializer(), posture)
        assertEquals(identity, ProtocolJson.json.decodeFromString(SecurityPosture.serializer(), raw).agentInstallation)
        assertFalse(raw.contains("sourceDir"))
        assertFalse(raw.contains("basePath"))
    }
    @Test fun `older ownership reports remain explicitly undated`() {
        val encoded = """{"storageEncrypted":true,"deviceSecure":true,"adbEnabled":false,"devOptionsEnabled":false,"unknownSourcesAllowed":false,"patchAgeDays":10,"isDeviceOwner":true}"""
        val legacy = ProtocolJson.json.decodeFromString(SecurityPosture.serializer(), encoded)
        assertNull(legacy.observedAt)
        assertNull(legacy.agentInstallation)
    }
}
