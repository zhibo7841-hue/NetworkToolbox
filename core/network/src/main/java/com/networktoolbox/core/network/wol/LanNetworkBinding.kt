package com.networktoolbox.core.network.wol

import com.networktoolbox.core.network.model.NetworkContext
import java.net.DatagramSocket

/** A current physical LAN and the operation needed to bind a socket to it. */
interface LanNetworkBinding {
    val networkContext: NetworkContext

    fun bindDatagramSocket(socket: DatagramSocket)
}

fun interface LanNetworkBindingProvider {
    /** Returns a fresh physical LAN binding, or null when none is usable. */
    fun current(): LanNetworkBinding?
}

object NoOpLanNetworkBindingProvider : LanNetworkBindingProvider {
    override fun current(): LanNetworkBinding? = null
}
