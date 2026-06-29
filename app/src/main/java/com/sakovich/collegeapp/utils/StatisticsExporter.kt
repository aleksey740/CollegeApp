package com.sakovich.collegeapp.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sakovich.collegeapp.data.models.Absence
import com.sakovich.collegeapp.data.models.AbsenceReason
import com.sakovich.collegeapp.data.models.Grade
import com.sakovich.collegeapp.data.models.MealSubscription
import com.sakovich.collegeapp.data.models.ScheduleItem
import com.sakovich.collegeapp.data.models.ScheduleType
import com.sakovich.collegeapp.data.models.Student
import com.sakovich.collegeapp.data.models.StudentStatistics
import com.sakovich.collegeapp.data.models.TeacherHourRecord
import com.sakovich.collegeapp.data.models.TeacherHourType
import com.sakovich.collegeapp.data.models.User
import com.sakovich.collegeapp.data.models.getAbsenceReasonDisplayName
import java.text.SimpleDateFormat
import java.util.*

object StatisticsExporter {

    private val COLOR_PRIMARY = Color.parseColor("#2B2B2B")
    private val COLOR_SUCCESS = Color.parseColor("#444444")
    private val COLOR_WARNING = Color.parseColor("#5A5A5A")
    private val COLOR_DANGER = Color.parseColor("#6A6A6A")
    private val COLOR_INFO = Color.parseColor("#505050")
    private val COLOR_DARK = Color.parseColor("#202020")
    private val COLOR_LIGHT_GRAY = Color.parseColor("#F3F3F3")
    private val COLOR_MEDIUM_GRAY = Color.parseColor("#8D8D8D")
    private val COLOR_TEXT = Color.parseColor("#111111")
    private const val PAGE_WIDTH = 595f
    private const val PAGE_HEIGHT = 842f
    private const val LANDSCAPE_PAGE_WIDTH = 842f
    private const val LANDSCAPE_PAGE_HEIGHT = 595f
    private const val MARGIN = 40f
    private const val CONTENT_BOTTOM_MARGIN = 100f
    private const val GRADES_REPORT_FOOTER_LINE_OFFSET = 76f
    private const val GRADES_REPORT_TABLE_HEADER_HEIGHT = 13f
    private const val GRADES_REPORT_TABLE_ROW_HEIGHT = 11f
    private const val GRADES_REPORT_DATES_PER_PAGE = 12
    private const val GRADES_REPORT_CONTENT_PADDING_ABOVE_FOOTER = 2f
    private const val ORG_LINE = "УЧЕБНОЕ ЗАВЕДЕНИЕ - ОФИЦИАЛЬНЫЙ ДОКУМЕНТ"

    private fun maxContentY(pageHeight: Float) = pageHeight - CONTENT_BOTTOM_MARGIN

    private fun isLessonType(type: String?): Boolean =
        type == "ОКР" || type == "ЛР" || type == "ПР"

    private fun formatStudentNameForReport(fullName: String): String {
        val clean = fullName
            .replace(Regex("\\s*\\(Староста\\)\\s*", RegexOption.IGNORE_CASE), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        val parts = clean.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 3 -> "${parts[0]} ${parts[1].first()}. ${parts[2].first()}."
            parts.size == 2 -> "${parts[0]} ${parts[1].first()}."
            else -> clean
        }
    }

    /** Ширина колонки ФИО: компактно для инициалов, расширяется под длинную фамилию. */
    private fun fioColumnWidthForReport(
        names: Collection<String>,
        minWidth: Float = 106f,
        maxWidth: Float = 142f,
        textSize: Float = 9.5f
    ): Float {
        val textPaint = Paint().apply { this.textSize = textSize }
        val padding = 12f
        val longestPx = names.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        return if (longestPx <= 0f) {
            112f
        } else {
            (longestPx + padding).coerceIn(minWidth, maxWidth)
        }
    }

    private fun formatGradeForReport(g: Grade?): String {
        if (g == null) return ""
        val type = g.type.takeIf { isLessonType(it) }
        return when {
            g.isAbsence() -> if (type != null) "$type Н" else "Н"
            type != null -> "$type ${g.value}"
            else -> g.value.toString()
        }
    }

