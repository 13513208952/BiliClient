package com.biliclient.feature.fav

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.net.FavMedia
import com.biliclient.repo.FavRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FavContentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val favRepo: FavRepo
) : ViewModel() {

    private val mediaId: Long = savedState.get<String>("mediaId")?.toLongOrNull() ?: 0

    data class FavContentUi(
        val loading: Boolean = true,
        val title: String = "",
        val items: List<FavMedia> = emptyList(),
        val pn: Int = 1,
        val loadingMore: Boolean = false,
        val noMore: Boolean = false,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(FavContentUi())
    val ui = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = FavContentUi(loading = true)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { favRepo.resources(mediaId, pn = 1) }.getOrNull()
            }
            _ui.value = FavContentUi(
                loading = false,
                title = data?.info?.title ?: "收藏夹",
                items = data?.medias ?: emptyList(),
                pn = 2,
                noMore = (data?.medias?.size ?: 0) < 20,
                error = if (data == null) "加载失败" else null
            )
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loadingMore || s.noMore || s.loading) return
        _ui.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { favRepo.resources(mediaId, pn = s.pn) }.getOrNull()
            }
            val more = data?.medias ?: emptyList()
            _ui.value = _ui.value.copy(
                loadingMore = false,
                items = s.items + more,
                pn = s.pn + 1,
                noMore = more.size < 20
            )
        }
    }
}
