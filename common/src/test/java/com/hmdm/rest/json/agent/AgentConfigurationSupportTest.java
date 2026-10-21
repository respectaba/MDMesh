package com.hmdm.rest.json.agent;
import com.hmdm.persistence.domain.DeviceState;
import org.junit.Test;
import static org.junit.Assert.*;
public class AgentConfigurationSupportTest {
    private DeviceState state(long time) { DeviceState s = new DeviceState(); s.setUpdatedAt(time); s.setAgentVersion("synthetic"); s.setKioskActive(true); return s; }
    @Test public void exposesOnlyConfigurationTokensAndSourceTime() {
        AgentConfigurationSupport p = AgentConfigurationSupport.snapshot(state(1000), state(1000),
            "{\"policy\":[\"brightness\",\"systemUpdatePolicy\",\"wipe\",\"brightness\"],\"device\":[\"kioskAppearance\",\"passcodeReset\"],\"appManagement\":[\"silentInstall\"]}");
        assertEquals(Long.valueOf(1000), p.getUpdatedAt()); assertTrue(p.isActiveLauncher());
        assertEquals(java.util.Arrays.asList("device.kioskAppearance", "policy.brightness", "policy.systemUpdatePolicy"), p.getCapabilities());
        try { p.getCapabilities().add("device.wipe"); fail("immutable"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void missingChangedOrOversizedSourcesAreUnavailable() {
        for (AgentConfigurationSupport p : new AgentConfigurationSupport[] {
            AgentConfigurationSupport.snapshot(null, state(1), "{}"),
            AgentConfigurationSupport.snapshot(state(1), state(2), "{}"),
            AgentConfigurationSupport.snapshot(state(0), state(0), "{}"),
            AgentConfigurationSupport.snapshot(state(1), state(1), String.join("", java.util.Collections.nCopies(16385, "x")))
        }) { assertNull(p.getUpdatedAt()); assertFalse(p.isActiveLauncher()); assertTrue(p.getCapabilities().isEmpty()); }
    }
    @Test public void malformedAndLegacyCapabilitiesDoNotInventSupport() {
        for (String raw : new String[]{"bad", "[]", "{}", "{\"policy\":\"brightness\"}", "{\"device\":[\"unknown\"]}"})
            assertTrue(AgentConfigurationSupport.snapshot(state(1), state(1), raw).getCapabilities().isEmpty());
    }
}
