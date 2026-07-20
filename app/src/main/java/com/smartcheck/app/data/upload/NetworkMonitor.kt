package com.smartcheck.app.data.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.smartcheck.app.data.sync.EmployeeSyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingUploadManager: PendingUploadManager,
    private val deviceHeartbeatManager: DeviceHeartbeatManager,
    private val employeeSyncEngine: EmployeeSyncEngine,
    private val appScope: CoroutineScope
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Timber.d("Network available, triggering pending uploads, heartbeat, and employee sync")
            pendingUploadManager.enqueue(0L)
            deviceHeartbeatManager.triggerImmediate()

            // 网络恢复时触发员工同步
            appScope.launch {
                try {
                    employeeSyncEngine.triggerSync()
                    Timber.d("Employee sync triggered on network recovery")
                } catch (e: Exception) {
                    Timber.w(e, "Employee sync on network recovery failed")
                }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Timber.d("Network lost")
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        Timber.d("NetworkMonitor started")
    }
}
