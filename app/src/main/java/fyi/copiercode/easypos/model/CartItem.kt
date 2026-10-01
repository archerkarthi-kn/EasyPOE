package fyi.copiercode.easypos.model

import fyi.copiercode.easypos.data.entity.ProductEntity

data class CartItem(
    val product: ProductEntity,
    val quantity: Int = 1
) {
    val totalPrice: Double
        get() {
            return if (product.comboQty > 0 && quantity >= product.comboQty) {
                val comboCount = quantity / product.comboQty
                val remainingCount = quantity % product.comboQty
                (comboCount * product.comboPrice) + (remainingCount * product.basePrice)
            } else {
                quantity * product.basePrice
            }
        }
}
