package com.wardlog.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 842   // A4 landscape, in points
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 32f
    private const val ROW_HEIGHT = 26f

    private val columnWidths = floatArrayOf(80f, 200f, 200f, 350f) // Bed, Name, Consultant, Details
    private val headers = arrayOf("Bed No.", "Patient Name", "Primary Consultant", "Details")

    fun exportToPdf(context: Context, records: List<Record>): File {
        val document = PdfDocument()
        val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true }
        val cellPaint = Paint().apply { textSize = 10f }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun drawHeader() {
            canvas.drawText(
                "WardLog Records — Generated ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}",
                MARGIN, y, titlePaint
            )
            y += 24f
            var x = MARGIN
            for (i in headers.indices) {
                canvas.drawText(headers[i], x + 2f, y, headerPaint)
                x += columnWidths[i]
            }
            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += ROW_HEIGHT - 12f
        }

        drawHeader()

        for (record in records) {
            if (y > PAGE_HEIGHT - MARGIN - ROW_HEIGHT) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                drawHeader()
            }
            var x = MARGIN
            val values = arrayOf(record.bedNumber, record.patientName, record.consultant, record.details)
            for (i in values.indices) {
                val text = truncateToWidth(values[i], columnWidths[i] - 4f, cellPaint)
                canvas.drawText(text, x + 2f, y, cellPaint)
                x += columnWidths[i]
            }
            y += ROW_HEIGHT
            canvas.drawLine(MARGIN, y - ROW_HEIGHT + 8f, PAGE_WIDTH - MARGIN, y - ROW_HEIGHT + 8f, linePaint)
        }

        document.finishPage(page)

        val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(
            exportDir,
            "WardLog_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        )
        document.writeTo(FileOutputStream(file))
        document.close()
        return file
    }

    fun getUriForFile(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun truncateToWidth(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }
}
