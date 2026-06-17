package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.FoodEntity
import com.example.data.local.GoalEntity
import com.example.data.local.WorkoutEntity
import com.example.data.repository.GymLogRepository
import com.example.data.repository.SyncResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GymLogViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = GymLogRepository(application, db.localDao)

    // Current selected date state (Default to today)
    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Real active today date (unaffected by history selection)
    val todayDateStr: String = getTodayDateString()

    // Sync state
    private val _syncStatus = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncStatus: StateFlow<SyncState> = _syncStatus.asStateFlow()

    // User Profile name state
    private val _userName = MutableStateFlow(repository.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    // Theme Mode state ("Light", "Dark", "System")
    private val _themeMode = MutableStateFlow(repository.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun updateThemeMode(mode: String) {
        repository.saveThemeMode(mode)
        _themeMode.value = mode
    }

    // Flow of goals
    val goals: StateFlow<GoalEntity> = repository.getGoalsFlow()
        .map { it ?: GoalEntity(dailyCalories = 2000, dailyProtein = 150.0, weightGoal = 75.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalEntity(dailyCalories = 2000, dailyProtein = 150.0, weightGoal = 75.0))

    // Reactive list of workouts for selected date
    @OptIn(ExperimentalCoroutinesApi::class)
    val workoutsForSelectedDate: StateFlow<List<WorkoutEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getWorkoutsByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive list of food for selected date
    @OptIn(ExperimentalCoroutinesApi::class)
    val foodForSelectedDate: StateFlow<List<FoodEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getFoodByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All workouts flow for autocomplete options
    val allWorkouts: StateFlow<List<WorkoutEntity>> = db.localDao.getAllWorkoutsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exercise autocomplete options list
    val exerciseSuggestions: StateFlow<List<String>> = allWorkouts
        .map { list -> list.map { it.exercise }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today's Workouts (for Dashboard widget summary)
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayWorkouts: StateFlow<List<WorkoutEntity>> = flowOf(todayDateStr)
        .flatMapLatest { date -> repository.getWorkoutsByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today's Food (for Dashboard nutrient calculations)
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayFood: StateFlow<List<FoodEntity>> = flowOf(todayDateStr)
        .flatMapLatest { date -> repository.getFoodByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Try initial load/sync on startup
        if (repository.isCredentialsConfigured()) {
            triggerSyncAll()
        }
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun addWorkout(exercise: String, weight: Double, reps: Int, sets: Int, notes: String) {
        viewModelScope.launch {
            val workout = WorkoutEntity(
                date = _selectedDate.value,
                exercise = exercise,
                weight = weight,
                reps = reps,
                sets = sets,
                notes = notes
            )
            repository.addWorkout(workout)
        }
    }

    fun addFood(mealType: String, foodName: String, calories: Int, protein: Double, carbs: Double) {
        viewModelScope.launch {
            val food = FoodEntity(
                date = _selectedDate.value,
                mealType = mealType,
                foodName = foodName,
                calories = calories,
                protein = protein,
                carbs = carbs
            )
            repository.addFood(food)
        }
    }

    fun deleteWorkout(workoutId: Int) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
        }
    }

    fun deleteFood(foodId: Int) {
        viewModelScope.launch {
            repository.deleteFood(foodId)
        }
    }

    fun updateProfile(name: String, dailyCalories: Int, dailyProtein: Double, weightGoal: Double) {
        viewModelScope.launch {
            repository.saveUserName(name)
            _userName.value = name
            repository.updateGoals(dailyCalories, dailyProtein, weightGoal)
        }
    }

    fun updateSheetConfig(spreadsheetId: String, serviceAccountJson: String) {
        repository.saveSpreadsheetId(spreadsheetId)
        repository.saveServiceAccountJson(serviceAccountJson)
        if (spreadsheetId.isNotEmpty() && serviceAccountJson.isNotEmpty()) {
            triggerSyncAll()
        }
    }

    fun triggerSyncAll() {
        viewModelScope.launch {
            _syncStatus.value = SyncState.Syncing
            when (val res = repository.syncAll()) {
                is SyncResult.Success -> {
                    _syncStatus.value = SyncState.Success
                }
                is SyncResult.Error -> {
                    _syncStatus.value = SyncState.Error(res.message)
                }
                is SyncResult.NoCredentials -> {
                    _syncStatus.value = SyncState.Error("Google Sheets credentials not set. Go to settings to configure them.")
                }
            }
        }
    }

    fun resetSyncStatus() {
        _syncStatus.value = SyncState.Idle
    }

    private fun getTodayDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Calendar.getInstance().time)
    }
}

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    object Success : SyncState
    data class Error(val message: String) : SyncState
}
