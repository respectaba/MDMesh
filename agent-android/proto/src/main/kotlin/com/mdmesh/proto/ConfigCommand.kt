package com.mdmesh.proto

import kotlinx.serialization.Serializable

/**
 * Payload of `config.apply` (proto/payloads/config-apply.schema.json): the server-computed desired state of the
 * device's configuration. Every field defaults so a newer server can add keys freely.
 *
 * @property revision sha256 of the canonical document; reported back as `appliedConfigRevision` once applied.
 * @property policies only the keys the configuration manages (true = allowed/enabled).
 * @property kiosk present = ensure kiosk with this payload; absent = the configuration does not assert kiosk
 *   (exit only if the previously applied configuration did — see ConfigApplier).
 * @property location capture cadence (`passive`, `active`, or `off` since 1.4), see [DeviceAction.LOCATION_MODE].
 */
@Serializable
data class ConfigApplyPayload(
    val revision: String = "",
    val configurationId: Int? = null,
    val policies: Map<String, Boolean> = emptyMap(),
    val kiosk: KioskApplyPayload? = null,
    val location: ConfigLocation? = null,
    val managedDisplay: ConfigDisplay? = null,
)

@Serializable
data class ConfigLocation(val mode: String = DeviceAction.LOCATION_PASSIVE)

/** JSON-encoded into `CommandResult.detail` (proto/payloads/config-apply-result.schema.json). */
@Serializable
data class ConfigApplyResult(
    val revision: String,
    /** keys: `policies.<key>`, `kiosk`, `location` → [ConfigOutcome] strings. */
    val outcomes: Map<String, String>,
)

object ConfigOutcome {
    const val APPLIED = "applied"
    const val UNSUPPORTED = "unsupported"
    fun failed(reason: String): String = "failed: $reason"

    /** 1.4: deliberately not applied now (e.g. Wi-Fi off while Wi-Fi is the only network); does not block. */
    fun deferred(reason: String): String = "deferred: $reason"
    fun isFailed(outcome: String): Boolean = outcome.startsWith("failed")
}

/** Ezi extension: requires device.configDisplay, never silently sent to older configApply agents. */
@Serializable
data class ConfigDisplay(
    val brightness: ConfigBrightness? = null,
    val screenTimeoutSeconds: Int? = null,
    val mediaVolumePercent: Int? = null,
) {
    fun valid(): Boolean =
        (brightness == null || (if (brightness.automatic) brightness.value == null else brightness.value != null && brightness.value in 0..255)) &&
        (screenTimeoutSeconds == null || screenTimeoutSeconds in 1..(Int.MAX_VALUE / 1000)) &&
        (mediaVolumePercent == null || mediaVolumePercent in 0..100)
}
@Serializable
data class ConfigBrightness(val automatic: Boolean, val value: Int? = null)
