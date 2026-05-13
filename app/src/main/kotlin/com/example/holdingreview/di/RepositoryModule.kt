package com.example.holdingreview.di

import com.example.holdingreview.data.remote.EastmoneyKLineRemoteDataSource
import com.example.holdingreview.data.remote.KLineRemoteDataSource
import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.data.remote.TencentQuoteRemoteDataSource
import com.example.holdingreview.data.repository.DefaultPortfolioRepository
import com.example.holdingreview.data.repository.DefaultStockMonitorRepository
import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.data.repository.StockMonitorRepository
import com.example.holdingreview.data.seed.PersonalPortfolioSeedDataSource
import com.example.holdingreview.data.seed.PersonalPortfolioSeedSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 将仓库和远程数据源抽象绑定到默认实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(repository: DefaultPortfolioRepository): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindStockMonitorRepository(repository: DefaultStockMonitorRepository): StockMonitorRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRemoteDataSource(dataSource: TencentQuoteRemoteDataSource): QuoteRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindKLineRemoteDataSource(dataSource: EastmoneyKLineRemoteDataSource): KLineRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindPersonalPortfolioSeedSource(dataSource: PersonalPortfolioSeedDataSource): PersonalPortfolioSeedSource
}
