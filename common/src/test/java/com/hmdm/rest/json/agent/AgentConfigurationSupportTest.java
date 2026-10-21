package com.hmdm.rest.json.agent;
import com.hmdm.persistence.domain.DeviceState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;
public class AgentConfigurationSupportTest {
    private DeviceState state(long time, String snapshot) { DeviceState s=new DeviceState();s.setUpdatedAt(time);s.setAgentVersion("synthetic");s.setKioskActive(true);s.setConfigurationCapabilities(snapshot);return s; }
    @Test public void exposesOnlyTokensFromOneCompleteSnapshot() {
        String tokens=AgentConfigurationSupport.serializeCapabilities("{\"policy\":[\"brightness\",\"systemUpdatePolicy\",\"wipe\",\"brightness\"],\"device\":[\"kioskAppearance\",\"passcodeReset\"]}");
        AgentConfigurationSupport p=AgentConfigurationSupport.snapshot(state(1000,tokens));
        assertEquals(Long.valueOf(1000),p.getUpdatedAt());assertTrue(p.isKioskActive());assertEquals(java.util.Arrays.asList("device.kioskAppearance","policy.brightness","policy.systemUpdatePolicy"),p.getCapabilities());
        try{p.getCapabilities().add("device.wipe");fail("immutable");}catch(UnsupportedOperationException expected){}
    }
    @Test public void oldMissingInvalidOrUnboundedSnapshotsCannotBorrowCapabilities() {
        for(String snapshot:new String[]{null,"bad","{}","[\"device.wipe\"]","[\"policy.brightness\",\"policy.brightness\"]",String.join("",java.util.Collections.nCopies(4097,"x"))}){
            AgentConfigurationSupport p=AgentConfigurationSupport.snapshot(state(1000,snapshot));assertNull(p.getUpdatedAt());assertFalse(p.isKioskActive());assertTrue(p.getCapabilities().isEmpty());
        }
        assertNull(AgentConfigurationSupport.snapshot(null).getUpdatedAt());assertNull(AgentConfigurationSupport.snapshot(state(0,"[]")).getUpdatedAt());
    }
    @Test public void onlyCurrentBoundedCapabilityFramesCanBePersisted() {
        for(String raw:new String[]{null,"bad","[]",String.join("",java.util.Collections.nCopies(16385,"x"))})assertNull(AgentConfigurationSupport.serializeCapabilities(raw));
        assertEquals("[]",AgentConfigurationSupport.serializeCapabilities("{}"));assertEquals("[]",AgentConfigurationSupport.serializeCapabilities("{\"device\":[\"unknown\"]}"));
    }
    @Test public void snapshotIsNotAddedToLegacyStateResponses() throws Exception {
        String json=new ObjectMapper().writeValueAsString(state(1000,"[\"policy.brightness\"]"));assertFalse(json.contains("configurationCapabilities"));
    }
}
