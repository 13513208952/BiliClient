package com.biliclient.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.data.store.BiliStore
import com.biliclient.data.store.UiMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Activity 级 ViewModel（由 MainActivity 中 hiltViewModel() 获取）。
 * 观察 uiModeFlow，驱动 PHONE/TV 根 UI 切换。
 * SharingStarted.Eagerly + 初始值 PHONE：首帧立即渲染正确 UI，无闪烁。
 */
@HiltViewModel
class RootViewModel @Inject constructor(store: BiliStore) : ViewModel() {
    val uiMode = store.uiModeFlow.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Eagerly,
        initialValue = UiMode.PHONE
    )
    val tvUiScale = store.tvUiScaleFlow.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Eagerly,
        initialValue = 1.0f
    )
}
