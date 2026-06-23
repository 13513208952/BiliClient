package com.biliclient.feature.tv.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.biliclient.data.store.UiMode
import com.biliclient.feature.mine.MineViewModel
import com.biliclient.feature.settings.SettingsViewModel
import com.biliclient.feature.tv.common.tvFocusable

@Composable
fun TvMineScreen(
    mineVm         : MineViewModel     = hiltViewModel(),
    settingsVm     : SettingsViewModel = hiltViewModel(),
    onLogin        : () -> Unit = {},
    onFav          : () -> Unit = {},
    onHistory      : () -> Unit = {},
    onDownloads    : () -> Unit = {},
    onSettings     : () -> Unit = {},
    onSwitchToPhone: () -> Unit = {}
) {
    val account by mineVm.account.collectAsStateWithLifecycle()
    val menuFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { menuFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {

        // ── 左栏：账号信息 + 切换按钮 ─────────────────────────────
        Column(
            Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (account != null) {
                AsyncImage(
                    model              = account?.face?.takeIf { it.isNotBlank() },
                    contentDescription = null,
                    modifier           = Modifier.size(72.dp).clip(CircleShape)
                )
                Column {
                    Text(account?.uname ?: "", color = Color.White, fontSize = 18.sp)
                    Text("UID: ${account?.uid ?: ""}",
                         color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            } else {
                TvMineRow(Icons.Default.AccountCircle, "登录账号", onClick = onLogin)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x22FB7299), RoundedCornerShape(12.dp))
                    .tvFocusable(
                        onClick = { settingsVm.setUiMode(UiMode.PHONE); onSwitchToPhone() },
                        shape   = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhoneAndroid, null,
                     tint = Color(0xFFFB7299), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("切换到手机模式", color = Color(0xFFFB7299), fontSize = 14.sp)
            }
        }

        VerticalDivider(color = Color.White.copy(alpha = 0.1f))

        // ── 右栏：菜单列表 ─────────────────────────────────────────
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 32.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TvMineRow(Icons.Default.Settings, "设置",     onClick = onSettings,
                      focusMod = Modifier.focusRequester(menuFocusRequester))
            TvMineRow(Icons.Default.Star,     "我的收藏", onClick = onFav)
            TvMineRow(Icons.Default.History,  "观看历史", onClick = onHistory)
            TvMineRow(Icons.Default.Download, "我的下载", onClick = onDownloads)
            if (account != null) {
                TvMineRow(
                    icon    = Icons.AutoMirrored.Filled.ExitToApp,
                    label   = "退出登录",
                    tint    = Color(0xFFFF6B6B),
                    onClick = { mineVm.logout() }
                )
            }
        }
    }
}

@Composable
private fun TvMineRow(
    icon    : ImageVector,
    label   : String,
    tint    : Color    = Color.White,
    focusMod: Modifier = Modifier,
    onClick : () -> Unit
) {
    Row(
        modifier = focusMod
            .fillMaxWidth()
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
