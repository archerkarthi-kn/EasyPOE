package fyi.copiercode.easypos.data.dao

import kotlinx.serialization.Serializable

@Serializable
data class AuditRecord(
    val orderId: String,
    val timestamp: Long,
    val paymentMethod: String,
    val name: String,
    val brand: String,
    val categoryId: Long = 0,
    val quantity: Int,
    val priceAtSale: Double,
    val amountGiven: Double = 0.0,
    val balanceAmount: Double = 0.0
)
