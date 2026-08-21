package com.networktoolbox.di

import android.content.Context
import com.networktoolbox.core.network.data.AndroidDnsEngine
import com.networktoolbox.core.network.data.AndroidNetworkRepository
import com.networktoolbox.core.network.data.AndroidPingEngine
import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.feature.dashboard.domain.ObserveNetworkContextUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideNetworkRepository(
        @ApplicationContext context: Context,
    ): NetworkRepository = AndroidNetworkRepository(context)

    @Provides
    @Singleton
    fun providePingEngine(): PingEngine = AndroidPingEngine()

    @Provides
    @Singleton
    fun provideDnsEngine(): DnsEngine = AndroidDnsEngine()

    @Provides
    @Singleton
    fun provideObserveNetworkContextUseCase(
        repository: NetworkRepository,
    ): ObserveNetworkContextUseCase = ObserveNetworkContextUseCase(repository)
}
