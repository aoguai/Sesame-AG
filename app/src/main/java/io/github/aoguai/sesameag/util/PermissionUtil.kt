package io.github.aoguai.sesameag.util

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.hook.ApplicationHook

/**
 * 权限工具类，用于检查和请求所需权限。
 * 适配 Android 6.0 - 14.0+
 */
object PermissionUtil {
    // 修复 TAG 获取错误
    private val TAG: String = PermissionUtil::class.java.simpleName

    private const val REQUEST_EXTERNAL_STORAGE = 1
    private const val REQUEST_NOTIFICATION = 2

    // 基础存储权限 (Android 10及以下)
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    // --- 1. 文件存储权限 ---

    /**
     * 检查文件存储权限
     * 适配 Android 11+ (MANAGE_EXTERNAL_STORAGE) 和旧版本
     */
    fun checkFilePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            PERMISSIONS_STORAGE.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * 请求文件存储权限。
     *
     * 仅允许从模块前台 Activity 发起；自动调度链路只能读取权限状态。
     */
    fun checkOrRequestFilePermissions(activity: Activity): Boolean {
        if (checkFilePermissions(activity)) return true
        if (!ensureModulePermissionRequestHost(activity, "file")) return false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: 请求所有文件访问权限
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                startActivitySafely(activity, intent, Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            } else {
                // Android 10-: 请求读写权限
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "请求文件权限失败", e)
        }
        return false
    }

    // --- 2. 闹钟/后台运行权限 ---

    /**
     * 检查精确闹钟权限 (Android 12+)
     */
    @JvmStatic
    fun checkAlarmPermissions(context: Context? = null): Boolean {
        // 优先使用传入的 context，没有则尝试获取 Hook 的 context
        val ctx = context ?: contextSafely ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            return alarmManager?.canScheduleExactAlarms() == true
        }
        return true
    }

    /**
     * 请求精确闹钟权限。
     *
     * 仅允许从模块前台 Activity 发起；自动调度链路只能读取权限状态。
     */
    @JvmStatic
    fun checkOrRequestAlarmPermissions(activity: Activity): Boolean {
        if (checkAlarmPermissions(activity)) return true
        if (!ensureModulePermissionRequestHost(activity, "exact_alarm")) return false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                startActivitySafely(activity, intent, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "请求闹钟权限失败", e)
        }
        return false
    }

    // --- 3. 电池优化白名单 ---

    /**
     * 检查是否忽略电池优化
     */
    @JvmStatic
    fun checkBatteryPermissions(
        context: Context? = null,
        packageName: String? = null
    ): Boolean {
        val ctx = context ?: contextSafely ?: return false
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val targetPackage = packageName?.takeIf { it.isNotBlank() } ?: ctx.packageName
        return pm?.isIgnoringBatteryOptimizations(targetPackage) == true
    }

    /**
     * 请求加入电池优化白名单。
     *
     * 仅允许从模块前台 Activity 发起；自动调度链路只能读取权限状态。
     */
    @JvmStatic
    fun checkOrRequestBatteryPermissions(activity: Activity): Boolean {
        if (checkBatteryPermissions(activity)) return true
        if (!ensureModulePermissionRequestHost(activity, "battery_optimization")) return false

        try {
            // 尝试直接请求指定包名
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            startActivitySafely(activity, intent, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "请求电池优化权限失败", e)
        }
        return false
    }

    // --- 4. 通知权限 (Android 13+) ---

    /**
     * 检查通知权限 (Android 13+)
     */
    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // 旧版本默认允许
        }
    }

    /**
     * 请求通知权限。
     *
     * 仅允许从模块前台 Activity 发起；自动调度链路只能读取权限状态。
     */
    fun checkOrRequestNotificationPermission(activity: Activity): Boolean {
        if (checkNotificationPermission(activity)) return true
        if (!ensureModulePermissionRequestHost(activity, "notification")) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION)
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "请求通知权限失败", e)
            }
            return false
        }
        return true
    }

    // --- 内部辅助方法 ---

    /**
     * 安全启动 Activity，自动处理 Flag 和 异常
     */
    private fun startActivitySafely(context: Context, intent: Intent, fallbackAction: String? = null) {
        try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (!fallbackAction.isNullOrEmpty()) {
                try {
                    val fallbackIntent = Intent(fallbackAction).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Log.printStackTrace(TAG, "Fallback Intent 启动失败: $fallbackAction", ex)
                }
            } else {
                Log.printStackTrace(TAG, "Intent 启动失败: ${intent.action}", e)
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "未知错误", e)
        }
    }

    private fun ensureModulePermissionRequestHost(activity: Activity, permissionName: String): Boolean {
        if (activity.packageName == BuildConfig.APPLICATION_ID) {
            return true
        }
        Log.record(TAG, "忽略非模块前台的权限申请请求: permission=$permissionName package=${activity.packageName}")
        return false
    }

    /**
     * 获取 Hook 环境下的 Context (仅用于被 Hook 的宿主环境中)
     */
    private val contextSafely: Context?
        get() = try {
            if (ApplicationHook.isHooked) ApplicationHook.appContext else null
        } catch (_: Exception) {
            null
        }
}
