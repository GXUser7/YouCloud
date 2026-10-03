package com.example.myapplication.together

import com.example.myapplication.i18n.tr
import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** How listening together reaches the other phones: [TogetherSession], or a stand-in in tests. */
interface TogetherLink {
    val state: StateFlow<TogetherState>

    /** What arrives, with the endpoint it came from. */
    val messages: SharedFlow<Pair<String, TogetherMessage>>
    fun send(to: String, message: TogetherMessage)

    /** To every guest. */
    fun broadcast(message: TogetherMessage)

    /** Whether [message] fits in one message; a longer one is cut down by its sender. */
    fun fits(message: TogetherMessage): Boolean
    fun leave()
}

/** A phone on the other end. */
data class TogetherPeer(val id: String, val name: String)

/** Someone asking to join the host, with the code both screens show. */
data class TogetherRequest(val peer: TogetherPeer, val code: String)

sealed interface TogetherState {
    data object Idle : TogetherState

    /** Waiting for guests, and with whoever has joined. */
    data class Hosting(val guests: List<TogetherPeer>, val request: TogetherRequest? = null) : TogetherState

    /** Looking for a host nearby; [joining] the one asked to. */
    data class Searching(val hosts: List<TogetherPeer>, val joining: TogetherPeer? = null, val code: String? = null) : TogetherState

    /** Listening along with [host]. */
    data class Joined(val host: TogetherPeer) : TogetherState

    data class Failed(val message: String) : TogetherState
}

/**
 * Two (or more) phones side by side, talking directly — over Bluetooth, and Wi-Fi Direct once it
 * is faster — with Google Play services' Nearby Connections, no server between them. One phone
 * hosts: it is seen nearby and lets guests in, after its listener has checked the code both
 * screens show. Messages are [TogetherMessage]s, as JSON.
 *
 * Nearby calls back on the main thread; so does everything here.
 */
class TogetherSession(context: Context) : TogetherLink {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val gson = Gson()

    private val _state = MutableStateFlow<TogetherState>(TogetherState.Idle)
    override val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Pair<String, TogetherMessage>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val messages = _messages.asSharedFlow()

    private var myName = ""
    // The names endpoints gave when they asked to connect, or were found under.
    private val names = HashMap<String, String>()

    /** Seen nearby as [name], letting guests in. */
    fun host(name: String) {
        stopAll()
        myName = name
        _state.value = TogetherState.Hosting(emptyList())
        client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail(tr("Не удалось стать видимым"), it) }
    }

    /** Looking for a host nearby, to [join]. */
    fun search(name: String) {
        stopAll()
        myName = name
        _state.value = TogetherState.Searching(emptyList())
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail(tr("Не удалось искать рядом"), it) }
    }

    fun join(host: TogetherPeer) {
        val searching = _state.value as? TogetherState.Searching ?: return
        _state.value = searching.copy(joining = host, code = null)
        client.requestConnection(myName, host.id, lifecycle)
            .addOnFailureListener { e ->
                (_state.value as? TogetherState.Searching)?.let { _state.value = it.copy(joining = null, code = null) }
                fail(tr("Не удалось подключиться"), e, keep = true)
            }
    }

    /** The host's answer to [TogetherState.Hosting.request]. */
    fun answer(request: TogetherRequest, accept: Boolean) {
        val hosting = _state.value as? TogetherState.Hosting ?: return
        _state.value = hosting.copy(request = null)
        if (accept) client.acceptConnection(request.peer.id, payloads) else client.rejectConnection(request.peer.id)
    }

    override fun send(to: String, message: TogetherMessage) {
        client.sendPayload(to, Payload.fromBytes(encode(message)))
    }

    override fun broadcast(message: TogetherMessage) {
        val guests = (_state.value as? TogetherState.Hosting)?.guests?.map { it.id } ?: return
        if (guests.isEmpty()) return
        client.sendPayload(guests, Payload.fromBytes(encode(message)))
    }

    override fun fits(message: TogetherMessage) = encode(message).size <= MAX_BYTES

    override fun leave() {
        stopAll()
        _state.value = TogetherState.Idle
    }

    private fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        names.clear()
    }

    private fun encode(message: TogetherMessage) = gson.toJson(message).toByteArray(Charsets.UTF_8)

    private fun fail(what: String, e: Exception, keep: Boolean = false) {
        val code = (e as? ApiException)?.statusCode
        Log.w(TAG, "$what: ${code?.let(ConnectionsStatusCodes::getStatusCodeString)}", e)
        val why = when (code) {
            ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> tr("Включите Bluetooth")
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT,
            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES -> tr("Разрешите доступ к устройствам поблизости")
            ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING,
            ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> return
            else -> tr("Проверьте, что Bluetooth и Wi-Fi включены")
        }
        if (!keep) stopAll()
        _state.value = TogetherState.Failed("$what. $why")
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            names[endpointId] = info.endpointName
            val searching = _state.value as? TogetherState.Searching ?: return
            _state.value = searching.copy(hosts = searching.hosts.filterNot { it.id == endpointId } + TogetherPeer(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            val searching = _state.value as? TogetherState.Searching ?: return
            _state.value = searching.copy(hosts = searching.hosts.filterNot { it.id == endpointId })
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            val code = info.authenticationDigits
            when (val now = _state.value) {
                // A guest asking in: the host's listener decides, having seen the same code.
                is TogetherState.Hosting -> _state.value = now.copy(request = TogetherRequest(TogetherPeer(endpointId, info.endpointName), code))
                // The host being joined: this side accepts at once, showing the code meanwhile.
                is TogetherState.Searching -> {
                    _state.value = now.copy(code = code)
                    client.acceptConnection(endpointId, payloads)
                }
                else -> client.rejectConnection(endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val ok = result.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            val peer = TogetherPeer(endpointId, names[endpointId] ?: tr("Телефон"))
            when (val now = _state.value) {
                is TogetherState.Hosting -> if (ok) {
                    _state.value = now.copy(guests = now.guests.filterNot { it.id == endpointId } + peer)
                }
                is TogetherState.Searching -> if (ok) {
                    client.stopDiscovery()
                    _state.value = TogetherState.Joined(peer)
                    send(endpointId, TogetherMessage(t = "hello", name = myName))
                } else {
                    _state.value = now.copy(joining = null, code = null)
                    if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                        _state.value = TogetherState.Failed(tr("«%s» не пустил подключиться", peer.name))
                    }
                }
                else -> Unit
            }
        }

        override fun onDisconnected(endpointId: String) {
            when (val now = _state.value) {
                is TogetherState.Hosting -> _state.value = now.copy(guests = now.guests.filterNot { it.id == endpointId })
                is TogetherState.Joined -> if (now.host.id == endpointId) {
                    stopAll()
                    _state.value = TogetherState.Failed(tr("Связь с «%s» прервалась", now.host.name))
                }
                else -> Unit
            }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val message = runCatching { gson.fromJson(String(bytes, Charsets.UTF_8), TogetherMessage::class.java) }.getOrNull() ?: return
            _messages.tryEmit(endpointId to message)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    companion object {
        private const val TAG = "Together"
        private const val SERVICE_ID = "com.example.myapplication.together"
        // One host, any number of guests: the host's phone at the middle of a star.
        private val STRATEGY = Strategy.P2P_STAR
        // Nearby's limit for a message sent whole.
        private const val MAX_BYTES = 30_000

        /** What "Allow access to nearby devices" covers, for Nearby to work. */
        val PERMISSIONS = arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.NEARBY_WIFI_DEVICES
        )
    }
}
