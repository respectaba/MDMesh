package com.hmdm.rest.json.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.persistence.domain.DeviceState;
import com.hmdm.util.AgentCapabilityTokens;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Projection from one atomically persisted check-in snapshot, never from the
 * independently mutable legacy device capability column. */
public final class AgentConfigurationSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ALLOWED = Set.of(
        "policy.systemUpdatePolicy", "policy.passwordRequirements", "policy.brightness",
        "policy.screenTimeout", "policy.mediaVolume", "device.kioskAppearance");
    private final Long updatedAt;
    private final boolean kioskActive;
    private final List<String> capabilities;
    private AgentConfigurationSupport(Long updatedAt, boolean kioskActive, List<String> capabilities) {
        this.updatedAt = updatedAt; this.kioskActive = kioskActive;
        this.capabilities = Collections.unmodifiableList(capabilities);
    }
    public Long getUpdatedAt() { return updatedAt; }
    public boolean isKioskActive() { return kioskActive; }
    public List<String> getCapabilities() { return capabilities; }
    /** Called only for capabilities carried by THIS check-in. Missing inputs
     * do not borrow a previous advertisement. Never persist arbitrary tokens. */
    public static String serializeCapabilities(String raw) {
        if (raw == null || raw.length() > 16384) return null;
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isObject()) return null;
            List<String> tokens = new ArrayList<>();
            for (String token : AgentCapabilityTokens.flatten(root)) if (ALLOWED.contains(token)) tokens.add(token);
            Collections.sort(tokens);
            return JSON.writeValueAsString(tokens);
        } catch (Exception invalid) { return null; }
    }
    public static AgentConfigurationSupport snapshot(DeviceState state) {
        List<String> tokens = new ArrayList<>();
        if (state != null && state.getUpdatedAt() != null && state.getUpdatedAt() > 0 &&
            state.getConfigurationCapabilities() != null && state.getConfigurationCapabilities().length() <= 4096) {
            try {
                JsonNode array = JSON.readTree(state.getConfigurationCapabilities());
                if (array == null || !array.isArray() || array.size() > ALLOWED.size()) throw new IllegalArgumentException();
                for (JsonNode token : array) {
                    if (!token.isTextual() || !ALLOWED.contains(token.asText()) || tokens.contains(token.asText())) throw new IllegalArgumentException();
                    tokens.add(token.asText());
                }
                Collections.sort(tokens);
                return new AgentConfigurationSupport(state.getUpdatedAt(), Boolean.TRUE.equals(state.getKioskActive()), tokens);
            } catch (Exception invalid) { /* Unavailable, never fall back to legacy capabilities. */ }
        }
        return new AgentConfigurationSupport(null, false, new ArrayList<>());
    }
}
