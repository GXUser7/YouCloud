package com.example.myapplication.player

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where the music is heard: the phone's own speaker, what is plugged in or connected over
 * Bluetooth, a speaker paired before but not connected now, or a TV over Google Cast. The app says
 * which ([preferredDeviceId]); the playback service sends its sound there, and the crossfade's
 * other player's too.
 */
object AudioOutputs {
    enum class Kind { Phone, Bluetooth, Wired, Usb, HearingAid, Cast }

    class Output(
        /** Stable across a sheet's refreshes: what it is, not where it is in the list. */
        val key: String,
        val name: String,
        val kind: Kind,
        /** Connected, so sound can go there at once; a paired speaker that isn't, can't. */
        val connected: Boolean,
        internal val deviceId: Int? = null
    )

    /**
     * The device the app's sound goes to, by its AudioDeviceInfo id; null for wherever Android
     * sends it (the last connected headphones or speaker, the phone otherwise). Kept until it is
     * changed or the app closes: "only on the phone" stays so when headphones connect.
     */
    val preferredDeviceId = MutableStateFlow<Int?>(null)

    /** The Cast device the music plays on, by its name, while it does. */
    val castingTo = MutableStateFlow<String?>(null)

    private val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE)
    private val USB = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_DOCK)
    private val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)

    private fun kindOf(type: Int): Kind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.Phone
        in BLUETOOTH -> Kind.Bluetooth
        in WIRED -> Kind.Wired
        in USB -> Kind.Usb
        AudioDeviceInfo.TYPE_HEARING_AID -> Kind.HearingAid
        else -> null
    }

    /** What sound can go to right now: the phone first, then each connected device once. */
    fun connected(context: Context, phoneName: String): List<Output> {
        val audio = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        val seen = mutableSetOf<String>()
        val outputs = mutableListOf<Output>()
        for (device in audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val kind = kindOf(device.type) ?: continue
            // A Bluetooth LE device lists itself more than once; a speaker, once.
            val name = if (kind == Kind.Phone) phoneName else device.productName?.toString()?.takeIf { it.isNotBlank() } ?: continue
            val key = if (kind == Kind.Bluetooth) "bt:${device.address.ifBlank { name }}" else "${kind.name}:$name"
            if (!seen.add(key)) continue
            outputs += Output(key, name, kind, connected = true, deviceId = device.id)
        }
        return outputs.sortedBy { if (it.kind == Kind.Phone) 0 else 1 }
    }

    /** Whether the app may see the phone's paired Bluetooth devices. */
    fun canListPaired(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /**
     * Speakers and headphones paired with the phone but not connected now ([connectedKeys] are),
     * by name. Android lets no app connect them itself: picking one opens the phone's own output
     * switcher, which does.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun paired(context: Context, connectedKeys: Set<String>): List<Output> {
        if (!canListPaired(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .filter { "bt:${it.address}" !in connectedKeys }
            .mapNotNull { device ->
                val name = runCatching { device.alias ?: device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Output("bt:${device.address}", name, Kind.Bluetooth, connected = false)
            }
            .sortedBy { it.name }
    }

    /**
     * Where the sound goes now, of [connected]: the one asked for while it is there; otherwise
     * where Android sends media — the last headphones or speaker connected, the phone without.
     */
    fun current(connected: List<Output>): Output? {
        val preferred = preferredDeviceId.value
        connected.firstOrNull { it.deviceId == preferred }?.let { return it }
        return connected.firstOrNull { it.kind == Kind.Bluetooth || it.kind == Kind.HearingAid }
            ?: connected.firstOrNull { it.kind == Kind.Wired }
            ?: connected.firstOrNull { it.kind == Kind.Usb }
            ?: connected.firstOrNull { it.kind == Kind.Phone }
    }

    /** The device with [id], to hand the player: null when it's gone, or wasn't asked for. */
    fun deviceInfo(context: Context, id: Int?): AudioDeviceInfo? {
        if (id == null) return null
        val audio = context.getSystemService(AudioManager::class.java) ?: return null
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
    }

    /**
     * The phone's own output switcher, where a paired speaker gets connected; Bluetooth's
     * settings where there is none.
     */
    fun openSystemSwitcher(context: Context) {
        val shown = runCatching { android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
        if (!shown) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
