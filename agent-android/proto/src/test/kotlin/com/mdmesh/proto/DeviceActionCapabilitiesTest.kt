package com.mdmesh.proto
import org.junit.Assert.*
import org.junit.Test
class DeviceActionCapabilitiesTest {
    @Test fun `personal unmanaged devices do not inherit owner actions`() {
        val keys = DeviceAction.supportedKeys(false, false, 35, true)
        for (key in listOf("lock", "reboot", "wipe", "passcodeReset", "lockscreenMessage", "configApply")) assertFalse(key in keys)
        assertTrue("ring" in keys)
        assertTrue(DeviceAction.LOCATION_OFF_KEY in keys)
        assertTrue("configApply" in DeviceAction.supportedKeys(true, true, 35, true))
    }
    @Test fun `active classic admin can lock without becoming device owner`() {
        val keys = DeviceAction.supportedKeys(false, true, 35, true)
        assertTrue("lock" in keys); assertFalse("wipe" in keys); assertFalse("reboot" in keys)
    }
    @Test fun `reset needs both supported sdk and activated registered token`() {
        assertFalse("passcodeReset" in DeviceAction.supportedKeys(true, true, 24, true))
        assertFalse("passcodeReset" in DeviceAction.supportedKeys(true, true, 35, false))
        assertTrue("passcodeReset" in DeviceAction.supportedKeys(true, true, 35, true))
        assertFalse("wipe" in DeviceAction.supportedKeys(true, false, 35, true))
    }
}
