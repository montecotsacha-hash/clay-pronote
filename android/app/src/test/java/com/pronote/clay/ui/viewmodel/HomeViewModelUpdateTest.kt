package com.pronote.clay.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.AppContainer
import com.pronote.clay.data.remote.UpdateChecker
import com.pronote.clay.data.remote.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Le fil de mise à jour du ViewModel : vérification silencieuse au lancement,
 * proposition d'une version plus récente, « Plus tard », et vérification
 * manuelle depuis les réglages.
 *
 * Robolectric fournit un vrai AppContainer (contexte Android réel) ; le
 * vérificateur est factice, donc aucun test ne parle au réseau.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelUpdateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppContainer.debugConnector = null
    }

    private fun container() = AppContainer(RuntimeEnvironment.getApplication())

    private fun checker(body: String, currentVersionCode: Int = 11) =
        UpdateChecker(currentVersionCode = currentVersionCode, siteUrl = "https://site.example") { body }

    private fun manifest(versionCode: Int) =
        """{"app":"Ardoise","versionName":"1.5.0","versionCode":$versionCode,"apkUrl":"/Ardoise.apk"}"""

    @Test
    fun `au lancement, une version plus récente est proposée`() = runTest(dispatcher) {
        val vm = HomeViewModel(
            container(),
            checker(manifest(12)),
            ioDispatcher = dispatcher,
        )
        dispatcher.scheduler.advanceUntilIdle()

        val status = vm.updateStatus.value
        assertTrue(status is UpdateStatus.Available)
        assertTrue((status as UpdateStatus.Available).promptVisible)
        assertEquals(12, status.info.versionCode)
    }

    @Test
    fun `à jour, rien n'est proposé`() = runTest(dispatcher) {
        val vm = HomeViewModel(
            container(),
            checker(manifest(11)),
            ioDispatcher = dispatcher,
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UpdateStatus.UpToDate, vm.updateStatus.value)
    }

    @Test
    fun `plus tard retire la proposition mais garde l'information pour les réglages`() =
        runTest(dispatcher) {
            val vm = HomeViewModel(
                container(),
                checker(manifest(12)),
                ioDispatcher = dispatcher,
            )
            dispatcher.scheduler.advanceUntilIdle()

            vm.dismissUpdate()

            val status = vm.updateStatus.value
            assertTrue(status is UpdateStatus.Available)
            assertFalse((status as UpdateStatus.Available).promptVisible)
        }

    @Test
    fun `la vérification manuelle suit le même chemin que celle du lancement`() =
        runTest(dispatcher) {
            val vm = HomeViewModel(
                container(),
                checker(manifest(12)),
                ioDispatcher = dispatcher,
            )
            dispatcher.scheduler.advanceUntilIdle()

            vm.dismissUpdate()
            vm.checkForUpdate()
            dispatcher.scheduler.advanceUntilIdle()

            // Une nouvelle vérification repose la question.
            val status = vm.updateStatus.value
            assertTrue(status is UpdateStatus.Available)
            assertTrue((status as UpdateStatus.Available).promptVisible)
        }

    @Test
    fun `sans vérificateur, la vérification manuelle dit qu'elle est impossible`() =
        runTest(dispatcher) {
            val vm = HomeViewModel(container(), updateChecker = null, ioDispatcher = dispatcher)
            dispatcher.scheduler.advanceUntilIdle()

            vm.checkForUpdate()

            assertEquals(UpdateStatus.Unavailable, vm.updateStatus.value)
        }

    @Test
    fun `une panne réseau ne bloque pas l'application`() = runTest(dispatcher) {
        val broken = UpdateChecker(currentVersionCode = 11, siteUrl = "https://site.example") {
            throw java.io.IOException("réseau")
        }
        val vm = HomeViewModel(container(), broken, ioDispatcher = dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UpdateStatus.Unavailable, vm.updateStatus.value)
    }
}
