package com.mindorks.ridesharing.data.network

import com.mindorks.ridesharing.simulator.WebSocket
import com.mindorks.ridesharing.simulator.WebSocketListener

class NetworkService {
    fun createWenSocket(webSocketListener: WebSocketListener): WebSocket {
        return WebSocket(webSocketListener)

    }
}