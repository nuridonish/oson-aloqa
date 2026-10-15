package com.osonaloqa.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocked")
data class BlockedUser(
    @PrimaryKey val address: String,
    val name: String,
    val ts: Long
)

@Entity(tableName = "pending_media")
data class PendingMedia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val uri: String,
    val kind: String,
    val ts: Long
)

@Entity(tableName = "net_messages")
data class NetMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val body: String,
    val ts: Long,
    val out: Boolean,
    val kind: String,
    val enc: Boolean,
    val gid: String = "",
    val gname: String = "",
    val uuid: String = "",
    val seq: Long = 0,
    val ackState: String = ""
)

@Dao
interface BlockedDao {
    @Query("SELECT * FROM blocked ORDER BY ts DESC")
    fun all(): Flow<List<BlockedUser>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked WHERE address = :address)")
    suspend fun isBlocked(address: String): Boolean

    @Query("SELECT * FROM blocked ORDER BY ts DESC")
    suspend fun list(): List<BlockedUser>

    @Insert
    suspend fun add(user: BlockedUser)

    @Query("DELETE FROM blocked WHERE address = :address")
    suspend fun remove(address: String)
}

@Dao
interface PendingMediaDao {
    @Query("SELECT * FROM pending_media ORDER BY ts DESC")
    suspend fun list(): List<PendingMedia>

    @Insert
    suspend fun add(item: PendingMedia)
}

@Dao
interface NetDao {
    @Query("SELECT * FROM net_messages WHERE address = :address AND gid = :gid ORDER BY ts ASC")
    suspend fun forAddress(address: String, gid: String): List<NetMessage>

    @Query("SELECT * FROM net_messages WHERE gid = :gid AND body = :body AND ts = :ts LIMIT 1")
    suspend fun existsGroup(gid: String, body: String, ts: Long): NetMessage?

    @Query("SELECT EXISTS(SELECT 1 FROM net_messages WHERE uuid = :uuid LIMIT 1)")
    suspend fun existsUuid(uuid: String): Boolean

    @Query("SELECT * FROM net_messages WHERE uuid = :uuid LIMIT 1")
    suspend fun byUuid(uuid: String): NetMessage?

    @Query("SELECT * FROM net_messages WHERE address = :address AND gid = '' ORDER BY ts DESC LIMIT 1")
    suspend fun lastDirect(address: String): NetMessage?

    @Query("SELECT * FROM net_messages ORDER BY ts DESC")
    suspend fun allDesc(): List<NetMessage>

    @Insert
    suspend fun add(msg: NetMessage): Long

    @Query("UPDATE net_messages SET ackState = :state WHERE uuid = :uuid")
    suspend fun setAck(uuid: String, state: String)

    @Query("UPDATE net_messages SET seq = :seq, ackState = :state WHERE uuid = :uuid")
    suspend fun markSent(uuid: String, seq: Long, state: String)

    @Query("UPDATE net_messages SET ackState = :state WHERE seq = :seq AND seq > 0")
    suspend fun setAckBySeq(seq: Long, state: String)

    @Query("SELECT uuid FROM net_messages WHERE seq = :seq AND seq > 0 LIMIT 1")
    suspend fun uuidBySeq(seq: Long): String?

    @Query("UPDATE net_messages SET body = :body, kind = 'text' WHERE uuid = :uuid")
    suspend fun applyEdit(uuid: String, body: String)

    @Query("UPDATE net_messages SET body = :body WHERE seq = :seq AND seq > 0")
    suspend fun applyEditBySeq(seq: Long, body: String)

    @Query("UPDATE net_messages SET body = '🚫 Паём нест', kind = 'deleted' WHERE seq = :seq AND seq > 0")
    suspend fun applyDeleteBySeq(seq: Long)

    @Query("SELECT COUNT(*) FROM net_messages")
    suspend fun count(): Int
}

@Database(
    entities = [BlockedUser::class, PendingMedia::class, NetMessage::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedDao(): BlockedDao
    abstract fun pendingMediaDao(): PendingMediaDao
    abstract fun netDao(): NetDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE net_messages ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE net_messages ADD COLUMN seq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE net_messages ADD COLUMN ackState TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "osonaloqa_pro.db"
            )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                .also { inst = it }
        }
    }
}
