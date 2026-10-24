package com.mradio.app

/**
 * Extract stream and app-deep-link URLs from text shared by messaging apps and browsers.
 * Regex escaping is deliberately single-backslash here: Kotlin raw strings do not
 * process backslashes, so \s matches whitespace and \? matches a literal question mark.
 */
internal object StreamLinkParser {
    private val deepLink = Regex("""mradio://open\?[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val httpUrl = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)

    fun deepLinkFromSharedText(text: String): String? =
        deepLink.find(text)?.value?.trimEnd('.', ',', ')', ']', '}')

    fun httpUrlFromText(text: String): String? =
        httpUrl.find(text)?.value?.trimEnd('.', ',', ')', ']', '}')
}
