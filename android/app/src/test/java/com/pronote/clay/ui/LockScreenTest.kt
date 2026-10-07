package com.pronote.clay.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.ui.screens.LockScreen
import com.pronote.clay.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The lock screen replaces the app while the optional biometric lock is on:
 * it must render its own content (nothing behind it) and hand control back
 * when the user authenticates.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LockScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the lock screen explains the situation and offers an unlock action`() {
        rule.setContent {
            AppTheme(darkTheme = false, dynamicColor = false) {
                LockScreen(onUnlock = {})
            }
        }

        rule.onNodeWithText("Ardoise est verrouillé").assertIsDisplayed()
        rule.onNodeWithText(
            "Empreinte, visage ou code de l'appareil requis pour reprendre.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Déverrouiller").assertIsDisplayed()
    }

    @Test
    fun `unlocking hands control back to the caller`() {
        var unlocked = false
        rule.setContent {
            AppTheme(darkTheme = true, dynamicColor = false) {
                LockScreen(onUnlock = { unlocked = true })
            }
        }

        rule.onNodeWithText("Déverrouiller").performClick()
        assertTrue(unlocked)
    }
}
