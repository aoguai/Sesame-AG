package fansirsqi.xposed.sesame.hook

import android.annotation.SuppressLint
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import fansirsqi.xposed.sesame.BuildConfig
import fansirsqi.xposed.sesame.SesameApplication
import fansirsqi.xposed.sesame.data.Config
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.Status.Companion.load
import fansirsqi.xposed.sesame.data.Status.Companion.save
import fansirsqi.xposed.sesame.entity.AlipayVersion
import fansirsqi.xposed.sesame.hook.Toast.show
import fansirsqi.xposed.sesame.hook.TokenHooker.start
import fansirsqi.xposed.sesame.hook.XposedEnv.processName
import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fansirsqi.xposed.sesame.hook.internal.LocationHelper
import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fansirsqi.xposed.sesame.hook.internal.SecurityBodyHelper
import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager
import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager.cleanup
import fansirsqi.xposed.sesame.hook.keepalive.SmartSchedulerManager.schedule
import fansirsqi.xposed.sesame.hook.rpc.bridge.NewRpcBridge
import fansirsqi.xposed.sesame.hook.rpc.bridge.OldRpcBridge
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcBridge
import fansirsqi.xposed.sesame.hook.rpc.bridge.RpcVersion
import fansirsqi.xposed.sesame.hook.rpc.debug.DebugRpc
import fansirsqi.xposed.sesame.hook.rpc.intervallimit.RpcIntervalLimit.clearIntervalLimit
import fansirsqi.xposed.sesame.hook.server.ModuleHttpServerManager.startIfNeeded
import fansirsqi.xposed.sesame.model.BaseModel.Companion.batteryPerm
import fansirsqi.xposed.sesame.model.BaseModel.Companion.checkInterval
import fansirsqi.xposed.sesame.model.BaseModel.Companion.debugMode
import fansirsqi.xposed.sesame.model.BaseModel.Companion.destroyData
import fansirsqi.xposed.sesame.model.BaseModel.Companion.execAtTimeList
import fansirsqi.xposed.sesame.model.BaseModel.Companion.manualTriggerAutoSchedule
import fansirsqi.xposed.sesame.model.BaseModel.Companion.newRpc
import fansirsqi.xposed.sesame.model.BaseModel.Companion.sendHookData
import fansirsqi.xposed.sesame.model.BaseModel.Companion.sendHookDataUrl
import fansirsqi.xposed.sesame.model.BaseModel.Companion.wakenAtTimeList
import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.CoroutineTaskRunner
import fansirsqi.xposed.sesame.task.MainTask
import fansirsqi.xposed.sesame.task.ModelTask.Companion.stopAllTask
import fansirsqi.xposed.sesame.task.antForest.AntForest
import fansirsqi.xposed.sesame.task.customTasks.CustomTask
import fansirsqi.xposed.sesame.task.customTasks.ManualTask
import fansirsqi.xposed.sesame.task.customTasks.ManualTaskModel
import fansirsqi.xposed.sesame.util.DataStore.init
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.GlobalThreadPools.execute
import fansirsqi.xposed.sesame.util.GlobalThreadPools.shutdownAndRestart
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Log.error
import fansirsqi.xposed.sesame.util.Log.printStackTrace
import fansirsqi.xposed.sesame.util.Log.record
import fansirsqi.xposed.sesame.util.ModuleStatus
import fansirsqi.xposed.sesame.util.Notify
import fansirsqi.xposed.sesame.util.Notify.stop
import fansirsqi.xposed.sesame.util.Notify.updateStatusText
import fansirsqi.xposed.sesame.util.PermissionUtil
import fansirsqi.xposed.sesame.util.PermissionUtil.checkBatteryPermissions
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.WorkflowRootGuard
import fansirsqi.xposed.sesame.util.maps.UserMap
import fansirsqi.xposed.sesame.util.maps.UserMap.currentUid
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.lang.AutoCloseable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

class ApplicationHook {
    var xposedInterface: XposedInterface? = null

    private class TaskLock : AutoCloseable {
        private val acquired: Boolean

        init {
            synchronized(taskLock) {
                if (isTaskRunning) {
                    acquired = false
                    throw IllegalStateException("任务已在运行中")
                }
                isTaskRunning = true
                acquired = true
            }
        }

        override fun close() {
            if (acquired) {
                synchronized(taskLock) {
                    isTaskRunning = false
                }
            }
        }
    }

    // --- 入口方法 ---
    fun loadPackage(lpparam: PackageLoadedParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        handleHookLogic(
            lpparam.classLoader,
            lpparam.packageName,
            lpparam.applicationInfo.sourceDir,
            lpparam
        )
    }

    fun loadPackageCompat(lpparam: LoadPackageParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        val apkPath: String = (if (lpparam.appInfo != null) lpparam.appInfo.sourceDir else null)!!
        handleHookLogic(lpparam.classLoader, lpparam.packageName, apkPath, lpparam)
    }

    @SuppressLint("PrivateApi")
    private fun handleHookLogic(loader: ClassLoader?, packageName: String, apkPath: String, rawParam: Any?) {
        classLoader = loader
        // 1. 初始化配置读取
        val prefs = XSharedPreferences(General.MODULE_PACKAGE_NAME, SesameApplication.PREFERENCES_KEY)
        prefs.makeWorldReadable()

        // 2. 进程检查
        resolveProcessName(rawParam)
        if (!shouldHookProcess()) return

        init(Files.CONFIG_DIR)
        if (isHooked) return
        isHooked = true

        // 3. 基础环境 Hook
        val framework = ModuleStatus.detectFramework(classLoader!!)
        ModuleStatusReporter.updateNow(framework = framework, packageName = packageName, reason = "hook_detect")
        VersionHook.installHook(classLoader)
        initReflection(classLoader!!)

        // 4. 核心生命周期 Hook
        hookApplicationAttach(packageName)
        hookLauncherResume()
        hookLoginResume()
        hookServiceLifecycle(apkPath)

        HookUtil.hookOtherService(classLoader!!)
    }

