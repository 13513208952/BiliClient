package com.biliclient.feature.tv.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TvSearchUi(
    val inputText       : String                  = "",
    val cursorPos       : Int                     = 0,
    val pinyinBuffer    : String                  = "",
    val candidates      : List<String>            = emptyList(),
    val isKeyboardShown : Boolean                 = false,
    val isCnMode        : Boolean                 = true,
    val suggests        : List<String>            = emptyList(),
    val bangumiResults  : List<SearchBangumiItem> = emptyList(),
    val results         : List<SearchVideoItem>   = emptyList(),
    val resultKw        : String                  = "",
    val searching       : Boolean                 = false,
    val loadingMore     : Boolean                 = false,
    val noMore          : Boolean                 = false,
    val error           : String?                 = null
)

@HiltViewModel
class TvSearchViewModel @Inject constructor(
    private val searchRepo  : SearchRepo,
    private val pinyinEngine: PinyinEngine
) : ViewModel() {

    private val _ui = MutableStateFlow(TvSearchUi())
    val ui = _ui.asStateFlow()

    val history = searchRepo.searchHistory
        .map { list -> list.map { it.keyword } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var currentPage = 1
    private var suggestJob: Job? = null

    // ── 键盘控制 ──────────────────────────────────────────────────

    fun showKeyboard() { _ui.value = _ui.value.copy(isKeyboardShown = true) }

    fun hideKeyboard() {
        _ui.value = _ui.value.copy(
            isKeyboardShown = false,
            pinyinBuffer    = "",
            candidates      = emptyList()
        )
    }

    fun toggleMode() {
        _ui.value = _ui.value.copy(
            isCnMode     = !_ui.value.isCnMode,
            pinyinBuffer = "",
            candidates   = emptyList()
        )
    }

    // ── 文字输入 ──────────────────────────────────────────────────

    fun onKeyInput(char: Char) {
        val s = _ui.value
        if (s.isCnMode && char.isLetter()) {
            val newBuffer = s.pinyinBuffer + char.lowercaseChar()
            _ui.value = s.copy(
                pinyinBuffer = newBuffer,
                candidates   = pinyinEngine.getCandidates(newBuffer)
            )
        } else {
            val newText = s.inputText.insertAt(s.cursorPos, char.toString())
            _ui.value = s.copy(inputText = newText, cursorPos = s.cursorPos + 1)
            triggerSuggest(newText)
        }
    }

    fun onCandidateSelect(candidate: String) {
        val s = _ui.value
        val newText = s.inputText.insertAt(s.cursorPos, candidate)
        _ui.value = s.copy(
            inputText    = newText,
            cursorPos    = s.cursorPos + candidate.length,
            pinyinBuffer = "",
            candidates   = emptyList()
        )
        triggerSuggest(newText)
    }

    fun onBackspace() {
        val s = _ui.value
        if (s.pinyinBuffer.isNotEmpty()) {
            val newBuffer = s.pinyinBuffer.dropLast(1)
            _ui.value = s.copy(
                pinyinBuffer = newBuffer,
                candidates   = pinyinEngine.getCandidates(newBuffer)
            )
        } else if (s.inputText.isNotEmpty() && s.cursorPos > 0) {
            val newText = s.inputText.removeAt(s.cursorPos - 1)
            _ui.value = s.copy(inputText = newText, cursorPos = s.cursorPos - 1)
            triggerSuggest(newText)
        }
    }

    fun onSpace() {
        val s = _ui.value
        if (s.isCnMode && s.pinyinBuffer.isNotEmpty()) {
            val first = s.candidates.firstOrNull()
            if (first != null) { onCandidateSelect(first); return }
        }
        val newText = s.inputText.insertAt(s.cursorPos, " ")
        _ui.value = s.copy(inputText = newText, cursorPos = s.cursorPos + 1)
    }

    fun onCursorLeft() {
        if (_ui.value.pinyinBuffer.isNotEmpty()) return
        val pos = _ui.value.cursorPos
        if (pos > 0) _ui.value = _ui.value.copy(cursorPos = pos - 1)
    }

    fun onCursorRight() {
        if (_ui.value.pinyinBuffer.isNotEmpty()) return
        val pos = _ui.value.cursorPos
        val len = _ui.value.inputText.length
        if (pos < len) _ui.value = _ui.value.copy(cursorPos = pos + 1)
    }

    fun onInputTextChange(text: String) {
        _ui.value = _ui.value.copy(
            inputText    = text,
            cursorPos    = text.length,
            pinyinBuffer = "",
            candidates   = emptyList()
        )
        triggerSuggest(text)
    }

    // ── 搜索 ──────────────────────────────────────────────────────

    fun search() {
        val s = _ui.value
        val kw = if (s.pinyinBuffer.isNotEmpty()) {
            val first = s.candidates.firstOrNull()
            if (first != null) {
                val text = s.inputText + first
                _ui.value = s.copy(inputText = text, pinyinBuffer = "", candidates = emptyList())
                text
            } else s.inputText
        } else s.inputText
        if (kw.isBlank()) return
        _ui.value = _ui.value.copy(
            searching = true, results = emptyList(), suggests = emptyList(),
            resultKw  = kw, error = null
        )
        currentPage = 1
        viewModelScope.launch {
            withContext(Dispatchers.IO) { searchRepo.saveHistory(kw) }
            val (bangumis, items) = withContext(Dispatchers.IO) {
                val bAsync = async { runCatching { searchRepo.searchBangumi(kw, page = 1) }.getOrElse { emptyList() } }
                val vAsync = async { runCatching { searchRepo.searchVideo(kw, page = 1) }.getOrElse { emptyList() } }
                bAsync.await() to vAsync.await()
            }
            _ui.value = _ui.value.copy(
                searching      = false,
                bangumiResults = bangumis,
                results        = items,
                noMore         = items.size < 20
            )
            currentPage = 2
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loadingMore || s.noMore || s.searching || s.resultKw.isBlank()) return
        _ui.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching { searchRepo.searchVideo(s.resultKw, page = currentPage) }.getOrElse { emptyList() }
            }
            _ui.value = _ui.value.copy(
                loadingMore = false,
                results     = s.results + more,
                noMore      = more.size < 20
            )
            currentPage++
        }
    }

    fun clearSearch() {
        val s = _ui.value
        _ui.value = TvSearchUi(isKeyboardShown = s.isKeyboardShown, isCnMode = s.isCnMode)
        currentPage = 1
    }

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { searchRepo.clearHistory() }

    fun historySearch(kw: String) {
        _ui.value = _ui.value.copy(inputText = kw, cursorPos = kw.length)
        search()
    }

    private fun triggerSuggest(kw: String) {
        suggestJob?.cancel()
        _ui.value = _ui.value.copy(suggests = emptyList())
        if (kw.isBlank()) return
        suggestJob = viewModelScope.launch {
            delay(300)
            val list = withContext(Dispatchers.IO) {
                runCatching { searchRepo.suggest(kw) }.getOrElse { emptyList() }
            }
            _ui.value = _ui.value.copy(suggests = list)
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────

    private fun String.insertAt(pos: Int, s: String): String {
        val i = pos.coerceIn(0, length)
        return substring(0, i) + s + substring(i)
    }

    private fun String.removeAt(pos: Int): String =
        if (pos < 0 || pos >= length) this
        else substring(0, pos) + substring(pos + 1)
}
