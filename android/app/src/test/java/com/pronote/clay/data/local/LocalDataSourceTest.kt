package com.pronote.clay.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.domain.AverageSource
import com.pronote.clay.domain.Grade
import com.pronote.clay.domain.SchoolSnapshot
import com.pronote.clay.domain.Student
import com.pronote.clay.domain.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The cache heals old snapshots that carry grades but no general average
 * (hidden Pronote average, partial sync) so the app and the widgets agree.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocalDataSourceTest {

    private val store get() = LocalDataSource(RuntimeEnvironment.getApplication())

    private fun snapshot(generalValue: Double?) = SchoolSnapshot(
        student = Student("s1", "eleve", "M", "1 G2", "Lycée"),
        subjects = listOf(
            Subject("m1", "Maths", "MAT", "Mme X", 2.0, 0),
            Subject("m2", "Français", "FRA", "M. Y", 2.0, 1),
        ),
        grades = listOf(
            Grade("g1", "m1", "DS", 14.0, 20.0, 1.0, "2026-10-01"),
            Grade("g2", "m2", "Dictée", 10.0, 20.0, 1.0, "2026-10-02"),
        ),
        subjectAverages = emptyList(),
        generalAverage = generalValue?.let {
            com.pronote.clay.domain.GeneralAverage(it, "T1", AverageSource.OFFICIAL, 0L)
        },
        history = emptyList(),
        fetchedAt = 0L,
    )

    @Test
    fun `a cached snapshot without a general average is healed from its grades`() {
        store.saveSnapshot("a1", snapshot(generalValue = null))

        val healed = store.loadSnapshot("a1")!!

        val average = healed.generalAverage
        assertNotNull(average)
        assertEquals(12.0, average!!.value, 0.01)
        assertEquals(AverageSource.CALCULATED, average.source)
    }

    @Test
    fun `an official average is never overwritten`() {
        store.saveSnapshot("a1", snapshot(generalValue = 15.5))

        val loaded = store.loadSnapshot("a1")!!

        assertEquals(15.5, loaded.generalAverage!!.value, 0.0)
        assertEquals(AverageSource.OFFICIAL, loaded.generalAverage!!.source)
    }

    @Test
    fun `an empty snapshot stays empty instead of inventing a zero average`() {
        val bare = SchoolSnapshot(
            student = Student("s1", "eleve", "M", "1 G2", "Lycée"),
            subjects = emptyList(),
            grades = emptyList(),
            subjectAverages = emptyList(),
            generalAverage = null,
            history = emptyList(),
            fetchedAt = 0L,
        )
        assertNull(bare.generalAverage)
        store.saveSnapshot("a1", bare)
        assertNull(store.loadSnapshot("a1")!!.generalAverage)
    }
}
