package com.forma.app.ui.plan

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.plan.ShoppingItem
import com.forma.app.data.plan.StoreSection
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.CheckCircle
import com.forma.app.ui.components.EmptyState
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.ThinProgress
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModel(private val c: AppContainer, val weekStart: LocalDate) : ViewModel() {
    val pantry = c.mealPlan.pantry.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rebuilt whenever the plan, recipes or "always have" list change. */
    val items = combine(c.mealPlan.week(weekStart), c.mealPlan.pantry, c.recipes.recipes, c.recipes.allIngredients) { _, p, _, _ -> p }
        .mapLatest { p -> c.mealPlan.shoppingList(weekStart, p.map { it.itemKey }.toSet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val checks = c.mealPlan.checks(weekStart).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggle(key: String, checked: Boolean) = viewModelScope.launch { c.mealPlan.setChecked(weekStart, key, checked) }
    fun alwaysHave(i: ShoppingItem) = viewModelScope.launch { c.mealPlan.alwaysHave(i.key, i.name) }
    fun restore(key: String) = viewModelScope.launch { c.mealPlan.removeAlwaysHave(key) }
    fun uncheckAll() = viewModelScope.launch { c.mealPlan.clearChecks(weekStart) }
}

@Composable
fun ShoppingListScreen(nav: NavController, weekStartEpochDay: Long) {
    val week = LocalDate.ofEpochDay(weekStartEpochDay)
    val vm = formaViewModel(key = "shopping-$weekStartEpochDay") { ShoppingListViewModel(it, week) }
    val items by vm.items.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val pantry by vm.pantry.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Forma.colors
    var selected by remember { mutableStateOf<ShoppingItem?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    val checked = checks.filter { it.checked }.map { it.itemKey }.toSet()
    val list = items.orEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("shopping_list")) {
        BackBar("Shopping list", { nav.popBackStack() }) {
            if (list.isNotEmpty()) IconAction(Icons.Outlined.Share, "Share list", {
                val text = shareText(week, list, checked)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share shopping list"))
            })
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Text("Week of ${Fmt.shortDate(week)} – ${Fmt.shortDate(week.plusDays(6))}", style = Forma.type.caption, color = c.textTertiary)
            when {
                items == null -> {}
                list.isEmpty() -> EmptyState("Nothing to buy yet", "Plan some meals for this week and the ingredients will appear here.")
                else -> {
                    val toBuy = list.filter { it.section != StoreSection.STAPLES }
                    val done = toBuy.count { it.key in checked }
                    Spacer(Modifier.height(Space.m))
                    FormaCard {
                        Text("$done of ${toBuy.size} items", style = Forma.type.heading, color = c.text, modifier = Modifier.testTag("shopping_progress"))
                        Spacer(Modifier.height(Space.s))
                        ThinProgress(if (toBuy.isEmpty()) 0f else done / toBuy.size.toFloat())
                        if (done > 0) TextAction("Uncheck all", { vm.uncheckAll() }, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(Space.s))
                    Note("Amounts are worked out from your recipes for one person. Buy a little extra for produce trimming.")
                    list.groupBy { it.section }.toSortedMap().forEach { (section, sectionItems) ->
                        SectionHeader(section.label)
                        if (section == StoreSection.STAPLES) Text("Usually in your kitchen. Tap an item and choose \"Always have\" to hide it next time.",
                            style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(bottom = Space.xs))
                        FormaCard(padding = androidx.compose.foundation.layout.PaddingValues(start = Space.xs, end = Space.l, top = Space.xs, bottom = Space.xs)) {
                            sectionItems.forEachIndexed { i, it ->
                                if (i > 0) Hairline()
                                val isChecked = it.key in checked
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    CheckCircle(isChecked, { vm.toggle(it.key, !isChecked) }, "Got ${it.name}", Modifier.testTag("shop_check_${it.key}"))
                                    Column(Modifier.weight(1f).clickable { selected = it }) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(it.name, style = Forma.type.bodyStrong.copy(textDecoration = if (isChecked) TextDecoration.LineThrough else null),
                                                color = if (isChecked) c.textTertiary else c.text, modifier = Modifier.weight(1f))
                                        }
                                        if (it.quantity.isNotBlank()) Text(it.quantity, style = Forma.type.numberSmall, color = if (isChecked) c.textTertiary else c.accent,
                                            modifier = Modifier.testTag("shop_qty_${it.key}"))
                                        Text(it.usedIn, style = Forma.type.caption, color = c.textTertiary, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pantry.isNotEmpty()) {
                SectionHeader("Always have (${pantry.size})", action = if (showHidden) "Hide" else "Show", onAction = { showHidden = !showHidden })
                if (showHidden) FormaCard {
                    pantry.forEachIndexed { i, p ->
                        if (i > 0) Hairline()
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, style = Forma.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                            TextAction("Add back", { vm.restore(p.itemKey) })
                        }
                    }
                }
            }
        }
    }
    selected?.let { item ->
        FormaSheet({ selected = null }, item.name, item.quantity.ifBlank { "To taste" }) {
            Text("Used in: ${item.usedIn}", style = Forma.type.bodySmall, color = c.textSecondary)
            item.note?.let { Spacer(Modifier.height(Space.s)); Note(it) }
            Spacer(Modifier.height(Space.l))
            SecondaryButton("Always have — hide from lists", { vm.alwaysHave(item); selected = null }, Modifier.fillMaxWidth().testTag("always_have"))
            Spacer(Modifier.height(Space.s))
            PrimaryButton("Done", { selected = null }, Modifier.fillMaxWidth())
        }
    }
}

/** Plain-text list for WhatsApp/SMS, grouped by section, with ticked items marked. */
fun shareText(week: LocalDate, items: List<ShoppingItem>, checked: Set<String>): String = buildString {
    appendLine("FORMA shopping list · week of ${Fmt.shortDate(week)}")
    items.groupBy { it.section }.toSortedMap().forEach { (section, list) ->
        appendLine()
        appendLine(section.label.uppercase())
        list.forEach { appendLine("${if (it.key in checked) "☑" else "☐"} ${it.name}${if (it.quantity.isNotBlank()) " — ${it.quantity}" else ""}") }
    }
}
