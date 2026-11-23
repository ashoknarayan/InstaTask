package com.instatask.core.ai.di

import com.instatask.core.ai.DeterministicTextEmbedder
import com.instatask.core.ai.FakeIntelligenceEngine
import com.instatask.core.ai.OnDeviceTextEmbedder
import com.instatask.core.ai.TaskIntelligenceEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds @Singleton
    abstract fun bindIntelligenceEngine(engine: FakeIntelligenceEngine): TaskIntelligenceEngine

    @Binds @Singleton
    abstract fun bindTextEmbedder(embedder: DeterministicTextEmbedder): OnDeviceTextEmbedder
}
