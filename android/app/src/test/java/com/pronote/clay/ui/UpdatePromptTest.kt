package com.pronote.clay.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.data.remote.UpdateInfo
import com.pronote.clay.ui.components.UpdateAvailableDialog
import com.pronote.clay.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La proposition de mise à jour, au doigt : elle annonce la version publiée et
 * répond à ses deux actions.
 *
 * Même mode graphique natif que les autres tests d'interface du projet : sans
 * lui, les clics injectés ne se comportent pas comme sur un appareil.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdatePromptTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `la proposition affiche la version et répond à ses deux actions`() {
        var downloads = 0
        var dismissals = 0
        composeRule.setContent {
            AppTheme {
                UpdateAvailableDialog(
                    info = UpdateInfo(
                        versionName = "1.5.0",
                        versionCode = 12,
                        size = 11_952_691,
                        sha256 = "ab12",
                        apkUrl = "https://site.example/Ardoise.apk",
                    ),
                    onDownload = { downloads++ },
                    onDismiss = { dismissals++ },
                )
            }
        }

        composeRule.onNodeWithText("Mise à jour disponible").assertExists()
        composeRule.onNodeWithText("Ardoise 1.5.0", substring = true).assertExists()

        composeRule.onNodeWithText("Télécharger").performClick()
        assertEquals(1, downloads)

        composeRule.onNodeWithText("Plus tard").performClick()
        assertEquals(1, dismissals)
    }
}
