package com.capstone.navicamp

import android.content.Context
import android.net.Uri
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExportUtils {
    
    private const val PAGE_WIDTH = 842 // A4 landscape width in points
    private const val PAGE_HEIGHT = 595 // A4 landscape height in points
    private const val MARGIN = 50
    private const val CELL_HEIGHT = 30
    private const val HEADER_HEIGHT = 40
    
    fun exportIncidentDataToPdf(context: Context, uri: Uri, data: List<List<String>>, officerName: String) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            createPdfDocument(outputStream, data, officerName)
        }
    }
    
    private fun createPdfDocument(outputStream: OutputStream, data: List<List<String>>, officerName: String) {
        val pdfDocument = PdfDocument()
        
        if (data.isEmpty()) {
            createEmptyPdfPage(pdfDocument)
        } else {
            val headers = listOf(
                "Alert ID", "User ID", "Device ID", "Name", "Coordinates", 
                "Floor Level", "Status", "Time of Alert", "Resolved On", 
                "Officer Name", "Incident Description"
            )
            createDataPages(pdfDocument, headers, data, officerName)
        }
        
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
    
    private fun createEmptyPdfPage(pdfDocument: PdfDocument) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.DEFAULT
        }
        
        canvas.drawText("No incidents found for the selected filters.", 
                       MARGIN.toFloat(), (PAGE_HEIGHT / 2).toFloat(), paint)
        
        pdfDocument.finishPage(page)
    }
    
    private fun createDataPages(pdfDocument: PdfDocument, headers: List<String>, data: List<List<String>>, officerName: String) {
        val headerPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        
        val lineSpacing = cellPaint.fontSpacing

        val headerBackgroundPaint = Paint().apply {
            color = Color.parseColor("#2196F3") // Primary blue color
        }
        
        val borderPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        val columnWidths = calculateColumnWidths(headers, data)
        val rowsPerPage = (PAGE_HEIGHT - 2 * MARGIN - HEADER_HEIGHT) / CELL_HEIGHT
        val totalPages = kotlin.math.ceil(data.size.toDouble() / rowsPerPage).toInt().coerceAtLeast(1)
        
        for (pageNum in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            var yPosition = MARGIN.toFloat()
            
            // Title
            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }
            val subtitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 12f
                typeface = Typeface.DEFAULT
            }

            val currentDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Assistance Log Export - $currentDate",
                MARGIN.toFloat(), yPosition, titlePaint)
            yPosition += titlePaint.fontSpacing

            canvas.drawText("Exported by: $officerName",
                MARGIN.toFloat(), yPosition, subtitlePaint)
            yPosition += titlePaint.fontSpacing + 10
            
            // Draw headers
            var xPosition = MARGIN.toFloat()
            val headerY = yPosition
            
            for (i in headers.indices) {
                val headerRect = RectF(xPosition, headerY, 
                                     (xPosition + columnWidths[i]),
                                     (headerY + HEADER_HEIGHT))
                
                canvas.drawRect(headerRect, headerBackgroundPaint)
                canvas.drawRect(headerRect, borderPaint)
                
                val textX = xPosition + 5f
                val textY = headerY + HEADER_HEIGHT / 2f + 5f
                canvas.drawText(headers[i], textX, textY, headerPaint)
                
                xPosition += columnWidths[i]
            }
            yPosition += HEADER_HEIGHT
            
            // Draw data rows for this page
            val startRow = pageNum * rowsPerPage
            val endRow = kotlin.math.min(startRow + rowsPerPage, data.size)
            
            for (rowIndex in startRow until endRow) {
                val row = data[rowIndex]
                xPosition = MARGIN.toFloat()
                
                // Calculate max lines for the current row
                var maxLines = 1
                for (colIndex in headers.indices) {
                    val cellText = if (colIndex < row.size) row[colIndex] else ""
                    val lines = splitTextIntoLines(cellText, columnWidths[colIndex] - 10, cellPaint)
                    if (lines.size > maxLines) {
                        maxLines = lines.size
                    }
                }
                val rowHeight = maxLines * lineSpacing + 10 // Add some padding
                
                // Check for page break
                if (yPosition + rowHeight > PAGE_HEIGHT - MARGIN) {
                    // This should not happen with current logic, but as a safeguard
                    pdfDocument.finishPage(page)
                    val newPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum + 2).create()
                    val newPage = pdfDocument.startPage(newPageInfo)
                    // Redraw headers on new page if needed (omitted for simplicity)
                    yPosition = MARGIN.toFloat()
                }
                
                for (colIndex in headers.indices) {
                    val cellRect = RectF(xPosition, yPosition,
                                       (xPosition + columnWidths[colIndex]),
                                       (yPosition + rowHeight))
                    
                    canvas.drawRect(cellRect, borderPaint)
                    
                    val cellText = if (colIndex < row.size) row[colIndex] else ""
                    val lines = splitTextIntoLines(cellText, columnWidths[colIndex] - 10, cellPaint)
                    var textY = yPosition + lineSpacing
                    for (line in lines) {
                        canvas.drawText(line, xPosition + 5f, textY, cellPaint)
                        textY += lineSpacing
                    }
                    xPosition += columnWidths[colIndex]
                }
                yPosition += rowHeight
            }
            
            // Page number
            val pageText = "Page ${pageNum + 1} of $totalPages"
            canvas.drawText(pageText, (PAGE_WIDTH - MARGIN - 60).toFloat(), (PAGE_HEIGHT - 20).toFloat(), cellPaint)
            
            pdfDocument.finishPage(page)
        }
    }
    
    private fun calculateColumnWidths(headers: List<String>, data: List<List<String>>): IntArray {
        val availableWidth = PAGE_WIDTH - 2 * MARGIN
        val columnCount = headers.size
        val widths = IntArray(columnCount)
        
        // Give more space to description and name
        if (columnCount >= 11) {
            widths[0] = (availableWidth * 0.05f).toInt() // Alert ID
            widths[1] = (availableWidth * 0.08f).toInt() // User ID
            widths[2] = (availableWidth * 0.07f).toInt() // Device ID
            widths[3] = (availableWidth * 0.10f).toInt() // Name
            widths[4] = (availableWidth * 0.10f).toInt() // Coordinates
            widths[5] = (availableWidth * 0.08f).toInt() // Floor Level
            widths[6] = (availableWidth * 0.07f).toInt() // Status
            widths[7] = (availableWidth * 0.10f).toInt() // Time of Alert
            widths[8] = (availableWidth * 0.10f).toInt() // Resolved On
            widths[9] = (availableWidth * 0.10f).toInt() // Officer Name
            widths[10] = (availableWidth * 0.15f).toInt()// Description
        } else {
            val baseWidth = availableWidth / columnCount
            for (i in 0 until columnCount) {
                widths[i] = baseWidth
            }
        }
        return widths
    }
    
    private fun splitTextIntoLines(text: String, maxWidth: Int, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()
        
        for (word in words) {
            // Check if the word itself is wider than the column
            if (paint.measureText(word) > maxWidth) {
                // Flush the current line before processing the long word
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                }

                // Break the long word character by character
                var tempWord = StringBuilder()
                for (char in word) {
                    if (paint.measureText(tempWord.toString() + char) > maxWidth) {
                        lines.add(tempWord.toString())
                        tempWord.clear()
                    }
                    tempWord.append(char)
                }
                if (tempWord.isNotEmpty()) {
                    currentLine.append(tempWord)
                }
            } else {
                // Check if adding the next word exceeds the line width
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) > maxWidth) {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                    currentLine.append(word)
                } else {
                    if (currentLine.isNotEmpty()) {
                        currentLine.append(" ")
                    }
                    currentLine.append(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
} 