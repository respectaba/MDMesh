package com.mdmesh.proto

/**
 * Open registry of device-action command types. By convention the action's command [type] is
 * identical to its capability token and to the server-side `requiresCapability` string, so there
 * is exactly one string per action and no drift between agent, server, and UI.
 *
 * The agent advertises [ADVERTISED_KEYS] in `capabilities.device`; the server flattens each into a
 * `device.<key>` token (see `AgentCapabilityTokens`).
 */
object DeviceAction {
    const val LOCK = "device.lock"
    const val REBOOT = "device.reboot"
    const val LOCKSCREEN_MESSAGE = "device.lockscreenMessage"
    const val ALERT = "device.alert"
    const val RING = "device.ring"
    const val RING_STOP = "device.ringStop"
    const val PASSCODE_RESET = "device.passcodeReset"
    const val WIPE = "device.wipe"

    /** Set the agent's connectivity power mode. Payload: `{ "mode": "adaptive" | "alwaysOn" }`. */
    const val POWER_MODE = "device.powerMode"

    /** Connectivity power-mode values (see [POWER_MODE]). */
    const val POWER_ADAPTIVE = "adaptive"
    const val POWER_ALWAYS_ON = "alwaysOn"

    /** Set how location is captured. Payload: `{ "mode": "passive" | "active" | "off" }` (`off` since 1.4). */
    const val LOCATION_MODE = "device.locationMode"

    /**
     * Location-mode values (see [LOCATION_MODE]). Passive = last-known (cheap); active = fresh fix;
     * off = no location is read at all (the configuration's "Do not track"). An agent without
     * [LOCATION_OFF_KEY] treats `off` as passive, so the server must not assume it is honoured there.
     */
    const val LOCATION_PASSIVE = "passive"
    const val LOCATION_ACTIVE = "active"
    const val LOCATION_OFF = "off"

    /** Device key advertised when the agent honours [LOCATION_OFF] (protocol 1.4). */
    const val LOCATION_OFF_KEY = "locationOff"

    /** Desired-state document push (see ConfigApplyPayload). Advertised as device key [CONFIG_APPLY_KEY]. */
    const val CONFIG_APPLY = "config.apply"
    const val CONFIG_APPLY_KEY = "configApply"

    /** Keys (after the `device.` prefix) advertised in `capabilities.device`. */
    val ADVERTISED_KEYS: List<String> = listOf(
        "lock", "reboot", "lockscreenMessage", "alert", "ring", "ringStop",
        "passcodeReset", "wipe", "powerMode", "locationMode", "configApply", "configDisplay", LOCATION_OFF_KEY,
    )

    /** Probe facts, never a fleet-wide assumption. Unknown future keys fail closed. */
    fun supportedKeys(deviceOwner: Boolean, adminActive: Boolean, sdkInt: Int, resetReady: Boolean): List<String> =
        ADVERTISED_KEYS.filter { key ->
            when (key) {
                "lock" -> adminActive
                "reboot" -> deviceOwner && adminActive && sdkInt >= 24
                "wipe", "lockscreenMessage", "configApply" -> deviceOwner && adminActive
                "configDisplay" -> deviceOwner && adminActive && sdkInt >= 28
                "passcodeReset" -> deviceOwner && adminActive && sdkInt >= 26 && resetReady
                else -> key in setOf("alert", "ring", "ringStop", "powerMode", "locationMode", LOCATION_OFF_KEY)
            }
        }
}
