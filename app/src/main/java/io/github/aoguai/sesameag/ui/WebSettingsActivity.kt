package io.github.aoguai.sesameag.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.fasterxml.jackson.core.type.TypeReference
import io.github.aoguai.sesameag.BuildConfig
import io.github.aoguai.sesameag.R
import io.github.aoguai.sesameag.data.Config
import io.github.aoguai.sesameag.data.Status
import io.github.aoguai.sesameag.entity.AlipayUser
import io.github.aoguai.sesameag.hook.ApplicationHookConstants
import io.github.aoguai.sesameag.model.Model
import io.github.aoguai.sesameag.model.ModelConfig
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.model.ModelGroup
import io.github.aoguai.sesameag.model.ModelFields
import io.github.aoguai.sesameag.model.SelectModelFieldFunc
import io.github.aoguai.sesameag.ui.dto.ModelDto
import io.github.aoguai.sesameag.ui.dto.ModelFieldInfoDto
import io.github.aoguai.sesameag.ui.dto.ModelFieldShowDto
import io.github.aoguai.sesameag.ui.dto.ModelGroupDto
import io.github.aoguai.sesameag.ui.widget.ListDialog
import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.GlobalThreadPools
import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.LanguageUtil
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.PortUtil
import io.github.aoguai.sesameag.util.ToastUtil
import io.github.aoguai.sesameag.util.maps.BeachMap
import io.github.aoguai.sesameag.util.maps.CooperateMap
import io.github.aoguai.sesameag.util.maps.IdMapManager
import io.github.aoguai.sesameag.util.maps.MemberBenefitsMap
import io.github.aoguai.sesameag.util.maps.ParadiseCoinBenefitIdMap
import io.github.aoguai.sesameag.util.maps.ReserveaMap
import io.github.aoguai.sesameag.util.maps.SesameGiftMap
import io.github.aoguai.sesameag.util.maps.UserMap
import io.github.aoguai.sesameag.util.maps.VitalityRewardsMap
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers

class WebSettingsActivity : BaseActivity() {

    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>
    private lateinit var webView: WebView
    private lateinit var context: Context
    private var userId: String? = null
    private var userName: String? = null
    private val tabList = ArrayList<ModelDto>()
    private val groupList = ArrayList<ModelGroupDto>()

