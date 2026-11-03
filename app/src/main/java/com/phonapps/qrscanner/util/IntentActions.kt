package com.phonapps.qrscanner.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.phonapps.qrscanner.data.model.ScanContent

/** All outbound actions a scan result can trigger. Every intent is guarded. */
object IntentActions {

    /** Returns false when there is no app that can handle the action. */
    fun openUrl(context: Context, url: String): Boolean {
        // Only http(s) leaves the app as a browsable page. Everything else is
        // handled by its own typed action below.
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) return false
        return fire(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun openDeepLink(context: Context, raw: String): Boolean =
        fire(context, Intent(Intent.ACTION_VIEW, Uri.parse(raw)))

    fun dial(context: Context, number: String): Boolean =
        fire(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

    fun sendSms(context: Context, number: String, message: String): Boolean {
        val uri = Uri.parse("smsto:$number")
        val intent = Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", message)
        return fire(context, intent)
    }

    fun sendEmail(context: Context, address: String, subject: String, body: String): Boolean {
        val uri = Uri.parse("mailto:$address")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
        }
        return fire(context, intent)
    }

    fun openMap(context: Context, content: ScanContent.Geo): Boolean {
        val uri = when {
            content.latitude != null && content.longitude != null ->
                Uri.parse("geo:${content.latitude},${content.longitude}?q=${content.latitude},${content.longitude}")
            content.query.isNotBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(content.query)}")
            else -> Uri.parse("geo:0,0?q=${Uri.encode(content.raw)}")
        }
        return fire(context, Intent(Intent.ACTION_VIEW, uri))
    }

    fun addCalendarEvent(context: Context, content: ScanContent.Calendar): Boolean {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            type = "vnd.android.cursor.item/event"
            if (content.summary.isNotBlank()) putExtra("title", content.summary)
            if (content.location.isNotBlank()) putExtra("eventLocation", content.location)
            if (content.description.isNotBlank()) putExtra("description", content.description)
        }
        return fire(context, intent)
    }

    fun saveContact(context: Context, content: ScanContent.Contact): Boolean {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (content.name.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, content.name)
            }
            content.phones.firstOrNull()?.let {
                putExtra(ContactsContract.Intents.Insert.PHONE, it)
            }
            content.emails.firstOrNull()?.let {
                putExtra(ContactsContract.Intents.Insert.EMAIL, it)
            }
            if (content.organization.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, content.organization)
            }
        }
        return fire(context, intent)
    }

    fun openWifiSettings(context: Context): Boolean =
        fire(context, Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        fire(context, Intent.createChooser(intent, "Share"))
    }

    fun copyText(context: Context, label: String, text: String) {
        val manager = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        if (manager == null) {
            Toast.makeText(context, "Clipboard unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own copy confirmation overlay.
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    fun toastUnavailable(context: Context) {
        Toast.makeText(context, "No app available for this action", Toast.LENGTH_SHORT).show()
    }

    private fun fire(context: Context, intent: Intent): Boolean {
        return try {
            // Chooser intents always resolve; implicit ones are checked so we
            // never crash when no handler app is installed.
            if (intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
