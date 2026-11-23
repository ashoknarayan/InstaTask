package com.instatask.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InstaTaskDatabase =
        Room.databaseBuilder(context, InstaTaskDatabase::class.java, "instatask.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(database: InstaTaskDatabase): InstaTaskDao = database.dao()
}
