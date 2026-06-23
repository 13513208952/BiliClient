package com.biliclient.feature.tv.desktop

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 一条可启动应用的信息。 */
data class LaunchableApp(
    val pkg: String,
    val label: String,
    val icon: Drawable
)

/**
 * 枚举设备上全部带 LAUNCHER Category 的应用，并提供启动功能。
 * queryIntentActivities 是同步调用，需在 IO 线程执行。
 */
@Singleton
class AppLauncherRepo @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 查询全部可启动应用（按 label 字母排序）。
     * 须在 Dispatchers.IO 中调用。
     */
    fun queryApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(intent, 0)
            .map { ri ->
                LaunchableApp(
                    pkg   = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                    icon  = ri.loadIcon(pm)
                )
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * 启动指定包名的应用。
     * 若应用不存在或无 LAUNCHER activity，静默失败（不崩溃）。
     */
    fun launch(pkg: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}
