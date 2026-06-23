package com.biliclient.feature.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

/**
 * 我的页（doc 12 §3.6）：头像 / 用户名 + 收藏/历史/下载/设置/登录入口。
 */
@Composable
fun MineScreen(
    onLogin: () -> Unit,
    onFav: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    vm: MineViewModel = hiltViewModel()
) {
    val account by vm.account.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 用户信息头部
        Row(
            Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = account?.face?.takeIf { it.isNotBlank() },
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape)
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    account?.uname ?: "未登录",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                if (account != null) {
                    Text("UID: ${account!!.uid}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Divider()
        Spacer(Modifier.height(8.dp))

        MineItem(icon = Icons.Default.Favorite, label = "我的收藏", onClick = onFav)
        MineItem(icon = Icons.Default.History, label = "观看历史", onClick = onHistory)
        MineItem(icon = Icons.Default.Download, label = "下载管理", onClick = onDownloads)
        MineItem(icon = Icons.Default.Settings, label = "设置", onClick = onSettings)

        Spacer(Modifier.height(16.dp))

        if (account != null) {
            TextButton(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
        } else {
            TextButton(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            ) { Text("登录", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun MineItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 15.sp)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
