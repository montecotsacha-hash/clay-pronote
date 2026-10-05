package com.pronote.clay

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Smoke tests that launch the actual Compose Activity under Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the app opens on the login form instead of a blank screen`() {
        composeRule.onNodeWithText("Ardoise").assertExists()
        composeRule.onNodeWithText("Connexion").assertExists()
        composeRule.onNodeWithText("Adresse Pronote de l'établissement").assertExists()
        composeRule.onNodeWithText("Identifiant").assertExists()
        composeRule.onNodeWithText("Mot de passe").assertExists()
        composeRule.onNodeWithText("Se connecter").assertExists()
    }

    @Test
    fun `the app is not stuck on the loading screen`() {
        assert(
            composeRule.onAllNodesWithText("Connexion à Pronote…")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `login button stays disabled until the required fields are filled`() {
        composeRule.onNodeWithText("Se connecter").assertIsNotEnabled()
    }

    @Test
    fun `the connection is described as direct and the ENT limitation is clear`() {
        composeRule.onNodeWithText("Connexion directe à Pronote").assertExists()
        composeRule.onNodeWithText(
            "Saisis l'adresse de ton espace élève, puis tes identifiants Pronote. " +
                "Ils sont transmis directement au serveur de ton établissement, pas à un serveur Ardoise. " +
                "Seul le jeton de session est conservé chiffré sur cet appareil. " +
                "Les connexions via un ENT ne sont pas prises en charge.",
        ).assertExists()
    }
}
