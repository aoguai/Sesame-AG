package fansirsqi.xposed.sesame.util

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import fansirsqi.xposed.sesame.hook.ApplicationHook

object NetworkUtils {

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isNetworkAvailable(): Boolean {
        val context = ApplicationHook.appContext ?: return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getNetworkType(): String {
        val context = ApplicationHook.appContext ?: return "UNKNOWN"
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "UNKNOWN"
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "UNKNOWN"
        }
    }
}
