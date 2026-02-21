package fansirsqi.xposed.sesame.ui.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import fansirsqi.xposed.sesame.entity.UserEntity
import fansirsqi.xposed.sesame.ui.SettingActivity
import fansirsqi.xposed.sesame.ui.WebSettingsActivity
import fansirsqi.xposed.sesame.ui.model.UiMode
import fansirsqi.xposed.sesame.ui.repository.ConfigRepository
import fansirsqi.xposed.sesame.util.Detector
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ToastUtil

/**
 * 扩展函数：打开浏览器
 */

fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}

fun joinQQGroup(context: Context) {
    val intent = Intent()
//    intent.data = Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key")
    // 或者使用更通用的协议：
    intent.data = Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&card_type=group&uin=1002616652")
//    intent.data = Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1002616652&card_type=group&source=qrcode")

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // 如果没安装 QQ 或唤起失败，回退到打开网页
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, "https://qm.qq.com/q/Aj0Xby6AGQ".toUri()) // 这里的 URL 结构可能需要根据实际生成的链接调整
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }
}


fun Context.performNavigationToSettings(user: UserEntity) {
    if (Detector.loadLibrary("checker")) {
        Log.record("载入用户配置 ${user.showName}")
        try {
            // 1. 【改动点】从仓库获取当前模式
            val currentMode = ConfigRepository.uiMode.value
            // 2. 【改动点】获取对应的 Activity 类 (使用上面定义的扩展属性)
            val targetActivity = currentMode.targetActivity

            val intent = Intent(this, targetActivity).apply {
                putExtra("userId", user.userId)
                putExtra("userName", user.showName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.showToast(this, "无法启动设置页面: ${e.message}")
        }
    } else {
        Detector.tips(this, "缺少必要依赖！")
    }
}

val UiMode.targetActivity: Class<*>
    get() = when (this) {
        UiMode.Web -> WebSettingsActivity::class.java
        UiMode.New -> SettingActivity::class.java
    }

