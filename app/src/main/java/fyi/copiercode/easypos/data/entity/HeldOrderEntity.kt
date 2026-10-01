package fyi.copiercode.easypos.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "held_orders")
data class HeldOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val totalQuantity: Int,
    val label: String = "",
    val itemsJson: String // JSON string storing the List<CartItem>
)
