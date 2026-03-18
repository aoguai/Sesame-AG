package fansirsqi.xposed.sesame.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fansirsqi.xposed.sesame.SesameApplication.Companion.PREFERENCES_KEY
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.service.ConnectionState
import fansirsqi.xposed.sesame.service.LsposedServiceManager
import fansirsqi.xposed.sesame.util.CommandUtil
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.DirectoryWatcher
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.IconManager
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {



    // --- 内部状态定义 ---
    sealed class ModuleStatus {
        data object Loading : ModuleStatus()
        data object NotActivated : ModuleStatus()
        data class Activated(
            val frameworkName: String,     // 框架名称 (LSPosed, LSPatch...)
            val frameworkVersion: String,  // 版本号 (LSPosed才有，其他可能为空)
            val apiVersion: Int            // API版本
        ) : ModuleStatus()
    }



    companion object {
        const val TAG = "MainViewModel"
    }

    // 1. 定义状态
    private val prefs = application.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE)

    private val _oneWord = MutableStateFlow("正在获取句子...")
    val oneWord: StateFlow<String> = _oneWord.asStateFlow()

    private val _isOneWordLoading = MutableStateFlow(false)
    val isOneWordLoading = _isOneWordLoading.asStateFlow()

    private val _moduleStatus = MutableStateFlow<ModuleStatus>(ModuleStatus.Loading)
    val moduleStatus: StateFlow<ModuleStatus> = _moduleStatus.asStateFlow()

    private val _activeUser = MutableStateFlow<UserEntity?>(null)
    val activeUser: StateFlow<UserEntity?> = _activeUser.asStateFlow()

    private val _userList = MutableStateFlow<List<UserEntity>>(emptyList())
    val userList: StateFlow<List<UserEntity>> = _userList.asStateFlow()

    // --- 监听器 ---

    // 监听 LSPosed 服务连接 (仅用于更新详细版本信息)
    private val serviceListener: (ConnectionState) -> Unit = { _ ->
        refreshModuleFrameworkStatus()
    }


    private var isInitialized = false

    fun initAppLogic() {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(Dispatchers.IO) {
            initEnvironment()

            // 加载初始数据
            refreshUserConfigs()
            fetchOneWord()
            // 初始检查状态
            refreshModuleFrameworkStatus()
            refreshActiveUser()
            // 🔥 新增：触发 CommandService 连接
            // 连接成功后，AIDL 回调会自动更新 serviceStatus
            CommandUtil.connect(getApplication())

            // 注册监听
            LsposedServiceManager.addConnectionListener(serviceListener)
            startConfigDirectoryObserver()
        }
    }

    override fun onCleared() {
        super.onCleared()
        LsposedServiceManager.removeConnectionListener(serviceListener)
    }



    /**
     * 刷新模块框架激活状态
     */
    private fun refreshModuleFrameworkStatus() {
        val lspState = LsposedServiceManager.connectionState

        if (lspState is ConnectionState.Connected) {
            val service = lspState.service
            _moduleStatus.value = ModuleStatus.Activated(
                frameworkName = runCatching { service.frameworkName }.getOrDefault("Xposed"),
                frameworkVersion = runCatching { service.frameworkVersion }.getOrDefault(""),
                apiVersion = runCatching { service.apiVersion }.getOrDefault(100)
            )
        } else {
            _moduleStatus.value = ModuleStatus.NotActivated
        }
    }

    /**
     * 刷新当前激活用户
     * 从 DataStore (文件) 读取
     */
    private fun refreshActiveUser() {
        try {
            val activeUserEntity = DataStore.get("activedUser", UserEntity::class.java)
            _activeUser.value = activeUserEntity
        } catch (e: Exception) {
            Log.e(TAG, "Read active user failed", e)
            _activeUser.value = null
        }
    }

    @OptIn(FlowPreview::class)
    private fun startConfigDirectoryObserver() {
        viewModelScope.launch(Dispatchers.IO) {
            DirectoryWatcher.observeDirectoryChanges(Files.CONFIG_DIR)
                .debounce(100)
                .collectLatest {
                    refreshUserConfigs()
                    refreshActiveUser()
                }
        }
    }

    /**
     * 刷新用户配置
     */
    fun refreshUserConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val latestUserIds = FansirsqiUtil.getFolderList(Files.CONFIG_DIR.absolutePath)
                val newList = mutableListOf<UserEntity>()
                for (userId in latestUserIds) {
                    UserMap.loadSelf(userId)
                    UserMap.get(userId)?.let { newList.add(it) }
                }
                _userList.value = newList
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading user configs", e)
            }
        }
    }


    private fun initEnvironment() {
        try {
            LsposedServiceManager.init()
            DataStore.init(Files.CONFIG_DIR)
        } catch (e: Exception) {
            Log.e(TAG, "Environment init failed", e)
        }
    }

    fun fetchOneWord() {
        viewModelScope.launch {
            _isOneWordLoading.value = true
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { FansirsqiUtil.getOneWord() }
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 2500) delay(500 - elapsedTime)
            _oneWord.value = result
            _isOneWordLoading.value = false
        }
    }

    fun syncIconState(isHidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            IconManager.syncIconState(getApplication(), isHidden)
        }
    }
}
