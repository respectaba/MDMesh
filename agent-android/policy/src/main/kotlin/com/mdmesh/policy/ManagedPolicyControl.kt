package com.mdmesh.policy

/** Typed, narrow policy port. No automatic wipe or credential reset settings. */
interface ManagedPolicyControl {
    fun supportedKeys(): Set<String>
    fun systemUpdate(mode: Int, startMinutes: Int?, endMinutes: Int?): PolicyOutcome
    fun password(quality: String, minimumLength: Int): PolicyOutcome
}
