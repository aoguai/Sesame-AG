package fansirsqi.xposed.sesame.hook.lsp100

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import de.robv.android.xposed.XposedBridge
import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.XposedEnv
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class HookEntry() : XposedModule() {
    private val tag = "LsposedEntry"
    private var processName = ""
    private var initialized = false
    private val customHooker = ApplicationHook()

    // API 100 仍通过构造器传入框架接口和进程参数，这里保留旧签名避免直接断兼容。
    constructor(base: XposedInterface, param: ModuleLoadedParam) : this() {
        initialize(base, param.processName)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        initialize(this, param.processName)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        try {
            if (!shouldHandlePackage(param.packageName)) return
            val legacyClassLoader = extractLegacyPackageClassLoader(param)
            if (legacyClassLoader == null) {
                XposedBridge.log("$tag: onPackageLoaded ${param.packageName} only exposes default classloader, waiting for onPackageReady")
                return
            }
            prepareEnv(param.packageName, param.applicationInfo, legacyClassLoader)
            customHooker.loadPackage(param)
            XposedBridge.log("$tag: Hooking ${param.packageName} in process $processName via onPackageLoaded")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: Hook failed - ${e.message}")
            XposedBridge.log(e)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        try {
            if (!shouldHandlePackage(param.packageName)) return
            prepareEnv(param.packageName, param.applicationInfo, param.classLoader)
            customHooker.loadPackage(param)
            XposedBridge.log("$tag: Hooking ${param.packageName} in process $processName via onPackageReady")
        } catch (e: Throwable) {
            XposedBridge.log("$tag: Hook failed - ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun initialize(base: XposedInterface, processName: String) {
        if (initialized) return
        initialized = true
        this.processName = processName
        customHooker.xposedInterface = base

        XposedBridge.log("$tag: Initialized for process $processName")

        val frameworkName = runCatching { base.frameworkName }.getOrDefault("unknown")
        val frameworkVersion = runCatching { base.frameworkVersion }.getOrDefault("unknown")
        val frameworkVersionCode = runCatching { base.frameworkVersionCode }.getOrDefault(-1L)
        val moduleProcess = resolveModuleProcessName(base) ?: "unknown"
        XposedBridge.log(
            "$tag: Framework from base: $frameworkName $frameworkVersion $frameworkVersionCode target_model_process: $moduleProcess"
        )
    }

    private fun prepareEnv(packageName: String, applicationInfo: ApplicationInfo, classLoader: ClassLoader) {
        XposedEnv.classLoader = classLoader
        XposedEnv.appInfo = applicationInfo
        XposedEnv.packageName = packageName
        XposedEnv.processName = processName
    }

    private fun shouldHandlePackage(packageName: String): Boolean {
        return General.PACKAGE_NAME == packageName
    }

    private fun extractLegacyPackageClassLoader(param: PackageLoadedParam): ClassLoader? {
        return runCatching {
            param.javaClass.methods
                .firstOrNull { it.name == "getClassLoader" && it.parameterCount == 0 }
                ?.invoke(param) as? ClassLoader
        }.getOrNull()
    }

    private fun resolveModuleProcessName(base: XposedInterface): String? {
        return runCatching { base.moduleApplicationInfo.processName }.getOrNull()
            ?: runCatching {
                base.javaClass.methods
                    .firstOrNull { it.name == "getApplicationInfo" && it.parameterCount == 0 }
                    ?.invoke(base) as? ApplicationInfo
            }.getOrNull()?.processName
    }
}
