package io.github.aoguai.sesameag.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.aoguai.sesameag.SesameApplication.Companion.PREFERENCES_KEY
import io.github.aoguai.sesameag.SesameApplication.Companion.hasPermissions
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.data.Config
import io.github.aoguai.sesameag.hook.AccountSlotRegistry
import io.github.aoguai.sesameag.hook.ApplicationHookConstants
import io.github.aoguai.sesameag.hook.keepalive.PersistentScheduleRegistry
import io.github.aoguai.sesameag.hook.keepalive.UnifiedScheduler
import io.github.aoguai.sesameag.service.ConnectionState
import io.github.aoguai.sesameag.service.LsposedServiceManager
import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.ui.permissions.PermissionHealthItem
import io.github.aoguai.sesameag.ui.permissions.PermissionHealthSnapshot
import io.github.aoguai.sesameag.ui.permissions.PermissionPolicy
import io.github.aoguai.sesameag.ui.permissions.PermissionRequirement
import io.github.aoguai.sesameag.ui.permissions.PermissionStatus
import io.github.aoguai.sesameag.ui.screen.MainScreen
import io.github.aoguai.sesameag.ui.navigation.LogSource
import io.github.aoguai.sesameag.ui.theme.AppTheme
import io.github.aoguai.sesameag.ui.theme.ThemeManager
import io.github.aoguai.sesameag.ui.viewmodel.MainViewModel
import io.github.aoguai.sesameag.task.customTasks.CustomTask
import io.github.aoguai.sesameag.util.CommandUtil
import io.github.aoguai.sesameag.util.DataStore
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.IconManager
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.LogChannel
import io.github.aoguai.sesameag.util.Logback
import io.github.aoguai.sesameag.util.PermissionUtil
import io.github.aoguai.sesameag.util.ToastUtil
import io.github.aoguai.sesameag.util.UserDataStoreManager
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class MainActivity : ComponentActivity() {
    private data class ExactAlarmManifestState(
        val targetSdkVersion: Int?,
        val requestsScheduleExactAlarm: Boolean,
        val usesExactAlarm: Boolean
    ) {
        val isAlwaysGranted: Boolean
            get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (targetSdkVersion != null && targetSdkVersion < Build.VERSION_CODES.S) ||
                usesExactAlarm

        val requiresManualSettingsCheck: Boolean
            get() = !isAlwaysGranted && requestsScheduleExactAlarm
    }

    private data class TargetPermissionSnapshot(
        val available: Boolean,
        val contextPackage: String,
        val targetBatteryIgnored: Boolean,
        val targetExactAlarmAllowed: Boolean?
    )

    private enum class PermissionRequestMode {
        AUTO_CRITICAL,
        MANUAL_CARD
    }

    private val viewModel: MainViewModel by viewModels()
    private val clearModuleDataFailures = MutableStateFlow<List<String>>(emptyList())
    private val runtimePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            onRuntimePermissionRequestFinished(result)
        }
    private val permissionSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingPermissionRequest = null
            requestTargetPermissionSnapshot(clearCached = true)
            continuePermissionQueueOrAuto()
        }
    private var pendingPermissionRequest: PermissionRequirement? = null
    private var activePermissionMode: PermissionRequestMode? = null
    private var activePermissionOrder: List<PermissionRequirement> = emptyList()
    private val requestedPermissionsThisVisibility = linkedSetOf<PermissionRequirement>()
    private val requestedPermissionsThisQueue = linkedSetOf<PermissionRequirement>()
    private val deniedPermissionsThisVisibility = linkedSetOf<PermissionRequirement>()
    private var latestTargetPermissionSnapshot: TargetPermissionSnapshot? = null
    private var pendingTargetPermissionSnapshotToken: Long = 0L

    private val autoCriticalPermissions = listOf(
        PermissionRequirement.MODULE_FILE,
        PermissionRequirement.LSPOSED_TARGET_SCOPE
    )

    private val lspConnectionListener: (ConnectionState) -> Unit = { state ->
        runOnUiThread {
            if (state !is ConnectionState.Connected &&
                pendingPermissionRequest == PermissionRequirement.LSPOSED_TARGET_SCOPE
            ) {
                markRequestFinished(PermissionRequirement.LSPOSED_TARGET_SCOPE)
            }
            refreshPermissionHealth()
            continuePermissionQueueOrAuto()
        }
    }

    private val targetPermissionSnapshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ApplicationHookConstants.BroadcastActions.PERMISSION_SNAPSHOT_RESULT) {
                return
            }
            val responseToken = intent.getLongExtra("requestToken", 0L)
            if (
                responseToken == 0L ||
                responseToken != pendingTargetPermissionSnapshotToken
            ) {
                return
            }
            latestTargetPermissionSnapshot = TargetPermissionSnapshot(
                available = intent.getBooleanExtra("available", false),
                contextPackage = intent.getStringExtra("contextPackage").orEmpty(),
                targetBatteryIgnored = intent.getBooleanExtra("targetBatteryIgnored", false),
                targetExactAlarmAllowed = if (intent.hasExtra("targetExactAlarmAllowed")) {
                    intent.getBooleanExtra("targetExactAlarmAllowed", false)
                } else {
                    null
                }
            ).takeIf { it.available && it.contextPackage == General.PACKAGE_NAME }
            pendingTargetPermissionSnapshotToken = 0L
            refreshPermissionHealth()
        }
    }

    // Shizuku 监听器
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1234) {
            if (pendingPermissionRequest == PermissionRequirement.SHELL_EXECUTOR) {
                pendingPermissionRequest = null
            }
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ToastUtil.showToast(this, "Shizuku 授权成功！")

                lifecycleScope.launch {
                    CommandUtil.executeCommand(this@MainActivity, "echo init_shizuku")
                    refreshPermissionHealth()
                }
            } else {
                deniedPermissionsThisVisibility.add(PermissionRequirement.SHELL_EXECUTOR)
                ToastUtil.showToast(this, "Shizuku 授权被拒绝")
            }
            continuePermissionQueueOrAuto()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        ContextCompat.registerReceiver(
            this,
            targetPermissionSnapshotReceiver,
            IntentFilter(ApplicationHookConstants.BroadcastActions.PERMISSION_SNAPSHOT_RESULT),
            ContextCompat.RECEIVER_EXPORTED
        )
        LsposedServiceManager.init()
        LsposedServiceManager.addConnectionListener(lspConnectionListener)
        setupShizuku()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CommandUtil.serviceStatus.collectLatest {
                    refreshPermissionHealth()
                    if (activePermissionMode == PermissionRequestMode.MANUAL_CARD ||
                        pendingPermissionRequest == PermissionRequirement.SHELL_EXECUTOR
                    ) {
                        continuePermissionQueueOrAuto()
                    }
                }
            }
        }
        continuePermissionQueueOrAuto()

        // 4. 同步图标状态
        val prefs = getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE)
        IconManager.syncIconState(this, prefs.getBoolean("is_icon_hidden", false))


        // 5. 设置 Compose 内容
        setContent {
            // 收集 ViewModel 状态
            val oneWord by viewModel.oneWord.collectAsStateWithLifecycle()
            val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
            val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()
            //  获取实时的 UserEntity 列表
            val userList by viewModel.userList.collectAsStateWithLifecycle()
            val accountSlots by viewModel.accountSlots.collectAsStateWithLifecycle()
            val permissionHealth by viewModel.permissionHealth.collectAsStateWithLifecycle()
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            val clearFailurePaths by clearModuleDataFailures.collectAsStateWithLifecycle()

            // AppTheme 会处理状态栏颜色
            AppTheme(dynamicColor = isDynamicColor) {
                MainScreen(
                    oneWord = oneWord,
                    activeUserName = activeUser?.showName ?: "未载入账号",
                    hasActiveUser = activeUser != null,
                    moduleStatus = moduleStatus,
                    permissionHealth = permissionHealth,
                    viewModel = viewModel,
                    isDynamicColor = isDynamicColor, // 传给 MainScreen
                    // 传入回调
                    userList = userList, // 传入列表
                    accountSlots = accountSlots,
                    onPrepareManualTasks = ::ensureManualTaskConfigLoaded,
                    onRunManualTask = ::runManualTask,
                    clearModuleDataFailurePaths = clearFailurePaths,
                    onDismissClearModuleDataFailure = { clearModuleDataFailures.value = emptyList() },
                    onEvent = { event -> handleEvent(event) },
                    onExitRequested = ::finish,
                )
            }
        }
        requestHighestSupportedFrameRate()
    }

    private fun requestHighestSupportedFrameRate() {
        val modes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.supportedModes
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.supportedModes
        }
        val requestedFrameRate = modes?.maxOfOrNull { it.refreshRate } ?: return
        if (requestedFrameRate <= 60f) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.setRequestedFrameRate(requestedFrameRate)
        } else {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = requestedFrameRate
            }
        }
    }

    /**
     * 定义 UI 事件
     */
    sealed class MainUiEvent {
        data object RefreshOneWord : MainUiEvent()
        data class OpenLog(val channel: LogChannel) : MainUiEvent()
        data class ToggleIconHidden(val isHidden: Boolean) : MainUiEvent()
        data object OpenExtend : MainUiEvent()
        data object ClearConfig : MainUiEvent()
        data object RefreshEnvironment : MainUiEvent()
        data class RequestPermission(val requirement: PermissionRequirement) : MainUiEvent()
        data object OpenTargetApp : MainUiEvent()
    }

    /**
     * 统一处理事件
     */
    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RefreshOneWord -> viewModel.fetchOneWord()
            is MainUiEvent.OpenLog -> Unit
            MainUiEvent.RefreshEnvironment -> {
                CommandUtil.connect(applicationContext)
                refreshEnvironment()
            }
            is MainUiEvent.RequestPermission -> requestPermissionFromCard(event.requirement)
            MainUiEvent.OpenTargetApp -> {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(General.PACKAGE_NAME)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        ToastUtil.showToast(this, "暂时无法打开目标应用")
                    }
                } catch (e: Exception) {
                    Log.printStackTrace("MainActivity", "打开目标应用失败", e)
                    ToastUtil.showToast(this, "暂时无法打开目标应用")
                }
            }
            is MainUiEvent.ToggleIconHidden -> {
                val shouldHide = event.isHidden
                getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE).edit { putBoolean("is_icon_hidden", shouldHide) }
                viewModel.syncIconState(shouldHide)
                Toast.makeText(this, "设置已保存，可能需要重启桌面才能生效", Toast.LENGTH_SHORT).show()
            }

            MainUiEvent.OpenExtend -> Unit
            MainUiEvent.ClearConfig -> {
                clearModuleDataFailures.value = emptyList()
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { DataStore.shutdown() }
                    runCatching { UserDataStoreManager.shutdownAll() }
                    runCatching { PersistentScheduleRegistry.clearAll(applicationContext) }
                    runCatching { UnifiedScheduler.cleanup() }

                    val fileResult = Files.clearAllModuleData(applicationContext)
                    val preferencesCleared =
                        getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE)
                            .edit()
                            .clear()
                            .commit()
                    val failedPaths = fileResult.failedPaths.toMutableList()
                    if (!preferencesCleared) {
                        failedPaths += "SharedPreferences:$PREFERENCES_KEY"
                    }
                    if (failedPaths.isNotEmpty()) {
                        Log.runtime(
                            "MainActivity",
                            "清除模块数据失败: count=${failedPaths.size}, first=${failedPaths.first()}"
                        )
                    }

                    runCatching {
                        Logback.reloadFileLogging(enableCaptureAppender = true)
                    }

                    withContext(Dispatchers.Main) {
                        viewModel.refreshUserConfigs()
                        if (failedPaths.isEmpty()) {
                            ThemeManager.resetToDefaults()
                            IconManager.syncIconState(this@MainActivity, false)
                            sendBroadcast(
                                Intent(ApplicationHookConstants.BroadcastActions.RESTART).apply {
                                    putExtra("configReload", true)
                                }
                            )
                            ToastUtil.showToast(this@MainActivity, "已清除全部模块数据，正在恢复默认配置")
                            recreate()
                        } else {
                            clearModuleDataFailures.value = failedPaths.toList()
                        }
                    }
                }
            }
        }
    }

    // --- 辅助方法 ---

    private suspend fun ensureManualTaskConfigLoaded() = withContext(Dispatchers.IO) {
        Model.initAllModel()
        val activeUser = DataStore.get("activedUser", io.github.aoguai.sesameag.entity.UserEntity::class.java)
        activeUser?.userId?.let { userId ->
            UserMap.setCurrentUserId(userId)
            Config.load(userId)
        }
    }

    private fun runManualTask(task: CustomTask, params: Map<String, Any>): LogSource? {
        val activeUserId = DataStore.get("activedUser", io.github.aoguai.sesameag.entity.UserEntity::class.java)?.userId
        if (!AccountSlotRegistry.isExecutableUser(activeUserId)) {
            ToastUtil.showToast(this, "当前账号不在可执行槽位，无法运行手动任务")
            return null
        }
        return try {
            val intent = Intent(ApplicationHookConstants.BroadcastActions.MANUAL_TASK)
            intent.putExtra("task", task.name)
            params.forEach { (key, value) ->
                when (value) {
                    is Int -> intent.putExtra(key, value)
                    is String -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                }
            }
            sendBroadcast(intent)
            ToastUtil.showToast(this, "已发送指令: ${task.displayName}")
            val logFile = Files.getLogFile(LogChannel.RECORD)
            if (logFile.exists()) {
                LogSource.FilePath(logFile.absolutePath)
            } else {
                ToastUtil.showToast(this, "日志文件尚未生成")
                null
            }
        } catch (e: Exception) {
            ToastUtil.showToast(this, "发送失败: ${e.message}")
            null
        }
    }

    private fun setupShizuku() {
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        if (!Shizuku.pingBinder()) return

        val granted = checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (granted && hasPermissions) {
            lifecycleScope.launch {
                CommandUtil.executeCommand(this@MainActivity, "echo init_shizuku")
                refreshPermissionHealth()
            }
        }
    }

    private fun refreshEnvironment() {
        LsposedServiceManager.refreshScope()
        viewModel.refreshModuleFrameworkStatus()
        hasPermissions = PermissionUtil.checkFilePermissions(this)
        if (hasPermissions) {
            viewModel.initAppLogic()
            viewModel.refreshUserConfigs()
        }
        requestTargetPermissionSnapshot(clearCached = true)
        refreshPermissionHealth()
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironment()
        if (
            pendingPermissionRequest == PermissionRequirement.LSPOSED_TARGET_SCOPE &&
            LsposedServiceManager.hasTargetScope(General.PACKAGE_NAME)
        ) {
            markRequestFinished(PermissionRequirement.LSPOSED_TARGET_SCOPE)
        } else if (pendingPermissionRequest == PermissionRequirement.SHELL_EXECUTOR) {
            pendingPermissionRequest = null
        }
        CommandUtil.connect(applicationContext)
        continuePermissionQueueOrAuto()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            CommandUtil.unbind(applicationContext)
        }
        if (!isChangingConfigurations && pendingPermissionRequest == null) {
            requestedPermissionsThisVisibility.clear()
            deniedPermissionsThisVisibility.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            CommandUtil.unbind(applicationContext)
        }
        LsposedServiceManager.removeConnectionListener(lspConnectionListener)
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        runCatching { unregisterReceiver(targetPermissionSnapshotReceiver) }
    }

    private fun onRuntimePermissionRequestFinished(result: Map<String, Boolean>) {
        val deniedPermission = pendingPermissionRequest
        if (deniedPermission in setOf(
                PermissionRequirement.MODULE_FILE,
                PermissionRequirement.MODULE_NOTIFICATION
            ) && result.isNotEmpty() && !result.values.all { it }
        ) {
            deniedPermissionsThisVisibility.add(deniedPermission!!)
        }
        pendingPermissionRequest = null
        continuePermissionQueueOrAuto()
    }

    private fun requestPermissionFromCard(permission: PermissionRequirement) {
        val targetPackage = when (permission) {
            PermissionRequirement.TARGET_EXACT_ALARM, PermissionRequirement.TARGET_BATTERY -> General.PACKAGE_NAME
            else -> packageName
        }
        val settingsIntent = when (permission) {
            PermissionRequirement.MODULE_FILE -> Intent(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).setData(Uri.parse("package:$targetPackage"))
            PermissionRequirement.MODULE_NOTIFICATION -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, targetPackage)
            PermissionRequirement.MODULE_EXACT_ALARM, PermissionRequirement.TARGET_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:$targetPackage"))
                } else null
            }
            PermissionRequirement.MODULE_BATTERY, PermissionRequirement.TARGET_BATTERY ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            else -> null
        }
        if (settingsIntent != null) {
            if (targetPackage != packageName && !PermissionUtil.isPackageInstalled(this, targetPackage)) {
                ToastUtil.showToast(this, "未检测到目标应用")
                return
            }
            val fallbackAction = when (permission) {
                PermissionRequirement.MODULE_FILE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                } else null
                PermissionRequirement.MODULE_EXACT_ALARM, PermissionRequirement.TARGET_EXACT_ALARM ->
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                else -> null
            }
            if (!PermissionUtil.startActivitySafely(this, settingsIntent, fallbackAction, permissionSettingsLauncher)) {
                ToastUtil.showToast(this, "暂时无法打开系统设置")
            }
            return
        }
        if (pendingPermissionRequest != null) return
        clearActivePermissionQueue()
        runPermissionQueue(listOf(permission), PermissionRequestMode.MANUAL_CARD)
    }

    private fun continuePermissionQueueOrAuto() {
        if (pendingPermissionRequest != null) {
            refreshPermissionHealth()
            return
        }
        val mode = activePermissionMode
        val order = activePermissionOrder
        if (mode != null && order.isNotEmpty()) {
            runPermissionQueue(order, mode)
        } else {
            runPermissionQueue(autoCriticalPermissions, PermissionRequestMode.AUTO_CRITICAL)
        }
    }

    private fun runPermissionQueue(
        order: List<PermissionRequirement>,
        mode: PermissionRequestMode
    ): Boolean {
        if (pendingPermissionRequest != null) {
            refreshPermissionHealth()
            return false
        }
        activePermissionMode = mode
        activePermissionOrder = order
        hasPermissions = PermissionUtil.checkFilePermissions(this)
        if (hasPermissions) {
            viewModel.initAppLogic()
        }

        val snapshot = refreshPermissionHealth()
        for (permission in order) {
            val item = snapshot.item(permission) ?: continue
            if (!item.canRequest) continue
            if (!shouldRequest(permission, mode)) continue
            if (requestPermission(permission)) {
                return true
            }
            deniedPermissionsThisVisibility.add(permission)
            markRequestFinished(permission)
            if (permission != PermissionRequirement.LSPOSED_TARGET_SCOPE) {
                ToastUtil.showToast(this, "暂时无法发起请求，请重试")
            }
        }

        if (
            mode == PermissionRequestMode.MANUAL_CARD &&
            snapshot.item(PermissionRequirement.SHELL_EXECUTOR)?.status == PermissionStatus.REQUESTING
        ) {
            refreshPermissionHealth()
            return true
        }

        clearActivePermissionQueue()
        refreshPermissionHealth()
        return false
    }

    private fun shouldRequest(
        permission: PermissionRequirement,
        mode: PermissionRequestMode
    ): Boolean {
        if (pendingPermissionRequest == permission) {
            return false
        }
        val requestedSet = if (mode == PermissionRequestMode.AUTO_CRITICAL) {
            requestedPermissionsThisVisibility
        } else {
            requestedPermissionsThisQueue
        }
        if (!requestedSet.add(permission)) {
            return false
        }
        pendingPermissionRequest = permission
        refreshPermissionHealth()
        return true
    }

    private fun requestPermission(permission: PermissionRequirement): Boolean {
        return when (permission) {
            PermissionRequirement.MODULE_FILE -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                    permission in deniedPermissionsThisVisibility &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ) {
                    PermissionUtil.openAppSettings(this, permissionSettingsLauncher)
                } else {
                    PermissionUtil.checkOrRequestFilePermissions(this, runtimePermissionsLauncher, permissionSettingsLauncher)
                }
            }

            PermissionRequirement.MODULE_NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    permission in deniedPermissionsThisVisibility &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    PermissionUtil.openAppSettings(this, permissionSettingsLauncher)
                } else {
                    PermissionUtil.checkOrRequestNotificationPermission(this, runtimePermissionsLauncher, permissionSettingsLauncher)
                }
            }

            PermissionRequirement.LSPOSED_TARGET_SCOPE -> requestLsposedTargetScope()

            PermissionRequirement.MODULE_EXACT_ALARM -> {
                PermissionUtil.checkOrRequestExactAlarmPermissions(
                    this,
                    packageName,
                    permissionSettingsLauncher
                )
            }

            PermissionRequirement.TARGET_EXACT_ALARM -> {
                PermissionUtil.checkOrRequestExactAlarmPermissions(
                    this,
                    General.PACKAGE_NAME,
                    permissionSettingsLauncher
                )
            }

            PermissionRequirement.MODULE_BATTERY -> {
                PermissionUtil.checkOrRequestBatteryPermissions(this, packageName, permissionSettingsLauncher)
            }

            PermissionRequirement.TARGET_BATTERY -> {
                PermissionUtil.checkOrRequestBatteryPermissions(this, General.PACKAGE_NAME, permissionSettingsLauncher)
            }

            PermissionRequirement.SHELL_EXECUTOR -> requestShellExecutor()
        }
    }

    private fun requestLsposedTargetScope(): Boolean {
        val sent = LsposedServiceManager.requestTargetScope { result ->
            runOnUiThread {
                markRequestFinished(PermissionRequirement.LSPOSED_TARGET_SCOPE)
                if (result.success) {
                    ToastUtil.showToast(this, "已添加目标应用")
                } else {
                    deniedPermissionsThisVisibility.add(PermissionRequirement.LSPOSED_TARGET_SCOPE)
                    Log.runtime("MainActivity", "LSPosed scope request failed: ${result.message}")
                    ToastUtil.showToast(this, "添加失败，请重试或在 LSPosed 中选择目标应用")
                }
                continuePermissionQueueOrAuto()
            }
        }
        if (!sent) {
            markRequestFinished(PermissionRequirement.LSPOSED_TARGET_SCOPE)
        }
        return sent
    }

    private fun requestShellExecutor(): Boolean {
        CommandUtil.connect(applicationContext)
        if (isShizukuPermissionMissing()) {
            return try {
                Shizuku.requestPermission(1234)
                true
            } catch (e: Exception) {
                ToastUtil.showToast(this, "Shizuku 授权请求失败: ${e.message}")
                false
            }
        }

        lifecycleScope.launch {
            CommandUtil.executeCommand(this@MainActivity, "echo init_shell")
            markRequestFinished(PermissionRequirement.SHELL_EXECUTOR)
            refreshPermissionHealth()
            continuePermissionQueueOrAuto()
        }
        return true
    }

    private fun requestTargetPermissionSnapshot(clearCached: Boolean = false): Boolean {
        if (clearCached) {
            latestTargetPermissionSnapshot = null
        }
        if (!PermissionUtil.isPackageInstalled(this, General.PACKAGE_NAME)) {
            latestTargetPermissionSnapshot = null
            pendingTargetPermissionSnapshotToken = 0L
            return false
        }
        val requestToken = System.nanoTime()
        pendingTargetPermissionSnapshotToken = requestToken
        try {
            sendBroadcast(
                Intent(ApplicationHookConstants.BroadcastActions.PERMISSION_SNAPSHOT).apply {
                    setPackage(General.PACKAGE_NAME)
                    putExtra("requestToken", requestToken)
                }
            )
        } catch (e: Exception) {
            pendingTargetPermissionSnapshotToken = 0L
            Log.printStackTrace("MainActivity", "Target permission refresh failed", e)
            return false
        }
        lifecycleScope.launch {
            delay(350)
            if (pendingTargetPermissionSnapshotToken == requestToken) {
                pendingTargetPermissionSnapshotToken = 0L
                refreshPermissionHealth()
            }
        }
        return true
    }

    private fun markRequestFinished(permission: PermissionRequirement) {
        if (pendingPermissionRequest == permission) {
            pendingPermissionRequest = null
        }
    }

    private fun clearActivePermissionQueue() {
        activePermissionMode = null
        activePermissionOrder = emptyList()
        requestedPermissionsThisQueue.clear()
    }

    private fun refreshPermissionHealth(): PermissionHealthSnapshot {
        val snapshot = buildPermissionHealthSnapshot(pendingPermissionRequest)
        viewModel.updatePermissionHealth(snapshot)
        return snapshot
    }

    private fun buildPermissionHealthSnapshot(
        requesting: PermissionRequirement?
    ): PermissionHealthSnapshot {
        val targetInstalled = PermissionUtil.isPackageInstalled(this, General.PACKAGE_NAME)
        val targetPermissionSnapshot = latestTargetPermissionSnapshot
        val targetBatteryIgnored = targetInstalled && (
            targetPermissionSnapshot?.targetBatteryIgnored == true ||
                PermissionUtil.checkBatteryPermissions(this, General.PACKAGE_NAME)
            )
        val targetExactAlarmManifest = packageExactAlarmManifestState(General.PACKAGE_NAME)
        val targetExactAlarmPermissionStatus = targetExactAlarmStatus(
            targetInstalled = targetInstalled,
            targetBatteryIgnored = targetBatteryIgnored,
            manifestState = targetExactAlarmManifest,
            hookExactAlarmAllowed = targetPermissionSnapshot?.targetExactAlarmAllowed,
            requesting = requesting
        )
        val items = listOf(
            PermissionHealthItem(
                requirement = PermissionRequirement.MODULE_FILE,
                status = statusForGranted(
                    PermissionRequirement.MODULE_FILE,
                    PermissionUtil.checkFilePermissions(this),
                    requesting
                ),
                policy = PermissionPolicy.AUTO_CRITICAL,
                title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "所有文件访问" else "文件访问",
                description = "读取和保存配置、日志",
                actionLabel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                    PermissionRequirement.MODULE_FILE in deniedPermissionsThisVisibility &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ) "打开设置" else "授权文件访问"
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.MODULE_NOTIFICATION,
                status = statusForGranted(
                    PermissionRequirement.MODULE_NOTIFICATION,
                    PermissionUtil.checkNotificationPermission(this),
                    requesting
                ),
                policy = PermissionPolicy.MANUAL_CARD,
                title = "模块通知",
                description = "接收模块通知",
                actionLabel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    (PermissionRequirement.MODULE_NOTIFICATION in deniedPermissionsThisVisibility &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) "打开设置" else "开启通知"
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.LSPOSED_TARGET_SCOPE,
                status = lsposedScopeStatus(targetInstalled, requesting),
                policy = PermissionPolicy.AUTO_CRITICAL,
                title = "目标应用作用域",
                description = if (targetInstalled) {
                    "在目标应用中启用模块"
                } else {
                    "安装目标应用，或在应用隐藏设置中将其设为可见"
                },
                actionLabel = if (targetInstalled) "添加目标应用" else null
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.MODULE_EXACT_ALARM,
                status = statusForGranted(
                    PermissionRequirement.MODULE_EXACT_ALARM,
                    PermissionUtil.checkExactAlarmPermissions(this, packageName),
                    requesting
                ),
                policy = PermissionPolicy.MANUAL_CARD,
                title = "模块精确闹钟",
                description = "提高定时任务准点性",
                actionLabel = "打开设置"
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.TARGET_EXACT_ALARM,
                status = targetExactAlarmPermissionStatus,
                policy = PermissionPolicy.MANUAL_CARD,
                title = "目标应用精确闹钟",
                description = "提高定时任务准点性",
                actionLabel = if (targetInstalled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    targetExactAlarmManifest.requestsScheduleExactAlarm
                ) "打开设置" else null,
                requestWhenUnavailable = targetInstalled &&
                    targetExactAlarmManifest.requiresManualSettingsCheck &&
                    targetExactAlarmPermissionStatus == PermissionStatus.UNAVAILABLE
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.MODULE_BATTERY,
                status = statusForGranted(
                    PermissionRequirement.MODULE_BATTERY,
                    PermissionUtil.checkBatteryPermissions(this, packageName),
                    requesting
                ),
                policy = PermissionPolicy.MANUAL_CARD,
                title = "模块电池优化豁免",
                description = "减少后台任务被系统延迟",
                actionLabel = "打开设置"
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.TARGET_BATTERY,
                status = if (!targetInstalled) {
                    PermissionStatus.UNAVAILABLE
                } else {
                    statusForGranted(
                        PermissionRequirement.TARGET_BATTERY,
                        targetBatteryIgnored,
                        requesting
                    )
                },
                policy = PermissionPolicy.MANUAL_CARD,
                title = "目标应用电池优化豁免",
                description = "减少后台任务被系统延迟",
                actionLabel = if (targetInstalled) "打开设置" else null
            ),
            PermissionHealthItem(
                requirement = PermissionRequirement.SHELL_EXECUTOR,
                status = shellExecutorStatus(requesting),
                policy = PermissionPolicy.MANUAL_CRITICAL,
                title = "Root/Shizuku",
                description = "Root 与 Shizuku 任一可用即可",
                actionLabel = if (isShizukuPermissionMissing()) "授权 Shizuku" else "连接",
                requestWhenUnavailable = true
            )
        )
        return PermissionHealthSnapshot(items = items)
    }

    private fun statusForGranted(
        requirement: PermissionRequirement,
        granted: Boolean,
        requesting: PermissionRequirement?
    ): PermissionStatus {
        if (granted) {
            markPermissionGranted(requirement)
        }
        return when {
            granted -> PermissionStatus.GRANTED
            requesting == requirement -> PermissionStatus.REQUESTING
            requirement in deniedPermissionsThisVisibility -> PermissionStatus.DENIED
            else -> PermissionStatus.MISSING
        }
    }

    private fun lsposedScopeStatus(
        targetInstalled: Boolean,
        requesting: PermissionRequirement?
    ): PermissionStatus {
        if (!targetInstalled) return PermissionStatus.UNAVAILABLE
        val frameworkStatus = LsposedServiceManager.connectedFrameworkStatus() ?: return PermissionStatus.UNAVAILABLE
        if (!frameworkStatus.isSupportedLsposed) return PermissionStatus.UNSUPPORTED
        if (LsposedServiceManager.hasTargetScope(General.PACKAGE_NAME)) {
            markPermissionGranted(PermissionRequirement.LSPOSED_TARGET_SCOPE)
            return PermissionStatus.GRANTED
        }
        return when {
            requesting == PermissionRequirement.LSPOSED_TARGET_SCOPE -> PermissionStatus.REQUESTING
            PermissionRequirement.LSPOSED_TARGET_SCOPE in deniedPermissionsThisVisibility -> PermissionStatus.DENIED
            else -> PermissionStatus.MISSING
        }
    }

    private fun shellExecutorStatus(requesting: PermissionRequirement?): PermissionStatus {
        return when (CommandUtil.serviceStatus.value) {
            is CommandUtil.ServiceStatus.Active -> PermissionStatus.GRANTED
            is CommandUtil.ServiceStatus.Loading -> PermissionStatus.REQUESTING
            is CommandUtil.ServiceStatus.Inactive,
            is CommandUtil.ServiceStatus.Error -> {
                if (requesting == PermissionRequirement.SHELL_EXECUTOR) {
                    PermissionStatus.REQUESTING
                } else if (isShizukuPermissionMissing()) {
                    if (PermissionRequirement.SHELL_EXECUTOR in deniedPermissionsThisVisibility) {
                        PermissionStatus.DENIED
                    } else {
                        PermissionStatus.MISSING
                    }
                } else {
                    PermissionStatus.UNAVAILABLE
                }
            }
        }
    }

    private fun isShizukuPermissionMissing(): Boolean {
        return runCatching {
            Shizuku.pingBinder() &&
                checkSelfPermission(ShizukuProvider.PERMISSION) != PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private fun markPermissionGranted(requirement: PermissionRequirement) {
        deniedPermissionsThisVisibility.remove(requirement)
        // 自动闭环只在当前可见周期内抑制重复弹窗；一旦权限恢复，后续再次被撤销时应允许重新申请。
        requestedPermissionsThisVisibility.remove(requirement)
    }

    private fun targetExactAlarmStatus(
        targetInstalled: Boolean,
        targetBatteryIgnored: Boolean,
        manifestState: ExactAlarmManifestState,
        hookExactAlarmAllowed: Boolean?,
        requesting: PermissionRequirement?
    ): PermissionStatus {
        if (!targetInstalled) return PermissionStatus.UNAVAILABLE
        if (
            hookExactAlarmAllowed == true ||
            manifestState.isAlwaysGranted ||
            targetBatteryIgnored
        ) {
            markPermissionGranted(PermissionRequirement.TARGET_EXACT_ALARM)
            return PermissionStatus.GRANTED
        }
        if (requesting == PermissionRequirement.TARGET_EXACT_ALARM) {
            return PermissionStatus.REQUESTING
        }
        if (hookExactAlarmAllowed == false) return PermissionStatus.MISSING
        if (!manifestState.requiresManualSettingsCheck) {
            return PermissionStatus.UNSUPPORTED
        }
        return PermissionStatus.UNAVAILABLE
    }

    private fun packageExactAlarmManifestState(packageName: String): ExactAlarmManifestState {
        val info = packageInfo(packageName)
        val requestedPermissions = info?.requestedPermissions?.toSet().orEmpty()
        return ExactAlarmManifestState(
            targetSdkVersion = info?.applicationInfo?.targetSdkVersion,
            requestsScheduleExactAlarm = Manifest.permission.SCHEDULE_EXACT_ALARM in requestedPermissions,
            usesExactAlarm = Manifest.permission.USE_EXACT_ALARM in requestedPermissions
        )
    }

    private fun packageInfo(packageName: String) = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }.getOrNull()
}
