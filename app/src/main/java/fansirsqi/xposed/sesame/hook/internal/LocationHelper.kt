package fansirsqi.xposed.sesame.hook.internal

import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object LocationHelper {

    private const val TAG = "LocationInfoHelper"
    private var classLoader: ClassLoader? = null
    private const val LOCATION_KEY = "cached_location"

    // 保留旧的回调接口以兼容 Java 代码，但已标记为 Deprecated
    fun interface LocationCallback {
        fun onLocationResult(location: JSONObject?)
    }

    fun init(loader: ClassLoader) {
        classLoader = loader
    }

    /**
     * 同步获取缓存的位置信息
     */
    fun getLocation(): JSONObject? {
        return try {
            val map = DataStore.get(LOCATION_KEY, Map::class.java)
            map?.let { JSONObject(it) }
        } catch (e: Exception) {
            Log.error(TAG, "读取位置缓存失败: ${e.message}")
            null
        }
    }

    /**
     * ✅ 新增：挂起函数版本 (推荐 Kotlin 使用)
     * 在后台线程获取位置并返回结果，自动切回原线程
     */
    suspend fun requestLocationSuspend(): JSONObject = withContext(Dispatchers.Default) {
        try {
            if (classLoader == null) {
                return@withContext createAndSaveError("ClassLoader 未初始化")
            }

            // 执行反射调用 (耗时操作)
            val lnsctrUtilsClass = XposedHelpers.findClass("com.alipay.mobile.common.lnsctr.LnsctrUtils", classLoader)
            val latitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLatitude") as? Double
            val longitude = XposedHelpers.callStaticMethod(lnsctrUtilsClass, "getLongitude") as? Double

            if (latitude != null && longitude != null) {
                val locationMap = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
                saveLocationToDataStore(locationMap)
                JSONObject(locationMap)
            } else {
                createAndSaveError("等待目标应用初始化中...")
            }
        } catch (e: Throwable) {
            Log.error(TAG, "获取经纬度异常: ${e.message}")
            createAndSaveError("获取失败: ${e.message}")
        }
    }

    /**
     * 兼容旧版：回调风格 (内部使用协程)
     * 供 Java 代码或尚未迁移的 Kotlin 代码使用
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun requestLocation(callback: LocationCallback) {
        // 使用 GlobalScope 启动协程替代 new Thread
        // 注意：在 ApplicationHook 中最好使用受控的 Scope，这里作为单例工具类暂时用 GlobalScope
        GlobalScope.launch(Dispatchers.Main) {
            val result = requestLocationSuspend()
            callback.onLocationResult(result)
        }
    }

    private fun createAndSaveError(msg: String): JSONObject {
        val map = mapOf("status" to msg)
        saveLocationToDataStore(map)
        return JSONObject(map)
    }

    private fun saveLocationToDataStore(locationMap: Map<String, Any>) {
        try {
            DataStore.put(LOCATION_KEY, locationMap)
        } catch (e: Exception) {
            Log.error(TAG, "保存位置缓存失败: ${e.message}")
        }
    }
}