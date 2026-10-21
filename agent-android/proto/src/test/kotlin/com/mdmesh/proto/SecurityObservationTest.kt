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
    @Test fun `older ownership reports remain explicitly undated`() {
        val encoded = """{"storageEncrypted":true,"deviceSecure":true,"adbEnabled":false,"devOptionsEnabled":false,"unknownSourcesAllowed":false,"patchAgeDays":10,"isDeviceOwner":true}"""
        assertNull(ProtocolJson.json.decodeFromString(SecurityPosture.serializer(), encoded).observedAt)
    }
}
