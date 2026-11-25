package com.forma.app.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forma.app.ui.carryover.CarryoverScreen
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.food.AddFoodScreen
import com.forma.app.ui.food.FoodLogScreen
import com.forma.app.ui.health.HealthConnectScreen
import com.forma.app.ui.home.ActivityAddScreen
import com.forma.app.ui.home.EnergyBreakdownScreen
import com.forma.app.ui.home.HomeScreen
import com.forma.app.ui.progress.ProgressScreen
import com.forma.app.ui.recipes.RecipeDetailScreen
import com.forma.app.ui.recipes.RecipeEditorScreen
import com.forma.app.ui.recipes.RecipeImportScreen
import com.forma.app.ui.recipes.RecipesScreen
import com.forma.app.ui.settings.ProfileEditScreen
import com.forma.app.ui.settings.SettingsScreen
import com.forma.app.ui.settings.TargetsEditScreen
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.workout.ProgramDetailScreen
import com.forma.app.ui.workout.ScheduleScreen
import com.forma.app.ui.workout.WorkoutDetailScreen
import com.forma.app.ui.workout.WorkoutImportScreen
import com.forma.app.ui.workout.WorkoutLibraryScreen
import com.forma.app.ui.workout.WorkoutRoutes
import com.forma.app.ui.workout.WorkoutScreen

object Routes {
    const val HOME = "home"
    const val RECIPES = "recipes"
    const val WORKOUT = "workout"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val CARRYOVER = "carryover"
    const val HEALTH = "health"
    const val PROFILE = "profile_edit"
    const val TARGETS = "targets_edit"
    fun recipe(id: String) = "recipe/$id"
    fun newRecipe() = "recipe_edit"
    const val IMPORT = "recipe_import"
    fun editRecipe(id: String) = "recipe_edit?id=$id"
    fun copyRecipe(id: String) = "recipe_edit?copy=$id"
    fun log(day: Long) = "log/$day"
    fun addFood(day: Long, meal: String? = null) = "add_food/$day" + (meal?.let { "?meal=$it" } ?: "")
    fun energy(day: Long) = "energy/$day"
    fun activity(day: Long) = "activity_add/$day"
    fun metric(name: String) = "metric/$name"
    const val MEAL_PLAN = "meal_plan"
    fun shopping(weekStartEpochDay: Long) = "shopping/$weekStartEpochDay"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.WbSunny),
    Tab(Routes.RECIPES, "Recipes", Icons.AutoMirrored.Outlined.MenuBook),
    Tab(Routes.WORKOUT, "Workout", Icons.Outlined.FitnessCenter),
    Tab(Routes.PROGRESS, "Progress", Icons.AutoMirrored.Outlined.ShowChart),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun FormaRoot(nav: NavHostController = rememberNavController()) {
    val c = Forma.colors
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.weight(1f)) {
            NavHost(
                nav, startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 12 } },
            ) {
                composable(Routes.HOME, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { HomeScreen(nav) }
                composable(Routes.RECIPES, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { RecipesScreen(nav) }
                composable(Routes.WORKOUT, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { WorkoutScreen(nav) }
                composable(Routes.PROGRESS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { ProgressScreen(nav) }
                composable(Routes.SETTINGS, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { SettingsScreen(nav) }
                composable(
                    "recipe_edit?id={id}&copy={copy}",
                    listOf(navArgument("id") { nullable = true; defaultValue = null }, navArgument("copy") { nullable = true; defaultValue = null }),
                ) { RecipeEditorScreen(nav, it.arguments?.getString("id"), it.arguments?.getString("copy")) }
                composable("recipe/{id}") { RecipeDetailScreen(nav, it.arguments?.getString("id")!!) }
                composable("log/{day}", listOf(navArgument("day") { type = NavType.LongType })) {
                    FoodLogScreen(nav, it.arguments!!.getLong("day"))
                }
                composable(
                    "add_food/{day}?meal={meal}",
                    listOf(navArgument("day") { type = NavType.LongType }, navArgument("meal") { nullable = true; defaultValue = null }),
                ) { AddFoodScreen(nav, it.arguments!!.getLong("day"), it.arguments?.getString("meal")) }
                composable("energy/{day}", listOf(navArgument("day") { type = NavType.LongType })) {
                    EnergyBreakdownScreen(nav, it.arguments!!.getLong("day"))
                }
                composable("activity_add/{day}", listOf(navArgument("day") { type = NavType.LongType })) {
                    ActivityAddScreen(nav, it.arguments!!.getLong("day"))
                }
                composable(Routes.CARRYOVER) { CarryoverScreen(nav) }
                composable(Routes.IMPORT) { RecipeImportScreen(nav) }
                composable(Routes.MEAL_PLAN) { com.forma.app.ui.plan.MealPlanScreen(nav) }
                composable("shopping/{week}", listOf(navArgument("week") { type = NavType.LongType })) {
                    com.forma.app.ui.plan.ShoppingListScreen(nav, it.arguments!!.getLong("week"))
                }
                composable("metric/{name}") { com.forma.app.ui.metrics.MetricHistoryScreen(nav, it.arguments?.getString("name")!!) }
                composable(WorkoutRoutes.LIBRARY) { WorkoutLibraryScreen(nav) }
                composable(WorkoutRoutes.SCHEDULE) { ScheduleScreen(nav) }
                composable(WorkoutRoutes.IMPORT) { WorkoutImportScreen(nav) }
                composable("workout_detail/{id}") { WorkoutDetailScreen(nav, it.arguments?.getString("id")!!) }
                composable("program_detail/{id}") { ProgramDetailScreen(nav, it.arguments?.getString("id")!!) }
                composable(Routes.HEALTH) { HealthConnectScreen(nav) }
                composable(Routes.PROFILE) { ProfileEditScreen(nav) }
                composable(Routes.TARGETS) { TargetsEditScreen(nav) }
            }
        }
        if (showBar) BottomBar(route) { r ->
            nav.navigate(r) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    val c = Forma.colors
    Column(Modifier.fillMaxWidth().background(c.bg).windowInsetsPadding(WindowInsets.navigationBars)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { t ->
                val sel = current == t.route
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab) { onSelect(t.route) }.testTag("tab_${t.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, tint = if (sel) c.text else c.textTertiary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, style = Forma.type.caption, color = if (sel) c.text else c.textTertiary)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (sel) c.accent else Color.Transparent))
                }
            }
        }
    }
}
