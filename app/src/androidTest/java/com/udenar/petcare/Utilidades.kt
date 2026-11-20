package com.udenar.petcare

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText

fun ComposeTestRule.onAllNodesWithTagExists(tag: String) =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.onAllNodesWithTextExists(texto: String) =
    onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()

/** El nodo [indice] entre los que tienen ese texto (p. ej. el botón del diálogo). */
fun ComposeTestRule.onAllNodesWithTextFirst(texto: String, indice: Int): SemanticsNodeInteraction =
    onAllNodesWithText(texto)[indice]
