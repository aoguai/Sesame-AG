package io.github.aoguai.sesameag.hook.libxposed

import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.hook.XposedEnv
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class HookEntry : XposedModule() {
    private val tag = "LibXposedEntry"
    private var processName = ""
    private var initialized = false
    private val customHooker = ApplicationHook()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        initialize(param.processName)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        try {
            if (!shouldHandlePackage(param.packageName)) return
            prepareEnv(param.packageName, param.applicationInfo, param.classLoader)
            customHooker.loadPackage(param)
            logInfo("Hooking ${param.packageName} in process $processName via onPackageReady")
        } catch (e: Throwable) {
            logError("Hook failed - ${e.message}", e)
        }
    }

    private fun initialize(processName: String) {
        if (initialized) return
        initialized = true
        this.processName = processName
        customHooker.xposedInterface = this

        logInfo("Initialized for process $processName")

        val detectedFrameworkName = runCatching { frameworkName }.getOrDefault("unknown")
        val detectedFrameworkVersion = runCatching { frameworkVersion }.getOrDefault("unknown")
        val detectedFrameworkVersionCode = runCatching { frameworkVersionCode }.getOrDefault(-1L)
        val moduleProcess = runCatching { moduleApplicationInfo.processName }.getOrDefault("unknown")
        logInfo(
            "Framework: $detectedFrameworkName $detectedFrameworkVersion $detectedFrameworkVersionCode api=$apiVersion target_model_process: $moduleProcess"
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

    private fun logInfo(message: String) {
        logWithPriority(Log.INFO, message, null)
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        logWithPriority(Log.ERROR, message, throwable)
    }

    private fun logWithPriority(priority: Int, message: String, throwable: Throwable?) {
        if (throwable != null) {
            log(priority, tag, message, throwable)
        } else {
            log(priority, tag, message)
        }
    }
}

