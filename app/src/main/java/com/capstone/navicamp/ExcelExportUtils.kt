package com.capstone.navicamp

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExcelExportUtils {

    fun exportIncidentDataToExcel(context: Context, uri: Uri, data: List<List<String>>, officerName: String) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            createProperExcelFile(outputStream, data, officerName)
        }
    }

    private fun createProperExcelFile(outputStream: OutputStream, data: List<List<String>>, officerName: String) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Incident Log")

        // Add the "Exported by" line at the top
        val exportedByRow = sheet.createRow(0)
        exportedByRow.createCell(0).setCellValue("Exported by: $officerName")

        // --- Styling ---
        // Header Style
        val headerFont = workbook.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
        }
        val headerCellStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.ROYAL_BLUE.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        }

        // Data Cell Style
        val dataCellStyle = workbook.createCellStyle().apply {
            wrapText = true
        }

        // --- Headers ---
        val headers = listOf(
            "Alert ID", "User ID", "Device ID", "Name", "Coordinates",
            "Floor Level", "Status", "Time of Alert", "Resolved On",
            "Officer Name", "Incident Description"
        )
        val headerRow = sheet.createRow(2) // Start headers on the 3rd row (index 2)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerCellStyle
        }

        // --- Data Rows ---
        if (data.isEmpty()) {
            val row = sheet.createRow(3) // Start data on the 4th row
            row.createCell(0).setCellValue("No incidents found for the selected filters.")
        } else {
            data.forEachIndexed { rowIndex, rowData ->
                val row = sheet.createRow(rowIndex + 3) // Start data on the 4th row
                rowData.forEachIndexed { cellIndex, cellData ->
                    val cell = row.createCell(cellIndex)
                    // Format dates if they look like timestamps
                    if ((cellIndex == 7 || cellIndex == 8) && cellData.isNotBlank()) {
                        try {
                            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
                            val date = inputFormat.parse(cellData)
                            cell.setCellValue(if (date != null) outputFormat.format(date) else cellData)
                        } catch (e: Exception) {
                            cell.setCellValue(cellData)
                        }
                    } else {
                        cell.setCellValue(cellData)
                    }
                    cell.cellStyle = dataCellStyle
                }
            }
        }
        
        // --- Auto-size columns ---
        // This can be problematic on Android. A safer approach is to set reasonable fixed widths.
        sheet.setColumnWidth(0, 3000) // Alert ID
        sheet.setColumnWidth(1, 3000) // User ID
        sheet.setColumnWidth(2, 3000) // Device ID
        sheet.setColumnWidth(3, 5000) // Name
        sheet.setColumnWidth(4, 5000) // Coordinates
        sheet.setColumnWidth(5, 4000) // Floor Level
        sheet.setColumnWidth(6, 3000) // Status
        sheet.setColumnWidth(7, 5000) // Time of Alert
        sheet.setColumnWidth(8, 5000) // Resolved On
        sheet.setColumnWidth(9, 5000) // Officer Name
        sheet.setColumnWidth(10, 10000) // Incident Description

        workbook.write(outputStream)
        workbook.close()
    }
} 