    @SuppressLint("MissingInflatedId", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        context = this
        userId = intent?.getStringExtra("userId")
        userName = intent?.getStringExtra("userName")

        LanguageUtil.setLocale(this)
        setContentView(R.layout.activity_web_settings)

        // BaseActivity 采用 pendingSubtitle 驱动 Toolbar；这里直接赋值即可
        baseSubtitle = userName?.let { "${getString(R.string.settings)}: $it" } ?: getString(R.string.settings)

        webView = findViewById(R.id.webView)
        val progressBar: ProgressBar? = findViewById(R.id.progress_bar)

        // 返回键：优先 WebView 后退，否则保存并退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    Log.record(TAG, "WebSettingsActivity.handleOnBackPressed: go back")
                    webView.goBack()
                } else {
                    Log.record(TAG, "WebSettingsActivity.handleOnBackPressed: save")
                    save()
                    finish()
                }
            }
        })

        // 初始化导出逻辑（必须在 onCreate 中注册）
        exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                PortUtil.handleExport(this, result.data?.data, userId)
            }
        }

        // 初始化导入逻辑（必须在 onCreate 中注册）
        importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                PortUtil.handleImport(this, result.data?.data, userId)
            }
        }

        // 先展示 loading，后台加载配置与映射，避免阻塞 UI
        progressBar?.visibility = View.VISIBLE
        webView.visibility = View.GONE

        GlobalThreadPools.execute(Dispatchers.IO) {
            try {
                Model.initAllModel()
                UserMap.setCurrentUserId(userId)
                UserMap.load(userId)

                IdMapManager.getInstance(CooperateMap::class.java).load(userId)
                IdMapManager.getInstance(VitalityRewardsMap::class.java).load(userId)
                IdMapManager.getInstance(MemberBenefitsMap::class.java).load(userId)
                IdMapManager.getInstance(SesameGiftMap::class.java).load(userId)
                IdMapManager.getInstance(ParadiseCoinBenefitIdMap::class.java).load(userId)
                IdMapManager.getInstance(ReserveaMap::class.java).load()
                IdMapManager.getInstance(BeachMap::class.java).load()

                userId?.let { Status.load(it) }
                Config.load(userId)

                runOnUiThread {
                    try {
                        progressBar?.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                        initializeWebView()
                    } catch (e: Exception) {
                        Log.printStackTrace(TAG, "WebSettingsActivity.initializeWebView failed", e)
                        Toast.makeText(this@WebSettingsActivity, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.printStackTrace(TAG, "WebSettingsActivity load failed", e)
                runOnUiThread {
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this@WebSettingsActivity, "加载配置失败: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun initializeWebView() {
        webView.settings.apply {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            defaultTextEncodingName = StandardCharsets.UTF_8.name()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                Log.error(TAG, "WebView加载错误: code=${error.errorCode}, desc=${error.description}, url=${request.url}")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val requestUrl = request.url
                val scheme = requestUrl.scheme
                return when {
                    scheme.equals("http", ignoreCase = true) ||
                        scheme.equals("https", ignoreCase = true) ||
                        scheme.equals("ws", ignoreCase = true) ||
                        scheme.equals("wss", ignoreCase = true) -> {
                        view.loadUrl(requestUrl.toString())
                        true
                    }
                    else -> {
                        view.stopLoading()
                        Toast.makeText(context, "Forbidden Scheme:\"$scheme\"", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.runtime(
                        TAG,
                        "WebView Console [${it.messageLevel()}]: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                    )
                }
                return true
            }
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // 先构建数据，避免页面启动过快时拿到空列表
        tabList.clear()
        groupList.clear()

        val modelConfigMap: Map<String, ModelConfig> = Model.getModelConfigMap()
        for ((code, modelConfig) in modelConfigMap.entries) {
            tabList.add(
                ModelDto(
                    modelCode = code,
                    modelName = modelConfig.name,
                    modelIcon = modelConfig.icon,
                    groupCode = modelConfig.group?.code ?: "",
                    modelFields = emptyList()
                )
            )
        }
        for (modelGroup in ModelGroup.values()) {
            groupList.add(ModelGroupDto(modelGroup.code, modelGroup.groupName, modelGroup.icon))
        }

        webView.addJavascriptInterface(WebViewCallback(), "HOOK")
        webView.loadUrl("file:///android_asset/web/semi_index.html")
        webView.requestFocus()
    }

    inner class WebViewCallback {
        @JavascriptInterface
        fun getTabs(): String {
            return JsonUtil.formatJson(tabList, false)
        }

        /**
         * 新增：检查当前系统是否为深色模式
         */
        @JavascriptInterface
        fun isNightMode(): Boolean {
            val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        }

        @JavascriptInterface
        fun getBuildInfo(): String {
            return "${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_NAME}"
        }

        @JavascriptInterface
        fun getGroup(): String {
            return JsonUtil.formatJson(groupList, false)
        }

        @JavascriptInterface
        fun getModelByGroup(groupCode: String): String {
            val modelGroup = ModelGroup.getByCode(groupCode) ?: return "[]"
            val modelConfigCollection = Model.getGroupModelConfig(modelGroup).values
            val modelDtoList = ArrayList<ModelDto>()
            for (modelConfig in modelConfigCollection) {
                val modelFields = ArrayList<ModelFieldShowDto>()
                for (modelField in modelConfig.fields.values) {
                    modelFields.add(ModelFieldShowDto.toShowDto(modelConfig.code, modelConfig.fields, modelField))
                }
                modelDtoList.add(ModelDto(modelConfig.code, modelConfig.name, modelConfig.icon, groupCode, modelFields))
            }
            return JsonUtil.formatJson(modelDtoList, false)
        }

        @JavascriptInterface
        fun setModelByGroup(groupCode: String, modelsValue: String): String {
            val modelDtoList = JsonUtil.parseObject(modelsValue, object : TypeReference<List<ModelDto>>() {})
            val modelGroup = ModelGroup.getByCode(groupCode) ?: return "FAILED"
            val modelConfigSet = Model.getGroupModelConfig(modelGroup)

            for (modelDto in modelDtoList) {
                val modelConfig = modelConfigSet[modelDto.modelCode] ?: continue
                val modelFields = modelDto.modelFields
                for (newModelField in modelFields) {
                    val modelField = modelConfig.getModelField(newModelField.code) ?: continue
                    modelField.setConfigValue(newModelField.configValue as String?)
                }
            }
            return "SUCCESS"
        }

        @JavascriptInterface
        fun getModel(modelCode: String): String {
            val modelConfig = Model.getModelConfigMap()[modelCode] ?: return "[]"

            val modelFields: ModelFields = modelConfig.fields
            val list = ArrayList<ModelFieldShowDto>()
            for (modelField: ModelField<*> in modelFields.values) {
                list.add(ModelFieldShowDto.toShowDto(modelConfig.code, modelFields, modelField))
            }
            return JsonUtil.formatJson(list, false)
        }

        @JavascriptInterface
        fun setModel(modelCode: String, fieldsValue: String): String {
            val modelConfig = Model.getModelConfigMap()[modelCode] ?: return "FAILED"
            try {
                val modelFields: ModelFields = modelConfig.fields
                val map: Map<String, ModelFieldShowDto> = JsonUtil.parseObject(
                    fieldsValue,
                    object : TypeReference<Map<String, ModelFieldShowDto>>() {}
                )
                for ((fieldCode, newModelField) in map.entries) {
                    val modelField = modelFields[fieldCode] ?: continue
                    modelField.setConfigValue(newModelField.configValue)
                }
                return "SUCCESS"
            } catch (e: Exception) {
                Log.printStackTrace("WebSettingsActivity", e)
            }
            return "FAILED"
        }

        @JavascriptInterface
        fun getField(modelCode: String, fieldCode: String): String? {
            val modelConfig = Model.getModelConfigMap()[modelCode] ?: return null
            val modelField = modelConfig.getModelField(fieldCode) ?: return null
            return JsonUtil.formatJson(ModelFieldInfoDto.toInfoDto(modelField), false)
        }

        @JavascriptInterface
        fun setField(modelCode: String, fieldCode: String, fieldValue: String): String {
            val modelConfig = Model.getModelConfigMap()[modelCode] ?: return "FAILED"
            return try {
                val modelField = modelConfig.getModelField(fieldCode) ?: return "FAILED"
                modelField.setConfigValue(fieldValue)
                "SUCCESS"
            } catch (e: Exception) {
                Log.printStackTrace(e)
                "FAILED"
            }
        }

        /**
         * 保存并退出：
         * 前端调用 window.HOOK.saveOnExit() 时触发
         */
        @JavascriptInterface
        fun saveOnExit(): Boolean {
            runOnUiThread {
                Log.record(TAG, "WebViewCallback: saveOnExit called")
                save()
                finish()
            }
            return true
        }

        @JavascriptInterface
        fun Log(log: String) {
            Log.record(TAG, "设置：$log")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 1, "导出配置")
        menu.add(0, 2, 2, "导入配置")
        menu.add(0, 3, 3, "删除配置")
        menu.add(0, 4, 4, "单向好友")
        menu.add(0, 6, 6, "保存")
        menu.add(0, 7, 7, "复制ID")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, "[$userName]-config_v2.json")
                }
                exportLauncher.launch(exportIntent)
            }
            2 -> {
                val importIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, "config_v2.json")
                }
                importLauncher.launch(importIntent)
            }
            3 -> {
                AlertDialog.Builder(context)
                    .setTitle("警告")
                    .setMessage("确认删除该配置？")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val currentUserId = userId
                        val userConfigDirectoryFile: File = if (currentUserId.isNullOrEmpty()) {
                            Files.getDefaultConfigV2File()
                        } else {
                            Files.getUserConfigDir(currentUserId)
                        }
                        if (Files.delFile(userConfigDirectoryFile)) {
                            Toast.makeText(this, "配置删除成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "配置删除失败", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .create()
                    .show()
            }
            4 -> {
                ListDialog.show(
                    this,
                    "单向好友列表",
                    AlipayUser.getList { user -> user.friendStatus != 1 },
                    SelectModelFieldFunc.newMapInstance(),
                    false,
                    ListDialog.ListType.SHOW
                )
            }
            6 -> {
                save()
            }
            7 -> {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = ClipData.newPlainText("userId", userId)
                cm.setPrimaryClip(clipData)
                ToastUtil.showToastWithDelay(this, "复制成功！", 100)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() {
        // 与 TK3 对齐：强制保存，避免 isModify 误判导致用户点击“保存”却未落盘
        if (Config.save(userId, true)) {
            Toast.makeText(context, "保存成功！", Toast.LENGTH_SHORT).show()
            if (!userId.isNullOrEmpty()) {
                try {
                    val intent = Intent(ApplicationHookConstants.BroadcastActions.RESTART).apply {
                        putExtra("userId", userId)
                        putExtra("configReload", true)
                    }
                    sendBroadcast(intent)
                } catch (th: Throwable) {
                    Log.printStackTrace(th)
                }
            }
        } else {
            Toast.makeText(context, "保存失败！", Toast.LENGTH_SHORT).show()
        }

        val currentUserId = userId
        if (!currentUserId.isNullOrEmpty()) {
            UserMap.save(currentUserId)
            IdMapManager.getInstance(CooperateMap::class.java).save(currentUserId)
        }
    }

    companion object {
        private const val TAG = "WebSettingsActivity"
    }
}

