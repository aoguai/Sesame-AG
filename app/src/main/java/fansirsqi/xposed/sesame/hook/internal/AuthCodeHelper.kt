package fansirsqi.xposed.sesame.hook.internal

import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.util.Log
import java.util.HashMap

/**
 * OAuth2 授权码服务助手类
 * 用于调用目标应用的 OpenAuthExtension.getAuthCode 方法
 */
object AuthCodeHelper {

    private const val TAG = "Oauth2AuthCodeHelper"
    private var classLoader: ClassLoader? = null

    /**
     * 初始化 Oauth2AuthCodeHelper
     * @param loader 应用类加载器
     */
    fun init(loader: ClassLoader) {
        classLoader = loader
        Log.record(TAG, "Oauth2AuthCodeHelper 初始化完成")
    }


    /**
     * 主动调用获取授权码
     * 通过反射调用 Oauth2AuthCodeService.getAuthSkipResult 方法获取授权码
     *
     * @param appId 应用ID
     * @return code，失败返回null
     */
    fun getAuthCode(
        appId: String
    ): String? {
        try {
            if (classLoader == null) {
                Log.error(TAG, "Oauth2AuthCodeHelper 未初始化，请先调用 init 方法")
                return null
            }
            val oauth2AuthCodeServiceImplClass = XposedHelpers.findClass("com.alibaba.ariver.rpc.biz.proxy.Oauth2AuthCodeServiceImpl", classLoader)
            val oauth2AuthCodeServiceImpl = XposedHelpers.newInstance(oauth2AuthCodeServiceImplClass)
            val authSkipRequestModelClass = XposedHelpers.findClass("com.alibaba.ariver.permission.openauth.model.request.AuthSkipRequestModel", classLoader)
            val authSkipRequestModel = XposedHelpers.newInstance(authSkipRequestModelClass)
            XposedHelpers.callMethod(authSkipRequestModel, "setAppId", appId)
            XposedHelpers.callMethod(authSkipRequestModel, "setCurrentPageUrl", "https://${appId}.hybrid.alipay-eco.com/index.html")
            XposedHelpers.callMethod(authSkipRequestModel, "setFromSystem", "mobilegw_android")
            XposedHelpers.callMethod(authSkipRequestModel, "setScopeNicks", listOf("auth_base"))
            XposedHelpers.callMethod(authSkipRequestModel, "setState", "QnJpbmcgc21hbGwgYW5kIGJlYXV0aWZ1bCBjaGFuZ2VzIHRvIHRoZSB3b3JsZA==")
            XposedHelpers.callMethod(authSkipRequestModel, "setIsvAppId", "")
            XposedHelpers.callMethod(authSkipRequestModel, "setExtInfo", HashMap<String, String>())
            val appExtInfo = HashMap<String, String>()
            appExtInfo["channel"] = "tinyapp"
            appExtInfo["clientAppId"] = appId
            XposedHelpers.callMethod(authSkipRequestModel, "setAppExtInfo", appExtInfo)
            val authSkipResult = XposedHelpers.callMethod(
                oauth2AuthCodeServiceImpl,
                "getAuthSkipResult",
                "AP",
                null,
                authSkipRequestModel
            )

            if (authSkipResult != null) {
                // 直接从返回的 AuthSkipResultModel 获取 authExecuteResult
                val authExecuteResult = XposedHelpers.callMethod(authSkipResult, "getAuthExecuteResult")
                if (authExecuteResult != null) {
                    val authCode = XposedHelpers.callMethod(authExecuteResult, "getAuthCode") as? String
                    return authCode
                }
            }

            return null
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "主动调用获取授权码失败: ${e.message}", e)
            return null
        }
    }

}
