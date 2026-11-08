package com.pronote.clay.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pronote.clay.data.remote.UpdateChecker
import com.pronote.clay.data.remote.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * La décision de mise à jour : le manifeste du site est comparé à la version
 * installée.
 *
 * Robolectric fournit le vrai `org.json` (celui de la plateforme, pas le stub
 * du `android.jar` de compilation), comme pour TimetableParserTest.
 *
 * Règle tenace : quand on ne sait pas (réseau, manifeste absent ou cassé), on
 * reste silencieux — jamais de proposition de mise à jour fausse.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateCheckerTest {

    private val site = "https://site.example"

    private fun checker(
        currentVersionCode: Int = 11,
        body: () -> String,
    ) = UpdateChecker(currentVersionCode = currentVersionCode, siteUrl = site, fetch = { body() })

    private fun manifest(versionName: String?, versionCode: Int?, apkUrl: String = "/Ardoise.apk") =
        buildString {
            append("""{"app":"Ardoise",""")
            append(""""versionName":${versionName?.let { "\"$it\"" } ?: "null"},""")
            append(""""versionCode":${versionCode ?: "null"},""")
            append(""""size":11952691,""")
            append(""""sha256":"ab12",""")
            append(""""apkUrl":"$apkUrl"}}""")
        }

    @Test
    fun `une version plus récente est proposée avec son lien de téléchargement`() {
        val status = checker(body = { manifest("1.5.0", 12) }).check()

        assertTrue(status is UpdateStatus.Available)
        val info = (status as UpdateStatus.Available).info
        assertTrue(status.promptVisible)
        assertEquals("1.5.0", info.versionName)
        assertEquals(12, info.versionCode)
        assertEquals(11_952_691L, info.size)
        assertEquals("ab12", info.sha256)
        // L'url relative du manifeste est résolue contre le site.
        assertEquals("$site/Ardoise.apk", info.apkUrl)
    }

    @Test
    fun `la version installée est à jour`() {
        val status = checker(currentVersionCode = 12, body = { manifest("1.5.0", 12) }).check()

        assertEquals(UpdateStatus.UpToDate, status)
    }

    @Test
    fun `une version publiée plus ancienne ne propose jamais un retour en arrière`() {
        val status = checker(currentVersionCode = 12, body = { manifest("1.4.0", 11) }).check()

        assertEquals(UpdateStatus.UpToDate, status)
    }

    @Test
    fun `un manifeste sans versionCode reste silencieux`() {
        val status = checker(body = { manifest("1.5.0", null) }).check()

        assertEquals(UpdateStatus.Unavailable, status)
    }

    @Test
    fun `un corps qui n'est pas du JSON reste silencieux`() {
        val status = checker(body = { "<html>Service indisponible</html>" }).check()

        assertEquals(UpdateStatus.Unavailable, status)
    }

    @Test
    fun `une panne réseau reste silencieuse`() {
        val status = checker(body = { throw IOException("pas de réseau") }).check()

        assertEquals(UpdateStatus.Unavailable, status)
    }

    @Test
    fun `une url absolue du manifeste est respectée`() {
        val body = manifest("1.5.0", 12, apkUrl = "$site/telechargements/Ardoise.apk")
        val info = UpdateChecker.parse(body, site)

        assertEquals("$site/telechargements/Ardoise.apk", info?.apkUrl)
    }

    @Test
    fun `un manifeste illisible est ignoré plutôt qu'inventé`() {
        assertNull(UpdateChecker.parse("", site))
        assertNull(UpdateChecker.parse("{pas du json", site))
    }
}
