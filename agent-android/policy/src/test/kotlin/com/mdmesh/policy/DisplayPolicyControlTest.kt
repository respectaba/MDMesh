package com.mdmesh.policy

import org.junit.Assert.*
import org.junit.Test

class DisplayPolicyControlTest {
    private class Device : DisplayDevice {
        var owner = true
        override var sdkInt = 35
        var auto = true; var light = 90; var timeout = 30000; var volume = 5
        var maxLock = 0L; var fixed = false; var accepts = true; var throws = false
        var checks = 0; var loseOnSecondCheck = false; var mutations = 0
        override fun isDeviceOwner(): Boolean { checks++; return owner && !(loseOnSecondCheck && checks > 1) }
        private fun change(action: () -> Unit) { mutations++; if (throws) throw SecurityException("private OEM detail"); if (accepts) action() }
        override fun setBrightnessMode(automatic: Boolean) = change { auto = automatic }
        override fun brightnessMode() = auto
        override fun setBrightness(value: Int) = change { light = value }
        override fun brightness() = light
        override fun maximumLockMillis() = maxLock
        override fun setTimeoutMillis(value: Int) = change { timeout = value }
        override fun timeoutMillis() = timeout
        override fun mediaVolumeFixed() = fixed
        override fun mediaVolumeMin() = 2
        override fun mediaVolumeMax() = 12
        override fun setMediaVolume(index: Int) = change { volume = index }
        override fun mediaVolume() = volume
    }
    @Test fun `manual brightness zero remains a valid value and auto has no manual override`() {
        val d = Device(); val c = DisplayPolicyControl(d)
        assertEquals(PolicyOutcome.Applied, c.brightness(false, 0)); assertFalse(d.auto); assertEquals(0, d.light)
        assertEquals(PolicyOutcome.Applied, c.brightness(true, null)); assertTrue(d.auto)
        val count = d.mutations
        for (pair in listOf(false to null, true to 99, false to -1, false to 256)) assertTrue(c.brightness(pair.first, pair.second) is PolicyOutcome.Failed)
        assertEquals(count, d.mutations)
    }
    @Test fun `timeout preserves stricter lock policy and prevents integer overflow`() {
        val d = Device(); d.maxLock = 60000; val c = DisplayPolicyControl(d)
        assertTrue(c.timeout(61) is PolicyOutcome.Failed); assertEquals(0, d.mutations)
        assertEquals(PolicyOutcome.Applied, c.timeout(60)); assertEquals(60000, d.timeout)
        for (seconds in listOf(0, -1, Int.MAX_VALUE, 2147484)) assertTrue(c.timeout(seconds) is PolicyOutcome.Failed)
        assertEquals(1, d.mutations)
    }
    @Test fun `media percent maps to actual min max and leaves other streams alone`() {
        val d = Device(); val c = DisplayPolicyControl(d)
        for ((percent, index) in listOf(0 to 2, 50 to 7, 100 to 12, 34 to 5)) {
            assertEquals(PolicyOutcome.Applied, c.mediaVolume(percent)); assertEquals(index, d.volume)
        }
        assertTrue(c.mediaVolume(101) is PolicyOutcome.Failed); assertEquals(4, d.mutations)
        d.fixed = true; assertFalse("mediaVolume" in c.supportedKeys()); assertEquals(PolicyOutcome.Unsupported, c.mediaVolume(50)); assertEquals(4, d.mutations)
    }
    @Test fun `unsupported sdk and ownership loss never call a setter`() {
        val d = Device(); val c = DisplayPolicyControl(d)
        d.sdkInt = 27; assertFalse("brightness" in c.supportedKeys()); assertEquals(PolicyOutcome.Unsupported, c.brightness(false, 100))
        d.sdkInt = 35; d.checks = 0; d.loseOnSecondCheck = true
        assertEquals(PolicyOutcome.Unsupported, c.timeout(10)); assertEquals(0, d.mutations)
        d.owner = false; assertTrue(c.supportedKeys().isEmpty())
    }
    @Test fun `silent OEM refusal and thrown restrictions cannot be reported applied`() {
        val d = Device(); val c = DisplayPolicyControl(d); d.accepts = false
        assertTrue(c.brightness(false, 120) is PolicyOutcome.Failed)
        assertTrue(c.timeout(120) is PolicyOutcome.Failed)
        assertTrue(c.mediaVolume(100) is PolicyOutcome.Failed)
        d.throws = true
        val result = c.timeout(120)
        assertTrue(result is PolicyOutcome.Failed)
        assertFalse((result as PolicyOutcome.Failed).reason.contains("private OEM"))
    }
}
