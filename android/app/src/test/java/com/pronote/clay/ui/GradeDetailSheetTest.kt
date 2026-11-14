package com.pronote.clay.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.AppContainer
import com.pronote.clay.MainActivity
import com.pronote.clay.data.remote.FakePronoteDataSource
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
 * La liste des notes ne garde que l'intitulé et la note ; tout le détail vit
 * dans la fiche qui s'ouvre au toucher.
 *
 * Régressions chassées :
 *  - la ligne de note affichait matière, date et coefficient, ce qui rendait la
 *    liste illisible (le retour utilisateur d'origine) ;
 *  - toucher une note ne faisait rien ;
 *  - la fiche doit se refermer et rendre la liste intacte.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GradeDetailSheetTest {

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

    /** Se connecte avec le connecteur de démonstration. */
    private fun signIn() {
        advance()
        composeRule.onNodeWithText("Adresse Pronote de l'établissement")
            .performTextInput("https://demo.index-education.net/pronote/")
        composeRule.onNodeWithText("Identifiant").performTextInput("eleve")
        composeRule.onNodeWithText("Mot de passe").performTextInput("demo123")
        composeRule.onNodeWithText("Se connecter").performClick()
        advance()
    }

    /** Descend le tableau de bord jusqu'à la liste des notes, comme un doigt. */
    private fun openFirstGrade() {
        repeat(10) {
            val found = composeRule.onAllNodesWithText("Contrôle fonctions")
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (found) return@repeat
            composeRule.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            advance()
        }
        // Le texte existe deux fois (la ligne fusionnée et son libellé) : on
        // touche la ligne qui porte réellement le geste.
        composeRule.onAllNodesWithText("Contrôle fonctions")
            .filterToOne(hasClickAction())
            .performClick()
        advance()
    }

    @Test
    fun `la fiche d une note montre le detail que la liste a retire`() {
        signIn()
        openFirstGrade()

        // Ce que la ligne ne montre plus, et que la fiche doit expliquer.
        composeRule.onNodeWithText("Coefficient").assertExists()
        composeRule.onNodeWithText("Barème").assertExists()
        composeRule.onNodeWithText("Date").assertExists()
        composeRule.onNodeWithText("Comptée dans la moyenne").assertExists()
        composeRule.onNodeWithText("Moyenne de la matière").assertExists()
        composeRule.onNodeWithText("Très bon travail.", substring = true).assertExists()
    }

    @Test
    fun `la fiche se referme et rend la liste intacte`() {
        signIn()
        openFirstGrade()
        composeRule.onNodeWithText("Fermer").performClick()
        advance()

        // Le panneau a disparu, la liste est toujours là.
        composeRule.onNodeWithText("Barème").assertDoesNotExist()
        composeRule.onNodeWithText("Contrôle fonctions").assertIsDisplayed()
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
