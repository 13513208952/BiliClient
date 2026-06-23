package com.biliclient.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.data.store.UiMode
import com.biliclient.feature.tv.common.QualityLabels

/** 设置页（doc 08 §4 + doc 12 §3.6）：画质/编码/下载线程/弹幕/保存位置/电池白名单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = hiltViewModel()
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val qualityOptions: List<Pair<Int, String>> = remember {
        QualityLabels.MAP.entries.sortedByDescending { it.key }.map { it.key to it.value }
    }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        sectionHeader("画质与编码")
        item {
            SettingDropdown(
                label    = "默认播放画质",
                options  = qualityOptions,
                current  = s.defaultPlayQuality,
                onSelect = { vm.setDefaultPlayQuality(it) }
            )
        }
        item {
            SettingDropdown(
                label    = "默认下载画质",
                options  = qualityOptions,
                current  = s.defaultDownloadQuality,
                onSelect = { vm.setDefaultDownloadQuality(it) }
            )
        }
        item {
            SettingDropdown(
                label = "默认编码（AVC 兼容性最好）",
                options = listOf(7 to "AVC / H.264", 12 to "HEVC / H.265（省空间）", 13 to "AV1（最新）"),
                current = s.videoCodec,
                onSelect = { vm.setVideoCodec(it) }
            )
        }

        sectionHeader("下载")
        item {
            SettingDropdown(
                label = "下载线程数",
                options = listOf(4 to "4 线程（默认，移动网络推荐）", 8 to "8 线程（大文件 / 快速网络）"),
                current = s.downloadThreads,
                onSelect = { vm.setDownloadThreads(it) }
            )
        }
        item {
            Text(
                "保存位置：系统相册 Movies/BiliClient（Scoped Storage，无需存储权限）",
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        sectionHeader("弹幕")
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("默认开启弹幕")
                    Text("进入视频页时的初始状态", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = s.danmakuOn, onCheckedChange = { vm.setDanmakuOn(it) })
            }
        }
        item {
            Text("弹幕字号：${"%.1f".format(s.danmakuTextScale)}×",
                 fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Slider(
                value = s.danmakuTextScale,
                onValueChange = { vm.setDanmakuTextScale(it) },
                valueRange = 0.5f..2.0f,
                steps = 14,
                modifier = Modifier.fillMaxWidth()
            )
            // 弹幕字号实时预览（M8）
            DanmakuScalePreview(scale = s.danmakuTextScale)
        }
        item {
            Text("弹幕透明度：${(s.danmakuAlpha * 100).toInt()}%",
                 fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Slider(
                value = s.danmakuAlpha,
                onValueChange = { vm.setDanmakuAlpha(it) },
                valueRange = 0.1f..1.0f,
                steps = 17,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        // ── 显示模式（TV 开关）────────────────────────────────
        sectionHeader("显示模式")
        item {
            ListItem(
                headlineContent   = { Text("电视模式") },
                supportingContent = { Text("横屏显示，支持遥控器/鼠标/触控三种输入") },
                trailingContent   = {
                    Switch(
                        checked         = s.uiMode == UiMode.TV,
                        onCheckedChange = { on ->
                            vm.setUiMode(if (on) UiMode.TV else UiMode.PHONE)
                        }
                    )
                }
            )
        }
        item {
            Text(
                "界面缩放：${"%.2f".format(s.tvUiScale)}×",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value         = s.tvUiScale,
                onValueChange = { vm.setTvUiScale(it) },
                valueRange    = 0.8f..1.4f,
                steps         = 11,
                modifier      = Modifier.fillMaxWidth()
            )
            Text(
                "仅影响电视模式 UI（图标/文字/间距同步缩放）",
                fontSize = 11.sp,
                color    = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // ── 番剧内容屏蔽（控制手机端 + TV 端被动浏览面）──────────
        sectionHeader("内容过滤")
        item {
            ListItem(
                headlineContent   = { Text("屏蔽番剧内容") },
                supportingContent = {
                    Text(
                        "开启时主页/浏览流不显示番剧；搜索不受影响。同时控制电视端与手机端，重启后保持。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                },
                trailingContent   = {
                    Switch(
                        checked         = s.blockPgc,
                        onCheckedChange = { vm.setBlockPgc(it) }
                    )
                }
            )
        }

        sectionHeader("后台与电池（ColorOS / MIUI 保活）")
        item {
            Text(
                "国产 ROM 可能会在 App 退到后台后终止下载进程。建议将本应用加入电池优化白名单，确保下载在后台持续运行。WorkManager 兜底续传仍会在进程被杀后尝试恢复。",
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Button(
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${ctx.packageName}")
                                )
                            )
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("申请电池优化白名单")
                }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: String) {
    item {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
        )
    }
}

/**
 * 弹幕字号实时预览。
 * DanmakuView 标准字号基线 = 16.25sp（DanmakuView.kt:78/153）。
 * 随 textScale 滑块联动，滑动时立即更新。
 */
@Composable
private fun DanmakuScalePreview(scale: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 黑色描边层（模拟 DanmakuView 描边效果）
        Text(
            text     = "弹幕预览  Danmaku 示例",
            color    = Color.Black.copy(alpha = 0.8f),
            fontSize = (16.25f * scale).sp,
            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
        )
        Text(
            text     = "弹幕预览  Danmaku 示例",
            color    = Color.White,
            fontSize = (16.25f * scale).sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    label: String,
    options: List<Pair<Int, String>>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: "$current"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}
