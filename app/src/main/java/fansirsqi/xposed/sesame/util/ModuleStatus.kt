package fansirsqi.xposed.sesame.util


import java.io.InputStream

/**
 * 模块状态与框架检测工具类
 *
 * 职责：
 * 1. 提供 UI 层调用的接口 getActivatedStatus()，默认返回 "Not Activated"。
 * 2. 提供 Hook 层调用的检测逻辑 detectFramework(ClassLoader)，用于识别具体框架。
 */
object ModuleStatus {

    /**
     * 获取当前激活状态 (UI 层调用入口)
     *
     * 默认情况下，此方法返回 "Not Activated"。
     * 当模块被 Xposed 框架加载且 Self-Hook 生效时，MainHook 会拦截此方法，
     * 并将其替换为返回 detectFramework() 的结果 (如 "LSPosed", "LSPatch" 等)。
     */
    fun getActivatedStatus(): String {
        return "Not Activated"
    }

    /**
     * 执行实际的框架检测 (Hook 层调用)
     *
     * @param classLoader 目标进程的 ClassLoader (通常是模块自身被注入后的 ClassLoader)
     * @return 框架名称字符串
     */
    fun detectFramework(classLoader: ClassLoader): String {
        return when {
            // 1. 优先检测 LSPatch / NPatch (因为它们通过修改 APK 实现，特征较特殊)
            isLSPatch(classLoader) -> "LSPatch"
            isNPatch(classLoader) -> "NPatch"

            // 2. 检测标准框架
            checkClass(classLoader, "de.robv.android.xposed.XposedInit") -> "LSPosed"
            checkClass(classLoader, "org.meowcat.edxposed.manager") -> "EdXposed"
            checkClass(classLoader, "de.robv.android.xposed.XposedBridge") -> "Xposed"

            // 3. 兜底：虽然被 Hook 了但无法识别框架
            else -> "Unknown Activated"
        }
    }

    // --- 内部检测逻辑 ---

    private fun isLSPatch(cl: ClassLoader): Boolean {
        // 检查类是否存在
        if (checkClass(cl, "org.lsposed.lspatch.loader.LSPApplication")) return true
        // 检查资源文件是否存在 (兼容魔改版)
        return checkResource(cl, "assets/lspatch/config.json")
    }

    private fun isNPatch(cl: ClassLoader): Boolean {
        if (checkClass(cl, "org.lsposed.npatch.loader.LSPApplication")) return true
        return checkResource(cl, "assets/npatch/config.json")
    }

    /**
     * 检查类是否存在于给定的 ClassLoader 中
     */
    private fun checkClass(cl: ClassLoader, className: String): Boolean {
        return try {
            // false: 不初始化类，只检查存在性
            Class.forName(className, false, cl)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * 检查资源文件是否存在于 ClassLoader 中
     */
    private fun checkResource(cl: ClassLoader, path: String): Boolean {
        return try {
            val stream: InputStream? = cl.getResourceAsStream(path)
            // 如果流不为空，说明资源存在；记得关闭流
            stream?.use { }
            stream != null
        } catch (e: Exception) {
            false
        }
    }
}