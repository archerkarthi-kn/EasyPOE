package fyi.copiercode.easypos.printing

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class NetworkPrinterHelper {

    suspend fun printBitmapNetwork(ipAddress: String, port: Int = 9100, bitmap: Bitmap, openCashDrawer: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var outputStream: OutputStream? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, port), 5000)
            outputStream = socket.getOutputStream()

            // Reset printer
            outputStream.write(byteArrayOf(0x1B, 0x40))

            val width = bitmap.width
            val height = bitmap.height
            val widthBytes = (width + 7) / 8

            // Standard ESC/POS GS v 0 command for raster bit image
            outputStream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
            outputStream.write(byteArrayOf((widthBytes % 256).toByte(), (widthBytes / 256).toByte()))
            outputStream.write(byteArrayOf((height % 256).toByte(), (height / 256).toByte()))

            val bytes = ByteArray(widthBytes * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val isBlack = Color.alpha(pixel) > 128 && (Color.red(pixel) < 128 || Color.green(pixel) < 128 || Color.blue(pixel) < 128)
                    
                    if (isBlack) {
                        val byteIndex = y * widthBytes + x / 8
                        val bitIndex = 7 - (x % 8)
                        bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                    }
                }
            }
            outputStream.write(bytes)

            // Cash Drawer Kick if requested
            if (openCashDrawer) {
                outputStream.write(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()))
            }

            // Feed and Cut
            outputStream.write("\n\n\n\n".toByteArray())
            outputStream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e("NetworkPrinter", "Failed to print to $ipAddress", e)
            false
        } finally {
            try {
                outputStream?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
