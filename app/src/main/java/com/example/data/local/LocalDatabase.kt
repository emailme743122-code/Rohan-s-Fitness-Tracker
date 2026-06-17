package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,          // YYYY-MM-DD
    val exercise: String,
    val weight: Double,
    val reps: Int,
    val sets: Int,
    val notes: String = "",
    val isSynced: Boolean = false
)

@Entity(tableName = "food")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,          // YYYY-MM-DD
    val mealType: String,      // Breakfast, Lunch, Dinner, Snack
    val foodName: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double = 0.0,
    val isSynced: Boolean = false
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: Int = 1,
    val dailyCalories: Int = 2000,
    val dailyProtein: Double = 150.0,
    val weightGoal: Double = 75.0,
    val isSynced: Boolean = false
)

@Dao
interface LocalDao {
    // Workouts
    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC")
    fun getAllWorkoutsFlow(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE date = :date ORDER BY id ASC")
    fun getWorkoutsByDateFlow(date: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE isSynced = 0")
    suspend fun getUnsyncedWorkouts(): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>)

    @Query("UPDATE workouts SET isSynced = 1 WHERE id = :id")
    suspend fun markWorkoutSynced(id: Int)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Int)

    // Food
    @Query("SELECT * FROM food ORDER BY date DESC, id DESC")
    fun getAllFoodFlow(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE date = :date ORDER BY id ASC")
    fun getFoodByDateFlow(date: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE isSynced = 0")
    suspend fun getUnsyncedFood(): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<FoodEntity>)

    @Query("UPDATE food SET isSynced = 1 WHERE id = :id")
    suspend fun markFoodSynced(id: Int)

    @Query("DELETE FROM food WHERE id = :id")
    suspend fun deleteFood(id: Int)

    // Goals
    @Query("SELECT * FROM goals WHERE id = 1")
    fun getGoalsFlow(): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = 1")
    suspend fun getGoalsDirect(): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: GoalEntity)

    @Query("UPDATE goals SET isSynced = 1 WHERE id = 1")
    suspend fun markGoalsSynced()
}

@Database(entities = [WorkoutEntity::class, FoodEntity::class, GoalEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val localDao: LocalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_food_log_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
