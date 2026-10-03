package fyi.copiercode.easypos.printing

import android.content.Context
import android.hardware.usb.*
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbPrinterHelper(private val context: Context) {

    suspend fun printBitmapUsb(bitmap: Bitmap, openCashDrawer: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return@withContext false
        val deviceList = usbManager.deviceList
        val printerDevice = deviceList.values.find { device ->
            (0 until device.interfaceCount).any { i ->
                device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER
            }
        } ?: deviceList.values.firstOrNull()

        if (printerDevice == null) {
            Log.e("UsbPrinter", "No USB printer found.")
            return@withContext false
        }

        var usbInterface: UsbInterface? = null
        var endpointOut: UsbEndpoint? = null

        for (i in 0 until printerDevice.interfaceCount) {
            val intf = printerDevice.getInterface(i)
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    usbInterface = intf
                    endpointOut = ep
                    break
                }
            }
            if (endpointOut != null) break
        }

        if (usbInterface == null || endpointOut == null) {
            Log.e("UsbPrinter", "No valid bulk out endpoint found on USB device.")
            return@withContext false
        }

        if (!usbManager.hasPermission(printerDevice)) {
            Log.e("UsbPrinter", "USB permission not granted for device: ${printerDevice.deviceName}")
            return@withContext false
        }

        var connection: UsbDeviceConnection? = null
        try {
            connection = usbManager.openDevice(printerDevice)
            if (connection == null || !connection.claimInterface(usbInterface, true)) {
                Log.e("UsbPrinter", "Failed to open USB connection or claim interface.")
                return@withContext false
            }

            // Reset printer
            val initCmd = byteArrayOf(0x1B, 0x40)
            connection.bulkTransfer(endpointOut, initCmd, initCmd.size, 3000)

            val width = bitmap.width
            val height = bitmap.height
            val widthBytes = (width + 7) / 8

            // ESC/POS GS v 0 raster bit image command
            val header = byteArrayOf(
                0x1D.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte(),
                (widthBytes % 256).toByte(), (widthBytes / 256).toByte(),
                (height % 256).toByte(), (height / 256).toByte()
            )

            val pixelBytes = ByteArray(widthBytes * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val isBlack = Color.alpha(pixel) > 128 && (Color.red(pixel) < 128 || Color.green(pixel) < 128 || Color.blue(pixel) < 128)
                    if (isBlack) {
                        val byteIndex = y * widthBytes + x / 8
                        val bitIndex = 7 - (x % 8)
                        pixelBytes[byteIndex] = (pixelBytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                    }
                }
            }

            val fullBuffer = header + pixelBytes

            // Send in smaller 1024-byte chunks for reliable USB host transfer across Android devices
            val chunkSize = 1024
            var offset = 0
            while (offset < fullBuffer.size) {
                val length = minOf(chunkSize, fullBuffer.size - offset)
                val chunk = fullBuffer.copyOfRange(offset, offset + length)
                val transferred = connection.bulkTransfer(endpointOut, chunk, chunk.size, 5000)
                if (transferred < 0) {
                    Log.e("UsbPrinter", "Bulk transfer failed at offset $offset")
                    break
                }
                offset += length
            }

            // Cash Drawer kick command if requested
            if (openCashDrawer) {
                val drawerCmd = byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte())
                connection.bulkTransfer(endpointOut, drawerCmd, drawerCmd.size, 3000)
            }

            // Feed and Cut
            val cutCmd = "\n\n\n\n".toByteArray() + byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x42.toByte(), 0x00.toByte())
            connection.bulkTransfer(endpointOut, cutCmd, cutCmd.size, 3000)

            connection.releaseInterface(usbInterface)
            true
        } catch (e: Exception) {
            Log.e("UsbPrinter", "USB printing exception", e)
            false
        } finally {
            connection?.close()
        }
    }
}
