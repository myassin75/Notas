package haddad.three.notas.ui.splash

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashScreenTest {
    @Test
    fun splashDesignTokens_matchApprovedReference() {
        assertEquals(Color(0xFFF6F3F8), SplashDesignTokens.Background)
        assertEquals(Color(0xFF393743), SplashDesignTokens.AppNameColor)
        assertEquals(Color(0xFF918D98), SplashDesignTokens.AttributionColor)
    }
}
