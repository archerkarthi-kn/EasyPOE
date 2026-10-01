package fyi.copiercode.easypos.data.dao

import androidx.room.*
import fyi.copiercode.easypos.data.entity.CategoryEntity
import fyi.copiercode.easypos.data.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY orderIndex ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY orderIndex ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getProductsByCategory(categoryId: Long): Flow<List<ProductEntity>>

    @Query("SELECT MAX(orderIndex) FROM products")
    suspend fun getMaxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity): Int

    @Transaction
    suspend fun swapProductOrder(id1: Long, index1: Int, id2: Long, index2: Int) {
        val p1 = getProductById(id1)
        val p2 = getProductById(id2)
        if (p1 != null && p2 != null) {
            updateProduct(p1.copy(orderIndex = index2))
            updateProduct(p2.copy(orderIndex = index1))
        }
    }

    @Delete
    suspend fun deleteProduct(product: ProductEntity): Int

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT DISTINCT brand FROM products ORDER BY brand ASC")
    fun getAllBrands(): Flow<List<String>>

    // Category Management
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity): Int

    @Delete
    suspend fun deleteCategory(category: CategoryEntity): Int
}
