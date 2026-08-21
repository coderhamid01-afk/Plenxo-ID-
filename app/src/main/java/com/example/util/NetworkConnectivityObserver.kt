package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkStatus {
    Available, Lost, Weak
}

class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _status = MutableStateFlow(NetworkStatus.Lost)
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = NetworkStatus.Available
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            _status.value = NetworkStatus.Lost
            _isConnected.value = false
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // Safely retrieve signal strength or bandwidth if available
            val isWeak = networkCapabilities.linkDownstreamBandwidthKbps in 1..150
            if (hasInternet) {
                if (isWeak) {
                    _status.value = NetworkStatus.Weak
                } else {
                    _status.value = NetworkStatus.Available
                }
                _isConnected.value = true
            } else {
                _status.value = NetworkStatus.Lost
                _isConnected.value = false
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            if (hasInternet) {
                _status.value = NetworkStatus.Available
                _isConnected.value = true
            } else {
                _status.value = NetworkStatus.Lost
                _isConnected.value = false
            }
        } catch (e: Exception) {
            _status.value = NetworkStatus.Lost
            _isConnected.value = false
        }
    }

    fun shutdown() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
