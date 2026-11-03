package com.phonapps.qrscanner.data.model

import android.net.Uri

/** Stable content kinds persisted in the history database. */
enum class ContentKind {
    URL,
    TEXT,
    WIFI,
    CONTACT,
    EMAIL,
    PHONE,
    SMS,
    GEO,
    CALENDAR,
    DEEP_LINK,
    UNSAFE;

    val label: String
        get() = when (this) {
            URL -> "Website"
            TEXT -> "Text"
            WIFI -> "Wi-Fi"
            CONTACT -> "Contact"
            EMAIL -> "Email"
            PHONE -> "Phone"
            SMS -> "SMS"
            GEO -> "Location"
            CALENDAR -> "Event"
            DEEP_LINK -> "App link"
            UNSAFE -> "Blocked link"
        }
}

/** Classified interpretation of a decoded QR payload. */
sealed interface ScanContent {
    val kind: ContentKind
    val title: String
    val raw: String

    data class Url(
        val url: String,
        val domain: String,
        val suspicious: Boolean,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.URL
        override val title = domain
    }

    data class Text(
        val text: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.TEXT
        override val title = text.take(80).replace('\n', ' ')
    }

    data class Wifi(
        val ssid: String,
        val security: String,
        val password: String,
        val hidden: Boolean,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.WIFI
        override val title = ssid
    }

    data class Contact(
        val name: String,
        val phones: List<String>,
        val emails: List<String>,
        val organization: String,
        val address: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.CONTACT
        override val title = name.ifBlank { phones.firstOrNull() ?: emails.firstOrNull() ?: "Contact" }
    }

    data class Email(
        val address: String,
        val subject: String,
        val body: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.EMAIL
        override val title = address
    }

    data class Phone(
        val number: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.PHONE
        override val title = number
    }

    data class Sms(
        val number: String,
        val message: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.SMS
        override val title = number.ifBlank { message.take(60) }
    }

    data class Geo(
        val latitude: Double?,
        val longitude: Double?,
        val query: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.GEO
        override val title =
            if (latitude != null && longitude != null) "$latitude, $longitude"
            else query.ifBlank { raw.take(60) }
    }

    data class Calendar(
        val summary: String,
        val start: String,
        val end: String,
        val location: String,
        val description: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.CALENDAR
        override val title = summary.ifBlank { "Calendar event" }
    }

    data class DeepLink(
        val uri: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.DEEP_LINK
        override val title = runCatching { Uri.parse(raw).host ?: raw.take(60) }.getOrDefault(raw.take(60))
    }

    /** Payload uses a scheme that must never be dispatched as an intent. */
    data class Unsafe(
        val scheme: String,
        override val raw: String
    ) : ScanContent {
        override val kind = ContentKind.UNSAFE
        override val title = "Potentially unsafe link"
    }
}

/**
 * Interprets a raw QR payload into a structured [ScanContent].
 * Never throws: unparseable input falls back to plain text.
 */
object QrClassifier {

    private val unsafeSchemes = setOf(
        "javascript", "jscript", "vbscript", "data", "file",
        "intent", "content", "jar", "about", "chrome"
    )

    private val safeAppSchemes = setOf(
        "http", "https", "mailto", "tel", "smsto", "sms", "geo", "wifi", "matmsg"
    )

    fun classify(rawInput: String): ScanContent {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return ScanContent.Text("", raw)
        return try {
            classifyOrThrow(raw)
        } catch (_: Exception) {
            ScanContent.Text(raw, raw)
        }
    }

