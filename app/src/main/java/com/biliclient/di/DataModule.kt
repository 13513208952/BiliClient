package com.biliclient.di

import android.content.Context
import com.biliclient.data.db.AccountDao
import com.biliclient.data.db.AppDatabase
import com.biliclient.data.db.DownloadDao
import com.biliclient.data.db.SearchHistoryDao
import com.biliclient.data.store.AndroidCookieStore
import com.biliclient.data.store.BiliStore
import com.biliclient.net.CookieStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase = AppDatabase.get(ctx)

    @Provides fun accountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun downloadDao(db: AppDatabase): DownloadDao = db.downloadDao()
    @Provides fun searchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides @Singleton
    fun biliStore(@ApplicationContext ctx: Context): BiliStore = BiliStore(ctx)

    @Provides @Singleton
    fun cookieStore(store: BiliStore, accountDao: AccountDao, scope: CoroutineScope): CookieStore =
        AndroidCookieStore(store, accountDao, scope)
}
