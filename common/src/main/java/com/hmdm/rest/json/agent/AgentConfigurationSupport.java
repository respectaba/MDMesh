package com.hmdm.rest.json.agent;

import com.hmdm.persistence.domain.DeviceState;
import com.hmdm.util.AgentCapabilityTokens;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only bounded projection. Qualification and current authority remain
 * separate checks; advertised support is never proof that a policy was applied. */
public final class AgentConfigurationSupport {
    private static final Set<String> ALLOWED = Set.of(
        "policy.systemUpdatePolicy", "policy.passwordRequirements", "policy.brightness",
        "policy.screenTimeout", "policy.mediaVolume", "device.kioskAppearance");
    private final Long updatedAt;
    private final boolean activeLauncher;
    private final List<String> capabilities;
    private AgentConfigurationSupport(Long updatedAt, boolean activeLauncher, List<String> capabilities) {
        this.updatedAt = updatedAt;
        this.activeLauncher = activeLauncher;
        this.capabilities = Collections.unmodifiableList(capabilities);
    }
    public Long getUpdatedAt() { return updatedAt; }
    public boolean isActiveLauncher() { return activeLauncher; }
    public List<String> getCapabilities() { return capabilities; }
    public static AgentConfigurationSupport snapshot(DeviceState before, DeviceState after, String raw) {
        // A changing heartbeat cannot bind capabilities to the identity read by
        // the caller. Fail closed instead of returning a mixed snapshot.
        if (before == null || after == null || before.getUpdatedAt() == null ||
            before.getUpdatedAt() <= 0 || !Objects.equals(before.getUpdatedAt(), after.getUpdatedAt()) ||
            !Objects.equals(before.getAgentVersion(), after.getAgentVersion()) ||
            raw == null || raw.length() > 16384) {
            return new AgentConfigurationSupport(null, false, new ArrayList<>());
        }
        List<String> tokens = new ArrayList<>();
        for (String token : AgentCapabilityTokens.flatten(raw)) if (ALLOWED.contains(token)) tokens.add(token);
        Collections.sort(tokens);
        return new AgentConfigurationSupport(after.getUpdatedAt(), Boolean.TRUE.equals(after.getKioskActive()), tokens);
    }
}
