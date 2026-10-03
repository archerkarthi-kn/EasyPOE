package fyi.copiercode.easypos.util

import android.graphics.*
import java.text.SimpleDateFormat
import java.util.*

object BitmapHelper {

    /**
     * Converts a bitmap to a 1-bit monochrome bitmap (pure Black and White)
     * optimized for thermal printers.
     */
    fun convertBitmapToMonochrome(bitmap: Bitmap, targetWidth: Int = 384): Bitmap {
        // 1. Scale while maintaining aspect ratio
        val ratio = targetWidth.toFloat() / bitmap.width
        val targetHeight = (bitmap.height * ratio).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        // 2. Create a new bitmap for the monochrome result
        val monoBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(monoBitmap)
        val paint = Paint()

        // 3. Convert to Grayscale
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

        // 4. Advanced Thresholding (Luminance)
        val pixels = IntArray(targetWidth * targetHeight)
        monoBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = Color.alpha(color)
            if (alpha < 10) {
                pixels[i] = Color.WHITE // Transparent -> White
                continue
            }
            
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            
            // Luminance formula
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            // Using a higher threshold (180) to ensure colors like red/green print as black
            pixels[i] = if (luminance < 180) Color.BLACK else Color.WHITE
        }

        monoBitmap.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return monoBitmap
    }

    /**
     * Renders a multi-line string into a monochrome bitmap with custom font support.
     */
    fun renderTextToMonochromeBitmap(
        text: String,
        lineWidth: Int,
        fontSize: Float = 24f,
        isBold: Boolean = false,
        isCenter: Boolean = false
    ): Bitmap {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = fontSize
            isAntiAlias = false // Best for thermal
            typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

        val lines = text.split("\n")
        val textHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top) * 1.2f
        val totalHeight = (lines.size * textHeight).toInt() + 20

        val bitmap = Bitmap.createBitmap(lineWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = -paint.fontMetrics.top + 10
        lines.forEach { line ->
            val x = if (isCenter) (lineWidth - paint.measureText(line)) / 2f else 0f
            canvas.drawText(line, x, y, paint)
            y += textHeight
        }

        return convertBitmapToMonochrome(bitmap, lineWidth)
    }

    /**
     * Advanced Canvas-based rendering matching the high-fidelity professional template.
     */
    fun drawFancyReceipt(
        lineWidth: Int = 576,
        logo: Bitmap?,
        shopName: String,
        billNo: String,
        tokenNumber: String? = null,
        slipTitle: String? = null,
        items: List<ReceiptBuilder.ReceiptItem>,
        total: Double,
        paymentMethod: String,
        amountReceived: Double = 0.0,
        change: Double = 0.0,
        footerContact: ReceiptBuilder.FooterContact
    ): Bitmap {
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        // 1. Setup - Using a very large buffer height to prevent any clipping
        val now = Date()
        val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        
        val totalHeight = 4000 // Large safety buffer for multi-item receipts
        val bitmap = Bitmap.createBitmap(lineWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 15f // Minimal top padding
        val centerX = lineWidth / 2f
        val margin = 50f 

        // --- TOKEN NUMBER (IF ENABLED) ---
        tokenNumber?.let { token ->
            paint.textSize = 42f
            paint.isFakeBoldText = true
            val tokenText = "TOKEN #$token"
            val tokenWidth = paint.measureText(tokenText)
            canvas.drawText(tokenText, centerX - (tokenWidth / 2f), y + 30f, paint)
            y += 45f
            
            paint.strokeWidth = 2f
            canvas.drawLine(margin, y, lineWidth - margin, y, paint)
            y += 15f
        }

        // --- TOP INFO: Bill No (Left) & Thank You (Right) ---
        paint.textSize = 21f // Slightly smaller
        paint.isFakeBoldText = false
        val labelWidth = paint.measureText("Bill No : ")
        canvas.drawText("Bill No : ", margin, y + 15f, paint)
        
        paint.textSize = 32f // Compacted header number
        paint.isFakeBoldText = true
        canvas.drawText(billNo, margin + labelWidth, y + 15f, paint)
        
        paint.isFakeBoldText = false
        paint.textSize = 20f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Thank You", lineWidth - margin, y, paint)
        canvas.drawText("Visit Again!", lineWidth - margin, y + 24f, paint)
        paint.textAlign = Paint.Align.LEFT
        
        y += 50f // Reduced top section gap

        // --- MAIN HEADER: Logo & Shop Name ---
        logo?.let {
            val scaledLogo = convertBitmapToMonochrome(it, 160) 
            canvas.drawBitmap(scaledLogo, centerX - (scaledLogo.width / 2f), y, null)
            y += scaledLogo.height + 12f
        }

        // DYNAMIC FONT SHRINKING for Shop Name
        paint.textSize = 48f // Slightly smaller base
        val maxHeaderWidth = lineWidth - (margin * 2) - 5f
        while (paint.measureText(shopName.uppercase()) > maxHeaderWidth && paint.textSize > 20f) {
            paint.textSize -= 2f
        }
        
        paint.isFakeBoldText = true
        paint.strokeWidth = 2.5f // Cleaner stroke
        paint.style = Paint.Style.FILL_AND_STROKE
        val nameWidth = paint.measureText(shopName.uppercase())
        canvas.drawText(shopName.uppercase(), centerX - (nameWidth / 2), y + 25f, paint)
        paint.style = Paint.Style.FILL 
        paint.strokeWidth = 0f
        y += 55f // Compacted shop name gap

        paint.textSize = 20f 
        paint.isFakeBoldText = true
        val subHeader = "BRIYANI KING"
        canvas.drawText(subHeader, centerX - (paint.measureText(subHeader) / 2), y, paint)
        y += 35f

        // Decorative ornament line
        paint.strokeWidth = 2f
        canvas.drawLine(centerX - 100f, y - 10f, centerX + 100f, y - 10f, paint)
        y += 20f 

        // Optional Slip Title Header (e.g. *** CUSTOMER RECEIPT *** or *** CASHIER COPY ***)
        slipTitle?.let { title ->
            paint.textSize = 24f
            paint.isFakeBoldText = true
            val titleText = "*** $title ***"
            canvas.drawText(titleText, centerX - (paint.measureText(titleText) / 2f), y, paint)
            y += 32f
        }

        // --- METADATA GRID ---
        paint.textSize = 20f // Standard compact metadata size
        val col2X = centerX + 15f
        
        // Row 1: Date & Time
        canvas.drawText("📅 Date   : $dateStr", margin, y, paint)
        canvas.drawText("🕒 Time   : $timeStr", col2X, y, paint)
        y += 30f
        // Row 2: Bill No & Table
        canvas.drawText("🗒 Bill No : $billNo", margin, y, paint)
        canvas.drawText("🪑 Table  : --", col2X, y, paint)
        y += 30f
        // Row 3: Cashier & Payment
        canvas.drawText("👤 Cashier : Manager", margin, y, paint)
        canvas.drawText("💳 Payment : $paymentMethod", col2X, y, paint)
        y += 40f // Compacted metadata block

        // --- ITEM TABLE HEADER ---
        paint.strokeWidth = 2f
        canvas.drawLine(margin, y, lineWidth - margin, y, paint)
        y += 28f
        
        paint.isFakeBoldText = true
        paint.textSize = 19f
        val snoX = margin
        val itemX = 100f 
        val qtyX = 310f  
        val rateX = 420f 
        val amountX = lineWidth - margin

        canvas.drawText("S.NO.", snoX, y, paint)
        canvas.drawText("ITEM NAME", itemX, y, paint)
        canvas.drawText("QTY", qtyX, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", rateX - 10f, y, paint) 
        canvas.drawText("AMOUNT", amountX, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += 10f
        canvas.drawLine(margin, y, lineWidth - margin, y, paint)
        y += 32f 

        // --- ITEMS ---
        paint.isFakeBoldText = false
        paint.textSize = 20f
        var itemIndex = 1
        items.forEach { item ->
            if (item.quantity == 0) {
                // Category Header Row
                y += 4f
                paint.isFakeBoldText = true
                paint.textSize = 21f
                canvas.drawText(item.name, snoX, y, paint)
                paint.isFakeBoldText = false
                paint.textSize = 20f
                y += 10f
                paint.strokeWidth = 2f
                canvas.drawLine(margin, y, lineWidth - margin, y, paint)
                y += 28f
            } else {
                val rate = item.price / item.quantity
                canvas.drawText(itemIndex.toString(), snoX + 10f, y, paint)
                itemIndex++
                
                val maxNameWidth = qtyX - itemX - 10f
                var name = item.name
                if (paint.measureText(name) > maxNameWidth) {
                    var truncated = name
                    while (paint.measureText("$truncated..") > maxNameWidth && truncated.length > 2) {
                        truncated = truncated.substring(0, truncated.length - 1)
                    }
                    name = "$truncated.."
                }
                canvas.drawText(name, itemX, y, paint)
                canvas.drawText(item.quantity.toString(), qtyX + 5f, y, paint)
                
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(String.format(Locale.US, "%.2f", rate), rateX - 10f, y, paint)
                canvas.drawText(String.format(Locale.US, "%.2f", item.price), amountX, y, paint)
                paint.textAlign = Paint.Align.LEFT
                
                y += 10f
                paint.strokeWidth = 1f
                paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                canvas.drawLine(margin, y, lineWidth - margin, y, paint)
                paint.pathEffect = null
                y += 30f // Tightened item spacing
            }
        }

        // --- SUMMARY ---
        y += 5f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SUBTOTAL :", rateX - 10f, y, paint)
        canvas.drawText(String.format(Locale.US, "%.2f", total), amountX, y, paint)
        y += 38f
        
        paint.textSize = 29f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL (SGD) :", rateX - 10f, y, paint)
        canvas.drawText(String.format(Locale.US, "%.2f", total), amountX, y, paint)
        
        if (paymentMethod == "CASH" || paymentMethod == "DIRECT") {
            y += 35f
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText("Received :", rateX - 10f, y, paint)
            canvas.drawText(String.format(Locale.US, "%.2f", amountReceived), amountX, y, paint)
            
            y += 28f
            canvas.drawText("Change :", rateX - 10f, y, paint)
            canvas.drawText(String.format(Locale.US, "%.2f", change), amountX, y, paint)
        }
        
        paint.textAlign = Paint.Align.LEFT
        y += 28f
        canvas.drawLine(margin, y, lineWidth - margin, y, paint)
        y += 40f 

        // --- FOOTER ---
        paint.textSize = 26f 
        paint.isFakeBoldText = true
        canvas.drawText("CONTACT US", margin, y, paint)
        canvas.drawText("OUR LOCATIONS", col2X, y, paint)
        y += 40f
        
        val maxColWidth = (lineWidth / 2f) - margin - 15f
        
        // ROW 1: Phone & Loc 1
        var currentRowMaxY = y
        
        paint.textSize = 22f 
        paint.isFakeBoldText = true
        if (footerContact.tel.isNotBlank()) {
            canvas.drawText("📞 ${footerContact.tel}", margin, y, paint)
            currentRowMaxY = (y + 22f).coerceAtLeast(currentRowMaxY)
        }
        
        paint.textSize = 16f
        paint.isFakeBoldText = false
        if (footerContact.loc1.isNotBlank()) {
            val lines = wrapText("📍 Loc 1: ${footerContact.loc1}", paint, maxColWidth)
            var locY = y
            lines.forEach { line ->
                canvas.drawText(line, col2X, locY, paint)
                locY += 18f
            }
            currentRowMaxY = locY.coerceAtLeast(currentRowMaxY)
        }
        
        y = currentRowMaxY + 22f 
        
        // ROW 2: Email & Loc 2
        var nextRowMaxY = y
        
        paint.textSize = 14f 
        if (footerContact.email.isNotBlank()) {
            val emailLines = wrapText("✉ ${footerContact.email}", paint, maxColWidth)
            var ey = y
            emailLines.forEach { line ->
                canvas.drawText(line, margin, ey, paint)
                ey += 17f
            }
            nextRowMaxY = ey.coerceAtLeast(nextRowMaxY)
        }
        
        paint.textSize = 16f
        if (footerContact.loc2.isNotBlank()) {
            val lines = wrapText("📍 Loc 2: ${footerContact.loc2}", paint, maxColWidth)
            var l2y = y
            lines.forEach { line ->
                canvas.drawText(line, col2X, l2y, paint)
                l2y += 18f
            }
            nextRowMaxY = l2y.coerceAtLeast(nextRowMaxY)
        }
        
        y = nextRowMaxY + 40f

        // THANK YOU MESSAGE
        paint.textSize = 21f
        paint.isFakeBoldText = true
        val msg = "Thank you for choosing $shopName!"
        canvas.drawText(msg, centerX - (paint.measureText(msg) / 2), y, paint)
        
        // Final trim with minimal padding (40px)
        val finalHeight = (y + 40f).toInt()
        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, lineWidth, finalHeight.coerceAtMost(totalHeight))
        
        return convertBitmapToMonochrome(finalBitmap, lineWidth)
    }

    /**
     * Helper to wrap text into multiple lines based on a maximum width.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (paint.measureText(currentLine.toString() + word) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) "" else " ").append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
