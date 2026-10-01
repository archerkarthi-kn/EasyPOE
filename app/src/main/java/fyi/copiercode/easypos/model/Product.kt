package fyi.copiercode.easypos.model

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val imageRes: Int? = null
)
