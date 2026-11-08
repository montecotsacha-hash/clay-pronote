package com.pronote.clay.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Le « téléphone virtuel » : un vrai runtime Android sur la JVM.
 *
 * Robolectric monte l'Activity réelle, applique le thème, exécute Compose et
 * rend de vrais pixels — sans émulateur, sans KVM, sans appareil branché. Ce
 * banc :
 *
 *  1. se connecte au connecteur de démonstration (voir
 *     [AppContainer.debugConnector], ignoré hors build de debug) ;     *  2. traverse l'écran de connexion, le tableau de bord, le menu, l'emploi
     *     du temps puis les paramètres comme le ferait un doigt sur l'écran ;
 *  3. écrit un PNG par écran dans `app/build/screenshots/`, à la taille d'un
 *     Pixel (411 x 914 dp, 420 dpi).
 *
 * C'est la vérification visuelle qui manquait : une capture montre en une image
 * un chevauchement ou une barre de titre passée sous la barre d'état, ce
 * qu'aucune assertion de texte ne peut voir.
 *
 * Les captures ne remplacent pas un test sur un appareil réel : le protocole
 * Pronote, les performances et les gestes système ne peuvent être validés
 * qu'installé. Mais la mise en page, elle, se vérifie ici.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-420dpi")
// Skia natif : sans lui, les Bitmaps sont des coquilles vides et il n'y a rien à voir.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceScreenCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val outputDir: File
        get() = File(System.getProperty("user.dir"), "build/screenshots")

    /**
     * L'horloge d'animation est pilotée à la main : le tableau de bord contient
     * une rotation infinie (le bouton Actualiser), et `waitForIdle` en mode
     * auto-avance attendrait une frame qui n'arrive jamais.
     */
    @Before
    fun takeManualControlOfTheClock() {
        composeRule.mainClock.autoAdvance = false
        advance(800)
    }

    /**
     * Laisse le temps virtuel s'écouler : frames Compose d'un côté, coroutines
     * en attente (`delay`) sur le Looper principal de l'autre — les deux
     * horloges sont distinctes, il faut avancer chacune.
     */
    private fun advance(millis: Long = 1_800) {
        val step = millis / 12
        repeat(12) {
            composeRule.mainClock.advanceTimeBy(step)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
        }
        composeRule.waitForIdle()
    }

    /**
     * Rend l'écran courant et l'écrit sur disque.
     *
     * On dessine la vue dans un Bitmap au lieu d'appeler `captureToImage()` :
     * celui-ci passe par PixelCopy, qui n'existe pas hors d'un vrai écran. Le
     * rendu direct utilise le Skia natif de Robolectric.
     */
    private fun capture(name: String) {
        outputDir.mkdirs()
        val file = File(outputDir, name)
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val width = decor.width.takeIf { it > 0 } ?: DECOR_WIDTH_PX
            val height = decor.height.takeIf { it > 0 } ?: DECOR_HEIGHT_PX
            if (decor.width == 0) {
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                decor.layout(0, 0, width, height)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        check(file.length() > 0) { "Capture vide : $name" }
    }

    @Test
    fun `capture connexion tableau de bord menu et emploi du temps`() {
        advance()
        capture("01-connexion.png")

        composeRule.onNodeWithText("Adresse Pronote de l'établissement")
            .performTextInput("https://demo.index-education.net/pronote/")
        composeRule.onNodeWithText("Identifiant").performTextInput("eleve")
        composeRule.onNodeWithText("Mot de passe").performTextInput("demo123")
        composeRule.onNodeWithText("Se connecter").performClick()

        // Le connecteur de démonstration répond « Alex Martin » : arriver ici
        // prouve que la connexion a parcouru ViewModel -> use case -> repository
        // puis que le tableau de bord s'est composé avec les données.
        advance()
        composeRule.onNodeWithText("Bonjour Alex").assertExists()
        // Le bug vu sur le téléphone : l'emploi du temps dessiné SOUS le
        // tableau de bord. Une seule section doit être composée à la fois.
        composeRule.onAllNodesWithText("Lun.").assertCountEquals(0)
        // Le menu est le seul chemin : l'engrenage des paramètres a été retiré
        // du tableau de bord.
        composeRule.onAllNodesWithContentDescription("Paramètres").assertCountEquals(0)
        capture("02-tableau-de-bord.png")

        composeRule.onNodeWithContentDescription("Menu").performClick()
        advance()
        // Tout vit dans le menu : sections, thème, déconnexion, suppression.
        composeRule.onNodeWithText("Paramètres").assertExists()
        composeRule.onNodeWithText("Mode sombre").assertExists()
        composeRule.onNodeWithText("Se déconnecter").assertExists()
        composeRule.onNodeWithText("Supprimer mes données").assertExists()
        capture("03-menu.png")

        composeRule.onNodeWithText("Emploi du temps").performClick()
        advance()
        // Et réciproquement : le tableau de bord a bien été démonté.
        composeRule.onAllNodesWithText("Évolution").assertCountEquals(0)
        composeRule.onAllNodesWithText("Matières").assertCountEquals(0)
        capture("04-emploi-du-temps.png")

        // Les paramètres ne s'atteignent que par le menu.
        composeRule.onNodeWithContentDescription("Retour").performClick()
        advance()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        advance()
        composeRule.onNodeWithText("Paramètres").performClick()
        advance()
        composeRule.onNodeWithText("COMPTE").assertExists()
        capture("05-parametres.png")
    }

    companion object {
        /** Pixel 5 : 411 x 914 dp à 420 dpi. */
        private const val DECOR_WIDTH_PX = 1080
        private const val DECOR_HEIGHT_PX = 2400

        /**
         * Le connecteur factice doit être en place AVANT que la Rule ne lance
         * MainActivity : le conteneur lit `dataSource` à sa construction.
         */
        @JvmStatic
        @BeforeClass
        fun useDemoConnector() {
            AppContainer.debugConnector = FakePronoteDataSource()
        }
    }
}
