package com.pronote.clay

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Smoke test on the real Android runtime (Robolectric): launches MainActivity
 * and inspects the composed semantics tree.
 *
 * This is the regression guard for the shipped bug where MainActivity rendered
 * a bare Text("Pronote") placeholder and the app looked stuck on an empty
 * screen. It runs on the JVM, so no emulator is needed.
 *
 * Assertions target the semantics tree rather than the viewport: Robolectric
 * renders on a small synthetic display, so an element can legitimately exist
 * while being scrolled out of view.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the app composes the login screen, not a blank placeholder`() {
        composeRule.onNodeWithText("Ardoise").assertExists()
        composeRule.onNodeWithText("Connexion").assertExists()
        composeRule.onNodeWithText("Se connecter").assertExists()
    }

    @Test
    fun `the app is not stuck on the loading screen`() {
        composeRule.onNodeWithText("Connexion à Pronote…").assertDoesNotExist()
    }

    @Test
    fun `the demo credentials are prefilled so the app is usable immediately`() {
        composeRule.onNodeWithText("demo").assertExists()
        composeRule.onNodeWithText("https://0123456a.index-education.net/pronote/").assertExists()
        // The password field is masked by PasswordVisualTransformation, so its
        // value is never exposed in the semantics tree: assert the field label.
        composeRule.onNodeWithText("Mot de passe").assertExists()
    }

    @Test
    fun `the privacy promise is part of the login screen`() {
        composeRule.onNodeWithText(
            "Les identifiants sont préremplis pour explorer l'app tout de suite. " +
                "Ils restent chiffrés sur l'appareil (Keystore Android) et ne sont " +
                "jamais envoyés à un serveur tiers.",
        ).assertExists()
    }
}