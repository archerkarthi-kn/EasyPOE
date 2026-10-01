package fyi.copiercode.easypos.printing

import android.content.Context
import android.util.Log
import com.imin.printer.PrinterHelper as IminSDK
import fyi.copiercode.easypos.data.dao.AuditRecord
import fyi.copiercode.easypos.data.entity.OrderEntity
import fyi.copiercode.easypos.data.entity.OrderItemEntity
import fyi.copiercode.easypos.util.BitmapHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PrinterHelper(private val context: Context) {
    private val sdk = IminSDK.getInstance()
    // Target width for 58mm is 384, for 80mm is 576. 
    // We'll use 384 as a safe default or match lineWidth if possible.

    fun init() {
        try {
            Log.d("POS_PRINTER", "Binding iMin Printer Service...")
            sdk.initPrinterService(context)
        } catch (e: Exception) {
            Log.e("POS_PRINTER", "Failed to bind printer service", e)
        }
    }

    fun getPrinterStatus(): Int {
        return try {
            sdk.getPrinterStatus()
        } catch (e: Exception) {
            -1
        }
    }

    suspend fun printCustomReceipt(receiptText: String, logoUri: String? = null) {
        try {
            Log.d("POS_PRINTER", "Step 1: Checking for Logo...")
            logoUri?.let { uriString ->
                val originalBitmap = withContext(Dispatchers.IO) { getBitmapFromUri(uriString) }
                originalBitmap?.let {
                    Log.d("POS_PRINTER", "Step 2: Converting Logo to 1-bit Monochrome...")
                    
                    // Determine target width: 58mm ~ 384px, 80mm ~ 576px
                    // We detect based on typical receipt text length or default to 384
                    val lineLength = receiptText.split("\n").firstOrNull()?.length ?: 0
                    val targetWidth = if (lineLength > 40) 576 else 384
                    
                    val monochromeBitmap = BitmapHelper.convertBitmapToMonochrome(it, targetWidth)
                    
                    Log.d("POS_PRINTER", "Step 2.1: Sending Monochrome Logo to SDK...")
                    sdk.printBitmap(monochromeBitmap, null) 
                    delay(300) // Extra buffer for hardware processing
                    sdk.printAndFeedPaper(10)
                }
            }

            Log.d("POS_PRINTER", "Step 3: Sending Receipt Text payload...")
            sdk.printText(receiptText, null)
            
            Log.d("POS_PRINTER", "Step 4: Finalizing print (Feed & Cut)...")
            sdk.printAndFeedPaper(100)
            sdk.partialCut()
            
            Log.d("POS_PRINTER", "Receipt job successfully handed to iMin SDK")
        } catch (e: Exception) {
            Log.e("POS_PRINTER", "CRITICAL: Print Job Aborted", e)
            throw e
        }
    }

    private fun getBitmapFromUri(uriString: String): android.graphics.Bitmap? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e("POS_PRINTER", "Error decoding bitmap from URI: $uriString", e)
            null
        }
    }

    fun printReceipt(
        shopName: String,
        footerText: String,
        order: OrderEntity,
        items: List<OrderItemEntity>
    ) {
        try {
            Log.d("POS_PRINTER", "Starting blind print for order: ${order.orderId}")
            
            sdk.printTextWithAli(shopName + "\n", 1, null)
            sdk.printText("--------------------------------\n", null)
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdk.printText("Order: ${order.orderId}\n", null)
            sdk.printText("Date: ${sdf.format(Date(order.timestamp))}\n", null)
            sdk.printText("Pay: ${order.paymentMethod}\n", null)
            sdk.printText("--------------------------------\n", null)

            items.forEach { item ->
                sdk.printText("${item.name}\n", null)
                val priceLine = "${item.quantity} x $${String.format("%.2f", item.priceAtSale / item.quantity)}"
                val totalLine = "$${String.format("%.2f", item.priceAtSale)}"
                
                sdk.printColumnsText(
                    arrayOf(priceLine, totalLine),
                    intArrayOf(192, 192), intArrayOf(0, 2), intArrayOf(20, 20), null
                )
            }

            sdk.printText("--------------------------------\n", null)
            sdk.printColumnsText(
                arrayOf("GRAND TOTAL", "$${String.format("%.2f", order.totalAmount)}"),
                intArrayOf(192, 192), intArrayOf(0, 2), intArrayOf(26, 26), null
            )

            if (order.paymentMethod == "CASH") {
                sdk.printText("Given: $${String.format("%.2f", order.amountGiven)}\n", null)
                sdk.printText("Change: $${String.format("%.2f", order.balanceAmount)}\n", null)
            }

            sdk.printTextWithAli("\n$footerText\n", 1, null)
            sdk.printAndFeedPaper(100)
            sdk.partialCut()
            
            Log.d("POS_PRINTER", "Print job sent successfully")
        } catch (e: Exception) {
            Log.e("POS_PRINTER", "Blind print crashed", e)
        }
    }

    fun printAuditReport(
        records: List<AuditRecord>,
        totalRevenue: Double,
        totalUnits: Int,
        filterDesc: String
    ) {
        try {
            sdk.printTextWithAli("AUDIT REPORT\n", 1, null)
            sdk.printTextWithAli(filterDesc + "\n", 1, null)
            sdk.printText("--------------------------------\n", null)

            records.forEach { record ->
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                sdk.printText("$time | ${record.name} | x${record.quantity} | $${String.format("%.2f", record.priceAtSale)}\n", null)
            }

            sdk.printText("--------------------------------\n", null)
            sdk.printText("Total Units: $totalUnits\n", null)
            sdk.printTextWithAli("TOTAL REVENUE: $${String.format("%.2f", totalRevenue)}\n", 1, null)
            
            sdk.printAndFeedPaper(100)
            sdk.partialCut()
        } catch (e: Exception) {
            Log.e("POS_PRINTER", "Audit print failed", e)
        }
    }
}