    private fun classifyOrThrow(raw: String): ScanContent {
        val lower = raw.lowercase()
        // Scheme-less www. links are common in the wild.
        if (lower.startsWith("www.") && !raw.contains(' ')) {
            return urlContent("https://$raw", raw)
        }
        val schemeEnd = raw.indexOf(':')
        if (schemeEnd in 1..20 && raw[schemeEnd - 1] != ' ') {
            val scheme = lower.substring(0, schemeEnd)
            if (scheme in unsafeSchemes) return ScanContent.Unsafe(scheme, raw)
            when (scheme) {
                "http", "https" -> return urlContent(raw, raw)
                "wifi" -> parseWifi(raw)?.let { return it }
                "mailto" -> return parseMailto(raw)
                "matmsg" -> return parseMatmsg(raw)
                "tel" -> return ScanContent.Phone(raw.substringAfter(':').trim(), raw)
                "smsto", "sms" -> return parseSms(raw)
                "smsto:" -> return parseSms(raw)
                "geo" -> return parseGeo(raw)
                "begin" -> if (lower.startsWith("begin:vcard")) {
                    return parseVCard(raw)
                } else if (lower.startsWith("begin:vevent")) {
                    return parseVEvent(raw)
                }
                "mecard" -> return parseMeCard(raw)
                else -> if (scheme !in safeAppSchemes && scheme.matches(Regex("[a-z][a-z0-9+.-]*"))) {
                    return ScanContent.DeepLink(raw, raw)
                }
            }
        }
        // Multi-line payloads that look like contact / calendar cards.
        if (lower.contains("begin:vcard")) return parseVCard(raw)
        if (lower.contains("begin:vevent")) return parseVEvent(raw)
        // Bare URLs without scheme but with a dot and no spaces.
        if (!raw.contains(' ') && raw.contains('.') && !raw.contains(':')) {
            val candidate = "https://$raw"
            if (looksLikeDomain(candidate)) return urlContent(candidate, raw)
        }
        // Bare email address.
        if (isBareEmail(raw)) {
            return ScanContent.Email(raw, "", "", raw)
        }
        // Bare international phone number.
        if (raw.matches(Regex("""\+?[0-9][0-9\s\-().]{5,}[0-9]""")) && raw.count { it.isDigit() } in 7..15) {
            return ScanContent.Phone(raw, raw)
        }
        return ScanContent.Text(raw, raw)
    }

    // ------------------------------------------------------------------ URLs

    private fun urlContent(url: String, raw: String): ScanContent {
        val domain = runCatching {
            (Uri.parse(url).host ?: url).removePrefix("www.")
        }.getOrDefault(url)
        return ScanContent.Url(url, domain, isSuspiciousUrl(url), raw)
    }

    private fun looksLikeDomain(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
        return host.contains('.') && !host.contains(' ')
    }

    /**
     * Conservative heuristics: flags links worth a second look without ever
     * claiming a security verdict. Fully guarded: on any parsing failure the
     * link is treated as suspicious (fail closed).
     */
    fun isSuspiciousUrl(url: String): Boolean = runCatching {
        val uri = Uri.parse(url) ?: return@runCatching true
        val host = uri.host?.lowercase() ?: return@runCatching true
        if (host.startsWith("xn--")) return@runCatching true // punycode / lookalike domains
        if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return@runCatching true // bare IPv4 host
        if (host.startsWith("[") && host.endsWith("]")) return@runCatching true // bare IPv6 host
        if ('@' in url.substringBefore('?')) return@runCatching true // userinfo obfuscation
        if (uri.scheme != "https") return@runCatching true // plain http or odd scheme
        if (host.split('.').size > 4) return@runCatching true // excessive subdomains
        false
    }.getOrDefault(true)

    // ------------------------------------------------------------------ Wi-Fi

