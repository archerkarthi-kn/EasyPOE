package fyi.copiercode.easypos.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import fyi.copiercode.easypos.data.dao.OrderDao
import fyi.copiercode.easypos.data.dao.ProductDao
import fyi.copiercode.easypos.data.entity.*
import kotlinx.coroutines.launch

@Database(
    entities = [ProductEntity::class, OrderEntity::class, OrderItemEntity::class, AppSetting::class, CategoryEntity::class, HeldOrderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `held_orders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `totalQuantity` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `itemsJson` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "easypos_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        suspend fun populateDatabase(database: AppDatabase) {
            val productDao = database.productDao()
            val orderDao = database.orderDao()

            // Default Settings
            orderDao.insertSetting(AppSetting("shop_name", "Easy POS"))
            orderDao.insertSetting(AppSetting("is_printing_enabled", "true"))

            // Seed Categories
            val beerCategoryId = productDao.insertCategory(CategoryEntity(name = "Beer"))
            
            // Seed Products
            val seedProducts = listOf(
                ProductEntity(name = "Black Label", brand = "Black Label", basePrice = 4.30, categoryId = beerCategoryId),
                ProductEntity(name = "Anchor", brand = "Anchor", basePrice = 4.30, categoryId = beerCategoryId),
                ProductEntity(name = "British Empire", brand = "British Empire", basePrice = 3.80, comboQty = 3, comboPrice = 10.50, categoryId = beerCategoryId),
                ProductEntity(name = "Bullet", brand = "Bullet", basePrice = 3.80, comboQty = 3, comboPrice = 10.50, categoryId = beerCategoryId),
                ProductEntity(name = "5000", brand = "5000", basePrice = 4.00, comboQty = 3, comboPrice = 11.00, categoryId = beerCategoryId),
                ProductEntity(name = "Bs", brand = "Bs", basePrice = 3.80, comboQty = 3, comboPrice = 10.00, categoryId = beerCategoryId),
                ProductEntity(name = "Black Pearl", brand = "Black Pearl", basePrice = 4.00, comboQty = 3, comboPrice = 10.50, categoryId = beerCategoryId),
                ProductEntity(name = "93", brand = "93", basePrice = 3.50, comboQty = 3, comboPrice = 9.00, categoryId = beerCategoryId)
            )

            seedProducts.forEach { productDao.insertProduct(it) }
        }
    }
}
