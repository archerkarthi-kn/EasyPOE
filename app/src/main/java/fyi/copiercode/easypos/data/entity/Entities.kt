package fyi.copiercode.easypos.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String,
    val basePrice: Double,
    val categoryId: Long = 0, // Reference to CategoryEntity
    val orderIndex: Int = 0,
    val comboQty: Int = 0,
    val comboPrice: Double = 0.0,
    val imageUri: String? = null,
    val barcode: String? = null
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val totalQuantity: Int, // Added for quick auditing
    val brandCategory: String, // For quick filtering/auditing
    val paymentMethod: String = "CASH",
    val amountGiven: Double = 0.0,
    val balanceAmount: Double = 0.0,
    val cashGiven: Double = 0.0, // Added for Audit
    val changeGiven: Double = 0.0  // Added for Audit
)

@Entity(
    tableName = "order_items",
    foreignKeys = [ForeignKey(
        entity = OrderEntity::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val categoryId: Long = 0,
    val name: String,
    val brand: String,
    val quantity: Int,
    val priceAtSale: Double
)
