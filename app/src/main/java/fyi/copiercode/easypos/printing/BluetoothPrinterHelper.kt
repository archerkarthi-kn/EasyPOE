package fyi.copiercode.easypos.printing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import fyi.copiercode.easypos.util.BitmapHelper
import fyi.copiercode.easypos.data.entity.OrderEntity
import fyi.copiercode.easypos.data.entity.OrderItemEntity
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class BluetoothPrinterHelper {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Unified print function that sends a pre-formatted receipt string and a logo.
     * Replaces the unstable AIDL SDK path for Order printing.
     */
    private fun openLogoStream(context: Context, logoUri: String): InputStream? {
        return try {
            if (logoUri.startsWith("content://") || logoUri.startsWith("file://") || logoUri.startsWith("android.resource://")) {
                context.contentResolver.openInputStream(Uri.parse(logoUri))
            } else {
                // Assume absolute file path
                java.io.FileInputStream(logoUri)
            }
        } catch (e: Exception) {
            Log.e("BluetoothPrinter", "Failed to open logo stream: $logoUri", e)
            null
        }
    }

    fun printFancyOrder(
        context: Context,
        targetName: String = "InnerPrinter",
        logoUri: String?,
        shopName: String,
        billNo: String,
        items: List<fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem>,
        total: Double,
        paymentMethod: String,
        amountReceived: Double = 0.0,
        change: Double = 0.0,
        footerContact: fyi.copiercode.easypos.util.ReceiptBuilder.FooterContact
    ): Boolean {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return false

            val pairedDevices = adapter.bondedDevices
            val device = pairedDevices.find { it.name == targetName } ?: return false

            adapter.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            if (!socket.isConnected) return false

            outputStream = socket.outputStream
            val printer = ESCPOSWriter(outputStream)

            // Load Logo if available
            val logoBitmap = if (!logoUri.isNullOrEmpty()) {
                try {
                    openLogoStream(context, logoUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    null
                }
            } else null

            // Render Full Fancy Receipt to Bitmap
            // Strictly using 576px for 80mm paper to fill the breadth and fix cropping
            val paperWidthPx = 576 
            
            val fancyReceipt = BitmapHelper.drawFancyReceipt(
                lineWidth = paperWidthPx,
                logo = logoBitmap,
                shopName = shopName,
                billNo = billNo,
                items = items,
                total = total,
                paymentMethod = paymentMethod,
                amountReceived = amountReceived,
                change = change,
                footerContact = footerContact
            )

            printer.printImage(fancyReceipt)

            // Finalize - minimal paper waste
            printer.printText("\n\n")
            printer.cut()

            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e("BluetoothPrinter", "Fancy print failed", e)
            false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    fun printFormattedText(
        context: Context,
        targetName: String = "InnerPrinter",
        logoUri: String?,
        receiptText: String
    ): Boolean {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return try {
            Log.d("BluetoothPrinter", "Starting print job targeting: $targetName")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.e("BluetoothPrinter", "Bluetooth adapter is null or disabled")
                return false
            }

            val pairedDevices = adapter.bondedDevices
            val device = pairedDevices.find { it.name == targetName } 
            if (device == null) {
                Log.e("BluetoothPrinter", "Device '$targetName' not found in paired devices")
                return false
            }

            adapter.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            if (!socket.isConnected) {
                Log.e("BluetoothPrinter", "Failed to connect to printer socket")
                return false
            }

            outputStream = socket.outputStream
            val printer = ESCPOSWriter(outputStream)

            // 1. Print Logo with Monochrome Conversion
            if (!logoUri.isNullOrEmpty()) {
                Log.d("BluetoothPrinter", "Processing logo: $logoUri")
                try {
                    openLogoStream(context, logoUri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            printer.setAlignCenter()
                            val lineLength = receiptText.split("\n").firstOrNull()?.length ?: 0
                            
                            // SCALE LOGO: requested "small logo" (approx 2cm ~ 150-200px)
                            val targetLogoWidth = 200 
                            
                            Log.d("BluetoothPrinter", "Converting logo to monochrome, target width: $targetLogoWidth")
                            val monoBitmap = BitmapHelper.convertBitmapToMonochrome(bitmap, targetLogoWidth)
                            printer.printImage(monoBitmap)
                            printer.printText("\n")
                            Log.d("BluetoothPrinter", "Logo sent to output stream")
                        } else {
                            Log.e("BluetoothPrinter", "Failed to decode bitmap from stream")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothPrinter", "Logo processing failed", e)
                }
            } else {
                Log.d("BluetoothPrinter", "No logo URI provided, skipping logo")
            }

            // 2. Print Receipt Text as a High-Contrast Monochrome Bitmap for "Poppins" look
            Log.d("BluetoothPrinter", "Rendering receipt text to bitmap...")
            val firstLineLength = receiptText.split("\n").firstOrNull()?.length ?: 0
            val textBitmap = BitmapHelper.renderTextToMonochromeBitmap(
                text = receiptText,
                lineWidth = if (firstLineLength > 40) 576 else 384,
                fontSize = 24f,
                isBold = false,
                isCenter = false
            )
            printer.printImage(textBitmap)

            // 3. Finalize - Removed excessive bottom space
            printer.printText("\n\n")
            printer.cut()

            outputStream.flush()
            Log.d("BluetoothPrinter", "Print job flushed successfully")
            true
        } catch (e: Exception) {
            Log.e("BluetoothPrinter", "Print failed with exception", e)
            false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
                Log.d("BluetoothPrinter", "Socket closed")
            } catch (e: Exception) {
                Log.e("BluetoothPrinter", "Error closing socket", e)
            }
        }
    }

    fun printReceipt(
        context: Context,
        targetName: String = "InnerPrinter", // Specifically target internal hardware
        shopName: String,
        footerText: String,
        logoUri: String?,
        order: OrderEntity,
        items: List<OrderItemEntity>
    ): Boolean {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return false

            // Targeted scanning for "InnerPrinter"
            val pairedDevices = adapter.bondedDevices
            val device = pairedDevices.find { it.name == targetName } 
                ?: return false // Root cause fix: Explicitly target "InnerPrinter"

            // Cancel discovery before connecting
            adapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            
            if (!socket.isConnected) {
                Log.e("BluetoothPrinter", "Socket not connected after connect() call")
                return false
            }

            outputStream = socket.outputStream

            val printer = ESCPOSWriter(outputStream)

            // Logo
            if (!logoUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(logoUri)
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        printer.setAlignCenter()
                        val scaledBitmap = if (bitmap.width > 576) {
                            val ratio = 576f / bitmap.width
                            Bitmap.createScaledBitmap(bitmap, 576, (bitmap.height * ratio).toInt(), true)
                        } else {
                            bitmap
                        }
                        printer.printImage(scaledBitmap)
                        printer.printText("\n")
                    }
                    inputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Header
            printer.setAlignCenter()
            printer.setBold(true)
            printer.setTextSize(2)
            printer.printText("$shopName\n")
            
            printer.setBold(false)
            printer.setTextSize(1)
            printer.printText("Order: ${order.orderId}\n")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            printer.printText("Date: ${sdf.format(Date(order.timestamp))}\n")
            printer.printLine()

            // Items
            printer.setAlignLeft()
            items.forEach { item ->
                val priceStr = String.format("%.2f", item.priceAtSale)
                val name = if (item.name.length > 35) item.name.take(35) + ".." else item.name
                val qtyStr = "x${item.quantity}"
                val firstLine = "$name $qtyStr"
                printer.printText("$firstLine\n")
                
                printer.setAlignRight()
                printer.printText("$priceStr\n")
                printer.setAlignLeft()
            }

            printer.printLine()

            // Total
            printer.setAlignRight()
            printer.setBold(true)
            printer.setTextSize(2)
            printer.printText("TOTAL: ${String.format("%.2f", order.totalAmount)}\n")

            // Payment Details
            printer.setBold(false)
            printer.setTextSize(1)
            printer.printText("Payment: ${order.paymentMethod}\n")
            if (order.paymentMethod == "CASH") {
                printer.printText("Given:   ${String.format("%.2f", order.amountGiven)}\n")
                printer.printText("Change:  ${String.format("%.2f", order.balanceAmount)}\n")
            }

            // Footer
            printer.setAlignCenter()
            printer.setBold(false)
            printer.setTextSize(1)
            printer.printText("\n$footerText\n")
            
            printer.printText("\n\n\n\n\n\n") 
            printer.cut()

            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e("BluetoothPrinter", "Print failed: ${e.message}", e)
            false
        } finally {
            outputStream?.close()
            socket?.close()
        }
    }

    private class ESCPOSWriter(private val outputStream: OutputStream) {
        fun printText(text: String) {
            outputStream.write(text.toByteArray())
        }

        fun printLine() {
            outputStream.write("------------------------------------------------\n".toByteArray())
        }

        fun setAlignCenter() {
            outputStream.write(byteArrayOf(0x1B, 0x61, 0x01))
        }

        fun setAlignLeft() {
            outputStream.write(byteArrayOf(0x1B, 0x61, 0x00))
        }

        fun setAlignRight() {
            outputStream.write(byteArrayOf(0x1B, 0x61, 0x02))
        }

        fun setBold(enable: Boolean) {
            outputStream.write(byteArrayOf(0x1B, 0x45, if (enable) 0x01 else 0x00))
        }

        fun setTextSize(size: Int) {
            val s = when (size) {
                2 -> 0x11.toByte()
                else -> 0x00.toByte()
            }
            outputStream.write(byteArrayOf(0x1D, 0x21, s))
        }

        fun cut() {
            outputStream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        }

        fun printImage(bitmap: Bitmap) {
            val width = bitmap.width
            val height = bitmap.height
            val widthBytes = (width + 7) / 8
            
            Log.d("BluetoothPrinter", "printImage: width=$width, height=$height, bytesPerRow=$widthBytes")
            
            // GS v 0 command
            outputStream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
            outputStream.write(byteArrayOf((widthBytes % 256).toByte(), (widthBytes / 256).toByte()))
            outputStream.write(byteArrayOf((height % 256).toByte(), (height / 256).toByte()))

            val bytes = ByteArray(widthBytes * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    // Simplified: check if it's not white (or transparent)
                    // If it's pure monochrome from BitmapHelper, Color.BLACK is 0xFF000000
                    val isBlack = Color.alpha(pixel) > 128 && (Color.red(pixel) < 128 || Color.green(pixel) < 128 || Color.blue(pixel) < 128)
                    
                    if (isBlack) {
                        val byteIndex = y * widthBytes + x / 8
                        val bitIndex = 7 - (x % 8)
                        bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                    }
                }
            }
            outputStream.write(bytes)
            Log.d("BluetoothPrinter", "printImage: data sent, size=${bytes.size}")
        }
    }
}
