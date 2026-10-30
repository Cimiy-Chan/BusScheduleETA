package org.cimiy.busscheduleeta.data

import android.content.Context
import androidx.room.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteTrip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val company: String,
    val route: String,
    val direction: String,
    val serviceType: String = "1",
    val stopId: String,
    val routeLabel: String,
    val destLabel: String,
    val stopLabel: String,
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

    @Update
    suspend fun updateAll(trips: List<FavoriteTrip>)

    @Delete
    suspend fun delete(trip: FavoriteTrip)
}

@Database(entities = [FavoriteTrip::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao

    /** 依畫面順序重寫 sortOrder；放在 DB 類別以確保單一 transaction */
    suspend fun persistFavoriteSortOrders(ordered: List<FavoriteTrip>) {
        if (ordered.isEmpty()) return
        val dao = favoriteDao()
        withTransaction {
            dao.updateAll(ordered.mapIndexed { index, trip -> trip.copy(sortOrder = index) })
        }
    }

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
