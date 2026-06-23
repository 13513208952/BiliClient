package com.biliclient.feature.tv.desktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.feature.tv.common.tvFocusable

@Composable
fun TvDesktopScreen(vm: TvDesktopViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    // manageMode Back → 退出管理模式，不退出 App（doc 05 §3.1）
    BackHandler(enabled = ui.manageMode) { vm.exitManageMode() }

    if (ui.manageMode) {
        TvDesktopManageView(ui = ui, vm = vm)
    } else {
        TvDesktopMainView(ui = ui, vm = vm)
    }
}

// ── 主桌面：固定图标网格 ─────────────────────────────────────────

@Composable
private fun TvDesktopMainView(ui: DesktopUi, vm: TvDesktopViewModel) {
    val batchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(ui.removeMode) {
        if (ui.removeMode) {
            try { batchFocusRequester.requestFocus() } catch (_: Exception) { }
        }
    }

    BackHandler(enabled = ui.removeMode) { vm.exitRemoveMode() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                    if (ui.removeMode) vm.exitRemoveMode() else vm.enterRemoveMode()
                    true
                } else false
            }
    ) {
        if (ui.pinnedApps.isEmpty() && !ui.removeMode) {
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("桌面还没有应用", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick  = { vm.enterManageMode() },
                    modifier = Modifier.tvFocusable(onClick = { vm.enterManageMode() })
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("添加应用")
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (ui.removeMode) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1A))
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "已选 ${ui.pendingRemove.size} 个",
                            color = Color.White, fontSize = 16.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                Modifier
                                    .focusRequester(batchFocusRequester)
                                    .tvFocusable(
                                        onClick = { vm.exitRemoveMode() },
                                        shape   = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) { Text("取消", color = Color.White) }
                            Box(
                                Modifier
                                    .background(Color(0xFFFF4444), RoundedCornerShape(8.dp))
                                    .tvFocusable(
                                        onClick = { vm.confirmRemove() },
                                        shape   = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) { Text("移除 ${ui.pendingRemove.size} 个", color = Color.White) }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns               = GridCells.Adaptive(minSize = 120.dp),
                    modifier              = Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement   = Arrangement.spacedBy(16.dp)
                ) {
                    items(ui.pinnedApps, key = { it.pkg }) { app ->
                        if (ui.removeMode) {
                            RemovableIconCell(
                                app      = app,
                                selected = app.pkg in ui.pendingRemove,
                                onClick  = { vm.togglePendingRemove(app.pkg) }
                            )
                        } else {
                            AppIconCellWithMenu(
                                app      = app,
                                onClick  = { vm.launch(app.pkg) },
                                onRemove = { vm.removeOne(app.pkg) }
                            )
                        }
                    }
                    if (!ui.removeMode) {
                        item {
                            Box(
                                modifier         = Modifier
                                    .size(120.dp)
                                    .tvFocusable(
                                        onClick = { vm.enterManageMode() },
                                        shape   = RoundedCornerShape(16.dp)
                                    )
                                    .background(Color(0x22FFFFFF), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, contentDescription = "添加",
                                         tint = Color.White, modifier = Modifier.size(36.dp))
                                    Text("管理", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 管理界面：全部应用勾选 ────────────────────────────────────────

@Composable
private fun TvDesktopManageView(ui: DesktopUi, vm: TvDesktopViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("管理桌面应用", color = Color.White, fontSize = 22.sp,
                 modifier = Modifier.weight(1f))
            IconButton(
                onClick  = { vm.exitManageMode() },
                modifier = Modifier.tvFocusable(onClick = { vm.exitManageMode() })
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (ui.allAppsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFB7299))
            }
        } else {
            LazyVerticalGrid(
                columns               = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.allApps, key = { it.pkg }) { app ->
                    val pinned = app.pkg in ui.pinnedPkgs
                    Box(
                        modifier = Modifier.tvFocusable(
                            onClick = { vm.togglePin(app.pkg) },
                            shape   = RoundedCornerShape(12.dp)
                        )
                    ) {
                        AppIconCell(app = app, onClick = { vm.togglePin(app.pkg) })
                        if (pinned) {
                            Icon(
                                imageVector        = Icons.Default.Check,
                                contentDescription = "已固定",
                                tint               = Color(0xFFFB7299),
                                modifier           = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 单个图标格子（管理界面复用）──────────────────────────────────

@Composable
private fun AppIconCell(app: LaunchableApp, onClick: () -> Unit) {
    val bitmap: ImageBitmap = remember(app.pkg) {
        app.icon.toBitmap(width = 96, height = 96).asImageBitmap()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .size(120.dp)
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Image(
            bitmap             = bitmap,
            contentDescription = app.label,
            modifier           = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = app.label,
            color     = Color.White,
            fontSize  = 12.sp,
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── 图标格（主桌面正常模式：点击启动，长按/右键弹菜单）──────────

@Composable
private fun AppIconCellWithMenu(
    app     : LaunchableApp,
    onClick : () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val bitmap: ImageBitmap = remember(app.pkg) {
        app.icon.toBitmap(width = 96, height = 96).asImageBitmap()
    }

    Box(
        Modifier
            .pointerInput(app.pkg) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed
                        ) {
                            menuExpanded = true
                        }
                    }
                }
            }
            .tvFocusable(
                onClick     = onClick,
                onLongClick = { menuExpanded = true },
                shape       = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.size(120.dp).padding(8.dp)
        ) {
            Image(
                bitmap             = bitmap,
                contentDescription = app.label,
                modifier           = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = app.label,
                color     = Color.White,
                fontSize  = 12.sp,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        DropdownMenu(
            expanded         = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier         = Modifier.background(Color(0xDD111111))
        ) {
            DropdownMenuItem(
                text    = { Text("从桌面移除", color = Color(0xFFFF6B6B)) },
                onClick = { menuExpanded = false; onRemove() }
            )
        }
    }
}

// ── 图标格（主桌面批量移除模式：点击切换选中）────────────────────

@Composable
private fun RemovableIconCell(
    app     : LaunchableApp,
    selected: Boolean,
    onClick : () -> Unit
) {
    val bitmap: ImageBitmap = remember(app.pkg) {
        app.icon.toBitmap(width = 96, height = 96).asImageBitmap()
    }
    Box(
        Modifier
            .size(120.dp)
            .tvFocusable(onClick = onClick, shape = RoundedCornerShape(16.dp))
            .background(
                color = if (selected) Color(0x44FF4444) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Image(
                bitmap             = bitmap,
                contentDescription = app.label,
                modifier           = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = app.label,
                color     = Color.White,
                fontSize  = 12.sp,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Icon(
            imageVector        = if (selected) Icons.Default.RemoveCircle
                                 else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (selected) "已选中" else "未选中",
            tint               = if (selected) Color(0xFFFF4444)
                                 else Color.White.copy(alpha = 0.6f),
            modifier           = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
        )
    }
}
