package com.example.holdingreview.di

import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.data.remote.TencentQuoteRemoteDataSource
import com.example.holdingreview.data.repository.DefaultPortfolioRepository
import com.example.holdingreview.data.repository.PortfolioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(repository: DefaultPortfolioRepository): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRemoteDataSource(dataSource: TencentQuoteRemoteDataSource): QuoteRemoteDataSource
}
