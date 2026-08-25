package com.networktoolbox.di

import android.content.Context
import com.networktoolbox.core.network.data.AndroidDnsEngine
import com.networktoolbox.core.network.data.AndroidNetworkRepository
import com.networktoolbox.core.network.data.AndroidPingEngine
import com.networktoolbox.core.network.data.AndroidPingSessionProbe
import com.networktoolbox.core.network.data.AndroidTcpPortChecker
import com.networktoolbox.core.network.dns.DnsEngine
import com.networktoolbox.core.network.ping.PingEngine
import com.networktoolbox.core.network.ping.DefaultPingSessionEngine
import com.networktoolbox.core.network.ping.PingProbe
import com.networktoolbox.core.network.ping.PingSessionEngine
import com.networktoolbox.core.network.tcp.TcpPortChecker
import com.networktoolbox.core.network.repository.NetworkRepository
import com.networktoolbox.feature.dashboard.domain.ObserveNetworkContextUseCase
import com.networktoolbox.feature.dns.domain.LookupDnsUseCase
import com.networktoolbox.feature.ping.domain.ExecutePingUseCase
import com.networktoolbox.feature.port.domain.CheckTcpPortUseCase
import com.networktoolbox.feature.report.diagnostic.BasicDiagnosticAnalyzer
import com.networktoolbox.feature.report.diagnostic.DiagnosticAnalyzer
import com.networktoolbox.feature.report.domain.DnsUseCase as ReportDnsUseCase
import com.networktoolbox.feature.report.domain.PingUseCase as ReportPingUseCase
import com.networktoolbox.feature.report.domain.TcpUseCase as ReportTcpUseCase
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
    fun providePingProbe(): PingProbe = AndroidPingSessionProbe()

    @Provides
    @Singleton
    fun providePingSessionEngine(probe: PingProbe): PingSessionEngine =
        DefaultPingSessionEngine(probe)

    @Provides
    @Singleton
    fun provideDnsEngine(): DnsEngine = AndroidDnsEngine()

    @Provides
    @Singleton
    fun provideTcpPortChecker(): TcpPortChecker = AndroidTcpPortChecker()

    @Provides
    @Singleton
    fun provideObserveNetworkContextUseCase(
        repository: NetworkRepository,
    ): ObserveNetworkContextUseCase = ObserveNetworkContextUseCase(repository)

    @Provides
    @Singleton
    fun provideDiagnosticAnalyzer(): DiagnosticAnalyzer = BasicDiagnosticAnalyzer()

    @Provides
    @Singleton
    fun provideReportPingUseCase(
        executePing: ExecutePingUseCase,
    ): ReportPingUseCase = ReportPingUseCase { target ->
        executePing(target, persistHistory = false)
    }

    @Provides
    @Singleton
    fun provideReportDnsUseCase(
        lookupDns: LookupDnsUseCase,
    ): ReportDnsUseCase = ReportDnsUseCase { domain ->
        lookupDns(domain, persistHistory = false)
    }

    @Provides
    @Singleton
    fun provideReportTcpUseCase(
        checkTcpPort: CheckTcpPortUseCase,
    ): ReportTcpUseCase = ReportTcpUseCase { host, port ->
        checkTcpPort(host, port, persistHistory = false)
    }
}
