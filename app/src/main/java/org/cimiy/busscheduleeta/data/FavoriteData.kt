package org.cimiy.busscheduleeta.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteTrip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val company: String,          // "KMB" 或 "CTB"
    val route: String,            // 例如 "104"
    val direction: String,        // "O" 或 "I"
    val serviceType: String = "1", // 九巴用；城巴可固定 "1"
    val stopId: String,
    val routeLabel: String,       // 畫面上大字：路線號碼
    val destLabel: String,        // 終點，例如「旺角」
    val stopLabel: String,        // 上車站，例如「彌敦道」
    val sortOrder: Int = 0
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY sortOrder, id")
    fun all(): Flow<List<FavoriteTrip>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Insert
    suspend fun insert(trip: FavoriteTrip): Long

    @Update
    suspend fun update(trip: FavoriteTrip)

    @Delete
    suspend fun delete(trip: FavoriteTrip)
}

@Database(entities = [FavoriteTrip::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "bus_eta.db"
                ).build().also { instance = it }
            }
    }
}