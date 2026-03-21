package io.github.aoguai.sesameag.util

import io.github.aoguai.sesameag.hook.ApplicationHook
import io.github.aoguai.sesameag.service.patch.SafeRootShell
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统一工作流执行权限门禁。
 *
 * 兼容旧 API 命名：`hasRoot/hasGrantedRoot` 现在表示“当前进程已被 Hook 注入或实时 Root 可用”。
 * 配置文件可以存在，但未通过此门禁时不允许进入运行态。
 */
object WorkflowRootGuard {
    private const val TAG = "WorkflowRootGuard"
    private const val CHECK_CACHE_WINDOW_MS = 3_000L
    private val checkMutex = Mutex()
    private val rootShell = SafeRootShell()

    @Volatile
    private var lastCheckAtMs: Long = 0L

    @Volatile
    private var lastGranted: Boolean = false

    @Volatile
    private var lastLoggedState: Boolean? = null

    fun hasGrantedRoot(): Boolean = lastGranted || resolveHookAccessSource() != null

    suspend fun hasRoot(forceRefresh: Boolean = false, reason: String? = null): Boolean {
        val now = System.currentTimeMillis()
        resolveHookAccessSource()?.let { hookSource ->
            lastCheckAtMs = now
            lastGranted = true
            logState(true, reason)
            Log.record(TAG, "✅ 当前进程已完成 $hookSource 注入，允许启动工作流")
            return true
        }

        if (!forceRefresh && now - lastCheckAtMs < CHECK_CACHE_WINDOW_MS) {
            return lastGranted
        }

        return checkMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            resolveHookAccessSource()?.let { hookSource ->
                lastCheckAtMs = lockedNow
                lastGranted = true
                logState(true, reason)
                Log.record(TAG, "✅ 当前进程已完成 $hookSource 注入，允许启动工作流")
                return@withLock true
            }
            if (!forceRefresh && lockedNow - lastCheckAtMs < CHECK_CACHE_WINDOW_MS) {
                return@withLock lastGranted
            }

            val granted = try {
                resolveRootAvailability(lockedNow)
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "检测执行权限失败", t)
                false
            }

            lastCheckAtMs = lockedNow
            lastGranted = granted
            logState(granted, reason)
            granted
        }
    }

    fun invalidate() {
        lastCheckAtMs = 0L
        lastGranted = false
    }

    private suspend fun resolveRootAvailability(nowMs: Long): Boolean {
        val classLoader = ApplicationHook.classLoader
        if (classLoader != null) {
            val detectedFramework = try {
                ModuleStatus.detectFramework(classLoader).trim()
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "当前进程框架识别失败", t)
                ""
            }
            val allowedByRuntimeFramework = isAllowedHookFramework(detectedFramework)
            Log.record(TAG, "🧩 当前进程框架识别: $detectedFramework")
            if (allowedByRuntimeFramework) {
                Log.record(TAG, "✅ 检测到当前进程由 $detectedFramework 注入，允许启动工作流")
                return true
            }
        } else {
            Log.record(TAG, "⚠️ 当前进程 classLoader 尚未就绪，继续进行实时 Root 探测")
        }

        val hasRoot = try {
            rootShell.isAvailable()
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "实时 Root 探测失败", t)
            false
        }
        Log.record(TAG, "🧪 实时 Root 探测结果: granted=$hasRoot at=$nowMs")
        return hasRoot
    }

    private fun resolveHookAccessSource(): String? {
        if (ApplicationHook.isHooked) {
            return resolveHookSourceLabel()
        }

        val classLoader = ApplicationHook.classLoader ?: return null
        val framework = try {
            ModuleStatus.detectFramework(classLoader).trim()
        } catch (_: Throwable) {
            return null
        }
        return framework.takeIf { isAllowedHookFramework(it) }
    }

    private fun resolveHookSourceLabel(): String {
        val classLoader = ApplicationHook.classLoader ?: return "Hook"
        return try {
            ModuleStatus.detectFramework(classLoader)
                .trim()
                .takeIf { it.isNotBlank() && it != "Unknown Activated" }
                ?: "Hook"
        } catch (_: Throwable) {
            "Hook"
        }
    }

    private fun isAllowedHookFramework(framework: String): Boolean {
        return framework == "LSPosed" || framework == "EdXposed" || framework == "Xposed"
    }

    private fun logState(granted: Boolean, reason: String?) {
        if (lastLoggedState == granted) {
            return
        }
        lastLoggedState = granted

        val suffix = reason?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        if (granted) {
            Log.record(TAG, "✅ 已检测到可用执行权限，允许启动工作流$suffix")
        } else {
            Log.record(TAG, "⛔ 未检测到可用执行权限，工作流与配置不会生效$suffix")
        }
    }
}