    private fun parseWifi(raw: String): ScanContent.Wifi? {
        // WIFI:T:WPA;S:MyNet;P:secret;H:false;;
        if (!raw.startsWith("WIFI:", ignoreCase = true)) return null
        val body = raw.substringAfter(':').trimEnd(';')
        val fields = splitUnescaped(body, ';')
            .mapNotNull {
                val key = it.substringBefore(':').trim().uppercase()
                val value = unescapeWifi(it.substringAfter(':', ""))
                if (key.isNotEmpty()) key to value else null
            }.toMap()
        val ssid = fields["S"].orEmpty()
        if (ssid.isBlank()) return null
        val auth = fields["T"].orEmpty().ifBlank { "nopass" }
        val security = when (auth.uppercase()) {
            "WPA", "WPA2", "WPA/WPA2", "WPA2/WPA3", "WPA3" -> auth.uppercase()
            "WEP" -> "WEP"
            "NOPASS", "OPEN", "" -> "Open"
            else -> auth
        }
        return ScanContent.Wifi(
            ssid = ssid,
            security = security,
            password = fields["P"].orEmpty(),
            hidden = fields["H"].equals("true", ignoreCase = true),
            raw = raw
        )
    }

    private fun splitUnescaped(input: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                current.append(c).append(input[i + 1])
                i += 2
            } else if (c == delimiter) {
                out.add(current.toString())
                current.clear()
                i++
            } else {
                current.append(c)
                i++
            }
        }
        out.add(current.toString())
        return out
    }

    private fun unescapeWifi(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length && value[i + 1] in setOf(';', ',', ':', '"', '\\')) {
                sb.append(value[i + 1])
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ Email

    /**
     * Manual query parsing: android.net.Uri misbehaves for opaque URIs on some
     * OEM ROMs, so the classifier never depends on it here.
     */
    private fun parseMailto(raw: String): ScanContent.Email {
        val after = raw.substringAfter(':')
        val query = parseQuery(after.substringAfter('?', ""))
        return ScanContent.Email(
            address = after.substringBefore('?'),
            subject = query["subject"].orEmpty(),
            body = query["body"].orEmpty(),
            raw = raw
        )
    }

    /** Parses a `k=v&k2=v2` query string without android.net.Uri. */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').trim().lowercase()
            if (key.isEmpty()) return@mapNotNull null
            val value = pair.substringAfter('=', "")
            key to runCatching {
                java.net.URLDecoder.decode(value, "UTF-8")
            }.getOrDefault(value)
        }.toMap()
    }

    private fun parseMatmsg(raw: String): ScanContent.Email {
        // MATMSG:TO:a@b.com;SUB:hi;BODY:hello;;
        val fields = raw.trimEnd(';').split(';')
            .mapNotNull {
                val k = it.substringBefore(':').trim().uppercase()
                val v = it.substringAfter(':', "")
                if (k.isNotEmpty()) k to v else null
            }.toMap()
        return ScanContent.Email(
            address = fields["TO"].orEmpty(),
            subject = fields["SUB"].orEmpty(),
            body = fields["BODY"].orEmpty(),
            raw = raw
        )
    }

    private fun isBareEmail(raw: String): Boolean {
        if (raw.contains(' ') || raw.contains('\n')) return false
        return raw.matches(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""))
    }

    // ------------------------------------------------------------------ SMS

    private fun parseSms(raw: String): ScanContent.Sms {
        // smsto:+123:hello | sms:+123?body=hello | SMSTO:+123:hello
        val withoutScheme = raw.substringAfter(':')
        return if ('?' in withoutScheme) {
            val query = parseQuery(withoutScheme.substringAfter('?'))
            ScanContent.Sms(
                number = withoutScheme.substringBefore('?'),
                message = query["body"].orEmpty(),
                raw = raw
            )
        } else {
            val number = withoutScheme.substringBefore(':')
            val message = if (':' in withoutScheme) withoutScheme.substringAfter(':') else ""
            ScanContent.Sms(number, message, raw)
        }
    }

    // ------------------------------------------------------------------ Geo

    private fun parseGeo(raw: String): ScanContent.Geo {
        // geo:37.7749,-122.4194 | geo:0,0?q=coffee
        val body = raw.substringAfter(':')
        val coords = body.substringBefore('?').split(',')
        val lat = coords.getOrNull(0)?.toDoubleOrNull()
        val lng = coords.getOrNull(1)?.toDoubleOrNull()
        val query = parseQuery(body.substringAfter('?', ""))["q"].orEmpty()
        return ScanContent.Geo(lat, lng, query, raw)
    }

    // ------------------------------------------------------------------ Cards

    private fun parseVCard(raw: String): ScanContent.Contact {
        var name = ""
        var formattedName = ""
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var org = ""
        var address = ""
        // Unfold continuation lines (a newline followed by a space/tab).
        val unfolded = raw.replace(Regex("\r?\n[ \t]"), "")
        for (line in unfolded.lines()) {
            val key = line.substringBefore(':').uppercase()
            val value = line.substringAfter(':', "").replace("\\n", "\n").trim()
            when {
                key == "FN" -> formattedName = value
                key == "N" -> {
                    val parts = value.split(';')
                    name = listOf(parts.getOrNull(1), parts.getOrNull(0))
                        .filter { !it.isNullOrBlank() }.joinToString(" ")
                }
                key.startsWith("TEL") -> if (value.isNotBlank()) phones.add(value)
                key.startsWith("EMAIL") -> if (value.isNotBlank()) emails.add(value)
                key == "ORG" -> org = value.trimEnd(';')
                key.startsWith("ADR") -> {
                    address = value.split(';').filter { it.isNotBlank() }.joinToString(", ")
                }
            }
        }
        return ScanContent.Contact(
            name = formattedName.ifBlank { name },
            phones = phones.distinct(),
            emails = emails.distinct(),
            organization = org,
            address = address,
            raw = raw
        )
    }

    private fun parseMeCard(raw: String): ScanContent.Contact {
        // MECARD:N:Name;TEL:+123;EMAIL:a@b;ADR:addr;;
        val fields = raw.removePrefix("MECARD:").trimEnd(';').split(';')
            .mapNotNull {
                val k = it.substringBefore(':').trim().uppercase()
                val v = it.substringAfter(':', "")
                if (k.isNotEmpty()) k to v else null
            }
        val phones = fields.filter { it.first == "TEL" }.map { it.second }.filter { it.isNotBlank() }
        val emails = fields.filter { it.first == "EMAIL" }.map { it.second }.filter { it.isNotBlank() }
        return ScanContent.Contact(
            name = fields.firstOrNull { it.first == "N" }?.second.orEmpty(),
            phones = phones,
            emails = emails,
            organization = "",
            address = fields.firstOrNull { it.first == "ADR" }?.second.orEmpty(),
            raw = raw
        )
    }

    private fun parseVEvent(raw: String): ScanContent.Calendar {
        val fields = mutableMapOf<String, String>()
        val unfolded = raw.replace(Regex("\r?\n[ \t]"), "")
        for (line in unfolded.lines()) {
            val key = line.substringBefore(':').substringBefore(';').trim().uppercase()
            val value = line.substringAfter(':', "").replace("\\n", "\n").trim()
            if (key.isNotEmpty() && value.isNotEmpty() && key !in fields) fields[key] = value
        }
        return ScanContent.Calendar(
            summary = fields["SUMMARY"].orEmpty(),
            start = formatEventDate(fields["DTSTART"].orEmpty()),
            end = formatEventDate(fields["DTEND"].orEmpty()),
            location = fields["LOCATION"].orEmpty(),
            description = fields["DESCRIPTION"].orEmpty(),
            raw = raw
        )
    }

    /** Keeps event dates human-readable without pulling in date libraries. */
    private fun formatEventDate(value: String): String {
        if (value.isBlank()) return ""
        // 20260115T103000Z -> 2026-01-15 10:30 UTC ; 20260115 -> 2026-01-15
        val date = value.trimEnd('Z')
        return if ('T' in date && date.length >= 15) {
            "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)} " +
                "${date.substring(9, 11)}:${date.substring(11, 13)}" +
                if (value.endsWith('Z')) " UTC" else ""
        } else if (date.length == 8 && date.all { it.isDigit() }) {
            "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
        } else {
            value
        }
    }
}
