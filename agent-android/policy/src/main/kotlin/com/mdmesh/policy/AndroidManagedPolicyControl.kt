package com.mdmesh.policy

import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mdmesh.policy.wifi.DpmHandle

/** Device-Owner only; rechecks ownership for every probe and application. */
class AndroidManagedPolicyControl(private val context: Context, private val handle: DpmHandle) : ManagedPolicyControl {
    override fun supportedKeys(): Set<String> = runCatching {
        if (Build.VERSION.SDK_INT < 23 || !handle.dpm.isDeviceOwnerApp(handle.admin.packageName)) emptySet()
        else buildSet {
            add("systemUpdatePolicy")
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN) &&
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) add("passwordRequirements")
        }
    }.getOrDefault(emptySet())

    override fun systemUpdate(mode: Int, startMinutes: Int?, endMinutes: Int?): PolicyOutcome {
        if ("systemUpdatePolicy" !in supportedKeys()) return PolicyOutcome.Unsupported
        if (mode !in 0..3 || (mode == 2 && (startMinutes == null || endMinutes == null || startMinutes !in 0..1439 || endMinutes !in 0..1439 || startMinutes == endMinutes)) ||
            (mode != 2 && (startMinutes != null || endMinutes != null))) return PolicyOutcome.Failed("Invalid system update window")
        return runCatching {
            val policy = when (mode) {
                0 -> null
                1 -> SystemUpdatePolicy.createAutomaticInstallPolicy()
                2 -> SystemUpdatePolicy.createWindowedInstallPolicy(startMinutes!!, endMinutes!!)
                else -> SystemUpdatePolicy.createPostponeInstallPolicy()
            }
            handle.dpm.setSystemUpdatePolicy(handle.admin, policy)
            PolicyOutcome.Applied
        }.getOrElse { PolicyOutcome.Failed("Android refused the system update policy") }
    }

    @Suppress("DEPRECATION") // Supported for fully managed company-owned devices on API31+.
    override fun password(quality: String, minimumLength: Int): PolicyOutcome {
        if ("passwordRequirements" !in supportedKeys()) return PolicyOutcome.Unsupported
        val qualities = mapOf("NONE" to DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED,
            "NUMERIC" to DevicePolicyManager.PASSWORD_QUALITY_NUMERIC,
            "NUMERIC_COMPLEX" to DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX,
            "ALPHANUMERIC" to DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC,
            "COMPLEX" to DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)
        val value = qualities[quality] ?: return PolicyOutcome.Failed("Unsupported password quality")
        if (minimumLength !in 0..16 || (quality == "NONE" && minimumLength != 0)) return PolicyOutcome.Failed("Invalid minimum password length")
        return runCatching {
            handle.dpm.setPasswordQuality(handle.admin, value)
            if (quality != "NONE") handle.dpm.setPasswordMinimumLength(handle.admin, minimumLength)
            // This configures requirements, never changes/reads the person's credential.
            PolicyOutcome.Applied
        }.getOrElse { PolicyOutcome.Failed("Android refused the password requirements") }
    }
}
