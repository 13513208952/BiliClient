package com.biliclient.feature.tv.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** B站粉（与手机版激活态 tint 一致），用作焦点环颜色。供同包其他文件引用。 */
internal val BiliPink = Color(0xFFFB7299)

/**
 * TV 通用焦点修饰符。
 *
 * 同时支持：触控点按、鼠标左键、遥控器 D-pad 方向键遍历 + OK 键激活、长按。
 *
 * @param onClick     点击/OK 键回调（必须）
 * @param onLongClick 长按回调（null=不启用）；遥控器长按 OK 键也触发
 * @param shape       焦点环形状（默认圆角 8dp）
 * @param enabled     是否可交互（false 时不响应任何输入且无焦点环）
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvFocusable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    enabled: Boolean = true
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.06f else 1.0f,
        label       = "tvFocusScale"
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .combinedClickable(
            enabled     = enabled,
            onClick     = onClick,
            onLongClick = onLongClick
        )
        // graphicsLayer/border 在 combinedClickable（FocusTargetNode）之后：
        // 焦点几何使用未缩放边界，避免宽单元格 focus rect 膨胀后覆盖相邻项导致隔项跳。
        // 视觉缩放（1.06×）与焦点环视觉效果不变。
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(
            width = if (focused && enabled) 3.dp else 0.dp,
            color = if (focused && enabled) BiliPink else Color.Transparent,
            shape = shape
        )
}
