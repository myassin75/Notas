package haddad.three.notas.data.auth

import haddad.three.notas.di.LaunchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthDeepLinkParserTest {

    @Test
    fun parse_directVerifyEmailLink_extractsModeAndOobCode() {
        val url = "https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=test-verify-123&apiKey=test-api-key&lang=en"
        val result = AuthDeepLinkParser.parse(url)

        assertNotNull(result)
        assertEquals("verifyEmail", result!!.mode)
        assertEquals("test-verify-123", result.oobCode)
    }

    @Test
    fun parse_directResetPasswordLink_extractsModeAndOobCode() {
        val url = "https://notas-7db14.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=test-reset-456&apiKey=test-api-key"
        val result = AuthDeepLinkParser.parse(url)

        assertNotNull(result)
        assertEquals("resetPassword", result!!.mode)
        assertEquals("test-reset-456", result.oobCode)
    }

    @Test
    fun parse_customSchemeLink_extractsModeAndOobCode() {
        val url = "notas://auth/action?mode=verifyEmail&oobCode=custom-scheme-code"
        val result = AuthDeepLinkParser.parse(url)

        assertNotNull(result)
        assertEquals("verifyEmail", result!!.mode)
        assertEquals("custom-scheme-code", result.oobCode)
    }

    @Test
    fun parse_nestedFirebaseHostingLink_extractsParametersFromNestedUrl() {
        val nestedUrl = "https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=nested-code-789&apiKey=test-key"
        val encodedNestedUrl = java.net.URLEncoder.encode(nestedUrl, "UTF-8")
        val outerUrl = "https://notas-7db14.firebaseapp.com/__/auth/links?link=$encodedNestedUrl"

        val result = AuthDeepLinkParser.parse(outerUrl)

        assertNotNull(result)
        assertEquals("verifyEmail", result!!.mode)
        assertEquals("nested-code-789", result.oobCode)
    }

    @Test
    fun parse_nestedDeepLinkIdParam_extractsParameters() {
        val nestedUrl = "https://notas-7db14.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=dyn-code-abc"
        val encodedNestedUrl = java.net.URLEncoder.encode(nestedUrl, "UTF-8")
        val outerUrl = "https://notas.page.link/?deep_link_id=$encodedNestedUrl"

        val result = AuthDeepLinkParser.parse(outerUrl)

        assertNotNull(result)
        assertEquals("resetPassword", result!!.mode)
        assertEquals("dyn-code-abc", result.oobCode)
    }

    @Test
    fun parse_missingOobCode_returnsNull() {
        val url = "https://notas-7db14.firebaseapp.com/__/auth/action?mode=verifyEmail&apiKey=test-key"
        val result = AuthDeepLinkParser.parse(url)

        assertNull(result)
    }

    @Test
    fun parse_missingMode_returnsNull() {
        val url = "https://notas-7db14.firebaseapp.com/__/auth/action?oobCode=12345&apiKey=test-key"
        val result = AuthDeepLinkParser.parse(url)

        assertNull(result)
    }

    @Test
    fun parse_nullOrEmpty_returnsNull() {
        assertNull(AuthDeepLinkParser.parse(null as String?))
        assertNull(AuthDeepLinkParser.parse(""))
        assertNull(AuthDeepLinkParser.parse("   "))
    }

    @Test
    fun parse_unrelatedUrl_returnsNull() {
        assertNull(AuthDeepLinkParser.parse("https://google.com/search?q=notas"))
        assertNull(AuthDeepLinkParser.parse("https://notas-7db14.firebaseapp.com/"))
    }
}
