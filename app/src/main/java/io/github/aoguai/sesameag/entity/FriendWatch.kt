package io.github.aoguai.sesameag.entity

import io.github.aoguai.sesameag.util.Files
import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.StringUtil
import io.github.aoguai.sesameag.util.TimeUtil
import io.github.aoguai.sesameag.util.maps.UserMap
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * 好友收取能量记录实体类
 *
 * **迁移说明**:
 * - 将Lombok的@Getter/@Setter替换为Kotlin属性
 * - 静态方法移至companion object
 * - 使用Kotlin的可空类型和扩展函数
 * 
 * @property id 用户ID
 * @property name 用户名称
 * @property startTime 开始统计时间
 * @property allGet 总收取能量
 * @property weekGet 周收取能量
 */
class FriendWatch(
    id: String,
    name: String,
    var startTime: String = "",
    var allGet: Int = 0,
    var weekGet: Int = 0
) : MapperEntity() {

    init {
        this.id = id
        this.name = name
    }

    override fun compareTo(other: MapperEntity): Int {
        if (other !is FriendWatch) {
            return super.compareTo(other)
        }
        return when {
            this.weekGet > other.weekGet -> -1
            this.weekGet < other.weekGet -> 1
            else -> super.compareTo(other)
        }
    }

    companion object {
        private const val TAG = "FriendWatch"

        @Volatile
        private var joFriendWatch: JSONObject = JSONObject()

        @JvmStatic
        fun getJoFriendWatch(): JSONObject = joFriendWatch

        @JvmStatic
        fun setJoFriendWatch(jo: JSONObject) {
            joFriendWatch = jo
        }

        /**
         * 记录好友收取能量
         */
        @JvmStatic
        fun friendWatch(id: String, collectedEnergy: Int) {
            try {
                val joSingle = joFriendWatch.optJSONObject(id) ?: JSONObject().apply {
                    put("name", UserMap.getMaskName(id))
                    put("allGet", 0)
                    put("startTime", TimeUtil.getDateStr())
                    joFriendWatch.put(id, this)
                }
                joSingle.put("weekGet", joSingle.optInt("weekGet", 0) + collectedEnergy)
            } catch (th: Throwable) {
                Log.runtime(TAG, "friendWatch err:")
                Log.printStackTrace(TAG, th)
            }
        }

        /**
         * 保存好友记录到文件
         */
        @JvmStatic
        @Synchronized
        fun save(userId: String) {
            try {
                val file = Files.getFriendWatchFile(userId) ?: return
                val notformat = joFriendWatch.toString()
                val formattedJson = JsonUtil.formatJson(joFriendWatch)
                val content = if (!formattedJson.isNullOrBlank()) formattedJson else notformat
                Files.write2File(content, file)
            } catch (e: Exception) {
                Log.runtime(TAG, "friendWatch save err:")
                Log.printStackTrace(TAG, e)
            }
        }

        /**
         * 更新每日/每周统计
         */
        @JvmStatic
        fun updateDay(userId: String) {
            val file = Files.getFriendWatchFile(userId) ?: return
            if (!needUpdateAll(file.lastModified())) {
                return
            }

            try {
                val dateStr = TimeUtil.getDateStr()
                val ids = joFriendWatch.keys()
                while (ids.hasNext()) {
                    val id = ids.next()
                    val joSingle = joFriendWatch.getJSONObject(id)
                    joSingle.apply {
                        put("name", optString("name"))
                        put("allGet", optInt("allGet", 0) + optInt("weekGet", 0))
                        put("weekGet", 0)
                        if (!has("startTime")) {
                            put("startTime", dateStr)
                        }
                    }
                    joFriendWatch.put(id, joSingle)
                }
                Files.write2File(joFriendWatch.toString(), file)
            } catch (th: Throwable) {
                Log.runtime(TAG, "friendWatchNewWeek err:")
                Log.printStackTrace(TAG, th)
            }
        }

        /**
         * 加载好友记录
         */
        @JvmStatic
        @Synchronized
        fun load(userId: String?): Boolean {
            return try {
                if (userId == null) return false

                val file = Files.getFriendWatchFile(userId) ?: return false
                val strFriendWatch = Files.readFromFile(file)
                
                joFriendWatch = if (strFriendWatch.isNotEmpty()) {
                    JSONObject(strFriendWatch)
                } else {
                    JSONObject()
                }
                true
            } catch (e: JSONException) {
                Log.printStackTrace(e)
                joFriendWatch = JSONObject()
                false
            }
        }

        /**
         * 卸载好友记录
         */
        @JvmStatic
        @Synchronized
        fun unload() {
            joFriendWatch = JSONObject()
        }

        /**
         * 判断是否需要更新（每周一更新）
         */
        @JvmStatic
        fun needUpdateAll(last: Long): Boolean {
            if (last == 0L) return true

            val cLast = Calendar.getInstance().apply {
                timeInMillis = last
            }
            val cNow = Calendar.getInstance()

            // 如果是同一天，不需要更新
            if (cLast.get(Calendar.DAY_OF_YEAR) == cNow.get(Calendar.DAY_OF_YEAR)) {
                return false
            }

            // 只在周一更新
            return cNow.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        }

        /**
         * 获取好友收取能量列表
         */
        @JvmStatic
        fun getList(userId: String): List<FriendWatch> {
            val list = ArrayList<FriendWatch>()
            val file = Files.getFriendWatchFile(userId) ?: return list
            val strFriendWatch = Files.readFromFile(file)

            try {
                val joFriendWatch = if (strFriendWatch.isNullOrEmpty()) {
                    JSONObject()
                } else {
                    JSONObject(strFriendWatch)
                }

                val ids = joFriendWatch.keys()
                while (ids.hasNext()) {
                    val id = ids.next()
                    val friend = joFriendWatch.optJSONObject(id) ?: JSONObject()
                    
                    val name = friend.optString("name")
                    val friendWatch = FriendWatch(id, name).apply {
                        startTime = friend.optString("startTime", "无")
                        weekGet = friend.optInt("weekGet", 0)
                        allGet = friend.optInt("allGet", 0) + weekGet
                        this.name = "$name(开始统计时间:$startTime)\n\n周收:$weekGet 总收:$allGet"
                    }
                    list.add(friendWatch)
                }
            } catch (t: Throwable) {
                Log.runtime(TAG, "FriendWatch getList: ")
                Log.printStackTrace(TAG, t)
                try {
                    file?.let { Files.write2File(JSONObject().toString(), it) }
                } catch (e: Exception) {
                    Log.printStackTrace(e)
                }
            }
            return list
        }
    }
}

