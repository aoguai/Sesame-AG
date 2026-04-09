package io.github.aoguai.sesameag.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aoguai.sesameag.data.General

object IconManager {
    // 请确保该常量对应 Manifest 中的完整路径
    // 例如: "io.github.aoguai.sesameag.ui.MainActivityAlias"
    const val COMPONENT_DEFAULT = General.MODULE_PACKAGE_UI_ICON

    /**
     * 根据用户是否想隐藏来同步桌面图标状态。
     */
    fun getIconVector(iconName: String?): ImageVector {
        return when (iconName) {
            "BaseModel.png" -> Icons.Default.Settings
            "AntForest.png" -> Icons.Default.Park
            "AntFarm.png" -> Icons.Default.Pets
            "AntOcean.png" -> Icons.Default.Waves
            "AntStall.png" -> Icons.Default.Store
            "AntOrchard.png" -> Icons.Default.Agriculture
            "AntMember.png" -> Icons.Default.CardMembership
            "AntSports.png" -> Icons.Default.DirectionsRun
            else -> Icons.Default.Settings
        }
    }

    fun syncIconState(context: Context, userWantsHide: Boolean) {
        val pm = context.packageManager
        if (userWantsHide) {
            disableComponent(context, pm, COMPONENT_DEFAULT)
            return
        }
        enableComponent(context, pm, COMPONENT_DEFAULT)
    }

    private fun enableComponent(context: Context, pm: PackageManager, className: String) {
        val componentName = ComponentName(context, className)
        if (pm.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun disableComponent(context: Context, pm: PackageManager, className: String) {
        val componentName = ComponentName(context, className)
        if (pm.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
