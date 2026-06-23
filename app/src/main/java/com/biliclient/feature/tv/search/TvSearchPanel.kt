package com.biliclient.feature.tv.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.feature.tv.common.tvFocusable
import com.biliclient.net.FeedItem
import com.biliclient.net.FeedOwner

@Composable
fun TvSearchPanel(
    vm            : TvSearchViewModel = hiltViewModel(),
    onPlayVideo   : (FeedItem) -> Unit,
    onPlayBangumi : (String) -> Unit = {}
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val openKbFocusRequester = remember { FocusRequester() }

    // 内置键盘展开时隐藏系统 IME；收起时焦点归还"开启键盘"按钮
    LaunchedEffect(ui.isKeyboardShown) {
        if (ui.isKeyboardShown) {
            keyboardController?.hide()
        } else {
            try { openKbFocusRequester.requestFocus() } catch (_: Exception) { }
        }
    }

    // 仅当键盘展开时拦截 Back（收起键盘）；Tab 导航由顶部 Tab 栏处理
    BackHandler(enabled = ui.isKeyboardShown) { vm.hideKeyboard() }

    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        // 左栏：搜索框 + 键盘
        val leftModifier = if (ui.isKeyboardShown)
            Modifier.weight(1f).fillMaxHeight().background(Color(0xFF141414))
        else
            Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF141414))

        Column(leftModifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            SearchBar(
                inputText     = ui.inputText,
                cursorPos     = ui.cursorPos,
                onValueChange = { vm.onInputTextChange(it) },
                onSearch      = { vm.search() },
                onShowSysIme  = { if (!ui.isKeyboardShown) keyboardController?.show() }
            )

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(
                visible = ui.isKeyboardShown,
                enter   = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                exit    = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
            ) {
                TvSoftKeyboard(
                    pinyinBuffer      = ui.pinyinBuffer,
                    candidates        = ui.candidates,
                    isCnMode          = ui.isCnMode,
                    onChar            = { vm.onKeyInput(it) },
                    onCandidateSelect = { vm.onCandidateSelect(it) },
                    onBackspace       = { vm.onBackspace() },
                    onCursorLeft      = { vm.onCursorLeft() },
                    onCursorRight     = { vm.onCursorRight() },
                    onSearch          = { vm.search() },
                    onToggleMode      = { vm.toggleMode() },
                    onClose           = { vm.hideKeyboard() }
                )
            }

            if (!ui.isKeyboardShown) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(openKbFocusRequester)
                        .tvFocusable(
                            onClick = { vm.showKeyboard() },
                            shape   = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "开启软键盘以输入",
                        color    = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        VerticalDivider(color = Color.White.copy(alpha = 0.1f))

        // 右栏：搜索结果 / 联想 / 历史
        Box(Modifier.weight(1f).fillMaxHeight()) {
            TvSearchResultArea(
                vm            = vm,
                onPlayVideo   = { item ->
                    onPlayVideo(
                        FeedItem(
                            bvid  = item.bvid,
                            id    = item.aid,
                            pic   = item.picHttps(),
                            title = item.cleanTitle(),
                            owner = FeedOwner(name = item.author)
                        )
                    )
                },
                onPlayBangumi = onPlayBangumi
            )
        }
    }
}

@Composable
private fun SearchBar(
    inputText    : String,
    cursorPos    : Int,
    onValueChange: (String) -> Unit,
    onSearch     : () -> Unit,
    onShowSysIme : () -> Unit
) {
    val tfValue = TextFieldValue(inputText, selection = TextRange(cursorPos))
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525), RoundedCornerShape(8.dp))
            .tvFocusable(onClick = onShowSysIme, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (inputText.isEmpty()) {
            Text("输入搜索词...", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp)
        }
        BasicTextField(
            value           = tfValue,
            onValueChange   = { onValueChange(it.text) },
            textStyle       = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush     = SolidColor(Color(0xFFFB7299)),
            singleLine      = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier        = Modifier.fillMaxWidth()
        )
    }
}