    private fun resolveProcessName(rawParam: Any?) {
        if (rawParam is LoadPackageParam) {
            finalProcessName = rawParam.processName
        } else if (rawParam is PackageLoadedParam) {
            finalProcessName = processName
        }
    }

    private fun shouldHookProcess(): Boolean {
        val isMainProcess = General.PACKAGE_NAME == finalProcessName
        return isMainProcess
//            record(TAG, "跳过辅助进程: $finalProcessName")
    }

    private fun initReflection(loader: ClassLoader) {
        try {
            XposedHelpers.findClass(ApplicationHookConstants.AlipayClasses.APPLICATION, loader)
            XposedHelpers.findClass(ApplicationHookConstants.AlipayClasses.SOCIAL_SDK, loader)
        } catch (_: Throwable) {
            // ignore
        }

        try {
            @SuppressLint("PrivateApi") val loadedApkClass = loader.loadClass(ApplicationHookConstants.AlipayClasses.LOADED_APK)
            deoptimizeClass(loadedApkClass)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun hookApplicationAttach(packageName: String?) {
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        appContext = param.args[0] as Context?
                        mainHandler = Handler(Looper.getMainLooper())
                        Log.init(appContext!!)
                        ensureScheduler()

                        SecurityBodyHelper.init(classLoader!!)
                        AlipayMiniMarkHelper.init(classLoader!!)
                        LocationHelper.init(classLoader!!)
                        AuthCodeHelper.init(classLoader!!)

                        initVersionInfo(packageName)
                        // 特殊版本处理
                        if (VersionHook.hasVersion() && alipayVersion.compareTo(AlipayVersion("10.7.26.8100")) == 0) {
                            HookUtil.bypassAccountLimit(classLoader!!)
                        }
                    }
                })
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "Hook attach failed", e)
        }
    }

    private fun hookLauncherResume() {
        try {
            XposedHelpers.findAndHookMethod(
                ApplicationHookConstants.AlipayClasses.LAUNCHER_ACTIVITY,
                classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        ApplicationHookConstants.submitEntry("launcher_onResume") {
                            val targetUid = HookUtil.getUserId(classLoader!!) ?: run {
                                show("用户未登录")
                                return@submitEntry
                            }

                            if (!init) {
                                if (initHandler("onResume")) init = true
                                return@submitEntry
                            }

                            val currentUid = currentUid
                            if (targetUid != currentUid) {
                                if (currentUid != null) {
                                    initHandler("user_switch")
                                    lastExecTime = 0
                                    show("用户已切换")
                                    return@submitEntry
                                }
                                HookUtil.hookUser(classLoader!!)
                            }

                            // 访问被拒绝/需要滑块验证后，用户手动完成验证并回到首页时会触发 onResume。
                            // 这里自动退出离线状态并恢复执行链路，避免长期卡在离线模式无法继续任务。
                            // 注意：手动开启离线（reason=null 且 untilMs<=0）不应被自动退出。
                            var recoveredFromOffline = false
                            if (ApplicationHookConstants.isOffline()) {
                                val reason = ApplicationHookConstants.offlineReason
                                val untilMs = ApplicationHookConstants.offlineUntilMs
                                val now = System.currentTimeMillis()
                                val cooldownExpired = untilMs > 0L && now >= untilMs

                                val lastReopenAt = lastReOpenAppLaunchAtMs
                                val autoResumeByReopen =
                                    reason == "auth_like" &&
                                        !cooldownExpired &&
                                        lastReopenAt > 0L &&
                                        (now - lastReopenAt) in 0..AUTH_LIKE_AUTO_RECOVER_GUARD_MS
                                if (autoResumeByReopen) {
                                    val offlineEnterAtMs = ApplicationHookConstants.lastOfflineEnterAtMs
                                    if (authLikeReopenGuardOfflineEnterAtMs != offlineEnterAtMs) {
                                        authLikeReopenGuardOfflineEnterAtMs = offlineEnterAtMs
                                        authLikeReopenGuardCountedReopenAtMs = 0L
                                        authLikeReopenGuardReopenCount = 0
                                    }

                                    if (lastReopenAt != authLikeReopenGuardCountedReopenAtMs) {
                                        authLikeReopenGuardCountedReopenAtMs = lastReopenAt
                                        authLikeReopenGuardReopenCount++
                                    }

                                    if (authLikeReopenGuardReopenCount <= AUTH_LIKE_AUTO_RECOVER_GUARD_IGNORE_REOPEN_COUNT) {
                                        record(
                                            TAG,
                                            "检测到 auth_like 离线，但 onResume 由 reOpenApp 触发(${now - lastReopenAt}ms)，保持离线等待用户完成验证(#$authLikeReopenGuardReopenCount)"
                                        )
                                        return@submitEntry
                                    }

                                    record(TAG, "检测到 auth_like 离线，但已多次 reOpenApp(#$authLikeReopenGuardReopenCount)，尝试自动恢复执行")
                                }
                                val shouldRecover = cooldownExpired || when (reason) {
                                    "auth_like",
                                    "system_busy",
                                    "login_timeout",
                                    "network_error_threshold",
                                    "rpc_error_threshold" -> true
                                    else -> false
                                }

                                if (shouldRecover) {
                                    record(TAG, "检测到 ${reason ?: "unknown"} 离线状态，尝试在 onResume 退出离线并恢复任务执行")
                                    ApplicationHookConstants.setOffline(false)
                                    SmartSchedulerManager.cancelNamedTask("重新登录")
                                    // 避免快速返回App被 2s 间隔保护挡住而无法立刻恢复
                                    lastExecTime = 0

                                    val statusMsg = when (reason) {
                                        "auth_like" -> "✅ 验证已解除，恢复执行"
                                        "system_busy" -> "✅ 已解除系统繁忙/验证态，恢复执行"
                                        "login_timeout" -> "✅ 登录已恢复，继续执行"
                                        else -> "✅ 离线已解除，恢复执行"
                                    }
                                    updateStatusText(statusMsg)

                                    val triggerReason =
                                        if (reason == "auth_like") "auth_like_recovered"
                                        else "offline_recovered:${reason ?: "unknown"}"
                                    val dedupeKey =
                                        if (reason == "auth_like") "auth_like_recovered"
                                        else "offline_recovered"

                                    // 离线恢复时，旧的执行链路可能仍在跑（队列里也可能有 pending trigger）。
                                    // 主动取消当前主任务与子任务，让恢复触发能尽快生效。
                                    val runningMainTask = mainTask
                                    if (runningMainTask?.isRunning == true) {
                                        record(TAG, "🔄 离线恢复：取消当前主任务以便立即恢复执行")
                                        runningMainTask.stopTask()
                                    }
                                    stopAllTask()
                                    ApplicationHookConstants.clearPendingTriggers("offline_recover")
                                    recoveredFromOffline = true

                                    ApplicationHookCore.requestExecution(
                                        ApplicationHookConstants.TriggerInfo(
                                            type = ApplicationHookConstants.TriggerType.ON_RESUME,
                                            priority = ApplicationHookConstants.TriggerPriority.HIGH,
                                            reason = triggerReason,
                                            dedupeKey = dedupeKey
                                        )
                                    )
                                }
                            }

                            val resumeAt = System.currentTimeMillis()
                            val recentlyReopenedByModule =
                                lastReOpenAppLaunchAtMs > 0L &&
                                    (resumeAt - lastReOpenAppLaunchAtMs) in 0..AUTH_LIKE_AUTO_RECOVER_GUARD_MS
                            if (!recentlyReopenedByModule && !recoveredFromOffline && manualTriggerAutoSchedule.value == true) {
                                record(TAG, "检测到手动回到目标应用，补触发一次任务执行")
                                ApplicationHookCore.requestExecution(
                                    ApplicationHookConstants.TriggerInfo(
                                        type = ApplicationHookConstants.TriggerType.ON_RESUME,
                                        priority = ApplicationHookConstants.TriggerPriority.NORMAL,
                                        reason = "manual_on_resume",
                                        dedupeKey = "manual_on_resume"
                                    )
                                )
                            }
                        }
                    }
                })
        } catch (t: Throwable) {
            printStackTrace(TAG, "Hook Launcher failed", t)
        }
    }

    /**
     * AlipayLogin 的 onResume 在部分版本/场景下比 LauncherActivity 更稳定（reOpenApp 也会显式拉起该 Activity）。
     * 用于兜底：当出现“需要验证/访问被拒绝”进入离线模式后，用户手动完成验证再回到 App 时可自动退出离线恢复任务链路。
     */
    private fun hookLoginResume() {
        try {
            XposedHelpers.findAndHookMethod(
                General.CURRENT_USING_ACTIVITY,
                classLoader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        ApplicationHookConstants.submitEntry("login_onResume") {
                            if (!init) return@submitEntry
                            if (!ApplicationHookConstants.isOffline()) return@submitEntry

                            val reason = ApplicationHookConstants.offlineReason
                            val untilMs = ApplicationHookConstants.offlineUntilMs
                            val now = System.currentTimeMillis()
                            val cooldownExpired = untilMs > 0L && now >= untilMs

                            val lastReopenAt = lastReOpenAppLaunchAtMs
                            val autoResumeByReopen =
                                reason == "auth_like" &&
                                    !cooldownExpired &&
                                    lastReopenAt > 0L &&
                                    (now - lastReopenAt) in 0..AUTH_LIKE_AUTO_RECOVER_GUARD_MS
                            if (autoResumeByReopen) {
                                val offlineEnterAtMs = ApplicationHookConstants.lastOfflineEnterAtMs
                                if (authLikeReopenGuardOfflineEnterAtMs != offlineEnterAtMs) {
                                    authLikeReopenGuardOfflineEnterAtMs = offlineEnterAtMs
                                    authLikeReopenGuardCountedReopenAtMs = 0L
                                    authLikeReopenGuardReopenCount = 0
                                }

                                if (lastReopenAt != authLikeReopenGuardCountedReopenAtMs) {
                                    authLikeReopenGuardCountedReopenAtMs = lastReopenAt
                                    authLikeReopenGuardReopenCount++
                                }

                                if (authLikeReopenGuardReopenCount <= AUTH_LIKE_AUTO_RECOVER_GUARD_IGNORE_REOPEN_COUNT) {
                                    record(
                                        TAG,
                                        "检测到 auth_like 离线，但 login.onResume 由 reOpenApp 触发(${now - lastReopenAt}ms)，保持离线等待用户完成验证(#$authLikeReopenGuardReopenCount)"
                                    )
                                    return@submitEntry
                                }

                                record(TAG, "检测到 auth_like 离线，但已多次 reOpenApp(#$authLikeReopenGuardReopenCount)，尝试自动恢复执行")
                            }
                            val shouldRecover = cooldownExpired || when (reason) {
                                "auth_like",
                                "system_busy",
                                "login_timeout",
                                "network_error_threshold",
                                "rpc_error_threshold" -> true
                                else -> false
                            }

                            if (!shouldRecover) return@submitEntry

                            record(TAG, "检测到 ${reason ?: "unknown"} 离线状态，尝试在 login.onResume 退出离线并恢复任务执行")
                            ApplicationHookConstants.setOffline(false)
                            SmartSchedulerManager.cancelNamedTask("重新登录")
                            lastExecTime = 0

                            val statusMsg = when (reason) {
                                "auth_like" -> "✅ 验证已解除，恢复执行"
                                "system_busy" -> "✅ 已解除系统繁忙/验证态，恢复执行"
                                "login_timeout" -> "✅ 登录已恢复，继续执行"
                                else -> "✅ 离线已解除，恢复执行"
                            }
                            updateStatusText(statusMsg)

                            val triggerReason =
                                if (reason == "auth_like") "auth_like_recovered"
                                else "offline_recovered:${reason ?: "unknown"}"
                            val dedupeKey =
                                if (reason == "auth_like") "auth_like_recovered"
                                else "offline_recovered"

                            // 离线恢复时，旧的执行链路可能仍在跑（队列里也可能有 pending trigger）。
                            // 主动取消当前主任务与子任务，让恢复触发能尽快生效。
                            val runningMainTask = mainTask
                            if (runningMainTask?.isRunning == true) {
                                record(TAG, "🔄 离线恢复：取消当前主任务以便立即恢复执行")
                                runningMainTask.stopTask()
                            }
                            stopAllTask()
                            ApplicationHookConstants.clearPendingTriggers("offline_recover")

                            ApplicationHookCore.requestExecution(
                                ApplicationHookConstants.TriggerInfo(
                                    type = ApplicationHookConstants.TriggerType.ON_RESUME,
                                    priority = ApplicationHookConstants.TriggerPriority.HIGH,
                                    reason = triggerReason,
                                    dedupeKey = dedupeKey
                                )
                            )
                        }
                    }
                })
        } catch (t: Throwable) {
            printStackTrace(TAG, "Hook Login failed", t)
        }
    }

    private fun hookServiceLifecycle(apkPath: String) {
        try {
            XposedHelpers.findAndHookMethod(ApplicationHookConstants.AlipayClasses.SERVICE, classLoader, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val appService = param.thisObject as Service
                    if (General.CURRENT_USING_SERVICE != appService.javaClass.getCanonicalName()) {
                        return
                    }

                    service = appService
                    appContext = appService.applicationContext
                    ensureScheduler()

                    mainTask = MainTask("主任务") { runMainTaskLogic() }
                    dayCalendar = Calendar.getInstance()
                    val initReason = pendingInitReason ?: "service_onCreate"
                    if (!init || pendingInit) {
                        if (initHandler(initReason)) {
                            init = true
                        }
                    } else {
                        // 已经初始化过，避免重复初始化导致重复 Toast、重置线程池等副作用
                        pendingInit = false
                        pendingInitReason = null
                    }
                }
            })

            XposedHelpers.findAndHookMethod(ApplicationHookConstants.AlipayClasses.SERVICE, classLoader, "onDestroy", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val s = param.thisObject as Service
                    if (General.CURRENT_USING_SERVICE == s.javaClass.getCanonicalName()) {
                        updateStatusText("目标应用前台服务被销毁")
                        destroyHandler()
                        service = null
                        mainTask = null
                        restartByBroadcast()
                    }
                }
            })
        } catch (t: Throwable) {
            printStackTrace(TAG, "Hook Service failed", t)
        }
    }

    private fun initVersionInfo(packageName: String?) {
        if (VersionHook.hasVersion()) {
            alipayVersion = VersionHook.getCapturedVersion() ?: AlipayVersion("")
            record(TAG, "📦 目标应用版本(Hook): $alipayVersion")
        } else {
            try {
                val pInfo: PackageInfo = appContext!!.packageManager.getPackageInfo(packageName!!, 0)
                alipayVersion = AlipayVersion(pInfo.versionName.toString())
            } catch (_: Exception) {
                alipayVersion = AlipayVersion("")
            }
        }
    }

    // --- 广播接收器 ---
    internal class AlipayBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action ?: return

            if (finalProcessName != null && finalProcessName!!.endsWith(":widgetProvider")) {
                return  // 忽略小组件进程
            }

            when (action) {
                ApplicationHookConstants.BroadcastActions.RESTART -> {
                    val safeIntent = Intent(intent)
                    ApplicationHookConstants.submitEntry("broadcast_restart") {
                        val targetUserId = safeIntent.getStringExtra("userId")
                        val currentUserId = HookUtil.getUserId(classLoader!!)
                        if (targetUserId != null && targetUserId != currentUserId) {
                            record(TAG, "忽略非当前用户的重启广播: target=$targetUserId, current=$currentUserId")
                            return@submitEntry
                        }
                        initHandler("broadcast_restart")
                    }
                }

                ApplicationHookConstants.BroadcastActions.EXECUTE -> handleExecuteBroadcast(context, intent)

                ApplicationHookConstants.BroadcastActions.PRE_WAKEUP -> handlePreWakeupBroadcast(context, intent)

                ApplicationHookConstants.BroadcastActions.RE_LOGIN -> reOpenApp()
                ApplicationHookConstants.BroadcastActions.RPC_TEST -> handleRpcTest(intent)
                ApplicationHookConstants.BroadcastActions.MANUAL_TASK -> {
                    record(TAG, "🚀 收到手动庄园任务指令")
                    execute {
                        val taskName = intent.getStringExtra("task")
                        if (taskName != null) {
                            val normalizedTaskName = taskName.replace("+", "_")
                            try {
                                val task = CustomTask.valueOf(normalizedTaskName)
                                val extraParams = HashMap<String, Any>()
                                when (task) {
                                    CustomTask.FOREST_WHACK_MOLE -> {
                                        extraParams["whackMoleMode"] = intent.getIntExtra("whackMoleMode", 1)
                                        extraParams["whackMoleGames"] = intent.getIntExtra("whackMoleGames", 5)
                                    }

                                    CustomTask.FOREST_ENERGY_RAIN -> {
                                        extraParams["exchangeEnergyRainCard"] = intent.getBooleanExtra("exchangeEnergyRainCard", false)
                                    }

                                    CustomTask.FARM_SPECIAL_FOOD -> {
                                        extraParams["specialFoodCount"] = intent.getIntExtra("specialFoodCount", 0)
                                    }

                                    CustomTask.FARM_USE_TOOL -> {
                                        extraParams["toolType"] = intent.getStringExtra("toolType") ?: ""
                                        extraParams["toolCount"] = intent.getIntExtra("toolCount", 1)
                                    }

                                    else -> {
                                        record(TAG, "❌ 无效的任务指令: $taskName")
                                    }
                                }
                                if (!WorkflowRootGuard.hasRoot(forceRefresh = true, reason = "manual_task")) {
                                    record(TAG, "⛔ 未检测到 Root 权限，忽略手动任务指令: $taskName")
                                    return@execute
                                }
                                ManualTask.runSingle(task, extraParams)
                            } catch (e: Exception) {
                                record(TAG, "❌ 无效的任务指令: $taskName -> ${e.message}")
                            }
                        } else {
                            if (!WorkflowRootGuard.hasRoot(forceRefresh = true, reason = "manual_task_model")) {
                                record(TAG, "⛔ 未检测到 Root 权限，忽略手动模型任务指令")
                                return@execute
                            }
                            for (model in Model.modelArray) {
                                if (model is ManualTaskModel) {
                                    model.startTask(true, 1)
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun handleExecuteBroadcast(context: Context?, intent: Intent) {
            val safeIntent = Intent(intent)
            val isAlarmTriggered = safeIntent.getBooleanExtra("alarm_triggered", false)
            val wakenAtTime = safeIntent.getBooleanExtra("waken_at_time", false)
            val wakenTime = safeIntent.getStringExtra("waken_time")?.trim().takeIf { !it.isNullOrBlank() }
            val normalizedWakenTime = if (wakenAtTime && wakenTime.isNullOrBlank()) "0000" else wakenTime

            val trigger = ApplicationHookConstants.TriggerInfo(
                type = ApplicationHookConstants.TriggerType.BROADCAST_EXECUTE,
                priority = if (isAlarmTriggered || wakenAtTime) {
                    ApplicationHookConstants.TriggerPriority.HIGH
                } else {
                    ApplicationHookConstants.TriggerPriority.NORMAL
                },
                alarmTriggered = isAlarmTriggered,
                wakenAtTime = wakenAtTime,
                wakenTime = normalizedWakenTime,
                reason = "broadcast_execute",
                dedupeKey = when {
                    wakenAtTime && !normalizedWakenTime.isNullOrBlank() -> "wakeup_$normalizedWakenTime"
                    isAlarmTriggered -> "alarm_execute"
                    else -> "broadcast_execute"
                }
            )

            ApplicationHookConstants.submitEntry("broadcast_execute") {
                if (!isReadyForExec()) {
                    record(TAG, "execute broadcast received but not ready: init=$init loaded=${Config.isLoaded()} service=${service != null}")
                }
                ApplicationHookCore.requestExecution(trigger)
            }
        }

        private fun handlePreWakeupBroadcast(context: Context?, intent: Intent) {
            val ctx = context?.applicationContext ?: context
            if (ctx == null) return

            val safeIntent = Intent(intent)
            val executionTimeMillis = safeIntent.getLongExtra("execution_time", 0L)
            val now = System.currentTimeMillis()
            val delayMillis = executionTimeMillis - now

            val triggerAtExecTime = ApplicationHookConstants.TriggerInfo(
                type = ApplicationHookConstants.TriggerType.BROADCAST_PREWAKEUP,
                priority = ApplicationHookConstants.TriggerPriority.HIGH,
                alarmTriggered = true,
                reason = if (executionTimeMillis > 0) "prewakeup_to_${TimeUtil.getCommonDate(executionTimeMillis)}" else "prewakeup",
                dedupeKey = if (executionTimeMillis > 0) "prewakeup_$executionTimeMillis" else "prewakeup"
            )

            SmartSchedulerManager.initialize(ctx)
            if (executionTimeMillis > 0 && delayMillis > 0) {
                record(TAG, "收到 prewakeup，计划在 ${TimeUtil.getCommonDate(executionTimeMillis)} 执行 (delay=${TimeUtil.formatDuration(delayMillis)})")
                schedule(delayMillis, "prewakeup_execute") {
                    ApplicationHookCore.requestExecution(triggerAtExecTime)
                }
                return
            }

            record(TAG, "收到 prewakeup，但 execution_time 无效/已过期，立即触发一次执行链路")
            ApplicationHookCore.requestExecution(triggerAtExecTime)
        }

        private fun handleRpcTest(intent: Intent) {
            execute({
                record(TAG, "RPC测试: $intent")
                try {
                    val rpc = DebugRpc()
                    rpc.start(
                        intent.getStringExtra("method") ?: "",
                        intent.getStringExtra("data") ?: "",
                        intent.getStringExtra("type") ?: ""
                    )
                } catch (_: Throwable) { /* ignore */
                }
            })
        }
    }

    companion object {
        const val TAG: String = "ApplicationHook" // 简化TAG
        var finalProcessName: String? = ""

        // 广播接收器实例，用于注销
        private var mBroadcastReceiver: AlipayBroadcastReceiver? = null

        @JvmField
        var classLoader: ClassLoader? = null

        @JvmField
        @get:JvmStatic
        @Volatile
        var appContext: Context? = null

        // 任务锁
        private val taskLock = Any()

        @Volatile
        private var isTaskRunning = false

        @JvmStatic
        var alipayVersion: AlipayVersion = AlipayVersion("")

        @get:JvmStatic
        @Volatile
        var isHooked: Boolean = false
            private set

        @Volatile
        private var init = false

        @Volatile
        private var pendingInit = false

        @Volatile
        private var pendingInitReason: String? = null

        @Volatile
        private var rootCheckInProgress = false

        @Volatile
        var dayCalendar: Calendar?

        @Volatile
        private var batteryPermissionChecked = false

        @SuppressLint("StaticFieldLeak")
        @Volatile
        var service: Service? = null

        var mainHandler: Handler? = null

        var mainTask: MainTask? = null

        internal fun isReadyForExec(): Boolean {
            return init && Config.isLoaded() && service != null && WorkflowRootGuard.hasGrantedRoot()
        }

        @Volatile
        var rpcBridge: RpcBridge? = null
        private val rpcBridgeLock = Any()

        private var rpcVersion: RpcVersion? = null

        @Volatile
        var lastExecTime: Long = 0

        @Volatile
        private var lastReOpenAppLaunchAtMs: Long = 0L

        private const val AUTH_LIKE_AUTO_RECOVER_GUARD_MS: Long = 1500L

        private const val AUTH_LIKE_AUTO_RECOVER_GUARD_IGNORE_REOPEN_COUNT: Int = 1

        @Volatile
        private var authLikeReopenGuardOfflineEnterAtMs: Long = 0L

        @Volatile
        private var authLikeReopenGuardCountedReopenAtMs: Long = 0L

        @Volatile
        private var authLikeReopenGuardReopenCount: Int = 0

        @Volatile
        var nextExecutionTime: Long = 0

        // Deoptimize 方法缓存
        private val deoptimizeMethod: Method?

        init {
            dayCalendar = Calendar.getInstance()
            ApplicationHookUtils.resetToMidnight(dayCalendar!!)
            var m: Method? = null
            try {
                m = XposedBridge::class.java.getDeclaredMethod("deoptimizeMethod", Member::class.java)
            } catch (_: Throwable) {
            }
            deoptimizeMethod = m
        }

        private suspend fun runMainTaskLogic() = withContext(Dispatchers.IO) {
            try {
                TaskLock().use { _ ->
                    if (!init || !Config.isLoaded()) return@withContext
                    if (!WorkflowRootGuard.hasRoot(forceRefresh = true, reason = "main_task")) {
                        record(TAG, "⛔ Root 权限不可用，终止主任务执行")
                        ApplicationHookConstants.clearPendingTriggers("root_denied")
                        destroyHandler()
                        return@withContext
                    }

                    val trigger = ApplicationHookConstants.consumePendingTrigger()
                    record(TAG, "🎯 本次执行触发: ${trigger?.summary() ?: "<none>"}")

                    val currentTime = System.currentTimeMillis()
                    val elapsedSinceLastExec = currentTime - lastExecTime
                    if (elapsedSinceLastExec < 2000) {
                        record(TAG, "⚠️ 间隔过短，跳过")
                        // 间隔保护仅用于防抖，重试应尽快（补足到 2s），而不是跟随执行间隔（如 50min）
                        val retryDelayMs = (2000L - elapsedSinceLastExec).coerceAtLeast(200L)
                        schedule(retryDelayMs, "间隔重试") {
                            ApplicationHookEntry.onIntervalRetry()
                        }
                        return@withContext
                    }

                    val currentUid = currentUid
                    val targetUid = HookUtil.getUserId(classLoader!!)
                    if (targetUid == null || targetUid != currentUid) {
                        reOpenApp()
                        return@withContext
                    }

                    lastExecTime = currentTime

                    val models = Model.modelArray.filterNotNull()
                    CoroutineTaskRunner(models).run(isFirst = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                record(TAG, "⚠️ " + e.message)
            } catch (e: Exception) {
                Log.printStackTrace(TAG, e)
            }
        }

        // --- 辅助方法 ---
        private fun ensureScheduler() {
            if (appContext != null) {
                SmartSchedulerManager.initialize(appContext!!)
            }
        }

        @Throws(InvocationTargetException::class, IllegalAccessException::class)
        fun deoptimizeClass(c: Class<*>) {
            if (deoptimizeMethod == null) return
            for (m in c.getDeclaredMethods()) {
                if (m.name == "makeApplicationInner") {
                    deoptimizeMethod.invoke(null, m)
                }
            }
        }


        fun scheduleNextExecutionInternal(lastTime: Long) {
            try {
                checkInactiveTime()
                val checkInterval = checkInterval.value ?: 0
                val execAtTimeList = execAtTimeList.value
                if (execAtTimeList != null && execAtTimeList.contains("-1")) {
                    record(TAG, "定时执行未开启")
                    return
                }
                var delayMillis = checkInterval.toLong()
                var targetTime: Long = 0
                if (execAtTimeList != null) {
                    val lastCal = TimeUtil.getCalendarByTimeMillis(lastTime)
                    val nextCal = TimeUtil.getCalendarByTimeMillis(lastTime + checkInterval.toLong())
                    for (timeStr in execAtTimeList) {
                        val execCal = TimeUtil.getTodayCalendarByTimeStr(timeStr)
                        if (execCal != null && lastCal < execCal && nextCal > execCal) {
                            record(TAG, "设置定时执行:$timeStr")
                            targetTime = execCal.getTimeInMillis()
                            delayMillis = targetTime - lastTime
                            break
                        }
                    }
                }
                nextExecutionTime = if (targetTime > 0) targetTime else (lastTime + delayMillis)
                ensureScheduler()
                schedule(delayMillis, "轮询任务") {
                    ApplicationHookEntry.onPollAlarm()
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "scheduleNextExecution failed", e)
            }
        }

        // --- 初始化核心逻辑 ---
        @Synchronized
        private fun initHandler(reason: String): Boolean {
            try {
                // 启动阶段可能出现 Service.onCreate 与 Launcher.onResume 并发触发 initHandler 的竞态：
                // - onResume 线程看到 init=false -> 进入 initHandler 等锁
                // - Service 线程完成初始化并将 init=true
                // - onResume 获锁后会因 init=true 走 destroyHandler()，导致二次初始化与双 Toast
                //
                // 对于“启动类触发”(service_onCreate/onResume)且当前已就绪的场景，直接判定为重复触发并跳过。
                if (init && isReadyForExec() && (reason == "onResume" || reason == "service_onCreate")) {
                    pendingInit = false
                    pendingInitReason = null
                    record(TAG, "✅ 已初始化完成，忽略重复初始化触发: $reason")
                    return true
                }

                if (init) destroyHandler()

                // 初始化广播（RPC 调试 / 手动任务等功能依赖）
                try {
                    registerBroadcastReceiver(appContext!!)
                } catch (_: Throwable) { /* ignore */ }

                ensureScheduler()
                Model.initAllModel()

                if (service == null) {
                    pendingInit = true
                    pendingInitReason = reason
                    record(TAG, "⏳ Service 未就绪，延后初始化: $reason")
                    return false
                }
                pendingInit = false
                pendingInitReason = null

                val userId = HookUtil.getUserId(classLoader!!)
                if (userId == null) {
                    show("用户未登录")
                    return false
                }

                HookUtil.hookUser(classLoader!!)
                record(TAG, "芝麻粒-AG 开始初始化...")
                if (!ensureRootAccessForWorkflow(reason)) {
                    return false
                }

                Config.load(userId)
                if (!Config.isLoaded()) return false

                // Phase 7：DataStore watcher 生命周期治理（用户切换/重载后重启 watcher，避免丢失跨进程同步能力）
                try {
                    init(Files.CONFIG_DIR)
                } catch (_: Throwable) { /* ignore */ }

                // 仅在用户开启“抓包调试模式”时启动调试 HTTP 服务（release 也可用）
                try {
                    if (debugMode.value == true) {
                        startIfNeeded(8080, "ET3vB^#td87sQqKaY*eMUJXP", processName, General.PACKAGE_NAME)
                    } else {
                        fansirsqi.xposed.sesame.hook.server.ModuleHttpServerManager.stop()
                    }
                } catch (_: Throwable) { /* ignore */ }

                Notify.start(service!!)
                setWakenAtTimeAlarm()

                synchronized(rpcBridgeLock) {
                    rpcBridge = if (newRpc.value == true) NewRpcBridge() else OldRpcBridge()
                    rpcBridge!!.load()
                    rpcVersion = rpcBridge!!.getVersion()
                }

                if (newRpc.value == true && debugMode.value == true) {
                    HookUtil.hookRpcBridgeExtension(classLoader!!, sendHookData.value == true, sendHookDataUrl.value ?: "")
                    HookUtil.hookDefaultBridgeCallback(classLoader!!)
                }

                start(userId)
                checkBatteryPermission()

                Model.bootAllModel(classLoader)
                load(userId)
                updateDay()

                val successMsg = "Loaded SesameTk " + BuildConfig.VERSION_NAME + "✨"
                record(successMsg)
                show(successMsg)

                ApplicationHookConstants.setOffline(false)
                init = true
                pendingInit = false
                pendingInitReason = null
                ModuleStatusReporter.requestUpdate(reason = "ready")
                ApplicationHookEntry.onInitCompleted(reason)
                return true
            } catch (th: Throwable) {
                printStackTrace(TAG, "startHandler", th)
                return false
            }
        }

        private fun checkBatteryPermission() {
            if (batteryPerm.value != true || batteryPermissionChecked) return

            val hasPermission = checkBatteryPermissions(appContext)
            batteryPermissionChecked = true
            if (!hasPermission) {
                record(TAG, "无后台运行权限，2秒后申请")
                mainHandler!!.postDelayed({
                    if (!PermissionUtil.checkOrRequestBatteryPermissions(appContext!!)) {
                        show("请授予目标应用始终在后台运行权限")
                    }
                }, 2000)
            }
        }

        @Synchronized
        fun destroyHandler() {
            try {
                init = false
                pendingInit = false
                pendingInitReason = null
                rootCheckInProgress = false
                lastExecTime = 0
                try {
                    fansirsqi.xposed.sesame.util.DataStore.shutdown()
                } catch (_: Throwable) {
                    // ignore
                }
                shutdownAndRestart()

                if (service != null) {
                    stopHandler()
                    destroyData()
                    Status.unload()
                    stop()
                    clearIntervalLimit()
                    Config.unload()
                    UserMap.unload()
                }

                cleanup()

                // 注销广播接收器
                unregisterBroadcastReceiver(appContext)

                synchronized(rpcBridgeLock) {
                    if (rpcBridge != null) {
                        rpcVersion = null
                        rpcBridge!!.unload()
                        rpcBridge = null
                    }
                    stopAllTask()
                }

                WorkflowRootGuard.invalidate()
            } catch (th: Throwable) {
                printStackTrace(TAG, "stopHandler err:", th)
            }
        }

        private fun ensureRootAccessForWorkflow(reason: String): Boolean {
            if (WorkflowRootGuard.hasGrantedRoot()) {
                pendingInit = false
                pendingInitReason = null
                return true
            }

            pendingInit = true
            pendingInitReason = reason
            if (rootCheckInProgress) {
                return false
            }

            rootCheckInProgress = true
            record(TAG, "⏳ 正在检查 Root 权限，暂不启动工作流: $reason")
            execute {
                try {
                    val granted = WorkflowRootGuard.hasRoot(forceRefresh = true, reason = reason)
                    if (!granted) {
                        updateStatusText("未检测到 Root 权限，已禁止工作流")
                        ApplicationHookConstants.clearPendingTriggers("root_denied")
                        return@execute
                    }

                    val retryReason = pendingInitReason ?: reason
                    rootCheckInProgress = false
                    if (service != null && !init) {
                        record(TAG, "✅ Root 权限检查通过，继续初始化: $retryReason")
                        if (initHandler(retryReason)) {
                            init = true
                        }
                    }
                } catch (th: Throwable) {
                    printStackTrace(TAG, "checkRootPermission", th)
                } finally {
                    rootCheckInProgress = false
                }
            }
            return false
        }

        private fun stopHandler() {
            if (mainTask != null) mainTask!!.stopTask()
            stopAllTask()
        }

        // --- 杂项方法 ---
        private fun checkInactiveTime() {
            if (lastExecTime == 0L) return
            val inactiveTime: Long = System.currentTimeMillis() - lastExecTime
            if (inactiveTime > ApplicationHookConstants.MAX_INACTIVE_TIME) {
                record(TAG, "⚠️ 检测到长时间未执行(" + inactiveTime / 60000 + "m)，重新登录")
                reOpenApp()
            }
        }

        fun updateDay() {
            val now = Calendar.getInstance()
            if (dayCalendar == null || dayCalendar!!.get(Calendar.DAY_OF_MONTH) != now.get(Calendar.DAY_OF_MONTH)) {
                dayCalendar = now.clone() as Calendar
                ApplicationHookUtils.resetToMidnight(dayCalendar!!)
                record(TAG, "日期更新")
                setWakenAtTimeAlarm()
            }
            try {
                save(now)
            } catch (_: Exception) {
            }
        }

        fun sendBroadcast(action: String?) {
            if (appContext != null) appContext!!.sendBroadcast(Intent(action))
        }

        fun sendBroadcastShell(api: String?, message: String?) {
            if (appContext == null) return
            val intent = Intent("fansirsqi.xposed.sesame.SHELL")
            intent.putExtra(api, message)
            appContext!!.sendBroadcast(intent, null)
        }

        @JvmStatic
        fun reLoginByBroadcast() {
            sendBroadcast(ApplicationHookConstants.BroadcastActions.RE_LOGIN)
        }

        fun restartByBroadcast() {
            sendBroadcast(ApplicationHookConstants.BroadcastActions.RESTART)
        }

        fun reOpenApp() {
            ensureScheduler()
            schedule(20000L, "重新登录") {
                try {
                    lastReOpenAppLaunchAtMs = System.currentTimeMillis()
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (appContext != null) appContext!!.startActivity(intent)
                } catch (e: Exception) {
                    error(TAG, "重启Activity失败: " + e.message)
                }
            }
        }

        // --- 定时唤醒 ---
        private fun setWakenAtTimeAlarm() {
            if (appContext == null) return
            ensureScheduler()

            val wakenAtTimeList = wakenAtTimeList.value
            if (wakenAtTimeList != null && wakenAtTimeList.contains("-1")) return

            // 1. 每日0点
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            ApplicationHookUtils.resetToMidnight(calendar)
            val delayToMidnight = calendar.getTimeInMillis() - System.currentTimeMillis()

                if (delayToMidnight > 0) {
                    schedule(delayToMidnight, "每日0点任务") {
                        record(TAG, "⏰ 0点任务触发")
                        updateDay()
                        ApplicationHookEntry.onWakeupMidnight()
                        setWakenAtTimeAlarm() // 递归设置明天
                    }
                }

            // 2. 自定义时间
            if (wakenAtTimeList != null) {
                val now = Calendar.getInstance()
                for (timeStr in wakenAtTimeList) {
                    try {
                        val target = TimeUtil.getTodayCalendarByTimeStr(timeStr)
                        if (target != null && target > now) {
                            val delay = target.getTimeInMillis() - System.currentTimeMillis()
                            schedule(delay, "自定义: $timeStr") {
                                record(TAG, "⏰ 自定义触发: $timeStr")
                                ApplicationHookEntry.onWakeupCustom(timeStr)
                            }
                        }
                    } catch (_: Exception) { /* ignore */
                    }
                }
            }
        }

        fun registerBroadcastReceiver(context: Context) {
            if (mBroadcastReceiver != null) return  // 防止重复注册

            try {
                mBroadcastReceiver = AlipayBroadcastReceiver()
                val filter = IntentFilter()
                filter.addAction(ApplicationHookConstants.BroadcastActions.RESTART)
                filter.addAction(ApplicationHookConstants.BroadcastActions.EXECUTE)
                filter.addAction(ApplicationHookConstants.BroadcastActions.PRE_WAKEUP)
                filter.addAction(ApplicationHookConstants.BroadcastActions.RE_LOGIN)
                filter.addAction(ApplicationHookConstants.BroadcastActions.RPC_TEST)
                filter.addAction(ApplicationHookConstants.BroadcastActions.MANUAL_TASK)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    ContextCompat.registerReceiver(
                        context,
                        mBroadcastReceiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                }
                record(TAG, "BroadcastReceiver registered")
            } catch (th: Throwable) {
                mBroadcastReceiver = null
                printStackTrace(TAG, "Register Receiver failed", th)
            }
        }

        fun unregisterBroadcastReceiver(context: Context?) {
            if (mBroadcastReceiver == null || context == null) return
            try {
                context.unregisterReceiver(mBroadcastReceiver)
                record(TAG, "BroadcastReceiver unregistered")
            } catch (_: Throwable) {
                // ignore: receiver not registered
            } finally {
                mBroadcastReceiver = null
            }
        }
    }
}
