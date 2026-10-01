package fyi.copiercode.easypos.util

import java.text.SimpleDateFormat
import java.util.*

class ReceiptBuilder(private val lineWidth: Int = 48) {

    fun formatLine(left: String, right: String): String {
        val availableSpace = lineWidth - left.length - right.length
        return if (availableSpace >= 0) {
            left + " ".repeat(availableSpace) + right
        } else {
            // If text is too long, truncate left
            val maxLeftLength = (lineWidth - right.length - 1).coerceAtLeast(0)
            val truncatedLeft = if (maxLeftLength > 0) left.take(maxLeftLength) else ""
            val padding = if (maxLeftLength > 0) " " else ""
            truncatedLeft + padding + right
        }
    }

    fun divider(char: Char = '-'): String {
        return char.toString().repeat(lineWidth)
    }

    fun center(text: String): String {
        if (text.length >= lineWidth) return text
        val padding = (lineWidth - text.length) / 2
        return " ".repeat(padding) + text
    }

    fun buildReceipt(
        shopName: String,
        billNo: String,
        items: List<ReceiptItem>,
        total: Double,
        paymentMethod: String,
        footerContact: FooterContact
    ): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Header
        sb.append(divider('=')).append("\n")
        sb.append(center(shopName.uppercase())).append("\n")
        sb.append(divider('=')).append("\n")

        // Meta Info
        sb.append("Date: ${sdf.format(Date())}\n")
        sb.append("Bill No.: $billNo\n")
        
        // Column headers
        val qtyWidth = 4
        val amountWidth = 9
        val itemWidth = lineWidth - qtyWidth - amountWidth - 2
        
        val header = "QTY".padEnd(qtyWidth) + " " + "ITEM NAME".padEnd(itemWidth) + " " + "AMOUNT".padStart(amountWidth)
        sb.append(header).append("\n")
        sb.append(divider()).append("\n")

        // Items
        items.forEach { item ->
            val qtyStr = item.quantity.toString().padEnd(qtyWidth)
            val amountStr = String.format(Locale.US, "%.2f", item.price).padStart(amountWidth)
            val itemName = if (item.name.length > itemWidth) item.name.take(itemWidth) else item.name.padEnd(itemWidth)
            sb.append("$qtyStr $itemName $amountStr").append("\n")
            sb.append(divider()).append("\n")
        }

        // Totals
        sb.append(formatLine("TOTAL:", String.format(Locale.US, "%.2f", total))).append("\n")
        sb.append("Payment: $paymentMethod\n")
        sb.append(divider()).append("\n")

        // Contact Info - Specific Arrangement
        // Center Tel & Email
        if (footerContact.tel.isNotBlank()) sb.append(center("Tel: ${footerContact.tel}")).append("\n")
        if (footerContact.email.isNotBlank()) sb.append(center("Email: ${footerContact.email}")).append("\n")
        
        // Loc 1 (Left) and Loc 2 (Right) on same or separate lines depending on length
        if (footerContact.loc1.isNotBlank() || footerContact.loc2.isNotBlank()) {
            val l1 = if (footerContact.loc1.length > lineWidth/2 - 2) footerContact.loc1.take(lineWidth/2 - 2) else footerContact.loc1
            val l2 = if (footerContact.loc2.length > lineWidth/2 - 2) footerContact.loc2.take(lineWidth/2 - 2) else footerContact.loc2
            sb.append(formatLine("Loc 1: $l1", "Loc 2: $l2")).append("\n")
        }

        // Final Footer Message at the very bottom
        sb.append(divider()).append("\n")
        sb.append(center("Thank you for your visit!")).append("\n")
        sb.append(divider('=')).append("\n")

        return sb.toString()
    }

    data class ReceiptItem(val name: String, val quantity: Int, val price: Double)
    data class FooterContact(val tel: String, val email: String, val loc1: String, val loc2: String)
}
