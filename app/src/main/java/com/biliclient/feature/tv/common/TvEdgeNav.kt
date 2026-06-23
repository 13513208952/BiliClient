package com.biliclient.feature.tv.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * 为某个焦点容器附加"边缘换页"逻辑。
 * 当焦点已到最左边再按 LEFT（或最右边再按 RIGHT）且无更多可移焦目标时，
 * 调用对应回调（外部执行 Tab 切换）。
 *
 * @param onLeftEdge  左边缘越界时回调（null = 无相邻左界面，不处理）
 * @param onRightEdge 右边缘越界时回调（null = 无相邻右界面，不处理）
 * @param enabled     是否启用（全屏模式时传 false）
 */
fun Modifier.tvEdgeAware(
    onLeftEdge : (() -> Unit)? = null,
    onRightEdge: (() -> Unit)? = null,
    enabled    : Boolean = true
): Modifier = if (!enabled || (onLeftEdge == null && onRightEdge == null)) this
else composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> if (onLeftEdge != null) {
                val moved = focusManager.moveFocus(FocusDirection.Left)
                if (!moved) { onLeftEdge(); true } else false
            } else false
            Key.DirectionRight -> if (onRightEdge != null) {
                val moved = focusManager.moveFocus(FocusDirection.Right)
                if (!moved) { onRightEdge(); true } else false
            } else false
            else -> false
        }
    }
}
