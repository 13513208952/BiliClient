package com.biliclient.feature.fav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.net.FavFolder
import com.biliclient.repo.FavRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FavViewModel @Inject constructor(private val favRepo: FavRepo) : ViewModel() {

    data class FavUi(
        val loading: Boolean = true,
        val folders: List<FavFolder> = emptyList(),
        val error: String? = null
    )

    private val _ui = MutableStateFlow(FavUi())
    val ui = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = FavUi(loading = true)
        viewModelScope.launch {
            val folders = withContext(Dispatchers.IO) {
                runCatching { favRepo.folders() }.getOrElse { emptyList() }
            }
            _ui.value = FavUi(loading = false, folders = folders,
                error = if (folders.isEmpty()) "暂无收藏夹（请先登录）" else null)
        }
    }
}
