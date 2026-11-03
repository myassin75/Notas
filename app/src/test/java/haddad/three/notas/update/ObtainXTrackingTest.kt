package haddad.three.notas.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtainXTrackingTest {
    private fun payload(
        flavor: String,
        appId: String,
    ): JsonObject {
        val json = checkNotNull(buildObtainXTrackingJson(flavor, appId))
        return Json.parseToJsonElement(json).jsonObject
    }

    private fun apkFilterRegEx(payload: JsonObject): Regex {
        // ObtainX jsonDecode()s additionalSettings, so it must be a JSON string, not a nested object.
        val additionalSettings = payload.getValue("additionalSettings") as JsonPrimitive
        assertTrue(additionalSettings.isString)
        val settings = Json.parseToJsonElement(additionalSettings.content).jsonObject
        return Regex(settings.getValue("apkFilterRegEx").jsonPrimitive.content)
    }

    @Test
    fun githubTracksGithubReleasesWithGithubApkFilter() {
        val payload = payload("github", "haddad.three.notas.gh")
        assertEquals("haddad.three.notas.gh", payload.getValue("id").jsonPrimitive.content)
        assertEquals("https://github.com/bikram-agarwal/Remember", payload.getValue("url").jsonPrimitive.content)
        assertEquals("""-github\.apk$""", apkFilterRegEx(payload).pattern)
    }

    @Test
    fun offlineTracksGithubReleasesWithOfflineApkFilter() {
        val payload = payload("offline", "haddad.three.notas.offline")
        assertEquals("haddad.three.notas.offline", payload.getValue("id").jsonPrimitive.content)
        assertEquals("https://github.com/bikram-agarwal/Remember", payload.getValue("url").jsonPrimitive.content)
        assertEquals("""-offline\.apk$""", apkFilterRegEx(payload).pattern)
    }

    @Test
    fun noPayloadOverridesTheSourceOrPinsAnApkIndex() {
        for ((flavor, appId) in listOf("github" to "a.gh", "offline" to "a.offline")) {
            val payload = payload(flavor, appId)
            assertFalse(payload.containsKey("overrideSource"))
            assertFalse(payload.containsKey("preferredApkIndex"))
        }
    }

    @Test
    fun playStoreBuildsAreNotOfferedTracking() {
        assertNull(buildObtainXTrackingJson("playstore", "haddad.three.notas"))
    }

    @Test
    fun releaseApplicationIdDropsOnlyTheDevReleaseSuffix() {
        assertEquals("haddad.three.notas.gh", releaseApplicationId("haddad.three.notas.gh.dev"))
        assertEquals("haddad.three.notas.offline", releaseApplicationId("haddad.three.notas.offline"))
        assertEquals("haddad.three.notas.gh", releaseApplicationId("haddad.three.notas.gh"))
    }

    @Test
    fun apkFiltersSelectOnlyTheMatchingFlavorAsset() {
        // ObtainX keeps an asset when the filter matches its name *or* its download URL.
        val downloadBase = "https://github.com/bikram-agarwal/Remember/releases/download/v1.9.0/"
        val assets = listOf("remember-v1.9.0-fdroid.apk", "remember-v1.9.0-github.apk", "remember-v1.9.0-offline.apk")
        val cases =
            mapOf(
                "github" to "remember-v1.9.0-github.apk",
                "offline" to "remember-v1.9.0-offline.apk",
            )
        for ((flavor, expected) in cases) {
            val filter = apkFilterRegEx(payload(flavor, "haddad.three.notas.$flavor"))
            val kept = assets.filter { name -> filter.containsMatchIn(name) || filter.containsMatchIn(downloadBase + name) }
            assertEquals(listOf(expected), kept)
        }
    }
}
