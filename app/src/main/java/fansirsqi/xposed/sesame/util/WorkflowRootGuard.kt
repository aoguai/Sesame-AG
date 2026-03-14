package fansirsqi.xposed.sesame.util

import fansirsqi.xposed.sesame.service.patch.SafeRootShell
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统一 Root 门禁。
 *
 * 工作流是否允许启动，只认“实际拿到 Root 权限”这一件事；
 * 配置文件可以存在，但未通过此门禁时不允许进入运行态。
 */
object WorkflowRootGuard {
    private const val TAG = "WorkflowRootGuard"
    private const val CHECK_CACHE_WINDOW_MS = 3_000L

    private val rootShell = SafeRootShell()
    private val checkMutex = Mutex()

    @Volatile
    private var lastCheckAtMs: Long = 0L

    @Volatile
    private var lastGranted: Boolean = false

    @Volatile
    private var lastLoggedState: Boolean? = null

    fun hasGrantedRoot(): Boolean = lastGranted

    suspend fun hasRoot(forceRefresh: Boolean = false, reason: String? = null): Boolean {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - lastCheckAtMs < CHECK_CACHE_WINDOW_MS) {
            return lastGranted
        }

        return checkMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!forceRefresh && lockedNow - lastCheckAtMs < CHECK_CACHE_WINDOW_MS) {
                return@withLock lastGranted
            }

            val granted = try {
                rootShell.isAvailable()
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, "检测 Root 权限失败", t)
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

    private fun logState(granted: Boolean, reason: String?) {
        if (lastLoggedState == granted) {
            return
        }
        lastLoggedState = granted

        val suffix = reason?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        if (granted) {
            Log.record(TAG, "✅ 已检测到 Root 权限，允许启动工作流$suffix")
        } else {
            Log.record(TAG, "⛔ 未检测到 Root 权限，工作流与配置不会生效$suffix")
        }
    }
}
