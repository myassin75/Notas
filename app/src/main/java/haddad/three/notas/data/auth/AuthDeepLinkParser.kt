package haddad.three.notas.data.auth

import android.net.Uri
import haddad.three.notas.di.LaunchAction
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses deep links and action links received by the application (direct Firebase Auth links,
 * custom-scheme links, or nested links from Firebase Hosting / Dynamic Links).
 */
object AuthDeepLinkParser {
    /**
     * Parses an incoming Android [Uri].
     *
     * @return [LaunchAction.AuthEmailAction] if valid `mode` and `oobCode` are present,
     * or `null` if the URI does not represent an auth email action.
     */
    fun parse(uri: Uri?): LaunchAction.AuthEmailAction? = parse(uri?.toString())

    /**
     * Parses a raw URL string. Handles both direct parameters and nested links
     * (e.g. Firebase Hosting `/__/auth/links?link=...` or `?deep_link_id=...`).
     *
     * @return [LaunchAction.AuthEmailAction] if valid `mode` and `oobCode` are present,
     * or `null` if the URL does not represent an auth email action.
     */
    fun parse(url: String?): LaunchAction.AuthEmailAction? {
        if (url.isNullOrBlank()) return null

        val queryParams = extractQueryParams(url)
        var mode = queryParams["mode"]
        var oobCode = queryParams["oobCode"]

        // If mode or oobCode are missing at top-level, check for wrapped/nested links
        // (common in Firebase Hosting auth links e.g. /__/auth/links?link=... or Dynamic Links ?deep_link_id=...)
        if (mode.isNullOrBlank() || oobCode.isNullOrBlank()) {
            val nestedUrl = queryParams["link"] ?: queryParams["deep_link_id"]
            if (!nestedUrl.isNullOrBlank()) {
                val nestedParams = extractQueryParams(nestedUrl)
                if (mode.isNullOrBlank()) mode = nestedParams["mode"]
                if (oobCode.isNullOrBlank()) oobCode = nestedParams["oobCode"]
            }
        }

        return if (!mode.isNullOrBlank() && !oobCode.isNullOrBlank()) {
            LaunchAction.AuthEmailAction(mode = mode, oobCode = oobCode)
        } else {
            null
        }
    }

    private fun extractQueryParams(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart < 0 || queryStart >= url.length - 1) return emptyMap()
        val queryString = url.substring(queryStart + 1).substringBefore('#')

        val map = mutableMapOf<String, String>()
        for (param in queryString.split('&')) {
            if (param.isBlank()) continue
            val parts = param.split('=', limit = 2)
            val key =
                try {
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8.toString())
                } catch (_: Exception) {
                    parts[0]
                }
            val value =
                if (parts.size > 1) {
                    try {
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8.toString())
                    } catch (_: Exception) {
                        parts[1]
                    }
                } else {
                    ""
                }
            if (key.isNotEmpty() && !map.containsKey(key)) {
                map[key] = value
            }
        }
        return map
    }
}
