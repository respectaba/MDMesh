package com.mdmesh.core.location

import com.mdmesh.proto.LocationDto

/** Current authority, permission, service and privacy probes; capture must never fall back to a cached fix. */
interface FreshLocationSource {
    fun supported(): Boolean
    suspend fun capture(): LocationDto?
}
