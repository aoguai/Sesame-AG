package fansirsqi.xposed.sesame.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.core.type.TypeReference
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.model.CustomSettings
import fansirsqi.xposed.sesame.util.DataStore
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil
import rikka.shizuku.Shizuku

// 定义菜单项数据类
data class MenuItem(
    val title: String,
    val onClick: () -> Unit
)

// 定义各种弹窗类型
sealed class ExtendDialog {
    data object None : ExtendDialog()

    // 清空图片确认框
    data class ClearPhotoConfirm(val count: Int) : ExtendDialog()

    // 写入光盘测试框
    data class WritePhotoTest(val message: String) : ExtendDialog()

    // 通用输入框 (用于获取DataStore / BaseUrl)
    data class InputDialog(
        val title: String,
        val initialValue: String = "",
        val onConfirm: (String) -> Unit
    ) : ExtendDialog()
}

class ExtendViewModel : ViewModel() {

    // 列表项数据
    val menuItems = mutableStateListOf<MenuItem>()

    // 当前显示的弹窗状态
    var currentDialog by mutableStateOf<ExtendDialog>(ExtendDialog.None)
        private set

    // 初始化数据
    fun loadData(context: Context) {
        menuItems.clear()

        // 1. 广播类功能
        val debugTips = context.getString(R.string.debug_tips)

        fun addBroadcastItem(titleResId: Int, type: String) {
            menuItems.add(MenuItem(context.getString(titleResId)) {
                sendItemsBroadcast(context, type)
                ToastUtil.makeText(context, debugTips, 0).show()
            })
        }

        addBroadcastItem(R.string.query_the_remaining_amount_of_saplings, "getTreeItems")
        addBroadcastItem(R.string.search_for_new_items_on_saplings, "getNewTreeItems")
        addBroadcastItem(R.string.search_for_unlocked_regions, "queryAreaTrees")
        addBroadcastItem(R.string.search_for_unlocked_items, "getUnlockTreeItems")
        menuItems.add(MenuItem("查询被浇水情况") {
            sendItemsBroadcast(context, "getWateredItems")
            ToastUtil.makeText(context, debugTips, 0).show()
        })
        menuItems.add(MenuItem("查询浇水情况") {
            sendItemsBroadcast(context, "getWateringItems")
            ToastUtil.makeText(context, debugTips, 0).show()
        })

        // 2. 清空图片
        menuItems.add(MenuItem(context.getString(R.string.clear_photo)) {
            val currentCount = DataStore
                .getOrCreate("plate", object : TypeReference<List<Map<String, String>>>() {})
                .size
            currentDialog = ExtendDialog.ClearPhotoConfirm(currentCount)
        })

        // 3. 每日单次运行 (特殊处理：调用原有逻辑)
        menuItems.add(MenuItem("每日单次运行设置") {
            CustomSettings.showSingleRunMenu(context) { loadData(context) }
        })

        // 4. Debug 功能
        menuItems.add(MenuItem("写入光盘") {
            currentDialog = ExtendDialog.WritePhotoTest("xxxx")
        })

        menuItems.add(MenuItem("获取DataStore字段") {
            currentDialog = ExtendDialog.InputDialog("输入字段Key") { key ->
                handleGetDataStore(context, key)
            }
        })

        menuItems.add(MenuItem("TestShow") {
            ToastUtil.showToast(context, "shizuku:" + isShizukuReady().toString())
        })
    }

    // --- 业务逻辑 ---

    fun dismissDialog() {
        currentDialog = ExtendDialog.None
    }

    fun clearPhotos(context: Context) {
        DataStore.remove("plate")
        ToastUtil.showToast(context, "光盘行动图片清空成功")
        dismissDialog()
    }

    fun writePhotoTest(context: Context) {
        val newPhotoEntry = mapOf(
            "before" to "before${FansirsqiUtil.getRandomString(10)}",
            "after" to "after${FansirsqiUtil.getRandomString(10)}"
        )
        val existingPhotos = DataStore.getOrCreate(
            "plate",
            object : TypeReference<MutableList<Map<String, String>>>() {})
        existingPhotos.add(newPhotoEntry)
        DataStore.put("plate", existingPhotos)
        ToastUtil.showToast(context, "写入成功$newPhotoEntry")
        dismissDialog()
    }

    private fun handleGetDataStore(context: Context, key: String) {
        val value: Any = try {
            DataStore.getOrCreate(key, object : TypeReference<Map<*, *>>() {})
        } catch (_: Exception) {
            DataStore.getOrCreate(key, object : TypeReference<String>() {})
        }
        ToastUtil.showToast(context, "$value \n输入内容: $key")
        dismissDialog()
    }

    private fun sendItemsBroadcast(context: Context, type: String) {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest").apply {
            putExtra("method", "")
            putExtra("data", "")
            putExtra("type", type)
        }
        context.sendBroadcast(intent)
        Log.debug("ExtendViewModel", "扩展工具主动调用广播查询📢：$type")
    }

    private fun isShizukuReady(): Boolean {
        return try {
            val isBinderAlive = Shizuku.pingBinder()
            val hasPermission = if (isBinderAlive) Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED else false
            isBinderAlive && hasPermission
        } catch (_: Exception) {
            false
        }
    }
}
