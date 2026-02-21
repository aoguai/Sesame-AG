package fansirsqi.xposed.sesame.model

import android.content.Context
import androidx.appcompat.app.AlertDialog
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.entity.MapperEntity
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.ListModelField.ListJoinCommaToStringModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.ui.widget.ListDialog
import fansirsqi.xposed.sesame.util.FansirsqiUtil
import fansirsqi.xposed.sesame.util.Files
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.ListUtil
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.TimeUtil
import fansirsqi.xposed.sesame.util.ToastUtil
import fansirsqi.xposed.sesame.util.maps.UserMap
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自定义设置管理类
 * 负责“每日单次运行”功能的逻辑封装、配置持久化及 UI 交互。
 */
object CustomSettings {
    private const val TAG = "CustomSettings"

    val onlyOnceDaily = BooleanModelField("onlyOnceDaily", "选中每日只运行一次的模块", false)
    val autoHandleOnceDaily = BooleanModelField("autoHandleOnceDaily", "定时自动关闭单次运行", false)

    val autoHandleOnceDailyTimes = ListJoinCommaToStringModelField(
        "autoHandleOnceDailyTimes",
        "自动全量时间点",
        ListUtil.newArrayList("0600", "2000")
    )

    val onlyOnceDailyList = SelectModelField(
        "onlyOnceDailyList",
        "每日只运行一次 | 模块选择",
        LinkedHashSet<String>().apply {
            add("antOrchard")
            add("antCooperate")
            add("antSports")
            add("antMember")
            add("EcoProtection")
            add("greenFinance")
            add("reserve")
        },
        getModuleList()
    )

    private fun getModuleList(): List<MapperEntity> {
        return listOf(
            SimpleEntity("antForest", "蚂蚁森林"),
            SimpleEntity("antFarm", "蚂蚁庄园"),
            SimpleEntity("antOcean", "海洋"),
            SimpleEntity("antOrchard", "农场"),
            SimpleEntity("antStall", "新村"),
            SimpleEntity("antDodo", "神奇物种"),
            SimpleEntity("antCooperate", "蚂蚁森林合种"),
            SimpleEntity("antSports", "运动"),
            SimpleEntity("antMember", "会员"),
            SimpleEntity("EcoProtection", "生态保护"),
            SimpleEntity("greenFinance", "绿色经营"),
            SimpleEntity("reserve", "保护地"),
            SimpleEntity("other", "其他任务")
        )
    }

    private fun getUserDisplayNameList(): Pair<List<String>, List<String>> {
        val uids = FansirsqiUtil.getFolderList(Files.CONFIG_DIR.absolutePath)
        val displayNames = mutableListOf<String>()
        val validUids = mutableListOf<String>()
        val backupUid = UserMap.currentUid
        uids.forEach { uid ->
            try {
                UserMap.loadSelf(uid)
                val user = UserMap.get(uid)
                displayNames.add(user?.showName ?: uid)
                validUids.add(uid)
            } catch (e: Exception) {
                displayNames.add(uid)
                validUids.add(uid)
            }
        }
        if (!backupUid.isNullOrEmpty()) {
            UserMap.setCurrentUserId(backupUid)
            UserMap.loadSelf(backupUid)
        }
        return Pair(displayNames, validUids)
    }

    private fun resetToDefault() {
        onlyOnceDaily.setObjectValue(false)
        autoHandleOnceDaily.setObjectValue(false)
        autoHandleOnceDailyTimes.setObjectValue(ListUtil.newArrayList("0600", "2000"))
        val defaultSet = LinkedHashSet<String>().apply {
            add("antOrchard")
            add("antCooperate")
            add("antSports")
            add("antMember")
            add("EcoProtection")
            add("greenFinance")
            add("reserve")
        }
        onlyOnceDailyList.setObjectValue(defaultSet)
    }

