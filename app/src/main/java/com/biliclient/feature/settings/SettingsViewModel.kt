package com.biliclient.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.data.store.BiliStore
import com.biliclient.data.store.UiMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: BiliStore) : ViewModel() {

    private val _settings = MutableStateFlow(
        BiliStore.Settings(defaultPlayQuality = 80, defaultDownloadQuality = 80, videoCodec = 7, danmakuOn = true, downloadThreads = 4)
    )
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch { _settings.value = store.settings() }
    }

    fun setDefaultPlayQuality(qn: Int) = viewModelScope.launch {
        store.setDefaultPlayQuality(qn)
        _settings.value = _settings.value.copy(defaultPlayQuality = qn)
    }

    fun setDefaultDownloadQuality(qn: Int) = viewModelScope.launch {
        store.setDefaultDownloadQuality(qn)
        _settings.value = _settings.value.copy(defaultDownloadQuality = qn)
    }

    fun setVideoCodec(c: Int) = viewModelScope.launch {
        store.setVideoCodec(c)
        _settings.value = _settings.value.copy(videoCodec = c)
    }

    fun setDanmakuOn(on: Boolean) = viewModelScope.launch {
        store.setDanmakuOn(on)
        _settings.value = _settings.value.copy(danmakuOn = on)
    }

    fun setDownloadThreads(n: Int) = viewModelScope.launch {
        store.setDownloadThreads(n)
        _settings.value = _settings.value.copy(downloadThreads = n)
    }

    fun setDanmakuTextScale(v: Float) = viewModelScope.launch {
        store.setDanmakuTextScale(v)
        _settings.value = _settings.value.copy(danmakuTextScale = v)
    }

    fun setDanmakuAlpha(v: Float) = viewModelScope.launch {
        store.setDanmakuAlpha(v)
        _settings.value = _settings.value.copy(danmakuAlpha = v)
    }

    fun setUiMode(m: UiMode) = viewModelScope.launch {
        store.setUiMode(m)
        _settings.value = _settings.value.copy(uiMode = m)
    }

    fun setTvUiScale(v: Float) = viewModelScope.launch {
        store.setTvUiScale(v)
        _settings.value = _settings.value.copy(tvUiScale = v)
    }

    fun setBlockPgc(on: Boolean) = viewModelScope.launch {
        store.setBlockPgc(on)
        _settings.value = _settings.value.copy(blockPgc = on)
    }
}
