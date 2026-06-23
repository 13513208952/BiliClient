package com.biliclient.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.net.HistoryItem
import com.biliclient.repo.HistoryRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val historyRepo: HistoryRepo) : ViewModel() {

    data class HistoryUi(
        val loading: Boolean = true,
        val items: List<HistoryItem> = emptyList(),
        val cursorMax: Long = 0,
        val cursorViewAt: Long = 0,
        val loadingMore: Boolean = false,
        val noMore: Boolean = false,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(HistoryUi())
    val ui = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = HistoryUi(loading = true)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { historyRepo.history() }.getOrNull()
            }
            _ui.value = HistoryUi(
                loading = false,
                items = data?.list ?: emptyList(),
                cursorMax = data?.cursor?.max ?: 0,
                cursorViewAt = data?.cursor?.viewAt ?: 0,
                noMore = (data?.list?.size ?: 0) < 20,
                error = if (data == null) "加载失败（请先登录）" else null
            )
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loadingMore || s.noMore || s.loading) return
        _ui.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { historyRepo.history(s.cursorMax, s.cursorViewAt) }.getOrNull()
            }
            val more = data?.list ?: emptyList()
            _ui.value = _ui.value.copy(
                loadingMore = false,
                items = s.items + more,
                cursorMax = data?.cursor?.max ?: s.cursorMax,
                cursorViewAt = data?.cursor?.viewAt ?: s.cursorViewAt,
                noMore = more.size < 20
            )
        }
    }
}
