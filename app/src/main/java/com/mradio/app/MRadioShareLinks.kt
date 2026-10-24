package com.mradio.app

import java.net.URI
import java.net.URLEncoder

/** HTTPS links are clickable and previewable in WhatsApp; MRadio receives them via App Links. */
internal object MRadioShareLinks {
    private const val URL = "https://n2k02.github.io/mradio/r/?u="

    fun forStream(streamUrl: String): String = URL + URLEncoder.encode(streamUrl, "UTF-8")

    fun isSharePage(candidate: String?): Boolean {
        val uri = runCatching { URI(candidate ?: return false) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host.equals("n2k02.github.io", true) &&
            uri.path?.trimEnd('/') == "/mradio/r"
    }
}
