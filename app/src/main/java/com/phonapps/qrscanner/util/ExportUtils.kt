package com.phonapps.qrscanner.util

import com.phonapps.qrscanner.data.db.ScanEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds CSV / JSON exports of scan history for the SAF create-document flow. */
object ExportUtils {

    fun toCsv(scans: List<ScanEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("id,type,title,content,source,favorite,scanned_at\n")
        for (scan in scans) {
            sb.append(scan.id).append(',')
                .append(csvCell(scan.contentType)).append(',')
                .append(csvCell(scan.title)).append(',')
                .append(csvCell(scan.rawValue)).append(',')
                .append(csvCell(scan.source)).append(',')
                .append(if (scan.favorite) "1" else "0").append(',')
                .append(csvCell(dateFormat.format(Date(scan.timestamp))))
                .append('\n')
        }
        return sb.toString()
    }

    fun toJson(scans: List<ScanEntity>): String {
        val array = JSONArray()
        for (scan in scans) {
            array.put(
                JSONObject()
                    .put("id", scan.id)
                    .put("type", scan.contentType)
                    .put("title", scan.title)
                    .put("content", scan.rawValue)
                    .put("source", scan.source)
                    .put("favorite", scan.favorite)
                    .put("scannedAt", scan.timestamp)
            )
        }
        return JSONObject().put("scans", array).toString(2)
    }

    fun fileName(extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "qr-scans-$stamp.$extension"
    }

    private fun csvCell(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
