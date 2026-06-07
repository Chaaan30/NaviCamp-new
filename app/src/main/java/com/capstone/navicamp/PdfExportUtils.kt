package com.capstone.navicamp

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates PDF reports using raw PDF format primitives.
 * This avoids android.graphics.pdf.PdfDocument and Canvas APIs entirely,
 * ensuring compatibility across all devices and emulators (no native libhwui dependency).
 */
object PdfExportUtils {

    // A4 Landscape dimensions in PDF points (1 point = 1/72 inch)
    private const val PAGE_W = 842f
    private const val PAGE_H = 595f
    private const val MARGIN = 40f
    private const val HEADER_ROW_H = 18f
    private const val CELL_PAD = 3f
    private const val TITLE_SIZE = 14f
    private const val SUBTITLE_SIZE = 10f
    private const val HDR_FONT_SIZE = 7f
    private const val CELL_FONT_SIZE = 6f
    private const val LINE_H = 8f

    // Approximate average character width as a fraction of font size (Helvetica)
    private const val CHAR_W_FACTOR = 0.52f

    fun exportIncidentDataToPdf(context: Context, uri: Uri, data: List<List<String>>, officerName: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            generatePdf(out, data, officerName)
        }
    }

    fun exportIncidentDataToPdfStream(outputStream: OutputStream, data: List<List<String>>, officerName: String) {
        generatePdf(outputStream, data, officerName)
    }

    // ──────────────────────────────────────────────
    //  Core PDF generation (no Canvas / PdfDocument)
    // ──────────────────────────────────────────────

    private fun generatePdf(outputStream: OutputStream, data: List<List<String>>, officerName: String) {
        val headers = listOf(
            "Alert ID", "User ID", "Device ID", "Name", "Coordinates",
            "Floor Level", "Status", "Assistance Type", "Date & Time of Alert",
            "Resolved On", "Responding Officer", "First Aid Action",
            "Further Information", "Relocated To"
        )
        val colW = colWidths(headers.size)
        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())

        // Build PDF content-stream strings, one per page
        val streams = mutableListOf<String>()
        val sb = StringBuilder()
        var y = PAGE_H - MARGIN
        var pageNum = 1

        // — Helper: emit title + header row at current y position —
        fun emitTitleAndHeaders() {
            // Title (bold)
            sb.pdfLine("BT /F2 $TITLE_SIZE Tf 0 0 0 rg 1 0 0 1 $MARGIN $y Tm")
            sb.pdfLine("(${esc("Assistance Log Export - $dateStr")}) Tj ET")
            y -= TITLE_SIZE + 6

            // Subtitle
            sb.pdfLine("BT /F1 $SUBTITLE_SIZE Tf 0 0 0 rg 1 0 0 1 $MARGIN $y Tm")
            sb.pdfLine("(${esc("Exported by: $officerName")}) Tj ET")
            y -= SUBTITLE_SIZE + 10

            // Header background (blue #2196F3)
            sb.pdfLine("q 0.129 0.588 0.953 rg")
            var hx = MARGIN
            for (w in colW) { sb.pdfLine("${fmt(hx)} ${fmt(y - HEADER_ROW_H)} ${fmt(w)} ${fmt(HEADER_ROW_H)} re"); hx += w }
            sb.pdfLine("f Q")

            // Header borders
            sb.pdfLine("q 0.5 0.5 0.5 RG 0.5 w")
            hx = MARGIN
            for (w in colW) { sb.pdfLine("${fmt(hx)} ${fmt(y - HEADER_ROW_H)} ${fmt(w)} ${fmt(HEADER_ROW_H)} re"); hx += w }
            sb.pdfLine("S Q")

            // Header text (white, bold)
            sb.pdfLine("BT /F2 $HDR_FONT_SIZE Tf 1 1 1 rg")
            hx = MARGIN
            for (i in headers.indices) {
                val tx = hx + CELL_PAD
                val ty = y - HEADER_ROW_H + CELL_PAD + 3
                sb.pdfLine("1 0 0 1 ${fmt(tx)} ${fmt(ty)} Tm (${esc(fitText(headers[i], colW[i]))}) Tj")
                hx += colW[i]
            }
            sb.pdfLine("ET")
            y -= HEADER_ROW_H
        }

        // — Helper: finish current page (add footer, flush) —
        fun finishPage() {
            sb.pdfLine("BT /F1 $CELL_FONT_SIZE Tf 0 0 0 rg")
            sb.pdfLine("1 0 0 1 ${fmt(PAGE_W - MARGIN - 40)} ${fmt(MARGIN / 2)} Tm (Page $pageNum) Tj ET")
            streams.add(sb.toString())
            sb.clear()
        }

        // ── Build pages ──
        emitTitleAndHeaders()

        if (data.isEmpty()) {
            sb.pdfLine("BT /F1 $SUBTITLE_SIZE Tf 0 0 0 rg")
            sb.pdfLine("1 0 0 1 ${fmt(MARGIN)} ${fmt(y - 20)} Tm (No incidents found for the selected filters.) Tj ET")
            finishPage()
        } else {
            for (row in data) {
                // Calculate dynamic row height from text wrapping
                var maxLines = 1
                for (c in headers.indices) {
                    val txt = if (c < row.size) row[c] else ""
                    val lines = wrap(txt, colW[c])
                    if (lines.size > maxLines) maxLines = lines.size
                }
                val rowH = maxLines * LINE_H + 2 * CELL_PAD

                // Page break?
                if (y - rowH < MARGIN) {
                    finishPage()
                    pageNum++
                    y = PAGE_H - MARGIN
                    emitTitleAndHeaders()
                }

                // Cell borders (gray)
                sb.pdfLine("q 0.75 0.75 0.75 RG 0.3 w")
                var cx = MARGIN
                for (w in colW) {
                    sb.pdfLine("${fmt(cx)} ${fmt(y - rowH)} ${fmt(w)} ${fmt(rowH)} re")
                    cx += w
                }
                sb.pdfLine("S Q")

                // Cell text (black)
                sb.pdfLine("BT /F1 $CELL_FONT_SIZE Tf 0 0 0 rg")
                cx = MARGIN
                for (c in headers.indices) {
                    val txt = if (c < row.size) row[c] else ""
                    val lines = wrap(txt, colW[c])
                    var ty = y - CELL_PAD - LINE_H + 2
                    for (line in lines) {
                        sb.pdfLine("1 0 0 1 ${fmt(cx + CELL_PAD)} ${fmt(ty)} Tm (${esc(line)}) Tj")
                        ty -= LINE_H
                    }
                    cx += colW[c]
                }
                sb.pdfLine("ET")

                y -= rowH
            }
            finishPage()
        }

        // ── Assemble final PDF bytes ──
        assemblePdf(outputStream, streams)
    }

    // ──────────────────────────────────────
    //  PDF file structure assembly
    // ──────────────────────────────────────

    private fun assemblePdf(outputStream: OutputStream, pageStreams: List<String>) {
        val buf = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        // PDF header + binary comment (signals binary content to transfer tools)
        buf.putStr("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        // Obj 1 – Catalog
        offsets += buf.size()
        buf.putStr("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Obj 2 – Pages tree
        offsets += buf.size()
        val firstPage = 5
        val kids = pageStreams.indices.joinToString(" ") { "${firstPage + it * 2} 0 R" }
        buf.putStr("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pageStreams.size} >>\nendobj\n")

        // Obj 3 – Font /F1 (Helvetica)
        offsets += buf.size()
        buf.putStr("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")

        // Obj 4 – Font /F2 (Helvetica-Bold)
        offsets += buf.size()
        buf.putStr("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")

        // Page + Content-stream object pairs
        for (i in pageStreams.indices) {
            val pageObj = firstPage + i * 2
            val contObj = pageObj + 1
            val contBytes = pageStreams[i].toByteArray(Charsets.ISO_8859_1)

            // Page object
            offsets += buf.size()
            buf.putStr("$pageObj 0 obj\n<< /Type /Page /Parent 2 0 R " +
                    "/MediaBox [0 0 ${PAGE_W.toInt()} ${PAGE_H.toInt()}] " +
                    "/Contents $contObj 0 R " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> >>\nendobj\n")

            // Content-stream object
            offsets += buf.size()
            buf.putStr("$contObj 0 obj\n<< /Length ${contBytes.size} >>\nstream\n")
            buf.write(contBytes)
            buf.putStr("\nendstream\nendobj\n")
        }

        // Cross-reference table
        val xrefPos = buf.size()
        val numObjs = offsets.size + 1
        buf.putStr("xref\n0 $numObjs\n")
        buf.putStr("0000000000 65535 f \n")
        for (off in offsets) {
            buf.putStr(String.format("%010d 00000 n \n", off))
        }

        // Trailer
        buf.putStr("trailer\n<< /Size $numObjs /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")

        outputStream.write(buf.toByteArray())
        outputStream.flush()
    }

    // ──────────────────────────────────────
    //  Column widths
    // ──────────────────────────────────────

    private fun colWidths(count: Int): FloatArray {
        val usable = PAGE_W - 2 * MARGIN
        val w = FloatArray(count)
        if (count >= 14) {
            val p = floatArrayOf(
                0.05f, 0.06f, 0.06f, 0.09f, 0.09f, 0.05f, 0.05f, 0.07f,
                0.09f, 0.08f, 0.08f, 0.07f, 0.08f, 0.08f
            )
            for (i in p.indices) w[i] = usable * p[i]
        } else {
            val base = usable / count
            for (i in 0 until count) w[i] = base
        }
        return w
    }

    // ──────────────────────────────────────
    //  Text utilities
    // ──────────────────────────────────────

    private fun charW(fontSize: Float) = fontSize * CHAR_W_FACTOR

    private fun fitText(text: String, colWidth: Float): String {
        val max = ((colWidth - 2 * CELL_PAD) / charW(HDR_FONT_SIZE)).toInt().coerceAtLeast(1)
        return if (text.length <= max) text else text.take((max - 2).coerceAtLeast(1)) + ".."
    }

    private fun wrap(text: String, colWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val maxChars = ((colWidth - 2 * CELL_PAD) / charW(CELL_FONT_SIZE)).toInt().coerceAtLeast(1)
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        for (word in text.split(" ")) {
            if (word.length > maxChars) {
                if (cur.isNotEmpty()) { lines += cur.toString(); cur.clear() }
                var rem = word
                while (rem.length > maxChars) { lines += rem.substring(0, maxChars); rem = rem.substring(maxChars) }
                if (rem.isNotEmpty()) cur.append(rem)
            } else {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (test.length > maxChars) {
                    lines += cur.toString(); cur.clear(); cur.append(word)
                } else {
                    if (cur.isNotEmpty()) cur.append(" ")
                    cur.append(word)
                }
            }
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines
    }

    /** Escape special PDF string characters */
    private fun esc(text: String): String =
        text.replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("\r", "")
            .replace("\n", " ")

    /** Format a float without trailing zeros for cleaner PDF output */
    private fun fmt(v: Float): String {
        val s = String.format(Locale.US, "%.2f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    // ──────────────────────────────────────
    //  Extension helpers
    // ──────────────────────────────────────

    private fun StringBuilder.pdfLine(s: String) { append(s); append('\n') }

    private fun ByteArrayOutputStream.putStr(s: String) { write(s.toByteArray(Charsets.ISO_8859_1)) }
}