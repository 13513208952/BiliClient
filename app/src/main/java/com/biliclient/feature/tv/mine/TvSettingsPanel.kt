package com.biliclient.feature.tv.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.data.store.UiMode
import com.biliclient.feature.settings.SettingsViewModel
import com.biliclient.feature.tv.common.tvFocusable

@Composable
fun TvSettingsPanel(
    vm    : SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val s by vm.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick  = onBack,
                modifier = Modifier.tvFocusable(onClick = onBack)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                     contentDescription = "返回", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text("设置", color = Color.White, fontSize = 22.sp)
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f),
                          modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // TV 界面缩放（需求①）
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("TV 界面缩放：${"%.2f".format(s.tvUiScale)}×",
                         color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value         = s.tvUiScale,
                        onValueChange = { vm.setTvUiScale(it) },
                        valueRange    = 0.8f..1.4f,
                        steps         = 11,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = SliderDefaults.colors(
                            thumbColor       = Color(0xFFFB7299),
                            activeTrackColor = Color(0xFFFB7299)
                        )
                    )
                    Text("拖动滑块实时调整 TV 界面整体大小",
                         color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }

            item { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) }

            // 弹幕开关
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("默认开启弹幕", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked         = s.danmakuOn,
                        onCheckedChange = { vm.setDanmakuOn(it) }
                    )
                }
            }

            item { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) }

            // 电视模式开关
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("电视模式", color = Color.White, fontSize = 14.sp)
                        Text("关闭后切换回手机模式",
                             color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Switch(
                        checked         = s.uiMode == UiMode.TV,
                        onCheckedChange = { on ->
                            vm.setUiMode(if (on) UiMode.TV else UiMode.PHONE)
                        }
                    )
                }
            }
        }
    }
}
