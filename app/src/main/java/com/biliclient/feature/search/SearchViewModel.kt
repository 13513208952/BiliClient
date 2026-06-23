package com.biliclient.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.core.Route
import com.biliclient.net.SearchBangumiItem
import com.biliclient.net.SearchVideoItem
import com.biliclient.repo.SearchRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(private val searchRepo: SearchRepo) : ViewModel() {

    data class SearchUi(
        val suggests: List<String> = emptyList(),
        val bangumiResults: List<SearchBangumiItem> = emptyList(),
        val results: List<SearchVideoItem> = emptyList(),
        val page: Int = 1,
        val loadingMore: Boolean = false,
        val noMore: Boolean = false,
        val resultKw: String = "",   // keyword currently shown in results
        val searching: Boolean = false,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()

    val history = searchRepo.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var suggestJob: Job? = null

    fun onInputChange(kw: String) {
        _ui.value = _ui.value.copy(suggests = emptyList())
        suggestJob?.cancel()
        if (kw.isBlank()) return
        suggestJob = viewModelScope.launch {
            delay(300)
            val list = withContext(Dispatchers.IO) { searchRepo.suggest(kw) }
            _ui.value = _ui.value.copy(suggests = list)
        }
    }

    /** 解析 URL/BV/AV/ep/ss → route；普通关键词 → 搜索结果。 */
    fun resolve(input: String, onRoute: (Route) -> Unit) {
        viewModelScope.launch {
            val route = withContext(Dispatchers.IO) { searchRepo.resolve(input) }
            if (route is Route.Video || route is Route.Bangumi || route is Route.Cheese) {
                onRoute(route)
            } else {
                search(input)
            }
        }
    }

    /** 并行搜索番剧 + 视频，番剧置顶混排。 */
    fun search(kw: String) {
        if (kw.isBlank()) return
        _ui.value = _ui.value.copy(
            searching = true, results = emptyList(), bangumiResults = emptyList(),
            suggests = emptyList(), resultKw = kw, error = null
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { searchRepo.saveHistory(kw) }
            val (bangumis, videos) = withContext(Dispatchers.IO) {
                val bAsync = async { runCatching { searchRepo.searchBangumi(kw, page = 1) }.getOrElse { emptyList() } }
                val vAsync = async { runCatching { searchRepo.searchVideo(kw, page = 1) }.getOrElse { emptyList() } }
                bAsync.await() to vAsync.await()
            }
            _ui.value = _ui.value.copy(
                searching = false,
                bangumiResults = bangumis,
                results = videos,
                page = 2,
                noMore = videos.size < 20
            )
        }
    }

    /** 加载更多视频（番剧不分页）。 */
    fun loadMore() {
        val s = _ui.value
        if (s.loadingMore || s.noMore || s.searching || s.resultKw.isBlank()) return
        _ui.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching { searchRepo.searchVideo(s.resultKw, page = s.page) }.getOrElse { emptyList() }
            }
            _ui.value = _ui.value.copy(
                loadingMore = false, results = s.results + more,
                page = s.page + 1, noMore = more.size < 20
            )
        }
    }

    fun clearHistory() { viewModelScope.launch(Dispatchers.IO) { searchRepo.clearHistory() } }
}
