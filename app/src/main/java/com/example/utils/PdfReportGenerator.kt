package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Shift
import java.io.File
import java.io.FileOutputStream

object PdfReportGenerator {

    fun generateDailyReportPdf(
        context: Context,
        date: String,
        shifts: List<Shift>,
        presentCount: Int,
        absentCount: Int,
        coverageCount: Int,
        excusedCount: Int,
        isApproved: Boolean
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val rightX = 545f
        val paint = Paint().apply {
            textSize = 13f
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val titlePaint = Paint().apply {
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.rgb(0, 168, 107)
            textAlign = Paint.Align.RIGHT
        }

        val subPaint = Paint().apply {
            textSize = 12f
            color = Color.DKGRAY
            textAlign = Paint.Align.RIGHT
        }

        var y = 55f
        canvas.drawText("فريق إسناد الطبي", rightX, y, titlePaint)
        y += 25f
        canvas.drawText("أعضاء مناوبين كلية الطب في هيئة مستشفى مأرب العام", rightX, y, subPaint)
        y += 35f

        val headerPaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("التقرير اليومي للمناوبات - التاريخ: $date", rightX, y, headerPaint)
        y += 30f

        canvas.drawText("حالة اعتماد اليوم: ${if (isApproved) "معتمد ومنتهي" else "غير معتمد / قيد المراجعة"}", rightX, y, subPaint)
        y += 25f

        canvas.drawText("الإحصائيات: حاضر ($presentCount) | غائب ($absentCount) | تغطية ($coverageCount) | مستأذن ($excusedCount)", rightX, y, subPaint)
        y += 35f

        canvas.drawText("قائمة السجلات والحضور:", rightX, y, Paint().apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        })
        y += 25f

        if (shifts.isEmpty()) {
            canvas.drawText("لا توجد سجلات مسجلة لهذا اليوم.", rightX, y, subPaint)
            y += 20f
        } else {
            for (shift in shifts) {
                if (y > 780f) break
                val line = "• ${shift.memberName} | الفترة: ${shift.period} | المكان: ${shift.serviceLocation} | الحالة: ${shift.status}"
                canvas.drawText(line, rightX, y, paint)
                y += 24f
            }
        }

        pdfDocument.finishPage(page)

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DailyReport_$date.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            Toast.makeText(context, "تم حفظ التقرير PDF بنجاح", Toast.LENGTH_SHORT).show()
            openOrSharePdf(context, file)
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            Toast.makeText(context, "فشل حفظ ملف PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    fun generateWeeklyReportPdf(
        context: Context,
        startDate: String,
        shifts: List<Shift>,
        summaryText: String
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val rightX = 545f
        val paint = Paint().apply { textSize = 12f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val titlePaint = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0, 168, 107); textAlign = Paint.Align.RIGHT }

        var y = 55f
        canvas.drawText("فريق إسناد الطبي - التقرير الأسبوعي", rightX, y, titlePaint)
        y += 25f
        canvas.drawText("يبدأ من تاريخ: $startDate", rightX, y, paint)
        y += 30f
        canvas.drawText(summaryText, rightX, y, paint)
        y += 35f

        canvas.drawText("تفاصيل السجلات خلال الأسبوع:", rightX, y, Paint().apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT })
        y += 25f

        for (shift in shifts.take(28)) {
            if (y > 780f) break
            canvas.drawText("${shift.date} | ${shift.memberName} | ${shift.status} | ${shift.serviceLocation}", rightX, y, paint)
            y += 22f
        }

        pdfDocument.finishPage(page)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "WeeklyReport_$startDate.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            Toast.makeText(context, "تم حفظ التقرير الأسبوعي PDF", Toast.LENGTH_SHORT).show()
            openOrSharePdf(context, file)
            return file
        } catch (e: Exception) {
            pdfDocument.close()
            return null
        }
    }

    fun generateMonthlyReportPdf(
        context: Context,
        monthYear: String,
        shifts: List<Shift>,
        summaryText: String
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val rightX = 545f
        val paint = Paint().apply { textSize = 12f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val titlePaint = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0, 168, 107); textAlign = Paint.Align.RIGHT }

        var y = 55f
        canvas.drawText("فريق إسناد الطبي - التقرير الشهري ($monthYear)", rightX, y, titlePaint)
        y += 30f
        canvas.drawText(summaryText, rightX, y, paint)
        y += 35f

        canvas.drawText("إجمالي السجلات الشهرية:", rightX, y, Paint().apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT })
        y += 25f

        for (shift in shifts.take(28)) {
            if (y > 780f) break
            canvas.drawText("${shift.date} | ${shift.memberName} | ${shift.status} | ${shift.serviceLocation}", rightX, y, paint)
            y += 22f
        }

        pdfDocument.finishPage(page)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MonthlyReport_$monthYear.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            Toast.makeText(context, "تم حفظ التقرير الشهري PDF", Toast.LENGTH_SHORT).show()
            openOrSharePdf(context, file)
            return file
        } catch (e: Exception) {
            pdfDocument.close()
            return null
        }
    }

    private fun openOrSharePdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "تقرير الإسناد الطبي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة أو فتح ملف PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