    @JvmStatic
    fun save(userId: String) {
        if (userId.isEmpty()) return
        try {
            val file = Files.getCustomSetFile(userId)
            val data = mutableMapOf<String, Any?>()
            data[onlyOnceDaily.code] = onlyOnceDaily.value
            data[onlyOnceDailyList.code] = onlyOnceDailyList.value
            data[autoHandleOnceDaily.code] = autoHandleOnceDaily.value
            data[autoHandleOnceDailyTimes.code] = autoHandleOnceDailyTimes.value
            val json = JsonUtil.formatJson(data)
            if (json != null) Files.write2File(json, file!!)
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "Failed to save custom settings", e)
        }
    }

    @JvmStatic
    fun load(userId: String) {
        if (userId.isEmpty()) return
        try {
            val file = Files.getCustomSetFile(userId)
            if (!file!!.exists()) {
                resetToDefault()
                return
            }
            val json = Files.readFromFile(file)
            if (json.isEmpty()) {
                resetToDefault()
                return
            }
            val data = JsonUtil.copyMapper().readValue(json, Map::class.java)
            if (data.containsKey(onlyOnceDaily.code)) onlyOnceDaily.setObjectValue(data[onlyOnceDaily.code])
            if (data.containsKey(onlyOnceDailyList.code)) onlyOnceDailyList.setObjectValue(data[onlyOnceDailyList.code])
            if (data.containsKey(autoHandleOnceDaily.code)) autoHandleOnceDaily.setObjectValue(data[autoHandleOnceDaily.code])
            if (data.containsKey(autoHandleOnceDailyTimes.code)) autoHandleOnceDailyTimes.setObjectValue(data[autoHandleOnceDailyTimes.code])
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "Failed to load custom settings", e)
        }
    }

    fun loadForTaskRunner() {
        val currentUid = UserMap.currentUid
        if (!currentUid.isNullOrEmpty()) load(currentUid)
    }

    fun getModuleId(taskInfo: String?): String? {
        if (taskInfo == null) return null
        return when {
            taskInfo.contains("合种") || taskInfo.contains("antCooperate") -> "antCooperate"
            taskInfo.contains("蚂蚁森林") || taskInfo.contains("antForest") -> "antForest"
            taskInfo.contains("蚂蚁庄园") || taskInfo.contains("antFarm") -> "antFarm"
            taskInfo.contains("海洋") || taskInfo.contains("antOcean") -> "antOcean"
            taskInfo.contains("农场") || taskInfo.contains("antOrchard") -> "antOrchard"
            taskInfo.contains("新村") || taskInfo.contains("antStall") -> "antStall"
            taskInfo.contains("神奇物种") || taskInfo.contains("antDodo") -> "antDodo"
            taskInfo.contains("运动") || taskInfo.contains("antSports") -> "antSports"
            taskInfo.contains("会员") || taskInfo.contains("antMember") -> "antMember"
            taskInfo.contains("生态保护") || taskInfo.contains("EcoProtection") -> "EcoProtection"
            taskInfo.contains("绿色经营") || taskInfo.contains("greenFinance") -> "greenFinance"
            taskInfo.contains("保护地") || taskInfo.contains("reserve") -> "reserve"
            taskInfo.contains("其他任务") || taskInfo.contains("other") -> "other"
            else -> null
        }
    }

    fun isOnceDailyBlackListed(taskInfo: String?, status: OnceDailyStatus? = null): Boolean {
        val s = status ?: getOnceDailyStatus(false)
        // 只有当单次运行模式生效，且今日已经完成过首轮全量运行的情况下，才执行黑名单排除
        if (s.isEnabledOverride && s.isFinishedToday) {
            val moduleId = getModuleId(taskInfo)
            if (moduleId != null) {
                return onlyOnceDailyList.value.contains(moduleId)
            }
        }
        return false
    }

    data class OnceDailyStatus(
        val isEnabledOverride: Boolean,
        val isFinishedToday: Boolean
    )

    @JvmStatic
    fun getOnceDailyStatus(enableLog: Boolean = false): OnceDailyStatus {
        val configEnabled = onlyOnceDaily.value == true
        val isFinished = try {
            Status.hasFlagToday("OnceDaily::Finished")
        } catch (e: Throwable) {
            false
        }

        val now = System.currentTimeMillis()
        val interval = BaseModel.checkInterval.value.toLong()
        val isSpecialTime = autoHandleOnceDailyTimes.value.any { timeStr ->
            val startCal = TimeUtil.getTodayCalendarByTimeStr(timeStr)
            if (startCal != null) {
                val startTime = startCal.timeInMillis
                val endTime = startTime + interval
                now in startTime..endTime
            } else {
                false
            }
        }

        var isEnabled = configEnabled

        if (isSpecialTime && autoHandleOnceDaily.value) {
            isEnabled = false
            if (enableLog) Log.record("自动单次运行触发: 现在处于自动全量运行时段，本次将运行所有已开启的任务")
        } else if (enableLog && autoHandleOnceDaily.value) {
            val sdf = SimpleDateFormat("HHmm", Locale.getDefault())
            val ranges = autoHandleOnceDailyTimes.value.mapNotNull { timeStr ->
                TimeUtil.getTodayCalendarByTimeStr(timeStr)?.let {
                    val endTime = it.timeInMillis + interval
                    "$timeStr-${sdf.format(Date(endTime))}"
                }
            }.joinToString(", ")
            Log.record("已设置自动全量运行，时段为：$ranges")
        }

        // 如果今日尚未完成首次全量运行，则不启用“跳过”拦截逻辑
        if (isEnabled && !isFinished) {
            isEnabled = false
            if (enableLog) Log.record("当日单次运行模式生效: 今日尚未完成首次全量运行，本次将运行所有任务")
        } else if (isEnabled) {
            if (enableLog) Log.record("当日单次运行模式生效: 今日已完成全量运行，已启用跳过黑名单任务")
        }

        return OnceDailyStatus(isEnabled, isFinished)
    }


    @JvmStatic
    fun showSingleRunMenu(context: Context, onRefresh: () -> Unit) {
        val (displayNames, userIds) = getUserDisplayNameList()
        if (userIds.isEmpty()) {
            ToastUtil.showToast(context, "未发现任何用户配置")
            return
        }
        AlertDialog.Builder(context)
            .setTitle("请选择操作目标账号")
            .setItems(displayNames.toTypedArray()) { _, which ->
                val selectedUid = userIds[which]
                val selectedShowName = displayNames[which]
                load(selectedUid)
                showAccountOps(context, selectedUid, selectedShowName, onRefresh)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAccountOps(context: Context, uid: String, showName: String, onRefresh: () -> Unit) {
        val isFinished = try {
            Status.hasFlagToday("OnceDaily::Finished")
        } catch (e: Throwable) {
            false
        }
        val statusText = when {
            !onlyOnceDaily.value -> "单次运行：已关闭"
            autoHandleOnceDaily.value -> "单次运行：自动模式"
            isFinished -> "单次运行：今日已完成"
            else -> "单次运行：已开启"
        }
        val ops = arrayOf(statusText, "设置黑名单模块", "设置非单次运行的时段")
        AlertDialog.Builder(context)
            .setTitle("账号：$showName")
            .setItems(ops) { _, which ->
                if (which == 0) {
                    val currentOnlyOnce = onlyOnceDaily.value == true
                    val currentAuto = autoHandleOnceDaily.value == true
                    if (!currentOnlyOnce) {
                        onlyOnceDaily.value = true
                        autoHandleOnceDaily.value = false
                    } else if (!currentAuto) {
                        autoHandleOnceDaily.value = true
                    } else {
                        onlyOnceDaily.value = false
                        autoHandleOnceDaily.value = false
                    }
                    save(uid)
                    onRefresh()
                    showAccountOps(context, uid, showName, onRefresh)
                } else if (which == 1) {
                    ListDialog.show(context, "黑名单 | $showName", onlyOnceDailyList)
                    try {
                        val dialogField: Field = ListDialog::class.java.getDeclaredField("listDialog")
                        dialogField.isAccessible = true
                        val dialog = dialogField.get(null) as? androidx.appcompat.app.AlertDialog
                        dialog?.setOnDismissListener { save(uid) }
                    } catch (e: Exception) {
                    }
                } else if (which == 2) {
                    val edt = android.widget.EditText(context)
                    edt.setText(autoHandleOnceDailyTimes.configValue)
                    AlertDialog.Builder(context)
                        .setTitle("设置 ${showName} 非单次运行时段")
                        .setMessage("输入开始时间点(如0600)，多个用逗号隔开。时段为该时间点加\"设置\"中的执行间隔时间")
                        .setView(edt)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            autoHandleOnceDailyTimes.setConfigValue(edt.text.toString())
                            save(uid)
                            showAccountOps(context, uid, showName, onRefresh)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }
}

private class SimpleEntity(id: String, name: String) : MapperEntity() {
    init {
        this.id = id
        this.name = name
    }
}
