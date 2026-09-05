package com.networktoolbox.feature.report.presentation

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Android PDF projection for the same presentation used by the live and
 * restored report screens. It deliberately has no report facts of its own.
 */
internal object DiagnosticReportPdfRenderer {
    const val PDF_MIME_TYPE = "application/pdf"

    fun render(presentation: DiagnosticReportPresentation): ByteArray {
        val document = PdfDocument()
        return try {
            DiagnosticReportPdfLayout.pages(presentation).forEachIndexed { index, lines ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        DiagnosticReportPdfLayout.PAGE_WIDTH_POINTS,
                        DiagnosticReportPdfLayout.PAGE_HEIGHT_POINTS,
                        index + 1,
                    ).create(),
                )
                drawPage(page, lines)
                document.finishPage(page)
            }

            ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    fun fileName(timestamp: Long): String = DiagnosticReportPdfLayout.fileName(timestamp)

    private fun drawPage(page: PdfDocument.Page, lines: List<String>) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val titlePaint = Paint(bodyPaint).apply {
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val sectionPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        page.canvas.drawColor(Color.WHITE)
        var baseline = DiagnosticReportPdfLayout.TOP_MARGIN_POINTS + 16f
        lines.forEachIndexed { index, line ->
            val paint = when {
                index == 0 -> titlePaint
                line in DiagnosticReportPdfLayout.SECTION_TITLES -> sectionPaint
                else -> bodyPaint
            }
            if (line.isNotEmpty()) {
                page.canvas.drawText(line, DiagnosticReportPdfLayout.LEFT_MARGIN_POINTS, baseline, paint)
            }
            baseline += if (index == 0) 24f else DiagnosticReportPdfLayout.LINE_HEIGHT_POINTS
        }
    }
}

/**
 * Pure layout rules kept separate so pagination and filename safety can be
 * tested without requiring a device PDF renderer in JVM tests.
 */
internal object DiagnosticReportPdfLayout {
    const val PAGE_WIDTH_POINTS = 595
    const val PAGE_HEIGHT_POINTS = 842
    const val LEFT_MARGIN_POINTS = 42f
    const val TOP_MARGIN_POINTS = 42f
    const val LINE_HEIGHT_POINTS = 18f
    private const val MAX_CHARACTERS_PER_LINE = 44

    val SECTION_TITLES = setOf(
        "基本信息",
        "诊断结论",
        "检查结果",
        "网络环境提示",
        "发现的问题",
        "详细网络环境",
        "检查详情",
        "分析依据",
        "建议",
        "隐私说明",
    )

    private const val LINES_PER_PAGE = 42

    fun reportLines(presentation: DiagnosticReportPresentation): List<String> =
        DiagnosticReportTextFormatter
            .formatReport(presentation)
            .lineSequence()
            .flatMap { line -> wrap(line) }
            .toList()

    fun pages(presentation: DiagnosticReportPresentation): List<List<String>> =
        reportLines(presentation)
            .chunked(LINES_PER_PAGE)
            .ifEmpty { listOf(listOf("NetworkToolbox 网络诊断完整报告")) }

    fun fileName(timestamp: Long): String = runCatching {
        val formatter = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmm",
            Locale.ROOT,
        ).withZone(ZoneId.systemDefault())
        "NetworkToolbox-Diagnostic-${formatter.format(Instant.ofEpochMilli(timestamp))}.pdf"
    }.getOrDefault("NetworkToolbox-Diagnostic-report.pdf")

    private fun wrap(line: String): List<String> {
        if (line.isEmpty()) return listOf("")
        return line.chunked(MAX_CHARACTERS_PER_LINE)
    }
}
