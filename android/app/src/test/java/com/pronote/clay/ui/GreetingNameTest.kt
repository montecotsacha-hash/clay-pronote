package com.pronote.clay.ui

import com.pronote.clay.ui.screens.greetingName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The "Prénom à afficher" setting must win over whatever Pronote sends back:
 * it is the only name the user can actually change.
 */
class GreetingNameTest {

    @Test
    fun `the configured first name wins over the Pronote one`() {
        assertEquals(
            "Léa",
            greetingName(configured = "Léa", pronoteFirstName = "Camille", pronoteLastName = "Martin"),
        )
    }

    @Test
    fun `falls back to the Pronote first name when nothing is configured`() {
        assertEquals(
            "Camille",
            greetingName(configured = null, pronoteFirstName = "Camille", pronoteLastName = "Martin"),
        )
        assertEquals(
            "Camille",
            greetingName(configured = "   ", pronoteFirstName = "Camille", pronoteLastName = "Martin"),
        )
    }

    @Test
    fun `ignores the Pronote placeholder and a surname-only name`() {
        // Mappers writes "Élève" when Pronote sends no first name.
        assertNull(greetingName(configured = null, pronoteFirstName = "Élève", pronoteLastName = "Martin"))
        // A "first name" identical to the surname means Pronote sent no first name.
        assertNull(greetingName(configured = null, pronoteFirstName = "DUPONT", pronoteLastName = "DUPONT"))
        // Blank everywhere: no name at all.
        assertNull(greetingName(configured = null, pronoteFirstName = null, pronoteLastName = null))
    }

    @Test
    fun `uses the Pronote first name when the setting is left blank`() {
        assertEquals(
            "Camille",
            greetingName(configured = "", pronoteFirstName = "Camille", pronoteLastName = "Martin"),
        )
    }
}
