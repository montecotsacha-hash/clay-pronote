package com.pronote.clay.data.local

import com.pronote.clay.domain.Preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The biometric lock is opt-in: its flag must survive a save/load cycle and
 * must default to OFF so nobody is ever locked out by surprise.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PreferencesStoreTest {

    private val store get() = PreferencesStore(RuntimeEnvironment.getApplication())

    @Test
    fun `the biometric lock is off by default`() {
        assertFalse(store.load().biometricLock)
    }

    @Test
    fun `the biometric lock preference round-trips`() {
        store.save(Preferences(biometricLock = true))
        assertTrue(store.load().biometricLock)

        store.save(Preferences(biometricLock = false))
        assertFalse(store.load().biometricLock)
    }

    @Test
    fun `saving the lock keeps the other preferences untouched`() {
        store.save(Preferences(autoSync = false, notifyNewGrades = false, biometricLock = true))

        val loaded = store.load()
        assertTrue(loaded.biometricLock)
        assertFalse(loaded.autoSync)
        assertFalse(loaded.notifyNewGrades)
        assertTrue(loaded.notifyAverageChange)
    }
}
