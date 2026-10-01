package fyi.copiercode.easypos.data.dao

import androidx.room.*
import fyi.copiercode.easypos.data.entity.AppSetting
import fyi.copiercode.easypos.data.entity.HeldOrderEntity
import fyi.copiercode.easypos.data.entity.OrderEntity
import fyi.copiercode.easypos.data.entity.OrderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>): Array<Long>

    @Transaction
    suspend fun completeTransaction(order: OrderEntity, items: List<OrderItemEntity>): Long {
        val id = insertOrder(order)
        val itemsWithId = items.map { it.copy(orderId = id) }
        insertOrderItems(itemsWithId)
        return id
    }

    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    // Held Orders Management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeldOrder(heldOrder: HeldOrderEntity): Long

    @Query("DELETE FROM held_orders WHERE id = :id")
    suspend fun deleteHeldOrderById(id: Long)

    @Delete
    suspend fun deleteHeldOrder(heldOrder: HeldOrderEntity)

    @Query("SELECT * FROM held_orders ORDER BY timestamp DESC")
    fun getAllHeldOrders(): Flow<List<HeldOrderEntity>>

    @Query("""
        SELECT o.orderId, o.timestamp, o.paymentMethod, i.name, i.brand, i.categoryId, i.quantity, i.priceAtSale, o.amountGiven, o.balanceAmount
        FROM order_items i 
        JOIN orders o ON i.orderId = o.id 
        WHERE (:brand IS NULL OR i.brand = :brand)
        AND (:categoryId IS NULL OR :categoryId = 0 OR i.categoryId = :categoryId)
        AND (:startTime IS NULL OR o.timestamp >= :startTime)
        AND (:endTime IS NULL OR o.timestamp <= :endTime)
        AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod)
        ORDER BY o.timestamp DESC
    """)
    fun getFilteredAuditRecords(
        brand: String?,
        categoryId: Long?,
        startTime: Long?,
        endTime: Long?,
        paymentMethod: String?
    ): Flow<List<AuditRecord>>

    @Query("SELECT * FROM order_items WHERE orderId IN (:orderIds)")
    suspend fun getItemsForOrders(orderIds: List<Long>): List<OrderItemEntity>

    // Settings management
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<AppSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(AppSetting: AppSetting): Long
}
