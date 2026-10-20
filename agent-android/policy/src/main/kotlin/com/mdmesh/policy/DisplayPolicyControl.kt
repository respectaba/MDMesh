package com.mdmesh.policy

/** Narrow device surface; no settings names or arbitrary streams arrive from the wire. */
interface DisplayDevice {
    fun isDeviceOwner(): Boolean
    val sdkInt: Int
    fun setBrightnessMode(automatic: Boolean)
    fun brightnessMode(): Boolean
    fun setBrightness(value: Int)
    fun brightness(): Int
    fun maximumLockMillis(): Long
    fun setTimeoutMillis(value: Int)
    fun timeoutMillis(): Int
    fun mediaVolumeFixed(): Boolean
    fun mediaVolumeMin(): Int
    fun mediaVolumeMax(): Int
    fun setMediaVolume(index: Int)
    fun mediaVolume(): Int
}

class DisplayPolicyControl(private val device: DisplayDevice) {
    fun supportedKeys(): Set<String> = runCatching {
        if (!device.isDeviceOwner()) emptySet() else buildSet {
            if (device.sdkInt >= 28) addAll(setOf("brightness", "screenTimeout"))
            if (!device.mediaVolumeFixed()) add("mediaVolume")
        }
    }.getOrDefault(emptySet())

    fun brightness(automatic: Boolean, value: Int?): PolicyOutcome {
        if ((!automatic && value == null) || (automatic && value != null) || (value != null && value !in 0..255))
            return PolicyOutcome.Failed("Invalid brightness settings")
        if ("brightness" !in supportedKeys()) return PolicyOutcome.Unsupported
        return checked("brightness") {
            device.setBrightnessMode(automatic)
            if (value != null) device.setBrightness(value)
            device.brightnessMode() == automatic && (value == null || device.brightness() == value)
        }
    }

    fun timeout(seconds: Int): PolicyOutcome {
        // Android accepts a signed millisecond int. Zero is not a portable 'never' value.
        if (seconds !in 1..(Int.MAX_VALUE / 1000)) return PolicyOutcome.Failed("Invalid screen timeout")
        if ("screenTimeout" !in supportedKeys()) return PolicyOutcome.Unsupported
        return checked("screen timeout") {
            val millis = seconds * 1000
            val maximumLock = device.maximumLockMillis()
            if (maximumLock > 0 && millis.toLong() > maximumLock) return@checked false
            device.setTimeoutMillis(millis)
            device.timeoutMillis() == millis
        }
    }

    fun mediaVolume(percent: Int): PolicyOutcome {
        if (percent !in 0..100) return PolicyOutcome.Failed("Invalid media volume")
        if ("mediaVolume" !in supportedKeys()) return PolicyOutcome.Unsupported
        return checked("media volume") {
            val min = device.mediaVolumeMin(); val max = device.mediaVolumeMax()
            if (min < 0 || max <= min) return@checked false
            val index = min + (((max.toLong() - min) * percent + 50) / 100).toInt()
            device.setMediaVolume(index)
            device.mediaVolume() == index
        }
    }

    private inline fun checked(setting: String, apply: () -> Boolean): PolicyOutcome = try {
        // Recheck at the mutation boundary too; collection-time facts are not authority.
        if (!device.isDeviceOwner()) PolicyOutcome.Unsupported
        else if (apply()) PolicyOutcome.Applied
        else PolicyOutcome.Failed("Android did not confirm the requested $setting; prior changes may remain")
    } catch (_: Exception) {
        PolicyOutcome.Failed("Android refused the requested $setting; prior changes may remain")
    }
}
