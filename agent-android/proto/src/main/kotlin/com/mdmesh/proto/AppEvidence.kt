package com.mdmesh.proto

import kotlinx.serialization.Serializable

/** Explicit on-demand request; never included in check-in telemetry or apps.scan. */
@Serializable
data class AppEvidenceRequest(val packages: List<String> = emptyList())

/** An unknown observation is never evidence of a safe or vulnerable installation. */
@Serializable
data class AppInstallationEvidence(
    val pkg: String,
    val status: String,
    val reason: String? = null,
    val versionCode: Long? = null,
    val apkSha256: String? = null,
    val signerSha256: String? = null,
    val observedAt: String? = null,
)

@Serializable
data class AppEvidenceResult(val installations: List<AppInstallationEvidence> = emptyList())
