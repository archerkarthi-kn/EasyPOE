package fyi.copiercode.easypos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fyi.copiercode.easypos.data.dao.OrderDao
import fyi.copiercode.easypos.data.dao.ProductDao
import fyi.copiercode.easypos.data.entity.*
import fyi.copiercode.easypos.printing.BluetoothPrinterHelper
import fyi.copiercode.easypos.model.CartItem
import fyi.copiercode.easypos.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class CartItemDto(
    val productId: Long,
    val name: String,
    val brand: String,
    val basePrice: Double,
    val categoryId: Long = 0,
    val orderIndex: Int = 0,
    val comboQty: Int = 0,
    val comboPrice: Double = 0.0,
    val imageUri: String? = null,
    val barcode: String? = null,
    val quantity: Int
) {
    fun toCartItem(): CartItem {
        return CartItem(
            product = ProductEntity(
                id = productId,
                name = name,
                brand = brand,
                basePrice = basePrice,
                categoryId = categoryId,
                orderIndex = orderIndex,
                comboQty = comboQty,
                comboPrice = comboPrice,
                imageUri = imageUri,
                barcode = barcode
            ),
            quantity = quantity
        )
    }

    companion object {
        fun fromCartItem(item: CartItem): CartItemDto {
            return CartItemDto(
                productId = item.product.id,
                name = item.product.name,
                brand = item.product.brand,
                basePrice = item.product.basePrice,
                categoryId = item.product.categoryId,
                orderIndex = item.product.orderIndex,
                comboQty = item.product.comboQty,
                comboPrice = item.product.comboPrice,
                imageUri = item.product.imageUri,
                barcode = item.product.barcode,
                quantity = item.quantity
            )
        }
    }
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val heldOrders: StateFlow<List<HeldOrderEntity>> = orderDao.getAllHeldOrders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems = _cartItems.asStateFlow()

    private val _paymentMethod = MutableStateFlow("CASH")
    val paymentMethod = _paymentMethod.asStateFlow()

    private val _amountGiven = MutableStateFlow("")
    val amountGiven = _amountGiven.asStateFlow()

    val totalAmount = _cartItems.map { items ->
        items.sumOf { it.totalPrice }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val balanceAmount = combine(totalAmount, _amountGiven, _paymentMethod) { total, given, method ->
        if (method == "CASH" && given.isNotBlank()) {
            (given.toDoubleOrNull() ?: 0.0) - total
        } else 0.0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun holdCurrentOrder(label: String = "") {
        val currentItems = _cartItems.value
        if (currentItems.isEmpty()) return

        viewModelScope.launch {
            val dtos = currentItems.map { CartItemDto.fromCartItem(it) }
            val itemsJson = Json.encodeToString(dtos)
            val totalQty = currentItems.sumOf { it.quantity }
            val currentTotal = totalAmount.value

            val heldOrder = HeldOrderEntity(
                timestamp = System.currentTimeMillis(),
                totalAmount = currentTotal,
                totalQuantity = totalQty,
                label = label.ifBlank { "Hold #${(heldOrders.value.size + 1)}" },
                itemsJson = itemsJson
            )
            orderDao.insertHeldOrder(heldOrder)
            clearCart()
        }
    }

    fun resumeHeldOrder(heldOrder: HeldOrderEntity) {
        viewModelScope.launch {
            try {
                val dtos = Json.decodeFromString<List<CartItemDto>>(heldOrder.itemsJson)
                val restoredItems = dtos.map { it.toCartItem() }
                _cartItems.value = restoredItems
                orderDao.deleteHeldOrderById(heldOrder.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteHeldOrder(heldOrderId: Long) {
        viewModelScope.launch {
            orderDao.deleteHeldOrderById(heldOrderId)
        }
    }

    fun addToCart(product: ProductEntity) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val existingItem = currentItems[index]
            currentItems[index] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            currentItems.add(CartItem(product, 1))
        }
        _cartItems.value = currentItems.toList()
    }

    fun addByBarcode(barcode: String, onNotFound: () -> Unit = {}) {
        viewModelScope.launch {
            val trimmedInput = barcode.trim()
            if (trimmedInput.isEmpty()) return@launch
            
            val product = productDao.getProductByBarcode(trimmedInput)
            if (product != null) {
                addToCart(product)
            } else {
                onNotFound()
            }
        }
    }

    fun removeFromCart(product: ProductEntity) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val existingItem = currentItems[index]
            if (existingItem.quantity > 1) {
                currentItems[index] = existingItem.copy(quantity = existingItem.quantity - 1)
            } else {
                currentItems.removeAt(index)
            }
            _cartItems.value = currentItems.toList()
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _amountGiven.value = ""
    }

    fun setPaymentMethod(method: String) {
        _paymentMethod.value = method
    }

    fun updateAmountGiven(amount: String) {
        _amountGiven.value = amount
    }

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    fun checkout(context: android.content.Context, shopName: String, footerText: String, logoPath: String?, onComplete: () -> Unit) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        
        viewModelScope.launch {
            try {
                val totalQty = _cartItems.value.sumOf { it.quantity }
                val orderIdStr = settingsRepository.getNextBillNumber()
                
                val order = OrderEntity(
                    orderId = orderIdStr,
                    timestamp = System.currentTimeMillis(),
                    totalAmount = totalAmount.value,
                    totalQuantity = totalQty,
                    brandCategory = "Retail",
                    paymentMethod = _paymentMethod.value,
                    amountGiven = _amountGiven.value.toDoubleOrNull() ?: 0.0,
                    balanceAmount = balanceAmount.value
                )
                val id = orderDao.insertOrder(order)
                
                val orderItems = _cartItems.value.map { cartItem ->
                    OrderItemEntity(
                        orderId = id,
                        productId = cartItem.product.id,
                        name = cartItem.product.name,
                        brand = cartItem.product.brand,
                        quantity = cartItem.quantity,
                        priceAtSale = cartItem.totalPrice
                    )
                }
                orderDao.insertOrderItems(orderItems)
                
                // Print Receipt using updated logic
                val isTokenEnabled = settingsRepository.enableTokenNumber.first()
                val tokenNum = if (isTokenEnabled) {
                    val num = settingsRepository.getNextTokenNumber()
                    num.toString().padStart(3, '0')
                } else null

                val isTwoSlips = settingsRepository.printTwoSlips.first()
                val printPasses = if (isTwoSlips) 2 else 1

                val paperWidth = settingsRepository.paperWidth.first()
                val footerContact = fyi.copiercode.easypos.util.ReceiptBuilder.FooterContact(
                    tel = settingsRepository.footerTel.first(),
                    email = settingsRepository.footerEmail.first(),
                    loc1 = settingsRepository.footerLoc1.first(),
                    loc2 = settingsRepository.footerLoc2.first()
                )
                
                val itemsToPrint = _cartItems.value.map { fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem(it.product.name, it.quantity, it.totalPrice) }

                val logoBitmap = if (!logoPath.isNullOrEmpty()) {
                    try {
                        if (logoPath.startsWith("content://") || logoPath.startsWith("file://") || logoPath.startsWith("android.resource://")) {
                            val uri = android.net.Uri.parse(logoPath)
                            context.contentResolver.openInputStream(uri)?.use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        } else {
                            val file = java.io.File(logoPath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            } else null
                        }
                    } catch (e: Exception) { null }
                } else null

                val paperWidthPx = if (paperWidth == 48) 576 else 384
                val isCash = _paymentMethod.value == "CASH"
                val printerType = settingsRepository.printerType.first()
                
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repeat(printPasses) { pass ->
                        val slipTitle = if (isTwoSlips) {
                            if (pass == 0) "CASHIER COPY" else "CUSTOMER RECEIPT"
                        } else null

                        val passBitmap = fyi.copiercode.easypos.util.BitmapHelper.drawFancyReceipt(
                            lineWidth = paperWidthPx,
                            logo = logoBitmap,
                            shopName = shopName,
                            billNo = orderIdStr,
                            tokenNumber = tokenNum,
                            slipTitle = slipTitle,
                            items = itemsToPrint,
                            total = totalAmount.value,
                            paymentMethod = _paymentMethod.value,
                            amountReceived = _amountGiven.value.toDoubleOrNull() ?: 0.0,
                            change = balanceAmount.value,
                            footerContact = footerContact
                        )

                        when (printerType) {
                            "USB" -> {
                                fyi.copiercode.easypos.printing.UsbPrinterHelper(context).printBitmapUsb(passBitmap, openCashDrawer = isCash && pass == 0)
                            }
                            "NETWORK" -> {
                                val ip = settingsRepository.networkPrinterIp.first()
                                fyi.copiercode.easypos.printing.NetworkPrinterHelper().printBitmapNetwork(ip, 9100, passBitmap, openCashDrawer = isCash && pass == 0)
                            }
                            else -> {
                                fyi.copiercode.easypos.printing.BluetoothPrinterHelper().printFancyOrder(
                                    context = context,
                                    logoUri = logoPath,
                                    shopName = shopName,
                                    billNo = orderIdStr,
                                    tokenNumber = tokenNum,
                                    slipTitle = slipTitle,
                                    items = itemsToPrint,
                                    total = totalAmount.value,
                                    paymentMethod = _paymentMethod.value,
                                    amountReceived = _amountGiven.value.toDoubleOrNull() ?: 0.0,
                                    change = balanceAmount.value,
                                    footerContact = footerContact
                                )
                            }
                        }
                    }
                }
                
                clearCart()
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("PrintOrderError", "Failed to print", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Print Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
