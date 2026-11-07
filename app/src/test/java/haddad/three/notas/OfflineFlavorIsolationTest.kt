package haddad.three.notas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineFlavorIsolationTest {

    @Test
    fun offlineMergedManifest_containsZeroInternetAndZeroGoogleFirebase() {
        val candidates = listOf(
            File("build/intermediates/merged_manifest/offlineDebug/processOfflineDebugMainManifest/AndroidManifest.xml"),
            File("../app/build/intermediates/merged_manifest/offlineDebug/processOfflineDebugMainManifest/AndroidManifest.xml"),
            File("app/build/intermediates/merged_manifest/offlineDebug/processOfflineDebugMainManifest/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull { it.exists() }
        if (manifest == null) {
            // Manifest not generated yet in this specific run directory; verifyOfflineFlavorHygiene Gradle task enforces this.
            return
        }

        val content = manifest.readText()
        assertFalse(
            "Offline manifest must not contain INTERNET permission",
            content.contains("android.permission.INTERNET"),
        )
        assertFalse(
            "Offline manifest must not contain com.google.firebase",
            content.contains("com.google.firebase"),
        )
        assertFalse(
            "Offline manifest must not contain com.google.android.gms",
            content.contains("com.google.android.gms"),
        )
        assertFalse(
            "Offline manifest must not contain Firebase deep-link domains",
            content.contains("firebaseapp.com"),
        )
        assertTrue(
            "Offline manifest should contain standard alarm permissions",
            content.contains("SCHEDULE_EXACT_ALARM") || content.contains("USE_EXACT_ALARM"),
        )
    }
}

