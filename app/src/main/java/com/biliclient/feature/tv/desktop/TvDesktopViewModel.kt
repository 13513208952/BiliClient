package com.biliclient.feature.tv.desktop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.data.store.BiliStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DesktopUi(
    val pinnedApps    : List<LaunchableApp> = emptyList(),
    val allApps       : List<LaunchableApp> = emptyList(),
    val pinnedPkgs    : Set<String>         = emptySet(),
    val allAppsLoading: Boolean             = false,
    val manageMode    : Boolean             = false,
    val removeMode    : Boolean             = false,
    val pendingRemove : Set<String>         = emptySet()
)

@HiltViewModel
class TvDesktopViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val launcherRepo: AppLauncherRepo,
    private val store: BiliStore
) : ViewModel() {

    private val _ui = MutableStateFlow(DesktopUi())
    val ui = _ui.asStateFlow()

    init {
        // 订阅持久化的固定应用包名集合
        viewModelScope.launch {
            store.desktopPkgsFlow.collect { pkgs ->
                _ui.value = _ui.value.copy(pinnedPkgs = pkgs)
                val allApps = _ui.value.allApps
                if (allApps.isNotEmpty()) {
                    _ui.value = _ui.value.copy(pinnedApps = allApps.filter { it.pkg in pkgs })
                }
            }
        }
        loadAllApps()
    }

    fun loadAllApps() {
        if (_ui.value.allAppsLoading) return
        _ui.value = _ui.value.copy(allAppsLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val apps = launcherRepo.queryApps().filter { it.pkg != appContext.packageName }
            val pkgs = _ui.value.pinnedPkgs
            withContext(Dispatchers.Main) {
                _ui.value = _ui.value.copy(
                    allApps        = apps,
                    allAppsLoading = false,
                    pinnedApps     = apps.filter { it.pkg in pkgs }
                )
            }
        }
    }

    fun togglePin(pkg: String) {
        viewModelScope.launch {
            val current = _ui.value.pinnedPkgs.toMutableSet()
            if (pkg in current) current.remove(pkg) else current.add(pkg)
            store.setDesktopPkgs(current)
        }
    }

    fun launch(pkg: String) = launcherRepo.launch(pkg)

    fun enterManageMode() { _ui.value = _ui.value.copy(manageMode = true) }
    fun exitManageMode()  { _ui.value = _ui.value.copy(manageMode = false) }

    fun enterRemoveMode() {
        _ui.value = _ui.value.copy(removeMode = true, pendingRemove = emptySet())
    }

    fun exitRemoveMode() {
        _ui.value = _ui.value.copy(removeMode = false, pendingRemove = emptySet())
    }

    fun togglePendingRemove(pkg: String) {
        val current = _ui.value.pendingRemove.toMutableSet()
        if (pkg in current) current.remove(pkg) else current.add(pkg)
        _ui.value = _ui.value.copy(pendingRemove = current)
    }

    fun confirmRemove() {
        viewModelScope.launch {
            val remaining = _ui.value.pinnedPkgs - _ui.value.pendingRemove
            store.setDesktopPkgs(remaining)
            _ui.value = _ui.value.copy(removeMode = false, pendingRemove = emptySet())
        }
    }

    fun removeOne(pkg: String) {
        viewModelScope.launch {
            val remaining = _ui.value.pinnedPkgs - setOf(pkg)
            store.setDesktopPkgs(remaining)
        }
    }
}
