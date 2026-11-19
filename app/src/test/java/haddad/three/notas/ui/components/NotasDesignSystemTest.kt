package haddad.three.notas.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating Phase 02 Figma Design Tokens and Reusable Component properties.
 */
class NotasDesignSystemTest {
    @Test
    fun notasTokens_lightPalette_matchesFigmaAuditExactly() {
        assertEquals(Color(0xFFF5F5F5), NotasTokens.LightBackground)
        assertEquals(Color(0xFFFFFFFF), NotasTokens.LightCardSurface)
        assertEquals(Color(0xFFFAFAFA), NotasTokens.LightInputSurface)
        assertEquals(Color(0xFFE5E5E5), NotasTokens.LightBorder)
        assertEquals(Color(0xFF000000), NotasTokens.LightPrimary)
        assertEquals(Color(0xFFFFFFFF), NotasTokens.LightOnPrimary)
        assertEquals(Color(0xFF737373), NotasTokens.LightSecondaryText)
        assertEquals(Color(0xFF6B7280), NotasTokens.LightMutedText)
        assertEquals(Color(0xFFEF4444), NotasTokens.SemanticError)
    }

    @Test
    fun notasTokens_dimensions_matchFigmaSpecifications() {
        assertEquals(56.dp, NotasTokens.ButtonHeight)
        assertEquals(28.dp, NotasTokens.ButtonRadius)
        assertEquals(48.dp, NotasTokens.InputHeight)
        assertEquals(12.dp, NotasTokens.InputRadius)
        assertEquals(22.dp, NotasTokens.CardRadius)
        assertEquals(24.dp, NotasTokens.CardRadiusLarge)
        assertEquals(64.dp, NotasTokens.AvatarSize)
        assertEquals(80.dp, NotasTokens.AvatarSizeLarge)
        assertEquals(36.dp, NotasTokens.SheetHandleWidth)
        assertEquals(4.dp, NotasTokens.SheetHandleHeight)
        assertEquals(100.dp, NotasTokens.SheetHandleRadius)
    }

    @Test
    fun notasTokens_googleBrandColors_matchOfficialSpecification() {
        assertEquals(Color(0xFFEA4335), NotasTokens.Google.Red)
        assertEquals(Color(0xFFFBBC05), NotasTokens.Google.Yellow)
        assertEquals(Color(0xFF34A853), NotasTokens.Google.Green)
        assertEquals(Color(0xFF4285F4), NotasTokens.Google.Blue)
    }

    @Test
    fun notasTypography_scale_matchesFigmaTypeScale() {
        assertEquals(36.sp, NotasTypography.DisplayTitle.fontSize)
        assertEquals(28.sp, NotasTypography.ScreenTitle.fontSize)
        assertEquals(22.sp, NotasTypography.ModalTitle.fontSize)
        assertEquals(20.sp, NotasTypography.SectionTitle.fontSize)
        assertEquals(16.sp, NotasTypography.CardHeader.fontSize)
        assertEquals(15.sp, NotasTypography.Button.fontSize)
        assertEquals(14.sp, NotasTypography.BodyMedium.fontSize)
        assertEquals(13.sp, NotasTypography.Body.fontSize)
        assertEquals(12.sp, NotasTypography.Label.fontSize)
        assertEquals(11.sp, NotasTypography.Caption.fontSize)
        assertEquals(10.sp, NotasTypography.TabLabel.fontSize)
    }

    @Test
    fun syncStatusEnum_coversAllFigmaCloudSyncStates() {
        val states = SyncStatus.entries
        assertEquals(6, states.size)

        for (status in states) {
            assertTrue(status.title.isNotBlank())
            assertTrue(status.bannerTitle.isNotBlank())
            assertTrue(status.bannerDescription.isNotBlank())
        }

        assertEquals("Synced", SyncStatus.SYNCED.title)
        assertEquals("Cloud Sync is up to date", SyncStatus.SYNCED.bannerTitle)

        assertEquals("Syncing", SyncStatus.SYNCING.title)
        assertEquals("Sync in progress", SyncStatus.SYNCING.bannerTitle)

        assertEquals("Offline", SyncStatus.OFFLINE.title)
        assertEquals("You’re offline", SyncStatus.OFFLINE.bannerTitle)

        assertEquals("Error", SyncStatus.NETWORK_ERROR.title)
        assertEquals("Network Error", SyncStatus.NETWORK_ERROR.bannerTitle)

        assertEquals("Session Expired", SyncStatus.SESSION_EXPIRED.title)
        assertEquals("Session Expired", SyncStatus.SESSION_EXPIRED.bannerTitle)

        assertEquals("Not connected", SyncStatus.NOT_CONNECTED.title)
        assertEquals("Status unavailable", SyncStatus.NOT_CONNECTED.bannerTitle)
    }
}
