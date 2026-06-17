package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.data.local.FoodEntity
import com.example.data.local.GoalEntity
import com.example.data.local.WorkoutEntity
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.ui.theme.*
import com.example.ui.viewmodel.GymLogViewModel
import com.example.ui.viewmodel.SyncState
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Workout : Screen("workout", "Workout", Icons.Default.FitnessCenter)
    object Food : Screen("food", "Food", Icons.Default.Restaurant)
    object History : Screen("history", "History", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymAndFoodLogApp(viewModel: GymLogViewModel) {
    val navController = rememberNavController()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Observe syncStatus to show friendly snackbar feedback
    LaunchedEffect(syncStatus) {
        when (val status = syncStatus) {
            is SyncState.Success -> {
                snackbarHostState.showSnackbar("Sheets synchronization complete!")
                viewModel.resetSyncStatus()
            }
            is SyncState.Error -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.resetSyncStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianGray,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav")
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val items = listOf(
                    Screen.Dashboard,
                    Screen.Workout,
                    Screen.Food,
                    Screen.History,
                    Screen.Settings
                )

                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = if (isSelected) GymLime else TextMuted
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                color = if (isSelected) TextWhite else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = DeepSlate
                        ),
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(viewModel, navController)
            }
            composable(Screen.Workout.route) {
                WorkoutScreen(viewModel)
            }
            composable(Screen.Food.route) {
                FoodScreen(viewModel)
            }
            composable(Screen.History.route) {
                HistoryScreen(viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel)
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: GymLogViewModel, navController: NavController) {
    val userName by viewModel.userName.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val todayFoods by viewModel.todayFood.collectAsState()
    val todayWorkouts by viewModel.todayWorkouts.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    // Calculations
    val totalCalories = todayFoods.sumOf { it.calories }
    val totalProtein = todayFoods.sumOf { it.protein }

    val caloriePercent = if (goals.dailyCalories > 0) totalCalories.toFloat() / goals.dailyCalories else 0f
    val proteinPercent = if (goals.dailyProtein > 0) totalProtein.toFloat() / goals.dailyProtein.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("dashboard_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = getFormattedTodayDate(),
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Welcome, $userName!",
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            IconButton(
                onClick = { viewModel.triggerSyncAll() },
                modifier = Modifier
                    .background(DeepSlate, CircleShape)
                    .size(44.dp)
                    .testTag("dashboard_sync_button")
            ) {
                if (syncStatus is SyncState.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = GymLime,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = GymLime
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Motivational Quote Callout
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = VoltGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "\"Consistency is key. Track every rep, feed the machine, reach your goals.\"",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Dual Ring Progress Widget
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TODAY'S FOOD INTAKE STATS",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(20.dp))

                Box(contentAlignment = Alignment.Center) {
                    // Outer Ring: Calories
                    CircularProgressWidget(
                        progress = caloriePercent,
                        color = GymLime,
                        size = 180.dp,
                        strokeWidth = 14.dp
                    )
                    // Inner Ring: Protein
                    CircularProgressWidget(
                        progress = proteinPercent,
                        color = VoltGreen,
                        size = 135.dp,
                        strokeWidth = 10.dp
                    )

                    // Text Details
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalCalories",
                            color = GymLime,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "kcal intake / ${goals.dailyCalories}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1fg Protein", totalProtein),
                            color = VoltGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stats Legend / Metrics bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(GymLime, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Food Intake", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${(caloriePercent * 100).toInt()}% of Goal",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(VoltGreen, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Protein", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${(proteinPercent * 100).toInt()}% of Goal",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Today's Workouts Widget
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TODAY'S WORKOUT",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (todayWorkouts.isEmpty()) {
                    Text(
                        text = "No exercises logged today yet. Every workout gets you closer to your goal!",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                } else {
                    Column {
                        todayWorkouts.take(3).forEach { workout ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(workout.exercise, color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(
                                    "${workout.sets} x ${workout.reps} sets @ ${workout.weight}kg",
                                    color = GymLime,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (todayWorkouts.size > 3) {
                            Text(
                                "and ${todayWorkouts.size - 3} more exercises...",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { navController.navigate(Screen.Workout.route) },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("start_workout_quick_action"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = TextWhite)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Workout", color = TextWhite, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { navController.navigate(Screen.Food.route) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepSlate),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("log_food_quick_action"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GymLime)
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = GymLime)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Food", color = GymLime, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun CircularProgressWidget(
    progress: Float,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp
) {
    val trackColor = DeepSlate
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Track
            drawCircle(
                color = trackColor,
                style = Stroke(width = strokeWidth.toPx())
            )
            // Sweep progress
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceAtMost(1f),
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

fun getFormattedTodayDate(): String {
    val formatter = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
    return formatter.format(Calendar.getInstance().time)
}

// ==========================================
// 2. LOG WORKOUT SCREEN
// ==========================================
@Composable
fun WorkoutScreen(viewModel: GymLogViewModel) {
    val workoutList by viewModel.workoutsForSelectedDate.collectAsState()
    val exerciseSuggestions by viewModel.exerciseSuggestions.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    var exerciseInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var repsInput by remember { mutableStateOf("") }
    var setsInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var suggestionsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .padding(16.dp)
            .testTag("workout_screen")
    ) {
        Text(
            text = "LOG EXERCISE",
            color = GymLime,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Active Session: $selectedDate",
            color = TextWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header Form Area
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Exercise Autocomplete Dropdown Box
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = exerciseInput,
                        onValueChange = {
                            exerciseInput = it
                            suggestionsExpanded = it.isNotEmpty()
                        },
                        label = { Text("Exercise Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime,
                            unfocusedBorderColor = BorderGraphite,
                            focusedLabelColor = GymLime,
                            unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_name_input"),
                        leadingIcon = { Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = GymLime) }
                    )

                    val filteredSuggestions = exerciseSuggestions.filter {
                        it.contains(exerciseInput, ignoreCase = true)
                    }

                    if (suggestionsExpanded && filteredSuggestions.isNotEmpty()) {
                        DropdownMenu(
                            expanded = suggestionsExpanded,
                            onDismissRequest = { suggestionsExpanded = false },
                            modifier = Modifier
                                .background(CardGraphite)
                                .fillMaxWidth(0.9f)
                        ) {
                            filteredSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion, color = TextWhite) },
                                    onClick = {
                                        exerciseInput = suggestion
                                        suggestionsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Weight, Reps, Sets Inputs Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("workout_weight_input")
                    )

                    OutlinedTextField(
                        value = repsInput,
                        onValueChange = { repsInput = it },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("workout_reps_input")
                    )

                    OutlinedTextField(
                        value = setsInput,
                        onValueChange = { setsInput = it },
                        label = { Text("Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("workout_sets_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Notes (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workout_notes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Queue/Add Button
                Button(
                    onClick = {
                        if (exerciseInput.isNotEmpty() && weightInput.isNotEmpty() && repsInput.isNotEmpty() && setsInput.isNotEmpty()) {
                            viewModel.addWorkout(
                                exercise = exerciseInput,
                                weight = weightInput.toDoubleOrNull() ?: 0.0,
                                reps = repsInput.toIntOrNull() ?: 0,
                                sets = setsInput.toIntOrNull() ?: 0,
                                notes = notesInput
                            )
                            // Clear inputs
                            exerciseInput = ""
                            weightInput = ""
                            repsInput = ""
                            setsInput = ""
                            notesInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymLime),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_workout_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CarbonBlack)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Workout", color = CarbonBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session workout breakdown list
        Text(
            text = "TODAY'S INSTANT LOG",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = TextMuted,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (workoutList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Session is currently empty", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workoutList) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.exercise,
                                    color = TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.sets} sets x ${item.reps} reps @ ${item.weight} kg",
                                    color = GymLime,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.notes.isNotEmpty()) {
                                    Text(
                                        text = item.notes,
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Light,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Sync indicator
                                Icon(
                                    imageVector = if (item.isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                    contentDescription = if (item.isSynced) "Synced" else "Pending sheet sync",
                                    tint = if (item.isSynced) ForestGreen else VoltGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { viewModel.deleteWorkout(item.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. FOOD LOG SCREEN
// ==========================================
@Composable
fun FoodScreen(viewModel: GymLogViewModel) {
    val foodList by viewModel.foodForSelectedDate.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val calorieSum = foodList.sumOf { it.calories }
    val caloriesRemaining = (goals.dailyCalories - calorieSum).coerceAtLeast(0)

    val mealTypes = listOf("Breakfast", "Lunch", "Dinner", "Snack")
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("food_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GymLime,
                contentColor = CarbonBlack,
                modifier = Modifier.testTag("add_food_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Food")
            }
        },
        containerColor = CarbonBlack
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
        ) {
            // Running Total Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianGray),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Running Total - $selectedDate",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Intake: $calorieSum / ${goals.dailyCalories} kcal",
                                color = if (calorieSum <= goals.dailyCalories) GymLime else Color.Red,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Protein: ${String.format(Locale.US, "%.1f", foodList.sumOf { it.protein })} / ${goals.dailyProtein} g",
                                color = VoltGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Circular Mini Remaining View
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$caloriesRemaining",
                                color = TextWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "kcal intake left",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = if (goals.dailyCalories > 0) calorieSum.toFloat() / goals.dailyCalories else 0f,
                        color = GymLime,
                        trackColor = DeepSlate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Rows to select meal type
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CarbonBlack,
                contentColor = GymLime,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GymLime
                    )
                }
            ) {
                mealTypes.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) GymLime else TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of elements matching the chosen tab type
            val activeMealType = mealTypes[selectedTab]
            val filteredFood = foodList.filter { it.mealType.equals(activeMealType, ignoreCase = true) }

            if (filteredFood.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalDining, contentDescription = null, tint = DeepSlate, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No $activeMealType food entries logged", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFood) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.foodName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("${item.calories} kcal", color = GymLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${item.protein}g protein", color = VoltGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        if (item.carbs > 0) {
                                            Text("${item.carbs}g carbs", color = TextMuted, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Sync indicator
                                    Icon(
                                        imageVector = if (item.isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                        contentDescription = if (item.isSynced) "Synced with Sheet" else "Waiting to sync",
                                        tint = if (item.isSynced) ForestGreen else VoltGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteFood(item.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Food Dialog
    if (showAddDialog) {
        var foodName by remember { mutableStateOf("") }
        var caloriesInput by remember { mutableStateOf("") }
        var proteinInput by remember { mutableStateOf("") }
        var carbsInput by remember { mutableStateOf("") }
        var mealTypeChoice by remember { mutableStateOf(mealTypes[selectedTab]) }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardGraphite),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "ADD FOOD ENTRY",
                        fontWeight = FontWeight.ExtraBold,
                        color = GymLime,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Meal Type selector within dialog
                    var expandedChoice by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedChoice = true },
                            border = BorderStroke(1.dp, BorderGraphite),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Meal: $mealTypeChoice", color = TextWhite)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GymLime)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedChoice,
                            onDismissRequest = { expandedChoice = false },
                            modifier = Modifier.background(CardGraphite)
                        ) {
                            mealTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = TextWhite) },
                                    onClick = {
                                        mealTypeChoice = type
                                        expandedChoice = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        label = { Text("Food/Meal Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_food_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = caloriesInput,
                        onValueChange = { caloriesInput = it },
                        label = { Text("Intake Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_calories_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = proteinInput,
                            onValueChange = { proteinInput = it },
                            label = { Text("Protein (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                                focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_protein_input")
                        )

                        OutlinedTextField(
                            value = carbsInput,
                            onValueChange = { carbsInput = it },
                            label = { Text("Carbs (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                                focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_carbs_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = { showAddDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }

                        Button(
                            onClick = {
                                if (foodName.isNotEmpty() && caloriesInput.isNotEmpty() && proteinInput.isNotEmpty()) {
                                    viewModel.addFood(
                                        mealType = mealTypeChoice,
                                        foodName = foodName,
                                        calories = caloriesInput.toIntOrNull() ?: 0,
                                        protein = proteinInput.toDoubleOrNull() ?: 0.0,
                                        carbs = carbsInput.toDoubleOrNull() ?: 0.0
                                    )
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GymLime),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_food_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Entry", color = CarbonBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. HISTORY SCREEN (CALENDAR / DATE VIEW)
// ==========================================
@Composable
fun HistoryScreen(viewModel: GymLogViewModel) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val workouts by viewModel.workoutsForSelectedDate.collectAsState()
    val foods by viewModel.foodForSelectedDate.collectAsState()

    val context = LocalContext.current

    // Calculations of total
    val caloriesTotal = foods.sumOf { it.calories }
    val proteinTotal = foods.sumOf { it.protein }

    // Click Calendar Date Picker
    val year: Int
    val month: Int
    val day: Int
    val calendar = Calendar.getInstance()

    // Parse current selectedDate to initialize DatePicker (format YYYY-MM-DD)
    val tokens = selectedDate.split("-")
    if (tokens.size == 3) {
        year = tokens[0].toIntOrNull() ?: calendar.get(Calendar.YEAR)
        month = (tokens[1].toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
        day = tokens[2].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
    } else {
        year = calendar.get(Calendar.YEAR)
        month = calendar.get(Calendar.MONTH)
        day = calendar.get(Calendar.DAY_OF_MONTH)
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, yearSelected, monthSelected, daySelected ->
            val formattedDate = String.format(Locale.US, "%04d-%02d-%02d", yearSelected, monthSelected + 1, daySelected)
            viewModel.setDate(formattedDate)
        },
        year, month, day
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .padding(16.dp)
            .testTag("history_screen")
    ) {
        // Date Selector Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { shiftDate(selectedDate, -1, viewModel) },
                modifier = Modifier
                    .background(DeepSlate, CircleShape)
                    .size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Day", tint = GymLime)
            }

            // Click Date Text to Open Picker
            Row(
                modifier = Modifier
                    .background(DeepSlate, RoundedCornerShape(12.dp))
                    .clickable { datePickerDialog.show() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GymLime, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getReadableHistoryDate(selectedDate),
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            IconButton(
                onClick = { shiftDate(selectedDate, 1, viewModel) },
                modifier = Modifier
                    .background(DeepSlate, CircleShape)
                    .size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day", tint = GymLime)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Day Totals Callout
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL CALORIES", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$caloriesTotal kcal", color = GymLime, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Divider(
                    color = DeepSlate,
                    modifier = Modifier
                        .height(35.dp)
                        .width(1.dp)
                        .align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL PROTEIN", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "%.1fg", proteinTotal), color = VoltGreen, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Divider(
                    color = DeepSlate,
                    modifier = Modifier
                        .height(35.dp)
                        .width(1.dp)
                        .align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("EXERCISES", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${workouts.size}", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab detail streams
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Workouts
            item {
                Text(
                    text = "WORKOUT LOG",
                    fontWeight = FontWeight.ExtraBold,
                    color = GymLime,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            if (workouts.isEmpty()) {
                item {
                    Text(
                        "No exercise records for this day.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                items(workouts) { workout ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(workout.exercise, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${workout.sets} x ${workout.reps}", color = GymLime, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Weight: ${workout.weight} kg", color = TextMuted, fontSize = 12.sp)
                                if (workout.notes.isNotEmpty()) {
                                    Text(
                                        workout.notes,
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Food Eaten
            item {
                Text(
                    text = "FOOD LOG",
                    fontWeight = FontWeight.ExtraBold,
                    color = VoltGreen,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }

            if (foods.isEmpty()) {
                item {
                    Text(
                        "No food nutrition records for this day.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            } else {
                items(foods) { food ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ObsidianGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(food.foodName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Meal: ${food.mealType}", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("${food.calories} kcal", color = GymLime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${food.protein}g protein", color = VoltGreen, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shiftDate(currentDateStr: String, daysToShift: Int, viewModel: GymLogViewModel) {
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(currentDateStr) ?: return
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, daysToShift)
        val shifted = parser.format(cal.time)
        viewModel.setDate(shifted)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getReadableHistoryDate(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr) ?: return dateStr
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        formatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

// ==========================================
// 5. SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: GymLogViewModel) {
    val goals by viewModel.goals.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var nameInput by remember { mutableStateOf(userName) }
    var caloriesGoal by remember { mutableStateOf(goals.dailyCalories.toString()) }
    var proteinGoal by remember { mutableStateOf(goals.dailyProtein.toString()) }
    var weightGoal by remember { mutableStateOf(goals.weightGoal.toString()) }

    var sheetIdInput by remember { mutableStateOf(viewModel.repository.getSpreadsheetId()) }
    var serviceJsonInput by remember { mutableStateOf(viewModel.repository.getServiceAccountJson()) }

    var showGuide by remember { mutableStateOf(false) }

    // Re-verify updates inside textfields if states loaded subsequently
    LaunchedEffect(goals, userName) {
        if (nameInput.isEmpty()) nameInput = userName
        if (caloriesGoal == "2000" && goals.dailyCalories != 2000) caloriesGoal = goals.dailyCalories.toString()
        if (proteinGoal == "150.0" && goals.dailyProtein != 150.0) proteinGoal = goals.dailyProtein.toString()
        if (weightGoal == "75.0" && goals.weightGoal != 75.0) weightGoal = goals.weightGoal.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = "PROFILE & PREFERENCES",
            color = GymLime,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Settings",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Personal Goals
        Text("Your Metrics & Fitness Goals", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caloriesGoal,
                        onValueChange = { caloriesGoal = it },
                        label = { Text("Daily Calorie Intake Goal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_calories_input")
                    )

                    OutlinedTextField(
                        value = proteinGoal,
                        onValueChange = { proteinGoal = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_protein_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = weightGoal,
                    onValueChange = { weightGoal = it },
                    label = { Text("Weight Goal (kg / lbs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_weight_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.updateProfile(
                            name = nameInput,
                            dailyCalories = caloriesGoal.toIntOrNull() ?: 2000,
                            dailyProtein = proteinGoal.toDoubleOrNull() ?: 150.0,
                            weightGoal = weightGoal.toDoubleOrNull() ?: 75.0
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymLime),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("save_profile_button")
                ) {
                    Text("Save Goals", color = CarbonBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Theme Settings
        Text("Theme Preferences", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        val themeMode by viewModel.themeMode.collectAsState()
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Display Theme Mode",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("Dark", "Light", "System")
                    modes.forEach { mode ->
                        val isSelected = themeMode == mode
                        Button(
                            onClick = { viewModel.updateThemeMode(mode) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) GymLime else DeepSlate,
                                contentColor = if (isSelected) RawCarbonBlack else TextWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_btn_$mode")
                        ) {
                            Text(
                                text = mode,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Google Sheets Credentials Client Setup
        Text("Google Sheets Cloud Integration", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = sheetIdInput,
                    onValueChange = { sheetIdInput = it },
                    label = { Text("Spreadsheet ID") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_sheet_id_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = serviceJsonInput,
                    onValueChange = { serviceJsonInput = it },
                    label = { Text("Service Account JSON Key") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GymLime, unfocusedBorderColor = BorderGraphite,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedLabelColor = GymLime, unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("settings_service_json_input"),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Help Toggle
                    Row(
                        modifier = Modifier
                            .clickable { showGuide = !showGuide }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GymLime
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show Setup Guide", color = GymLime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.updateSheetConfig(
                                spreadsheetId = sheetIdInput,
                                serviceAccountJson = serviceJsonInput
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_config_button")
                    ) {
                        Text("Save & Sync", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }

                // Expandable Guide Text
                AnimatedVisibility(visible = showGuide) {
                    Column(
                        modifier = Modifier
                            .background(DeepSlate, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "How to Set Up Credentials:",
                            color = GymLime,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Go to Google Cloud Console (console.cloud.google.com)\n" +
                                    "2. Create a Project and enable Google Sheets API.\n" +
                                    "3. Go to IAM & Admin > Service Accounts > Create Service Account.\n" +
                                    "4. Generate a JSON Key for that account and download it.\n" +
                                    "5. Open your Google Sheet, share it (give Editor access) to your Service Account Email (e.g. account@project.iam.gserviceaccount.com).\n" +
                                    "6. Paste your Sheet ID and entire downloaded JSON file contents into these inputs, then click 'Save & Sync'.",
                            color = TextWhite,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Sheet Tabs & Columns Structure Guide:",
                            color = GymLime,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tab 1 Name: \"Workouts\"\nColumns: A: Date, B: Exercise, C: Weight, D: Reps, E: Sets, F: Notes\n\n" +
                                    "Tab 2 Name: \"Food\"\nColumns: A: Date, B: Meal_Type, C: Food_Name, D: Calories, E: Protein, F: Carbs\n\n" +
                                    "Tab 3 Name: \"Goals\"\nColumns: A: Goal_Key, B: Goal_Value\nRow 2: Daily_Calories | 2000\nRow 3: Daily_Protein | 150\nRow 4: Weight_Goal | 75",
                            color = TextWhite,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big manual Sync/Offline status card
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DATABASE HEALTH & SYNC",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Local Cache Mode", color = GymLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("All items save instantly locally even offline.", color = TextMuted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.triggerSyncAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSlate),
                        border = BorderStroke(1.dp, GymLime),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        if (syncStatus is SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GymLime, strokeWidth = 2.dp)
                        } else {
                            Text("Sync Now", color = GymLime)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}
