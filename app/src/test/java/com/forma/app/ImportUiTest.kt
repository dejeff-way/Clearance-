package com.forma.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class ImportUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    @Test fun `paste json, review, fix, import and find it in the library`() {
        val json = ImportFixtures.file(
            ImportFixtures.USER_EXAMPLE.substringAfter("[").substringBeforeLast("]"),
            ImportFixtures.recipe("needs-fix", name = "Beef Bowl", category = "Brunch"),
        )
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("import_recipes") }
        rule.onNodeWithTag("import_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("paste_input") }
        rule.onRoot().captureRoboImage("screenshots/12_import_choose_light.png")
        rule.onNodeWithTag("paste_input").performTextInput(json)
        rule.onNodeWithTag("check_import").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("cand_0") }
        rule.onRoot().captureRoboImage("screenshots/13_import_preview_light.png")
        // Fix the invalid category inline
        rule.onNodeWithTag("import_list").performScrollToNode(hasTestTag("fix_cat_SUPPER_1"))
        rule.onNodeWithTag("fix_cat_SUPPER_1").performClick()
        rule.onNodeWithTag("import_list").performScrollToNode(hasTestTag("fix_apply_1"))
        rule.onNodeWithTag("fix_apply_1").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("include_1") }
        rule.onNodeWithTag("confirm_import").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("import_report") }
        rule.onRoot().captureRoboImage("screenshots/14_import_report_light.png")
        val imported = runBlocking { c.db.recipes().all() }.filter { it.origin == "IMPORT" }
        assertEquals(setOf("chicken-shawarma-001", "needs-fix"), imported.map { it.id }.toSet())
        assertEquals("SUPPER", imported.first { it.id == "needs-fix" }.category)
        rule.onNodeWithTag("import_done").performScrollTo().performClick()
        rule.onNodeWithTag("filter_LUNCH").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_chicken-shawarma-001") }
        rule.onNodeWithTag("recipe_chicken-shawarma-001").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_detail") }
        rule.onRoot().captureRoboImage("screenshots/15_imported_recipe_light.png")
    }
}
