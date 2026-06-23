package com.biliclient.feature.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.data.db.AccountDao
import com.biliclient.data.db.AccountEntity
import com.biliclient.net.BiliApi
import com.biliclient.repo.UserRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
    private val userRepo: UserRepo,
    private val accountDao: AccountDao,
    private val api: BiliApi
) : ViewModel() {

    val account: StateFlow<AccountEntity?> = accountDao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val csrf = accountDao.get()?.biliJct ?: ""
            runCatching { if (csrf.isNotBlank()) api.logout(csrf) }
            accountDao.clear()
        }
    }
}
