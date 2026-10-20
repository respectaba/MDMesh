package com.mdmesh.policy

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.mdmesh.policy.wifi.DpmHandle

/** Uses the Device Owner API28 setting whitelist; never requests WRITE_SETTINGS. */
class AndroidDisplayDevice(private val context: Context, private val handle: DpmHandle) : DisplayDevice {
    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    override val sdkInt get() = Build.VERSION.SDK_INT
    override fun isDeviceOwner() = handle.dpm.isAdminActive(handle.admin) && handle.dpm.isDeviceOwnerApp(handle.admin.packageName)
    override fun setBrightnessMode(automatic: Boolean) = writeSystemSetting(Settings.System.SCREEN_BRIGHTNESS_MODE, if (automatic) "1" else "0")
    override fun brightnessMode() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    override fun setBrightness(value: Int) = writeSystemSetting(Settings.System.SCREEN_BRIGHTNESS, value.toString())
    override fun brightness() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    override fun maximumLockMillis() = handle.dpm.getMaximumTimeToLock(null)
    override fun setTimeoutMillis(value: Int) = writeSystemSetting(Settings.System.SCREEN_OFF_TIMEOUT, value.toString())
    override fun timeoutMillis() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
    override fun mediaVolumeFixed() = audio.isVolumeFixed
    override fun mediaVolumeMin() = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
    override fun mediaVolumeMax() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    override fun setMediaVolume(index: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    override fun mediaVolume() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    private fun writeSystemSetting(key: String, value: String) {
        if (Build.VERSION.SDK_INT < 28) throw UnsupportedOperationException("Display settings require API28")
        handle.dpm.setSystemSetting(handle.admin, key, value)
    }
}