    private fun drawGostFrame(
        canvas: android.graphics.Canvas,
        pageWidth: Float = PAGE_WIDTH,
        pageHeight: Float = PAGE_HEIGHT
    ) {
        val outer = Paint().apply {
            color = COLOR_TEXT
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val inner = Paint().apply {
            color = COLOR_MEDIUM_GRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        canvas.drawRect(20f, 20f, pageWidth - 20f, pageHeight - 20f, outer)
        canvas.drawRect(28f, 28f, pageWidth - 28f, pageHeight - 28f, inner)
    }

    private fun drawServiceFooter(
        canvas: android.graphics.Canvas,
        docCode: String,
        pageWidth: Float = PAGE_WIDTH,
        pageHeight: Float = PAGE_HEIGHT
    ) {
        val linePaint = Paint().apply {
            color = COLOR_MEDIUM_GRAY
            strokeWidth = 0.8f
        }
        val textPaint = Paint().apply {
            color = COLOR_MEDIUM_GRAY
            textSize = 9f
        }
        canvas.drawLine(28f, pageHeight - 76f, pageWidth - 28f, pageHeight - 76f, linePaint)
        canvas.drawText("Код документа: $docCode", 36f, pageHeight - 58f, textPaint)
        canvas.drawText(
            "Сформировано: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}",
            36f,
            pageHeight - 44f,
            textPaint
        )
    }

    private fun drawSignatureStampBlock(
        canvas: android.graphics.Canvas,
        y: Float,
        pageWidth: Float = PAGE_WIDTH,
        pageHeight: Float = PAGE_HEIGHT
    ) {
        val textPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 11f
        }
        val topY = minOf(maxOf(y, 72f), pageHeight - 32f)
        canvas.drawText("М.П.", pageWidth - 64f, topY, textPaint)
    }

    private fun drawHeader(canvas: android.graphics.Canvas, title: String, y: Float, pageWidth: Float): Float {
        val headerPaint = Paint().apply {
            color = COLOR_PRIMARY
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 17f
            isFakeBoldText = true
        }
        val orgPaint = Paint().apply {
            color = Color.parseColor("#DBEAFE")
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        val titleLines = wrapText(title, titlePaint, pageWidth - 56f)
        val orgLines = wrapText(ORG_LINE, orgPaint, (pageWidth - 56f) / 2f)
        val titleLineHeight = 15f
        val headerHeight = 18f + titleLines.size * titleLineHeight + 10f

        canvas.drawRect(0f, y, pageWidth, y + headerHeight, headerPaint)

        var titleY = y + 22f
        titleLines.forEach { line ->
            canvas.drawText(line, 40f, titleY, titlePaint)
            titleY += titleLineHeight
        }
        var orgY = y + 14f
        orgLines.forEach { line ->
            canvas.drawText(line, pageWidth - 40f, orgY, orgPaint)
            orgY += 10f
        }

        return y + headerHeight + 12f
    }

    private fun drawCompactReportHeader(
        canvas: android.graphics.Canvas,
        title: String,
        y: Float,
        pageWidth: Float,
        headerBottomInset: Float = 6f,
        contentGap: Float = 8f
    ): Float {
        val headerPaint = Paint().apply {
            color = COLOR_PRIMARY
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 13f
            isFakeBoldText = true
        }
        val titleLines = wrapText(title, titlePaint, pageWidth - 56f)
        val titleLineHeight = 14f
        val headerHeight = 14f + titleLines.size * titleLineHeight + headerBottomInset
        canvas.drawRect(0f, y, pageWidth, y + headerHeight, headerPaint)
        var titleY = y + 18f
        titleLines.forEach { line ->
            canvas.drawText(line, 40f, titleY, titlePaint)
            titleY += titleLineHeight
        }
        return y + headerHeight + contentGap
    }

    private fun gradesReportContentBottomY(pageHeight: Float) =
        pageHeight - GRADES_REPORT_FOOTER_LINE_OFFSET - GRADES_REPORT_CONTENT_PADDING_ABOVE_FOOTER

    private fun absenceReasonForPdf(reason: AbsenceReason): String = when (reason) {
        AbsenceReason.WITHOUT_REASON -> "Без причины"
        AbsenceReason.SICK -> "Болезнь"
        AbsenceReason.FAMILY -> "Семейные"
        AbsenceReason.OFFICIAL -> "Служебная"
        AbsenceReason.OTHER -> "Другое"
    }

    private fun absencePdfColumnWidths(showStudentName: Boolean, contentWidth: Float): List<Float> {
        val statusW = 46f
        val hoursW = 30f
        val dateW = 54f
        val reasonW = 100f
        val widths = if (showStudentName) {
            val subjectW = 86f
            val studentW = contentWidth - statusW - hoursW - dateW - reasonW - subjectW
            listOf(studentW.coerceAtLeast(96f), subjectW, dateW, hoursW, reasonW, statusW)
        } else {
            val subjectW = contentWidth - statusW - hoursW - dateW - reasonW
            listOf(subjectW.coerceAtLeast(120f), dateW, hoursW, reasonW, statusW)
        }
        val total = widths.sum()
        if (total <= contentWidth) return widths
        val scale = contentWidth / total
        return widths.map { it * scale }
    }

    private fun drawCompactTableHeader(
        canvas: android.graphics.Canvas,
        columns: List<String>,
        x: Float,
        y: Float,
        columnWidths: List<Float>,
        centeredColumns: Set<Int> = emptySet()
    ): Float {
        val headerPaint = Paint().apply {
            color = COLOR_DARK
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = COLOR_TEXT
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 7.5f
            isFakeBoldText = true
        }
        val cellPaddingX = 4f
        val lineHeight = 8f
        val headerHeight = lineHeight + 5f
        var currentX = x
        columns.forEachIndexed { index, text ->
            val width = columnWidths[index]
            canvas.drawRect(currentX, y, currentX + width, y + headerHeight, headerPaint)
            canvas.drawRect(currentX, y, currentX + width, y + headerHeight, borderPaint)
            if (index in centeredColumns) {
                headerTextPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, currentX + width / 2f, y + headerHeight - 2f, headerTextPaint)
                headerTextPaint.textAlign = Paint.Align.LEFT
            } else {
                canvas.drawText(text, currentX + cellPaddingX, y + headerHeight - 2f, headerTextPaint)
            }
            currentX += width
        }
        return y + headerHeight
    }

    private fun drawCompactTableRow(
        canvas: android.graphics.Canvas,
        data: List<String>,
        x: Float,
        y: Float,
        columnWidths: List<Float>,
        rowIndex: Int,
        isAlternate: Boolean = false,
        minRowHeight: Float? = null,
        centeredColumns: Set<Int> = emptySet()
    ): Float {
        val cellPaddingX = 4f
        val lineHeight = 8f
        val borderPaint = Paint().apply {
            color = COLOR_MEDIUM_GRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        val textPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 7.5f
        }
        val preparedLines = data.mapIndexed { index, text ->
            val colWidth = if (index < columnWidths.size) columnWidths[index] else 60f
            wrapText(text, textPaint, colWidth - cellPaddingX * 2)
        }
        var maxLines = 1
        preparedLines.forEach { maxLines = maxOf(maxLines, it.size) }
        val naturalHeight = maxLines * lineHeight + 3f
        val rowHeight = minRowHeight?.let { maxOf(naturalHeight, it) } ?: naturalHeight
        val textBlockHeight = maxLines * lineHeight
        val textTopPad = (rowHeight - textBlockHeight) / 2f
        var currentX = x
        preparedLines.forEachIndexed { index, lines ->
            val width = if (index < columnWidths.size) columnWidths[index] else 60f
            if (isAlternate) {
                val rowPaint = Paint().apply {
                    color = COLOR_LIGHT_GRAY
                    style = Paint.Style.FILL
                }
                canvas.drawRect(currentX, y, currentX + width, y + rowHeight, rowPaint)
            }
            canvas.drawRect(currentX, y, currentX + width, y + rowHeight, borderPaint)
            var lineY = y + textTopPad + textPaint.textSize
            lines.forEach { line ->
                if (index in centeredColumns) {
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(line, currentX + width / 2f, lineY, textPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                } else {
                    canvas.drawText(line, currentX + cellPaddingX, lineY, textPaint)
                }
                lineY += lineHeight
            }
            currentX += width
        }
        return y + rowHeight
    }

    private fun drawStatCard(
        canvas: android.graphics.Canvas,
        label: String,
        value: String,
        cardColor: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {

        val cardPaint = Paint().apply {
            this.color = cardColor
            style = Paint.Style.FILL
        }
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, 12f, 12f, cardPaint)

        val valuePaint = Paint().apply {
            this.color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(value, x + width / 2, y + 40f, valuePaint)

        val labelPaint = Paint().apply {
            this.color = Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, x + width / 2, y + 60f, labelPaint)
    }

    private fun drawTableHeader(
        canvas: android.graphics.Canvas,
        columns: List<String>,
        x: Float,
        y: Float,
        columnWidths: List<Float>
    ): Float {
        val headerPaint = Paint().apply {
            color = COLOR_DARK
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = COLOR_TEXT
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            isFakeBoldText = true
        }

        val cellPaddingX = 5f
        val lineHeight = 11f
        var maxLines = 1
        columns.forEachIndexed { index, text ->
            val lines = wrapText(text, headerTextPaint, columnWidths[index] - cellPaddingX * 2)
            maxLines = maxOf(maxLines, lines.size)
        }
        val headerHeight = maxLines * lineHeight + 10f

        var currentX = x
        columns.forEachIndexed { index, text ->
            val width = columnWidths[index]
            canvas.drawRect(currentX, y, currentX + width, y + headerHeight, headerPaint)
            canvas.drawRect(currentX, y, currentX + width, y + headerHeight, borderPaint)
            val lines = wrapText(text, headerTextPaint, width - cellPaddingX * 2)
            var lineY = y + 8f + headerTextPaint.textSize
            lines.forEach { line ->
                canvas.drawText(line, currentX + cellPaddingX, lineY, headerTextPaint)
                lineY += lineHeight
            }
            currentX += width
        }

        return y + headerHeight
    }

    private fun drawTableRow(
        canvas: android.graphics.Canvas,
        data: List<String>,
        x: Float,
        y: Float,
        columnWidths: List<Float>,
        rowIndex: Int,
        isAlternate: Boolean = false
    ): Float {
        val cellPaddingX = 5f
        val lineHeight = 11f
        val borderPaint = Paint().apply {
            color = COLOR_MEDIUM_GRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }

        val textPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 9.5f
        }

        val preparedLines = data.mapIndexed { index, text ->
            val colWidth = if (index < columnWidths.size) columnWidths[index] else 80f
            wrapText(text, textPaint, colWidth - cellPaddingX * 2)
        }

        var maxLines = 1
        preparedLines.forEach { maxLines = maxOf(maxLines, it.size) }
        val rowHeight = maxLines * lineHeight + 8f

        var currentX = x
        preparedLines.forEachIndexed { index, lines ->
            val width = if (index < columnWidths.size) columnWidths[index] else 80f
            if (isAlternate) {
                val rowPaint = Paint().apply {
                    color = COLOR_LIGHT_GRAY
                    style = Paint.Style.FILL
                }
                canvas.drawRect(currentX, y, currentX + width, y + rowHeight, rowPaint)
            }
            canvas.drawRect(currentX, y, currentX + width, y + rowHeight, borderPaint)
            var lineY = y + 7f + textPaint.textSize
            lines.forEach { line ->
                canvas.drawText(line, currentX + cellPaddingX, lineY, textPaint)
                lineY += lineHeight
            }
            currentX += width
        }

        return y + rowHeight
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank() || maxWidth <= 0f) return listOf("")
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""

        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = if (paint.measureText(word) <= maxWidth) {
                    word
                } else {

                    var remaining = word
                    while (remaining.isNotEmpty()) {
                        var i = remaining.length
                        while (i > 1 && paint.measureText(remaining.substring(0, i)) > maxWidth) i--
                        val part = remaining.substring(0, i)
                        lines.add(part)
                        remaining = remaining.substring(i)
                    }
                    ""
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun getGradeColor(grade: Int): Int {
        if (grade == Grade.VALUE_ABSENCE) return COLOR_MEDIUM_GRAY
        return when {
            grade >= 9 -> COLOR_SUCCESS
            grade >= 7 -> COLOR_PRIMARY
            grade >= 5 -> COLOR_WARNING
            grade >= 3 -> Color.parseColor("#F97316")
            else -> COLOR_DANGER
        }
    }

    private fun gradeDisplayValue(grade: Grade): String =
        if (grade.isAbsence()) "Н" else grade.value.toString()

    fun createAbsencesPdfDocument(
        title: String,
        absences: List<Absence>,
        showStudentName: Boolean,
        totalHours: Int? = null,
        excusedHours: Int? = null,
        unexcusedHours: Int? = null
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "ABS-${System.currentTimeMillis().toString().takeLast(6)}"

        var y = 0f
        val pageWidth = PAGE_WIDTH
        val pageHeight = PAGE_HEIGHT
        val margin = MARGIN
        val contentWidth = pageWidth - margin * 2
        drawGostFrame(canvas, pageWidth, pageHeight)

        y = drawCompactReportHeader(canvas, title, y, pageWidth)

        if (totalHours != null) {
            val summaryPaint = Paint().apply {
                color = COLOR_TEXT
                textSize = 8.5f
            }
            val summary = buildString {
                append("Всего: $totalHours ч.")
                if (excusedHours != null) append("  |  Уважительных: $excusedHours ч.")
                if (unexcusedHours != null) append("  |  Неуважительных: $unexcusedHours ч.")
            }
            canvas.drawText(summary, margin, y + 10f, summaryPaint)
            y += 20f
        }

        val columns = if (showStudentName) {
            listOf("Учащийся", "Предмет", "Дата", "Часы", "Причина", "Статус")
        } else {
            listOf("Предмет", "Дата", "Часы", "Причина", "Статус")
        }

        val columnWidths = absencePdfColumnWidths(showStudentName, contentWidth)
        val statusColumnIndex = columnWidths.lastIndex
        val centeredColumns = setOf(statusColumnIndex)

        y = drawCompactTableHeader(canvas, columns, margin, y, columnWidths, centeredColumns)

        absences.forEachIndexed { index, absence ->
            if (y > maxContentY(pageHeight)) {
                drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas, pageWidth, pageHeight)
                y = drawCompactReportHeader(canvas, title, 0f, pageWidth)
                y = drawCompactTableHeader(canvas, columns, margin, y, columnWidths, centeredColumns)
            }

            val statusText = if (absence.isExcused) "Уваж." else "Неуваж."

            val rowData = if (showStudentName) {
                listOf(
                    formatStudentNameForReport(absence.studentName),
                    absence.subject,
                    absence.date,
                    absence.hours.toString(),
                    absenceReasonForPdf(absence.reason),
                    statusText
                )
            } else {
                listOf(
                    absence.subject,
                    absence.date,
                    absence.hours.toString(),
                    absenceReasonForPdf(absence.reason),
                    statusText
                )
            }

            y = drawCompactTableRow(
                canvas,
                rowData,
                margin,
                y,
                columnWidths,
                index,
                index % 2 == 1,
                centeredColumns = centeredColumns
            )
        }
        drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
        document.finishPage(page)
        return document
    }

    fun exportAbsencesToCsv(context: Context, absences: List<Absence>, fileName: String): Uri? {
        return try {
            val content = StringBuilder()
            content.append("Учащийся,Группа,Предмет,Дата,Часы,Причина,Статус\n")
            absences.forEach { a ->
                val status = if (a.isExcused) "Уваж." else "Н/у"
                content.append("\"${a.studentName}\",\"${a.studentGroup}\",\"${a.subject}\",\"${a.date}\",${a.hours},\"${getAbsenceReasonDisplayName(a.reason)}\",\"$status\"\n")
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toString().toByteArray()) }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createTeacherHoursPdfDocument(
        teacherName: String,
        records: List<TeacherHourRecord>
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "THR-${System.currentTimeMillis().toString().takeLast(6)}"
        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawHeader(canvas, "Учебные и кураторские часы", y, pageWidth)

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }
        canvas.drawText("Преподаватель: $teacherName", margin, y, infoPaint)
        y += 20f
        canvas.drawText("Всего записей: ${records.size}", margin, y, infoPaint)
        y += 24f

        val teachingTotal = records.filter { it.type == TeacherHourType.TEACHING }.sumOf { it.hoursCount }
        val curatorTotal = records.filter { it.type == TeacherHourType.CURATOR }.sumOf { it.hoursCount }
        canvas.drawText("Учебные часы: $teachingTotal", margin, y, infoPaint)
        y += 18f
        canvas.drawText("Кураторские часы: $curatorTotal", margin, y, infoPaint)
        y += 24f

        val columns = listOf("Тип", "Тема", "Группа", "Дата", "Время", "Часы", "Прис.", "Комментарий")
        val columnWidths = listOf(52f, 120f, 55f, 68f, 55f, 38f, 38f, 95f)
        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        records.forEachIndexed { index, r ->
            if (y > 750) {
                drawServiceFooter(canvas, docCode)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas)
                y = drawHeader(canvas, "Учебные и кураторские часы", 0f, pageWidth) + 20f
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }
            val typeStr = if (r.type == TeacherHourType.TEACHING) "Учебн." else "Кур."
            val rowData = listOf(
                typeStr,
                r.topic,
                r.groupName,
                r.date,
                r.time,
                r.hoursCount.toString(),
                r.attendanceCount.toString(),
                r.notes
            )
            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)
        }
        drawSignatureStampBlock(canvas, y)
        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    fun createCuratorialInfoHoursPdfDocument(
        title: String,
        groupName: String,
        teacherName: String,
        hours: List<ScheduleItem>
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "CIH-${System.currentTimeMillis().toString().takeLast(6)}"
        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawHeader(canvas, title, y, pageWidth)

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }
        canvas.drawText("Группа: $groupName", margin, y, infoPaint)
        y += 18f
        canvas.drawText("Ответственный: $teacherName", margin, y, infoPaint)
        y += 24f

        val curatorialCount = hours.count { it.type == ScheduleType.CURATOR_HOUR }
        val infoCount = hours.count { it.type == ScheduleType.INFO_HOUR }
        val cardWidth = 160f
        val cardHeight = 80f
        val spacing = 20f
        val startX = margin
        drawStatCard(canvas, "Всего", hours.size.toString(), COLOR_PRIMARY, startX, y, cardWidth, cardHeight)
        drawStatCard(canvas, "Кураторских", curatorialCount.toString(), Color.parseColor("#8B5CF6"), startX + cardWidth + spacing, y, cardWidth, cardHeight)
        drawStatCard(canvas, "Информ. часов", infoCount.toString(), Color.parseColor("#7C3AED"), startX + (cardWidth + spacing) * 2, y, cardWidth, cardHeight)
        y += cardHeight + 24f

        val columns = listOf("Тип", "Тема", "Дата", "Время", "Аудитория")
        val columnWidths = listOf(95f, 180f, 85f, 95f, 100f)
        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        hours.sortedWith(compareBy<ScheduleItem> { it.date }.thenBy { it.time }).forEachIndexed { index, item ->
            if (y > 750) {
                drawServiceFooter(canvas, docCode)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas)
                y = drawHeader(canvas, title, 0f, pageWidth) + 20f
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }
            val typeStr = if (item.type == ScheduleType.CURATOR_HOUR) "Кураторский" else "Информ."
            val rowData = listOf(
                typeStr,
                item.subject.ifBlank { "—" },
                item.date,
                item.time,
                item.room.ifBlank { "—" }
            )
            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)
        }
        drawSignatureStampBlock(canvas, y)
        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    fun createPdfDocument(
        title: String,
        students: List<StudentStatistics>,
        showGroup: Boolean,
        groupName: String? = null,
        averageGrade: Double? = null,
        totalGrades: Int? = null
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "STS-${System.currentTimeMillis().toString().takeLast(6)}"

        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawCompactReportHeader(canvas, title, y, pageWidth)

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }

        if (averageGrade != null) {
            canvas.drawText("Средний балл: ${String.format("%.2f", averageGrade)}", margin, y, infoPaint)
            y += 18f
        }

        if (totalGrades != null) {
            canvas.drawText("Всего отметок: $totalGrades", margin, y, infoPaint)
            y += 14f
        }

        val columns = if (showGroup) {
            listOf("Группа", "Учащийся", "Средний балл", "Кол-во отметок")
        } else {
            listOf("Учащийся", "Средний балл", "Кол-во отметок")
        }

        val columnWidths = if (showGroup) {
            listOf(100f, 200f, 120f, 100f)
        } else {
            listOf(250f, 150f, 120f)
        }

        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        val pageHeight = PAGE_HEIGHT
        students.forEachIndexed { index, student ->
            if (y > maxContentY(pageHeight)) {
                drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas, pageWidth, pageHeight)
                y = drawCompactReportHeader(canvas, title, 0f, pageWidth)
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }

            val rowData = if (showGroup) {
                listOf(
                    student.groupName,
                    student.studentName,
                    String.format("%.2f", student.averageGrade),
                    student.gradesCount.toString()
                )
            } else {
                listOf(
                    student.studentName,
                    String.format("%.2f", student.averageGrade),
                    student.gradesCount.toString()
                )
            }

            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)
        }
        drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
        document.finishPage(page)
        return document
    }

    fun createStudentPersonalInfoPdfDocument(user: User): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val docCode = "PRS-${System.currentTimeMillis().toString().takeLast(6)}"

        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawHeader(canvas, "Личная информация учащегося", y, pageWidth)

        val labelPaint = Paint().apply {
            color = COLOR_DARK
            textSize = 11f
            isFakeBoldText = true
        }

        val valuePaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }

        fun drawInfoRow(label: String, value: String): Float {
            canvas.drawText(label, margin, y, labelPaint)
            canvas.drawText(value, margin + 150f, y, valuePaint)
            return y + 25f
        }

        y = drawInfoRow("ФИО:", user.fullName)
        y = drawInfoRow("Email:", user.email)
        y = drawInfoRow("Группа:", user.groupName)

        if (user.isHeadman()) {
            val badgePaint = Paint().apply {
                color = COLOR_INFO
                style = Paint.Style.FILL
            }
            val badgeRect = RectF(margin, y - 5f, margin + 120f, y + 20f)
            canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)

            val badgeTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                isFakeBoldText = true
            }
            canvas.drawText("Староста группы", margin + 10f, y + 12f, badgeTextPaint)
            y += 30f
        }

        if (user.birthDate.isNotEmpty()) {
            y = drawInfoRow("Дата рождения:", user.birthDate)
        }
        if (user.phone.isNotEmpty()) {
            y = drawInfoRow("Телефон:", user.phone)
        }
        if (user.address.isNotEmpty()) {
            y = drawInfoRow("Адрес:", user.address)
        }
        if (user.parentName.isNotEmpty()) {
            y = drawInfoRow("Родители:", user.parentName)
        }
        if (user.parentPhone.isNotEmpty()) {
            y = drawInfoRow("Телефон родителей:", user.parentPhone)
        }
        drawSignatureStampBlock(canvas, y)
        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    fun createStudentGradesPdfDocument(
        studentName: String,
        groupName: String,
        grades: List<Grade>,
        averageGrade: Double
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "GRD-${System.currentTimeMillis().toString().takeLast(6)}"

        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawHeader(canvas, "Отметки учащегося", y, pageWidth)

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }
        canvas.drawText("Учащийся: $studentName", margin, y, infoPaint)
        y += 20f
        canvas.drawText("Группа: $groupName", margin, y, infoPaint)
        y += 20f

        drawStatCard(canvas, "Средний балл", String.format("%.2f", averageGrade), COLOR_PRIMARY, margin, y, 180f, 70f)
        y += 90f

        val columns = listOf("Предмет", "Отметка", "Тип", "Дата", "Преподаватель")
        val columnWidths = listOf(150f, 70f, 100f, 90f, 150f)

        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        grades.forEachIndexed { index, grade ->
            if (y > 750) {
                drawServiceFooter(canvas, docCode)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas)
                y = drawHeader(canvas, "Отметки учащегося", 0f, pageWidth) + 20f
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }

            val gradeColor = getGradeColor(grade.value)
            val valueStr = if (grade.isAbsence()) "Н" else "${grade.value}/10"
            val rowData = listOf(
                grade.subject,
                valueStr,
                grade.typeDisplayLabel(),
                grade.date,
                grade.teacherName
            )

            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)
        }
        drawSignatureStampBlock(canvas, y)
        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    fun createMyGradesPdfDocument(
        grades: List<Grade>,
        averageGrade: Double
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "MGR-${System.currentTimeMillis().toString().takeLast(6)}"

        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawHeader(canvas, "Мои отметки", y, pageWidth)

        val cardWidth = 180f
        val cardHeight = 80f
        val spacing = 20f

        drawStatCard(canvas, "Средний балл", String.format("%.2f", averageGrade), COLOR_PRIMARY, margin, y, cardWidth, cardHeight)
        drawStatCard(canvas, "Всего отметок", grades.size.toString(), COLOR_INFO, margin + cardWidth + spacing, y, cardWidth, cardHeight)
        y += cardHeight + 30f

        val columns = listOf("Предмет", "Отметка", "Дата")
        val columnWidths = listOf(280f, 70f, 110f)

        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        grades.forEachIndexed { index, grade ->
            if (y > 750) {
                drawServiceFooter(canvas, docCode)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas)
                y = drawHeader(canvas, "Мои отметки", 0f, pageWidth) + 20f
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }

            val rowData = listOf(
                grade.subject,
                gradeDisplayValue(grade),
                grade.date
            )

            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)

            if (!grade.comment.isNullOrEmpty()) {
                val commentPaint = Paint().apply {
                    this.color = COLOR_MEDIUM_GRAY
                    textSize = 9f
                }
                canvas.drawText("💬 ${grade.comment}", margin + 20f, y + 15f, commentPaint)
                y += 20f
            }
        }
        drawSignatureStampBlock(canvas, y)
        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    fun createGroupStudentsPdfDocument(
        groupName: String,
        students: List<Student>,
        headmanName: String? = null
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "GST-${System.currentTimeMillis().toString().takeLast(6)}"

        var y = 0f
        val pageWidth = 595f
        val margin = 40f
        drawGostFrame(canvas)

        y = drawHeader(canvas, "Список учащихся группы", y, pageWidth)

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }
        canvas.drawText("Группа: $groupName", margin, y, infoPaint)
        y += 20f

        if (headmanName != null) {
            val badgePaint = Paint().apply {
                color = COLOR_INFO
                style = Paint.Style.FILL
            }
            val badgeRect = RectF(margin, y - 5f, margin + 200f, y + 20f)
            canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)

            val badgeTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 11f
                isFakeBoldText = true
            }
            canvas.drawText("Староста: $headmanName", margin + 10f, y + 12f, badgeTextPaint)
            y += 30f
        }

        drawStatCard(canvas, "Всего учащихся", students.size.toString(), COLOR_PRIMARY, margin, y, 200f, 70f)
        y += 90f

        val columns = listOf("№", "ФИО", "Статус")
        val columnWidths = listOf(40f, 400f, 100f)

        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        students.forEachIndexed { index, student ->
            if (y > 750) {
                drawServiceFooter(canvas, docCode)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas)
                y = drawHeader(canvas, "Список учащихся группы", 0f, pageWidth) + 20f
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }

            val status = if (student.isHeadman) "Староста" else "Учащийся"
            val rowData = listOf(
                "${index + 1}",
                student.fullName,
                status
            )

            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)
        }
        drawSignatureStampBlock(canvas, y)
        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    fun createTeacherGradesReportPdfDocument(
        title: String,
        groupName: String,
        periodLabel: String,
        subjectLabel: String,
        grades: List<Grade>,
        allGradesForFinal: List<Grade>,
        averageGrade: Double,
        studentsCount: Int,
        subjectsCount: Int,
        teacherName: String
    ): PdfDocument {
        val document = PdfDocument()
        var pageNumber = 1
        val pageWidth = LANDSCAPE_PAGE_WIDTH
        val pageHeight = LANDSCAPE_PAGE_HEIGHT
        val margin = 28f
        val docCode = "JRN-${System.currentTimeMillis().toString().takeLast(6)}"

        fun startPage(): Pair<PdfDocument.Page, android.graphics.Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth.toInt(),
                pageHeight.toInt(),
                pageNumber++
            ).create()
            val newPage = document.startPage(pageInfo)
            val newCanvas = newPage.canvas
            drawGostFrame(newCanvas, pageWidth, pageHeight)
            return newPage to newCanvas
        }

        fun finishPageWithFooter(page: PdfDocument.Page, canvas: android.graphics.Canvas) {
            drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
            document.finishPage(page)
        }

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 9f
        }
        val contentBottomY = gradesReportContentBottomY(pageHeight)

        var (page, canvas) = startPage()
        var y = 0f

        fun drawInfoBlock(startY: Float): Float {
            var infoY = startY
            canvas.drawText("Период: $periodLabel", margin, infoY, infoPaint)
            infoY += 10f
            canvas.drawText("Предмет: $subjectLabel", margin, infoY, infoPaint)
            return infoY + 8f
        }

        fun startNewPage(withInfo: Boolean = true): Float {
            finishPageWithFooter(page, canvas)
            val next = startPage()
            page = next.first
            canvas = next.second
            var newY = drawCompactReportHeader(canvas, title, 0f, pageWidth)
            if (withInfo) {
                newY = drawInfoBlock(newY)
            }
            return newY
        }

        y = drawCompactReportHeader(canvas, title, 0f, pageWidth)
        y = drawInfoBlock(y)

        val groupedBySubject = grades.groupBy { it.subject }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

        groupedBySubject.forEach { (subject, subjectGrades) ->
            val allDates = subjectGrades
                .map { it.date }
                .distinct()
                .sortedWith(compareBy({ parseGradeDate(it)?.time ?: Long.MAX_VALUE }, { it }))

            val studentsById = subjectGrades
                .groupBy { it.studentId }
                .mapValues { (_, list) -> list.maxByOrNull { it.createdAt }?.studentName ?: "" }
                .toList()
                .sortedBy { it.second.lowercase(Locale.getDefault()) }

            val formattedStudentNames = studentsById.map { formatStudentNameForReport(it.second) }
            val fioColWidth = fioColumnWidthForReport(
                formattedStudentNames,
                minWidth = 120f,
                maxWidth = 168f,
                textSize = 7.5f
            )
            val dateChunks = allDates.chunked(GRADES_REPORT_DATES_PER_PAGE).ifEmpty { listOf(emptyList()) }
            val byCell = subjectGrades
                .groupBy { "${it.studentId}|${it.date}" }
                .mapValues { (_, values) -> values.maxByOrNull { it.createdAt } }
            val allByStudentForSubject = allGradesForFinal
                .filter { it.subject.equals(subject, ignoreCase = true) }
                .groupBy { it.studentId }

            val tableHeightEstimate = GRADES_REPORT_TABLE_HEADER_HEIGHT +
                studentsById.size * GRADES_REPORT_TABLE_ROW_HEIGHT + 4f

            dateChunks.forEachIndexed { chunkIndex, dateChunk ->
                if (chunkIndex > 0 || y + tableHeightEstimate > contentBottomY) {
                    y = startNewPage()
                }

                val columns = mutableListOf("№", "ФИО")
                columns.addAll(dateChunk.map { if (it.length >= 5) it.substring(0, 5) else it })
                columns.add("Сем.")
                columns.add("Итог")

                val numColWidth = 18f
                val summaryColWidth = 28f
                val fixedWidth = numColWidth + fioColWidth + summaryColWidth + summaryColWidth
                val dateColWidth = if (dateChunk.isEmpty()) {
                    32f
                } else {
                    ((pageWidth - margin * 2 - fixedWidth) / dateChunk.size).coerceIn(28f, 48f)
                }
                val columnWidths = mutableListOf(numColWidth, fioColWidth)
                repeat(dateChunk.size) { columnWidths.add(dateColWidth) }
                columnWidths.add(summaryColWidth)
                columnWidths.add(summaryColWidth)

                y = drawCompactTableHeader(canvas, columns, margin, y, columnWidths)

                studentsById.forEachIndexed { rowIndex, pair ->
                    val studentId = pair.first
                    val studentName = formatStudentNameForReport(pair.second)
                    val row = mutableListOf(
                        (rowIndex + 1).toString(),
                        studentName
                    )
                    dateChunk.forEach { date ->
                        val g = byCell["$studentId|$date"]
                        row.add(formatGradeForReport(g))
                    }
                    val semesterValues = dateChunk.mapNotNull { date ->
                        byCell["$studentId|$date"]?.value
                    }.filter { it != Grade.VALUE_ABSENCE }
                    val sem = if (semesterValues.isNotEmpty()) {
                        String.format("%.0f", semesterValues.average())
                    } else {
                        ""
                    }
                    val fin = GradeJournalAverage.formatFinalGradeDisplay(
                        GradeJournalAverage.calculateFinalAverage(
                            allByStudentForSubject[studentId].orEmpty()
                        )
                    )
                    row.add(sem)
                    row.add(fin)

                    y = drawCompactTableRow(
                        canvas, row, margin, y, columnWidths, rowIndex, rowIndex % 2 == 1
                    )
                }
            }
        }

        drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
        document.finishPage(page)
        return document
    }

    fun createTeacherJournalPdfDocument(
        groupName: String,
        subject: String,
        weekLabel: String,
        students: List<Student>,
        weekDates: List<String>,
        gradesByCell: Map<String, Int?>
    ): PdfDocument {
        val document = PdfDocument()
        var pageNumber = 1
        val pageWidth = LANDSCAPE_PAGE_WIDTH
        val pageHeight = LANDSCAPE_PAGE_HEIGHT
        val margin = 28f
        val docCode = "JRN-W-${System.currentTimeMillis().toString().takeLast(6)}"

        fun startPage(): Pair<PdfDocument.Page, android.graphics.Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth.toInt(),
                pageHeight.toInt(),
                pageNumber++
            ).create()
            val newPage = document.startPage(pageInfo)
            val newCanvas = newPage.canvas
            drawGostFrame(newCanvas, pageWidth, pageHeight)
            return newPage to newCanvas
        }

        var (page, canvas) = startPage()
        var y = drawHeader(canvas, "Журнал отметок", 0f, pageWidth)

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 11f
        }
        canvas.drawText(weekLabel, margin, y, infoPaint)
        y += 14f

        val columns = mutableListOf("Учащийся")
        columns.addAll(weekDates)
        val formattedNames = students.map { formatStudentNameForReport(it.fullName) }
        val nameColWidth = fioColumnWidthForReport(formattedNames, maxWidth = 155f)
        val dateColWidth = if (weekDates.isEmpty()) {
            60f
        } else {
            ((pageWidth - margin * 2 - nameColWidth) / weekDates.size).coerceIn(48f, 72f)
        }
        val columnWidths = mutableListOf(nameColWidth)
        repeat(weekDates.size) { columnWidths.add(dateColWidth) }

        y = drawTableHeader(canvas, columns, margin, y, columnWidths)

        students.forEachIndexed { index, student ->
            if (y > maxContentY(pageHeight)) {
                drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
                document.finishPage(page)
                val next = startPage()
                page = next.first
                canvas = next.second
                y = drawHeader(canvas, "Журнал отметок", 0f, pageWidth) + 8f
                y = drawTableHeader(canvas, columns, margin, y, columnWidths)
            }

            val rowData = mutableListOf(formatStudentNameForReport(student.fullName))
            weekDates.forEach { date ->
                val key = "${student.id}|$date"
                val value = gradesByCell[key]
                rowData.add(value?.toString() ?: "—")
            }
            y = drawTableRow(canvas, rowData, margin, y, columnWidths, index, index % 2 == 1)
        }

        drawServiceFooter(canvas, docCode, pageWidth, pageHeight)
        document.finishPage(page)
        return document
    }

    fun exportTeacherJournalToCsv(
        context: Context,
        fileName: String,
        students: List<Student>,
        weekDates: List<String>,
        gradesByCell: Map<String, Int?>
    ): Uri? {
        return try {
            val content = StringBuilder()
            content.append("ФИО,")
            content.append(weekDates.joinToString(","))
            content.append("\n")

            students.forEach { student ->
                content.append("\"${student.fullName}\",")
                val values = weekDates.map { date ->
                    val key = "${student.id}|$date"
                    (gradesByCell[key]?.toString() ?: "")
                }
                content.append(values.joinToString(","))
                content.append("\n")
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toString().toByteArray())
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun savePdfToUri(context: Context, document: PdfDocument, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                document.writeTo(outputStream)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            document.close()
        }
    }

    fun exportGradesToCsv(context: Context, grades: List<Grade>, fileName: String): Uri? {
        return try {
            val content = StringBuilder()
            content.append("Предмет,Отметка,Тип,Дата,Преподаватель\n")

            grades.forEach { grade ->
                content.append("${grade.subject},")
                content.append(if (grade.isAbsence()) "Н" else "${grade.value}")
                content.append(",")
                content.append("${grade.typeDisplayLabel()},")
                content.append("${grade.date},")
                content.append("${grade.teacherName}\n")
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toString().toByteArray())
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportTeacherGradesReportToCsv(
        context: Context,
        fileName: String,
        groupName: String,
        periodLabel: String,
        subjectLabel: String,
        grades: List<Grade>,
        allGradesForFinal: List<Grade>
    ): Uri? {
        return try {
            val content = StringBuilder()
            content.append("Группа,$groupName\n")
            content.append("Период,$periodLabel\n")
            content.append("Предметы,$subjectLabel\n\n")

            val groupedBySubject = grades.groupBy { it.subject }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)

            groupedBySubject.forEach { (subject, subjectGrades) ->
                content.append("Предмет,\"$subject\"\n")
                val allDates = subjectGrades
                    .map { it.date }
                    .distinct()
                    .sortedWith(compareBy({ parseGradeDate(it)?.time ?: Long.MAX_VALUE }, { it }))

                content.append("№,ФИО,Предмет")
                allDates.forEach { d -> content.append(",\"$d\"") }
                content.append(",Сем.,Итог")
                content.append("\n")

                val studentsById = subjectGrades
                    .groupBy { it.studentId }
                    .mapValues { (_, list) -> list.maxByOrNull { it.createdAt }?.studentName ?: "" }
                    .toList()
                    .sortedBy { it.second.lowercase(Locale.getDefault()) }

                val byCell = subjectGrades
                    .groupBy { "${it.studentId}|${it.date}" }
                    .mapValues { (_, values) -> values.maxByOrNull { it.createdAt } }
                val allByStudentForSubject = allGradesForFinal
                    .filter { it.subject.equals(subject, ignoreCase = true) }
                    .groupBy { it.studentId }

                studentsById.forEachIndexed { index, pair ->
                    val studentId = pair.first
                    val studentName = pair.second
                    content.append("${index + 1},\"$studentName\",\"$subject\"")
                    val semesterValues = mutableListOf<Int>()
                    allDates.forEach { date ->
                        val g = byCell["$studentId|$date"]
                        val value = g?.value
                        if (value != null && value != Grade.VALUE_ABSENCE) semesterValues.add(value)
                        content.append("," + formatGradeForReport(g))
                    }
                    val sem = if (semesterValues.isNotEmpty()) {
                        String.format("%.0f", semesterValues.average())
                    } else ""
                    val fin = GradeJournalAverage.formatFinalGradeDisplay(
                        GradeJournalAverage.calculateFinalAverage(
                            allByStudentForSubject[studentId].orEmpty()
                        )
                    )
                    content.append(",$sem,$fin")
                    content.append("\n")
                }
                content.append("\n")
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toString().toByteArray())
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createNutritionPdfDocument(
        groupNameOrFilter: String,
        selectedDate: String,
        subscribed: List<MealSubscription>,
        eligibleTotal: Int,
        responsibleName: String
    ): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val docCode = "NTR-${System.currentTimeMillis().toString().takeLast(6)}"
        drawGostFrame(canvas)

        var y = 0f
        y = drawCompactReportHeader(
            canvas,
            "Ведомость питания",
            y,
            PAGE_WIDTH,
            headerBottomInset = 2f,
            contentGap = 14f
        )

        val infoPaint = Paint().apply {
            color = COLOR_TEXT
            textSize = 12f
        }
        canvas.drawText("Дата: $selectedDate", MARGIN, y, infoPaint)
        y += 18f
        canvas.drawText("Группа: $groupNameOrFilter", MARGIN, y, infoPaint)
        y += 18f
        canvas.drawText("Записано: ${subscribed.size} из $eligibleTotal", MARGIN, y, infoPaint)
        y += 18f
        canvas.drawText("Ответственный: $responsibleName", MARGIN, y, infoPaint)
        y += 20f

        val tableWidth = PAGE_WIDTH - MARGIN * 2f
        val numColWidth = 40f
        val fioColWidth = tableWidth - numColWidth
        val columns = listOf("№", "ФИО")
        val widths = listOf(numColWidth, fioColWidth)
        y = drawTableHeader(canvas, columns, MARGIN, y, widths)

        subscribed.forEachIndexed { index, item ->
            if (y > 740f) {
                drawServiceFooter(canvas, docCode)
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawGostFrame(canvas)
                y = drawCompactReportHeader(
                    canvas,
                    "Ведомость питания",
                    0f,
                    PAGE_WIDTH,
                    headerBottomInset = 2f,
                    contentGap = 14f
                ) + 12f
                y = drawTableHeader(canvas, columns, MARGIN, y, widths)
            }
            y = drawTableRow(
                canvas = canvas,
                data = listOf((index + 1).toString(), item.userName),
                x = MARGIN,
                y = y,
                columnWidths = widths,
                rowIndex = index,
                isAlternate = index % 2 == 1
            )
        }

        drawServiceFooter(canvas, docCode)
        document.finishPage(page)
        return document
    }

    private fun parseGradeDate(value: String): Date? {
        return try {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(value)
        } catch (_: Exception) {
            null
        }
    }

    fun exportToText(
        context: Context,
        title: String,
        students: List<StudentStatistics>,
        showGroup: Boolean
    ): Boolean {
        return try {
            val content = StringBuilder()
            content.append("$title\n")
            content.append("=".repeat(50))
            content.append("\n\n")

            students.forEach { student ->
                if (showGroup && student.groupName.isNotEmpty()) {
                    content.append("${student.groupName} - ")
                }
                content.append("${student.studentName}: ")
                content.append("${String.format("%.2f", student.averageGrade)} ")
                content.append("(${student.gradesCount} отметок)\n")
            }

            val fileName = "statistics_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents")
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toString().toByteArray())
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
