package com.forma.app

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput

fun AndroidComposeTestRule<*, *>.onAllNodesWithTagExists(tag: String) =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

fun AndroidComposeTestRule<*, *>.onNodeWithTagScrolled(tag: String) = onNodeWithTag(tag).performScrollTo()

fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextClearanceAndInput(text: String) {
    performTextClearance()
    performTextInput(text)
}

fun hasTestTagPrefix(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("TestTag starts with $prefix") { node ->
    val tag = node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }
    tag != null && tag.startsWith(prefix) && tag != "recipe_detail"
}
