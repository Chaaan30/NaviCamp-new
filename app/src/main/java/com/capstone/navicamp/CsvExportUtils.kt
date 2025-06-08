package com.capstone.navicamp

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.*

fun exportIncidentDataToCSV(context: Context, data: List<List<String>>, fileName: String = "incident_logs.csv"): File? {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // Android 10 and below: export directly to Documents
        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val exportDir = File(documentsDir, "Campus Navigator Incident Logs")
            if (!exportDir.exists()) exportDir.mkdirs()
            val csvFile = File(exportDir, fileName)
            FileWriter(csvFile).use { writer ->
                writeIncidentDataToWriter(writer, data)
            }
            csvFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    } else {
        // Android 11+ (API 30+): Use SAF, handled in Activity
        null
    }
}

fun writeIncidentDataToWriter(writer: Writer, data: List<List<String>>) {
    writer.appendLine("Alert ID,User ID,Device ID,Name,Coordinates,Floor Level,Status,Time of Alert,Resolved On,Officer Name,Incident Description")
    data.forEach { row ->
        writer.appendLine(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
    }
}

fun exportIncidentDataToCSVWithUri(context: Context, uri: Uri, data: List<List<String>>) {
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        OutputStreamWriter(outputStream).use { writer ->
            writeIncidentDataToWriter(writer, data)
        }
    }
}

object CsvExportUtils {

    fun exportIncidentDataToCsv(context: Context, uri: Uri, data: List<List<String>>, officerName: String) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val writer = OutputStreamWriter(outputStream)

            // Add the "Exported by" line
            writer.write("Exported by: $officerName\n")
            writer.write("\n") // Add a blank line for spacing

            // Write headers
            val headers = listOf(
                "Alert ID", "User ID", "Device ID", "Name", "Coordinates",
                "Floor Level", "Status", "Time of Alert", "Resolved On",
                "Officer Name", "Incident Description"
            )
            writer.write(headers.joinToString(",") + "\n")

            // Write data
            data.forEach { row ->
                writer.write(row.joinToString(",") { escapeCsvField(it) } + "\n")
            }
            writer.flush()
        }
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}