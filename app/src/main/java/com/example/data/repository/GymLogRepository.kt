package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.*
import com.example.data.remote.GoogleSheetsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

sealed interface SyncResult {
    object Success : SyncResult
    data class Error(val message: String) : SyncResult
    object NoCredentials : SyncResult
}

class GymLogRepository(
    private val context: Context,
    private val localDao: LocalDao,
    private val sheetsClient: GoogleSheetsClient = GoogleSheetsClient()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gym_food_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_SERVICE_ACCOUNT_JSON = "service_account_json"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    // --- Configuration Details ---
    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "Dark") ?: "Dark"
    }

    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getSpreadsheetId(): String {
        val saved = prefs.getString(KEY_SPREADSHEET_ID, "") ?: ""
        if (saved.isNotEmpty()) return saved
        // Fallback to BuildConfig if configured
        val bConfig = BuildConfig.GOOGLE_SPREADSHEET_ID
        return if (bConfig != "YOUR_SPREADSHEET_ID_HERE") bConfig else ""
    }

    fun saveSpreadsheetId(id: String) {
        prefs.edit().putString(KEY_SPREADSHEET_ID, id).apply()
    }

    fun getServiceAccountJson(): String {
        val saved = prefs.getString(KEY_SERVICE_ACCOUNT_JSON, "") ?: ""
        if (saved.isNotEmpty()) return saved
        // Fallback to BuildConfig if configured
        val bConfig = BuildConfig.GOOGLE_SERVICE_ACCOUNT_JSON
        return if (bConfig != "YOUR_SERVICE_ACCOUNT_JSON_HERE") bConfig else ""
    }

    fun saveServiceAccountJson(json: String) {
        prefs.edit().putString(KEY_SERVICE_ACCOUNT_JSON, json).apply()
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "Champ") ?: "Champ"
    }

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun isCredentialsConfigured(): Boolean {
        return getSpreadsheetId().isNotEmpty() && getServiceAccountJson().isNotEmpty()
    }

    // --- Local DB Accessors (Flows for Live UI) ---
    fun getWorkoutsByDateFlow(date: String): Flow<List<WorkoutEntity>> = localDao.getWorkoutsByDateFlow(date)
    fun getFoodByDateFlow(date: String): Flow<List<FoodEntity>> = localDao.getFoodByDateFlow(date)
    fun getGoalsFlow(): Flow<GoalEntity?> = localDao.getGoalsFlow()

    // --- Database Operations ---
    suspend fun addWorkout(workout: WorkoutEntity) {
        withContext(Dispatchers.IO) {
            val id = localDao.insertWorkout(workout.copy(isSynced = false))
            // Attempt background sync
            syncWorkoutToSheets(id.toInt())
        }
    }

    suspend fun addFood(food: FoodEntity) {
        withContext(Dispatchers.IO) {
            val id = localDao.insertFood(food.copy(isSynced = false))
            // Attempt background sync
            syncFoodToSheets(id.toInt())
        }
    }

    suspend fun deleteWorkout(id: Int) {
        withContext(Dispatchers.IO) {
            localDao.deleteWorkout(id)
            // Note: deleting in UI only. Advanced sync would handle deletions on Sheet, but simple sync works for now.
        }
    }

    suspend fun deleteFood(id: Int) {
        withContext(Dispatchers.IO) {
            localDao.deleteFood(id)
        }
    }

    suspend fun updateGoals(calories: Int, protein: Double, weight: Double) {
        withContext(Dispatchers.IO) {
            val goals = GoalEntity(id = 1, dailyCalories = calories, dailyProtein = protein, weightGoal = weight, isSynced = false)
            localDao.insertGoals(goals)
            syncGoalsToSheets()
        }
    }

    // --- Specific Sync Helpers ---
    private suspend fun syncWorkoutToSheets(workoutId: Int): Boolean {
        if (!isCredentialsConfigured()) return false
        return try {
            val workouts = localDao.getAllWorkoutsFlow().firstOrNull() ?: emptyList()
            val workout = workouts.find { it.id == workoutId } ?: return false
            if (workout.isSynced) return true

            sheetsClient.appendSheetValues(
                serviceAccountJson = getServiceAccountJson(),
                spreadsheetId = getSpreadsheetId(),
                range = "Workouts!A:F",
                values = listOf(
                    listOf(
                        workout.date,
                        workout.exercise,
                        workout.weight.toString(),
                        workout.reps.toString(),
                        workout.sets.toString(),
                        workout.notes
                    )
                )
            )
            localDao.markWorkoutSynced(workoutId)
            true
        } catch (e: Exception) {
            Log.e("GymLogRepository", "Workout sync error", e)
            false
        }
    }

    private suspend fun syncFoodToSheets(foodId: Int): Boolean {
        if (!isCredentialsConfigured()) return false
        return try {
            val foods = localDao.getAllFoodFlow().firstOrNull() ?: emptyList()
            val food = foods.find { it.id == foodId } ?: return false
            if (food.isSynced) return true

            sheetsClient.appendSheetValues(
                serviceAccountJson = getServiceAccountJson(),
                spreadsheetId = getSpreadsheetId(),
                range = "Food!A:F",
                values = listOf(
                    listOf(
                        food.date,
                        food.mealType,
                        food.foodName,
                        food.calories.toString(),
                        food.protein.toString(),
                        food.carbs.toString()
                    )
                )
            )
            localDao.markFoodSynced(foodId)
            true
        } catch (e: Exception) {
            Log.e("GymLogRepository", "Food sync error", e)
            false
        }
    }

    private suspend fun syncGoalsToSheets(): Boolean {
        if (!isCredentialsConfigured()) return false
        return try {
            val goals = localDao.getGoalsDirect() ?: return false
            if (goals.isSynced) return true

            sheetsClient.updateSheetValues(
                serviceAccountJson = getServiceAccountJson(),
                spreadsheetId = getSpreadsheetId(),
                range = "Goals!A1:B4",
                values = listOf(
                    listOf("Goal_Key", "Goal_Value"),
                    listOf("Daily_Calories", goals.dailyCalories.toString()),
                    listOf("Daily_Protein", goals.dailyProtein.toString()),
                    listOf("Weight_Goal", goals.weightGoal.toString())
                )
            )
            localDao.markGoalsSynced()
            true
        } catch (e: Exception) {
            Log.e("GymLogRepository", "Goals sync error", e)
            false
        }
    }

    // --- Master Sync (Bi-directional: upload local, download sheet) ---
    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        if (!isCredentialsConfigured()) {
            return@withContext SyncResult.NoCredentials
        }
        try {
            val serviceAccount = getServiceAccountJson()
            val spreadsheetId = getSpreadsheetId()

            // 1. Upload unsynced Workouts
            val unsyncedWorkouts = localDao.getUnsyncedWorkouts()
            if (unsyncedWorkouts.isNotEmpty()) {
                val workoutsValues = unsyncedWorkouts.map {
                    listOf(
                        it.date,
                        it.exercise,
                        it.weight.toString(),
                        it.reps.toString(),
                        it.sets.toString(),
                        it.notes
                    )
                }
                sheetsClient.appendSheetValues(serviceAccount, spreadsheetId, "Workouts!A:F", workoutsValues)
                unsyncedWorkouts.forEach { localDao.markWorkoutSynced(it.id) }
            }

            // 2. Upload unsynced Food
            val unsyncedFood = localDao.getUnsyncedFood()
            if (unsyncedFood.isNotEmpty()) {
                val foodsValues = unsyncedFood.map {
                    listOf(
                        it.date,
                        it.mealType,
                        it.foodName,
                        it.calories.toString(),
                        it.protein.toString(),
                        it.carbs.toString()
                    )
                }
                sheetsClient.appendSheetValues(serviceAccount, spreadsheetId, "Food!A:F", foodsValues)
                unsyncedFood.forEach { localDao.markFoodSynced(it.id) }
            }

            // 3. Upload unsynced Goals
            val goals = localDao.getGoalsDirect()
            if (goals != null && !goals.isSynced) {
                sheetsClient.updateSheetValues(
                    serviceAccount, spreadsheetId, "Goals!A1:B4",
                    listOf(
                        listOf("Goal_Key", "Goal_Value"),
                        listOf("Daily_Calories", goals.dailyCalories.toString()),
                        listOf("Daily_Protein", goals.dailyProtein.toString()),
                        listOf("Weight_Goal", goals.weightGoal.toString())
                    )
                )
                localDao.markGoalsSynced()
            }

            // 4. Download and Merge Workouts from Sheets
            try {
                val remoteWorkouts = sheetsClient.getSheetValues(serviceAccount, spreadsheetId, "Workouts!A2:F")
                val localWorkouts = localDao.getAllWorkoutsFlow().firstOrNull() ?: emptyList()
                val parsedWorkouts = remoteWorkouts.mapNotNull { row ->
                    if (row.size < 5) return@mapNotNull null
                    val date = row.getOrNull(0) ?: ""
                    val exercise = row.getOrNull(1) ?: ""
                    val weight = row.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                    val reps = row.getOrNull(3)?.toIntOrNull() ?: 0
                    val sets = row.getOrNull(4)?.toIntOrNull() ?: 0
                    val notes = row.getOrNull(5) ?: ""

                    if (date.isEmpty() || exercise.isEmpty()) return@mapNotNull null

                    // If it matches exactly with any local item, skip insertion to prevent duplicates
                    val alreadyExists = localWorkouts.any {
                        it.date == date && it.exercise == exercise && it.weight == weight && it.reps == reps && it.sets == sets
                    }
                    if (alreadyExists) return@mapNotNull null

                    WorkoutEntity(
                        date = date,
                        exercise = exercise,
                        weight = weight,
                        reps = reps,
                        sets = sets,
                        notes = notes,
                        isSynced = true
                    )
                }
                if (parsedWorkouts.isNotEmpty()) {
                    localDao.insertWorkouts(parsedWorkouts)
                }
            } catch (e: Exception) {
                Log.e("GymLogRepository", "Downloading workouts failed (perhaps tab lacks data?): ${e.message}")
            }

            // 5. Download and Merge Food from Sheets
            try {
                val remoteFood = sheetsClient.getSheetValues(serviceAccount, spreadsheetId, "Food!A2:F")
                val localFood = localDao.getAllFoodFlow().firstOrNull() ?: emptyList()
                val parsedFood = remoteFood.mapNotNull { row ->
                    if (row.size < 4) return@mapNotNull null
                    val date = row.getOrNull(0) ?: ""
                    val mealType = row.getOrNull(1) ?: ""
                    val foodName = row.getOrNull(2) ?: ""
                    val calories = row.getOrNull(3)?.toIntOrNull() ?: 0
                    val protein = row.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                    val carbs = row.getOrNull(5)?.toDoubleOrNull() ?: 0.0

                    if (date.isEmpty() || foodName.isEmpty()) return@mapNotNull null

                    val alreadyExists = localFood.any {
                        it.date == date && it.mealType == mealType && it.foodName == foodName && it.calories == calories
                    }
                    if (alreadyExists) return@mapNotNull null

                    FoodEntity(
                        date = date,
                        mealType = mealType,
                        foodName = foodName,
                        calories = calories,
                        protein = protein,
                        carbs = carbs,
                        isSynced = true
                    )
                }
                if (parsedFood.isNotEmpty()) {
                    localDao.insertFoods(parsedFood)
                }
            } catch (e: Exception) {
                Log.e("GymLogRepository", "Downloading food failed (perhaps tab lacks data?): ${e.message}")
            }

            // 6. Download and Update Goals from Sheets
            try {
                val remoteGoals = sheetsClient.getSheetValues(serviceAccount, spreadsheetId, "Goals!A2:B4")
                var cal = 2000
                var prot = 150.0
                var wt = 75.0
                remoteGoals.forEach { row ->
                    if (row.size >= 2) {
                        val key = row[0]
                        val value = row[1]
                        when (key) {
                            "Daily_Calories" -> cal = value.toIntOrNull() ?: cal
                            "Daily_Protein" -> prot = value.toDoubleOrNull() ?: prot
                            "Weight_Goal" -> wt = value.toDoubleOrNull() ?: wt
                        }
                    }
                }
                localDao.insertGoals(GoalEntity(id = 1, dailyCalories = cal, dailyProtein = prot, weightGoal = wt, isSynced = true))
            } catch (e: Exception) {
                Log.e("GymLogRepository", "Downloading goals failed: ${e.message}")
            }

            SyncResult.Success
        } catch (e: Exception) {
            Log.e("GymLogRepository", "Master-sync error", e)
            SyncResult.Error(e.localizedMessage ?: "Unknown synchronization failure")
        }
    }
}
