package com.biliclient

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.biliclient.core.RiskCookie
import com.biliclient.download.DownloadWorker
import com.biliclient.repo.UserRepo
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口（doc 01 §6）。启动后台初始化风控套件 + nav 取 wbi key（+登录态）。
 * 实现 Configuration.Provider 以支持 HiltWorker（doc 08 WorkManager 续传）。
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var riskCookie: RiskCookie
    @Inject lateinit var userRepo: UserRepo
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching {
                riskCookie.ensureInitialized()
                userRepo.nav()
            }
        }
        // 续传未完成的下载（doc 08 §3.2）
        DownloadWorker.scheduleResume(this)
    }
}
