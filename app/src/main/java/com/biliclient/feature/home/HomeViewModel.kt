package com.biliclient.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.net.FeedItem
import com.biliclient.repo.FeedRepo
import com.biliclient.repo.UserRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedRepo: FeedRepo,
    private val userRepo: UserRepo
) : ViewModel() {

    data class HomeUi(
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val items: List<FeedItem> = emptyList(),
        val freshIdx: Int = 0,
        val error: String? = null,
        val uname: String? = null
    )

    private val _ui = MutableStateFlow(HomeUi(loading = true))
    val ui = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val uname = withContext(Dispatchers.IO) { runCatching { userRepo.currentUname() }.getOrNull() }
            val items = withContext(Dispatchers.IO) { runCatching { feedRepo.rcmd(0) }.getOrElse { emptyList() } }
            _ui.value = HomeUi(loading = false, items = items, freshIdx = 1, uname = uname)
        }
    }

    fun refresh() {
        if (_ui.value.refreshing) return
        _ui.value = _ui.value.copy(refreshing = true, error = null)
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { runCatching { feedRepo.rcmd(0) }.getOrElse { emptyList() } }
            _ui.value = _ui.value.copy(refreshing = false, items = items, freshIdx = 1)
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loadingMore || s.loading || s.refreshing) return
        _ui.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) { runCatching { feedRepo.rcmd(s.freshIdx) }.getOrElse { emptyList() } }
            _ui.value = _ui.value.copy(loadingMore = false, items = s.items + more, freshIdx = s.freshIdx + 1)
        }
    }
}
