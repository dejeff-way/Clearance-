package com.forma.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forma.app.data.seed.Category
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Today's planned meals with one-tap logging. Shown only when something is planned for today. */
@Composable
fun TodayPlanCard(today: LocalDate, loggedFoodIds: Set<Long>, onOpenPlan: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val flow = remember(today) { container.mealPlan.day(today) }
    val planned by flow.collectAsStateWithLifecycle(emptyList())
    val recipes by container.recipes.recipes.collectAsStateWithLifecycle(emptyList())
    val names = recipes.associate { it.id to it.name }
    val c = Forma.colors
    if (planned.isEmpty()) return
    SectionHeader("Today's plan", action = "Meal plan", onAction = onOpenPlan)
    FormaCard(Modifier.testTag("today_plan")) {
        planned.sortedBy { Category.from(it.mealType).ordinal }.forEachIndexed { i, e ->
            if (i > 0) Hairline()
            val logged = e.loggedFoodId != null && e.loggedFoodId in loggedFoodIds
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Category.from(e.mealType).label, style = Forma.type.caption, color = c.textTertiary)
                    Text(names[e.recipeId] ?: "Removed recipe", style = Forma.type.body, color = if (logged) c.textSecondary else c.text)
                }
                if (logged) Tag("Logged", Tone.ACCENT)
                else TextAction("Log", { scope.launch { runCatching { container.mealPlan.log(e) } } }, modifier = Modifier.testTag("plan_log_${e.id}"))
            }
        }
        Text("Suggestions from your plan — tap Log when you eat it.", style = Forma.type.caption, color = c.textTertiary)
    }
}
