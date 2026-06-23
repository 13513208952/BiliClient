package com.biliclient.di

import android.content.Context
import com.biliclient.net.Ua
import com.biliclient.player.PlayerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): PlayerManager = PlayerManager(context, okHttpClient, Ua.PC)
}
