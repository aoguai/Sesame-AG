package fansirsqi.xposed.sesame.hook.internal

import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.Log

/**
 * 安全组件数据获取助手类
 * 用于调用目标应用的 ISecurityBodyComponent.getSecurityBodyDataEx 方法
 */
object SecurityBodyHelper {

    private const val TAG = "SecurityBodyHelper"
    private var classLoader: ClassLoader? = null

    /**
     * 初始化 SecurityBodyHelper
     * @param loader 应用类加载器
     */
    fun init(loader: ClassLoader) {
        classLoader = loader
        Log.record(TAG, "SecurityBodyHelper 初始化完成")
    }

    /**
     * 获取目标应用安全组件数据
     * 通过调用 ISecurityBodyComponent.getSecurityBodyDataEx 方法获取安全相关数据
     * 调用方式与 SecurityBodyWuaBridgeExtension 中完全一致
     *
     * @param type 类型参数，自定义值（如4或8）
     * @return 安全组件数据字符串，失败返回null
     */
    fun getSecurityBodyData(type: Int): String? {
        try {
            // 检查是否已初始化
            if (classLoader == null) {
                Log.error(TAG, "SecurityBodyHelper 未初始化，请先调用 init 方法")
                return null
            }

            // 使用 appContext 作为上下文
            val appContext = ApplicationHook.appContext
            if (appContext == null) {
                Log.error(TAG, "appContext 为 null，可能应用还未完全启动，请稍后再试")
                return null
            }
            // 获取 SecurityGuardManager 实例
            val securityGuardManager = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("com.alibaba.wireless.security.open.SecurityGuardManager", classLoader),
                "getInstance",
                appContext
            ) ?: run {
                Log.error(TAG, "无法获取 SecurityGuardManager 实例")
                return null
            }

            // 获取 ISecurityBodyComponent 实例
            val securityBodyComponent = XposedHelpers.callMethod(
                securityGuardManager,
                "getSecurityBodyComp"
            ) ?: run {
                Log.error(TAG, "无法获取 ISecurityBodyComponent 实例")
                return null
            }

            // 调用 getSecurityBodyDataEx 方法，参数与 SecurityBodyWuaBridgeExtension 中完全一致
            // getSecurityBodyDataEx((String) null, (String) null, "", (HashMap) null, type, 0)
            val result = XposedHelpers.callMethod(
                securityBodyComponent,
                "getSecurityBodyDataEx",
                null as String?,
                null as String?,
                "",
                null as HashMap<String, String>?,
                type,
                0
            ) as? String

            return if (!result.isNullOrEmpty()) {
                result
            } else {
                Log.error(TAG, "获取的安全组件数据为空")
                null
            }
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "获取安全组件数据失败: ${e.message}",e)
            return null
        }
    }

}