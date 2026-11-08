package com.pronote.clay.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.AppContainer
import com.pronote.clay.MainActivity
import com.pronote.clay.data.remote.FakePronoteDataSource
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * L'emploi du temps au doigt : pastilles de jours, balayage entre les jours,
 * flèches de semaine et cours en parallèle.
 *
 * Régressions chassées :
 *  - les pastilles de jours n'appelaient jamais leur callback : toucher un jour
 *    ne changeait rien ;
 *  - le geste de balayage capturait l'index du premier jour affiché : le
 *    deuxième balayage ne faisait plus rien, et un glissement lent (pas par pas
 *    plus petit que l'ancien seuil) ne déclenchait jamais ;
 *  - deux cours parallèles (même matière, même heure) plantaient la liste.
 *
 * Même banc que la capture d'écran : Robolectric monte la vraie activité, le
 * connecteur de démonstration alimente les données et l'horloge est pilotée à
 * la main.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
// Même Skia natif que la capture d'écran : sans lui, les passes de dessin
// (et certains clics injectés) ne se comportent pas comme sur un appareil.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimetableTouchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun takeManualControlOfTheClock() {
        composeRule.mainClock.autoAdvance = false
        advance(800)
    }

    /** Laisse le temps virtuel s'écouler (frames Compose + coroutines). */
    private fun advance(millis: Long = 1_800) {
        val step = millis / 12
        repeat(12) {
            composeRule.mainClock.advanceTimeBy(step)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
        }
        composeRule.waitForIdle()
    }

    /** Se connecte avec le connecteur de démonstration et ouvre l'emploi du temps. */
    private fun openTimetable() {
        advance()
        composeRule.onNodeWithText("Adresse Pronote de l'établissement")
            .performTextInput("https://demo.index-education.net/pronote/")
        composeRule.onNodeWithText("Identifiant").performTextInput("eleve")
        composeRule.onNodeWithText("Mot de passe").performTextInput("demo123")
        composeRule.onNodeWithText("Se connecter").performClick()
        advance()

        composeRule.onNodeWithContentDescription("Menu").performClick()
        advance()
        composeRule.onNodeWithText("Emploi du temps").performClick()
        advance()
    }

    @Test
    fun `les jours se choisissent au doigt`() {
        openTimetable()

        // Les pastilles de jours sont de vrais boutons : avant, toucher un jour
        // ne changeait rien.
        composeRule.onNodeWithText("Lun.").performClick()
        advance()
        composeRule.onNodeWithText("Lun.").assertIsSelected()
        // Lundi a toujours des cours dans la démo : le titre du jour est là.
        composeRule.onNodeWithText("Lundi", substring = true).assertExists()

        composeRule.onNodeWithText("Sam.").performClick()
        advance()
        composeRule.onNodeWithText("Sam.").assertIsSelected()
        composeRule.onNodeWithText("Lun.").assertIsNotSelected()
        // Le samedi n'a jamais cours dans la démo.
        composeRule.onNodeWithText("Aucun cours").assertExists()
    }

    @Test
    fun `le balayage avance d'un seul jour à chaque geste`() {
        openTimetable()

        composeRule.onNodeWithText("Lun.").performClick()
        advance()

        // Geste rapide : exactement un jour en avant, pas deux.
        composeRule.onRoot().performTouchInput { swipeLeft() }
        advance()
        composeRule.onNodeWithText("Mar.").assertIsSelected()
        composeRule.onNodeWithText("Mer.").assertIsNotSelected()

        // Geste lent : la trajectoire entière compte, pas le pas entre deux
        // frames. Avant, un glissement lent ne déclenchait jamais.
        composeRule.onRoot().performTouchInput { swipeLeft(durationMillis = 900) }
        advance()
        composeRule.onNodeWithText("Mer.").assertIsSelected()

        // Et le geste inverse recule d'un seul jour.
        composeRule.onRoot().performTouchInput { swipeRight() }
        advance()
        composeRule.onNodeWithText("Mar.").assertIsSelected()
        composeRule.onNodeWithText("Lun.").assertIsNotSelected()
    }

    @Test
    fun `les flèches de semaine changent de semaine et repartent du lundi`() {
        openTimetable()

        composeRule.onNodeWithText("Sam.").performClick()
        advance()

        val before = composeRule.onNode(hasText("Semaine du", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text

        composeRule.onNodeWithContentDescription("Semaine suivante").performClick()
        advance()

        val after = composeRule.onNode(hasText("Semaine du", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assertNotEquals(before, after)
        // Semaine suivante : on atterrit sur le lundi, jour sélectionné.
        composeRule.onNodeWithText("Lun.").assertIsSelected()
    }

    @Test
    fun `deux cours parallèles s'affichent tous les deux sans planter`() {
        openTimetable()

        // Mardi porte deux cours de Maths à la même heure, groupes A et B.
        composeRule.onNodeWithText("Mar.").performClick()
        advance()
        composeRule.onAllNodesWithText("Mathématiques").assertCountEquals(2)
        composeRule.onNodeWithText("Groupe A", substring = true).assertExists()
        composeRule.onNodeWithText("Groupe B", substring = true).assertExists()
    }

    companion object {
        /** Le connecteur factice doit être en place AVANT que la Rule ne lance MainActivity. */
        @JvmStatic
        @BeforeClass
        fun useDemoConnector() {
            AppContainer.debugConnector = FakePronoteDataSource()
        }
    }
}
