package com.instatask.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlanEntity::class,
        TaskEntity::class,
        ReelEntity::class,
        CommentEntity::class,
        ChatMessageEntity::class,
        MemoryChunkEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class InstaTaskDatabase : RoomDatabase() {
    abstract fun dao(): InstaTaskDao
}
