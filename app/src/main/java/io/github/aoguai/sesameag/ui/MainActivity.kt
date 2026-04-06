package io.github.aoguai.sesameag.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.aoguai.sesameag.SesameApplication.Companion.PREFERENCES_KEY
import io.github.aoguai.sesameag.SesameApplication.Companion.hasPermissions
import io.github.aoguai.sesameag.data.Config
import io.github.aoguai.sesameag.data.General
import io.github.aoguai.sesameag.entity.UserEntity
import io.github.aoguai.sesameag.model.BaseModel
import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.ui.extension.openUrl
import io.github.aoguai.sesameag.ui.extension.performNavigationToSettings
import io.github.aoguai.sesameag.ui.screen.MainScreen
import io.github.aoguai.sesameag.ui.theme.AppTheme
import io.github.aoguai.sesameag.ui.theme.ThemeManager
import io.github.aoguai.sesameag.ui.viewmodel.MainViewModel
import io.github.aoguai.sesameag.util.CommandUtil
import io.github.aoguai.sesameag.util.DataStore
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.IconManager
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.LogChannel
import io.github.aoguai.sesameag.util.PermissionUtil
import io.github.aoguai.sesameag.util.ToastUtil
import io.github.aoguai.sesameag.util.maps.UserMap
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private enum class ModulePermissionRequest {
        FILE,
        NOTIFICATION,
        EXACT_ALARM,
        BATTERY
    }

    private val viewModel: MainViewModel by viewModels()
    private var pendingPermissionRequest: ModulePermissionRequest? = null
    private val requestedPermissionsThisVisibility = linkedSetOf<ModulePermissionRequest>()

    // Shizuku 监听器
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1234) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ToastUtil.showToast(this, "Shizuku 授权成功！")

                // 关键修改：
                lifecycleScope.launch {
                    CommandUtil.executeCommand(this@MainActivity, "echo init_shizuku")
                }
            } else {
                ToastUtil.showToast(this, "Shizuku 授权被拒绝")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureModulePermissions(requestIfNeeded = true)

        // 3. 初始化 Shizuku
        setupShizuku()

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
            // 使用 derivedStateOf 优化性能，只在 userList 变化时重新映射
            val uidList by remember {
                derivedStateOf { userList.map { it.userId } }
            }
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()

            // AppTheme 会处理状态栏颜色
            AppTheme(dynamicColor = isDynamicColor) {
                MainScreen(
                    oneWord = oneWord,
                    activeUserName = activeUser?.showName ?: "未载入",
                    moduleStatus = moduleStatus,
                    viewModel = viewModel,
                    isDynamicColor = isDynamicColor, // 传给 MainScreen
                    // 传入回调
                    userList = userList, // 传入列表
                    // 🔥 处理跳转逻辑
                    onNavigateToSettings = { selectedUser ->
                        performNavigationToSettings(selectedUser)
                    },
                    onEvent = { event -> handleEvent(event) }
                )
            }
        }
    }

    @Deprecated(
        message = "Overrides deprecated framework callback; retained for the current permission flow."
    )
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        pendingPermissionRequest = null
        ensureModulePermissions(requestIfNeeded = true)
    }

    /**
     * 定义 UI 事件
     */
    sealed class MainUiEvent {
        data object RefreshOneWord : MainUiEvent()
        data class OpenLog(val channel: LogChannel) : MainUiEvent()
        data object OpenGithub : MainUiEvent()
        data class ToggleIconHidden(val isHidden: Boolean) : MainUiEvent()
        data object OpenExtend : MainUiEvent()
        data object ClearConfig : MainUiEvent()
    }

    /**
     * 统一处理事件
     */
    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RefreshOneWord -> viewModel.fetchOneWord()
            is MainUiEvent.OpenLog -> openLogChannel(event.channel)
            MainUiEvent.OpenGithub -> openUrl(General.PROJECT_HOMEPAGE_URL)
            is MainUiEvent.ToggleIconHidden -> {
                val shouldHide = event.isHidden
                getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE).edit { putBoolean("is_icon_hidden", shouldHide) }
                viewModel.syncIconState(shouldHide)
                Toast.makeText(this, "设置已保存，可能需要重启桌面才能生效", Toast.LENGTH_SHORT).show()
            }

            MainUiEvent.OpenExtend -> startActivity(Intent(this, ExtendActivity::class.java))
            MainUiEvent.ClearConfig -> {
                // 🔥 这里只负责执行逻辑，不再负责弹窗
                if (Files.delFile(Files.CONFIG_DIR)) {
                    ToastUtil.showToast(this, "🙂 清空配置成功")
                    // 可选：重载配置或刷新 UI
                    viewModel.refreshUserConfigs()
                } else {
                    ToastUtil.showToast(this, "😭 清空配置失败")
                }
            }
        }
    }

    private fun openLogChannel(channel: LogChannel) {
        openLogFile(Files.getLogFile(channel))
    }

    // --- 辅助方法 ---

    private fun setupShizuku() {
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        if (!Shizuku.pingBinder()) return

        val granted = checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Shizuku.requestPermission(1234)
            return
        }

        // 已授权时也主动触发一次轻量命令，刷新 ShellManager 选择状态，避免 UI 长期停留在 no_executor。
        if (hasPermissions) {
            lifecycleScope.launch {
                CommandUtil.executeCommand(this@MainActivity, "echo init_shizuku")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pendingPermissionRequest = null
        ensureModulePermissions(requestIfNeeded = true)
        CommandUtil.connect(applicationContext)
        if (hasPermissions) viewModel.refreshUserConfigs()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            CommandUtil.unbind(applicationContext)
        }
        if (!isChangingConfigurations && pendingPermissionRequest == null) {
            requestedPermissionsThisVisibility.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            CommandUtil.unbind(applicationContext)
        }
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }

    private fun openLogFile(logFile: File) {
        if (!logFile.exists()) {
            ToastUtil.showToast(this, "日志文件不存在: ${logFile.name}")
            return
        }
        val intent = Intent(this, LogViewerActivity::class.java).apply {
            data = logFile.toUri()
        }
        startActivity(intent)
    }

    private fun ensureModulePermissions(requestIfNeeded: Boolean) {
        hasPermissions = PermissionUtil.checkFilePermissions(this)
        if (!hasPermissions) {
            if (requestIfNeeded && shouldRequest(ModulePermissionRequest.FILE)) {
                PermissionUtil.checkOrRequestFilePermissions(this)
                return
            }
        } else {
            markResolved(ModulePermissionRequest.FILE)
            viewModel.initAppLogic()
        }

        if (!PermissionUtil.checkAlarmPermissions(this)) {
            if (requestIfNeeded && shouldRequest(ModulePermissionRequest.EXACT_ALARM)) {
                PermissionUtil.checkOrRequestAlarmPermissions(this)
                return
            }
        } else {
            markResolved(ModulePermissionRequest.EXACT_ALARM)
        }

        if (shouldRequestBatteryPermission() && !PermissionUtil.checkBatteryPermissions(this)) {
            if (requestIfNeeded && shouldRequest(ModulePermissionRequest.BATTERY)) {
                PermissionUtil.checkOrRequestBatteryPermissions(this)
                return
            }
        } else {
            markResolved(ModulePermissionRequest.BATTERY)
        }

        if (!PermissionUtil.checkNotificationPermission(this)) {
            if (requestIfNeeded && shouldRequest(ModulePermissionRequest.NOTIFICATION)) {
                PermissionUtil.checkOrRequestNotificationPermission(this)
                return
            }
        } else {
            markResolved(ModulePermissionRequest.NOTIFICATION)
        }
    }

    private fun shouldRequest(permission: ModulePermissionRequest): Boolean {
        if (pendingPermissionRequest == permission) {
            return false
        }
        if (!requestedPermissionsThisVisibility.add(permission)) {
            return false
        }
        pendingPermissionRequest = permission
        return true
    }

    private fun markResolved(permission: ModulePermissionRequest): Boolean {
        val wasRequested = requestedPermissionsThisVisibility.remove(permission)
        if (pendingPermissionRequest == permission) {
            pendingPermissionRequest = null
        }
        return wasRequested
    }

    private fun shouldRequestBatteryPermission(): Boolean {
        return runCatching {
            ensureConfigLoadedForPermissionChecks()
            BaseModel.batteryPerm.value == true
        }.getOrElse {
            Log.printStackTrace(TAG, "Load config for permission checks failed", it)
            true
        }
    }

    private fun ensureConfigLoadedForPermissionChecks() {
        if (Config.isLoaded()) {
            return
        }
        DataStore.init(Files.CONFIG_DIR)
        Model.initAllModel()
        val activeUserId = DataStore.get("activedUser", UserEntity::class.java)?.userId
        UserMap.setCurrentUserId(activeUserId)
        Config.load(activeUserId)
    }
}

