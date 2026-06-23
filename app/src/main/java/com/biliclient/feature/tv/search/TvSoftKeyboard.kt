package com.biliclient.feature.tv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliclient.feature.tv.common.tvFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ROW_1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
private val ROW_2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
private val ROW_3 = listOf("Z", "X", "C", "V", "B", "N", "M")

@Composable
fun TvSoftKeyboard(
    pinyinBuffer     : String,
    candidates       : List<String>,
    isCnMode         : Boolean,
    onChar           : (Char) -> Unit,
    onCandidateSelect: (String) -> Unit,
    onBackspace      : () -> Unit,
    onCursorLeft     : () -> Unit,
    onCursorRight    : () -> Unit,
    onSearch         : () -> Unit,
    onToggleMode     : () -> Unit,
    onClose          : () -> Unit,
    modifier         : Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .background(Color(0xFF181818))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CandidateBar(
            pinyinBuffer      = pinyinBuffer,
            candidates        = candidates,
            onCandidateSelect = onCandidateSelect
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ROW_1.forEach { key ->
                KbKey(
                    label    = if (isCnMode) key.lowercase() else key,
                    modifier = Modifier.weight(1f),
                    onClick  = { onChar(key[0]) }
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.weight(0.5f))
            ROW_2.forEach { key ->
                KbKey(
                    label    = if (isCnMode) key.lowercase() else key,
                    modifier = Modifier.weight(1f),
                    onClick  = { onChar(key[0]) }
                )
            }
            Spacer(Modifier.weight(0.5f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.weight(1f))
            ROW_3.forEach { key ->
                KbKey(
                    label    = if (isCnMode) key.lowercase() else key,
                    modifier = Modifier.weight(1f),
                    onClick  = { onChar(key[0]) }
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KbKey(
                label    = if (isCnMode) "中" else "EN",
                tint     = Color(0xFFFB7299),
                modifier = Modifier.width(64.dp),
                onClick  = onToggleMode
            )
            BackspaceKey(onBackspace = onBackspace, modifier = Modifier.width(56.dp))
            KbKey(
                label    = "◀",
                modifier = Modifier.width(48.dp),
                onClick  = onCursorLeft
            )
            KbKey(
                label    = "▶",
                modifier = Modifier.width(48.dp),
                onClick  = onCursorRight
            )
            KbKey(
                label    = "搜索",
                tint     = Color(0xFFFB7299),
                modifier = Modifier.width(72.dp),
                onClick  = onSearch
            )
            KbKey(
                label    = "↓收起",
                tint     = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(64.dp),
                onClick  = onClose
            )
        }
    }
}

@Composable
private fun CandidateBar(
    pinyinBuffer     : String,
    candidates       : List<String>,
    onCandidateSelect: (String) -> Unit
) {
    LazyRow(
        modifier              = Modifier.fillMaxWidth().height(40.dp)
                                    .background(Color(0xFF1C1C1C)),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding        = PaddingValues(horizontal = 8.dp)
    ) {
        if (pinyinBuffer.isNotEmpty()) {
            item {
                Box(
                    Modifier.fillMaxHeight().padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(pinyinBuffer, color = Color(0xFFFB7299), fontSize = 14.sp, fontStyle = FontStyle.Italic)
                }
            }
            item {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                    color    = Color.White.copy(alpha = 0.2f)
                )
            }
        }
        itemsIndexed(candidates) { _, c ->
            Box(
                Modifier
                    .fillMaxHeight()
                    .defaultMinSize(minWidth = 40.dp)
                    .tvFocusable(onClick = { onCandidateSelect(c) }, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(c, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun KbKey(
    label      : String,
    modifier   : Modifier = Modifier,
    tint       : Color    = Color.White,
    onClick    : () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier         = modifier
            .height(48.dp)
            .background(Color(0xFF252525), RoundedCornerShape(6.dp))
            .tvFocusable(onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BackspaceKey(onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Box(
        modifier         = modifier
            .height(48.dp)
            .background(Color(0xFF252525), RoundedCornerShape(6.dp))
            .tvFocusable(onClick = onBackspace, shape = RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var held = true
                        val job = scope.launch {
                            delay(500L)
                            while (held) {
                                onBackspace()
                                delay(80L)
                            }
                        }
                        tryAwaitRelease()
                        held = false
                        job.cancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("⌫", color = Color.White, fontSize = 15.sp)
    }
}
