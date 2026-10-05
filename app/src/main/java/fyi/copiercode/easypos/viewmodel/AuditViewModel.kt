package fyi.copiercode.easypos.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fyi.copiercode.easypos.data.SettingsRepository
import fyi.copiercode.easypos.data.dao.AuditRecord
import fyi.copiercode.easypos.data.dao.OrderDao
import fyi.copiercode.easypos.data.dao.ProductDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class DateFilterType { DAY, MONTH, YEAR, RANGE, ALL }

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val orderDao: OrderDao,
    private val productDao: ProductDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _selectedBrand = MutableStateFlow<String?>(null)
    val selectedBrand: StateFlow<String?> = _selectedBrand.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow<String?>(null)
    val selectedPaymentMethod: StateFlow<String?> = _selectedPaymentMethod.asStateFlow()

    private val _dateFilterType = MutableStateFlow(DateFilterType.ALL)
    val dateFilterType: StateFlow<DateFilterType> = _dateFilterType.asStateFlow()

    private val _selectedCalendar = MutableStateFlow(Calendar.getInstance())
    val selectedCalendar: StateFlow<Calendar> = _selectedCalendar.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate = _customEndDate.asStateFlow()

    val brands: StateFlow<List<String>> = productDao.getAllBrands()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories = productDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredRecords: StateFlow<List<AuditRecord>> = combine(
        listOf(_selectedBrand, _selectedPaymentMethod, _dateFilterType, _selectedCalendar, _customStartDate, _customEndDate, _selectedCategoryId)
    ) { args: Array<Any?> ->
        val brand = args[0] as String?
        val payment = args[1] as String?
        val dateType = args[2] as DateFilterType
        val calendar = args[3] as Calendar
        val customStart = args[4] as Long?
        val customEnd = args[5] as Long?
        val categoryId = args[6] as Long?

        val range = if (dateType == DateFilterType.RANGE) {
            Pair(customStart, customEnd)
        } else {
            calculateTimeRange(dateType, calendar)
        }
        orderDao.getFilteredAuditRecords(
            brand = if (brand == "All Items" || brand == null) null else brand,
            categoryId = categoryId,
            startTime = range?.first,
            endTime = range?.second,
            paymentMethod = payment
        )
    }.flatMapLatest { it }
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalRevenue: StateFlow<Double> = filteredRecords.map { records ->
        records.sumOf { it.priceAtSale }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val totalUnitsSold: StateFlow<Int> = filteredRecords.map { records ->
        records.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun setBrandFilter(brand: String?) {
        _selectedBrand.value = if (brand == "All Items") null else brand
    }

    fun setCategoryFilter(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun setPaymentFilter(method: String?) {
        _selectedPaymentMethod.value = method
    }

    fun setDateFilterType(type: DateFilterType) {
        _dateFilterType.value = type
    }

    fun updateCalendar(calendar: Calendar) {
        _selectedCalendar.value = calendar
    }

    fun setCustomRange(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
    }

    fun printAudit(context: Context, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val records = filteredRecords.value
            if (records.isEmpty()) {
                onError("No records to print.")
                return@launch
            }

            val totalCashReceived = records.filter { it.paymentMethod == "CASH" }.distinctBy { it.orderId }.sumOf { it.amountGiven }
            val totalChangeGiven = records.filter { it.paymentMethod == "CASH" }.distinctBy { it.orderId }.sumOf { it.balanceAmount }

            val periodStr = when (dateFilterType.value) {
                DateFilterType.ALL -> "All Time"
                DateFilterType.DAY -> SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(selectedCalendar.value.time)
                DateFilterType.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedCalendar.value.time)
                DateFilterType.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault()).format(selectedCalendar.value.time)
                DateFilterType.RANGE -> {
                    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    val start = customStartDate.value?.let { sdf.format(Date(it)) } ?: "Start"
                    val end = customEndDate.value?.let { sdf.format(Date(it)) } ?: "End"
                    "$start to $end"
                }
            }

            val categoriesList = productDao.getAllCategories().first()
            val categoryMap = categoriesList.associateBy { it.id }
            val allProducts = productDao.getAllProductsList()
            val productByName = allProducts.associateBy { it.name.trim().lowercase() }

            val groupedByCategory = records.groupBy { record ->
                var catName = categoryMap[record.categoryId]?.name
                if (catName.isNullOrBlank() || catName == "General") {
                    val prod = productByName[record.name.trim().lowercase()]
                    if (prod != null) {
                        catName = categoryMap[prod.categoryId]?.name ?: prod.brand
                    }
                }
                if (catName.isNullOrBlank() || catName == "Retail") {
                    if (record.brand.isNotBlank() && record.brand != "Retail" && record.brand != "Manual") {
                        record.brand
                    } else "General"
                } else catName
            }

            val categoryGroups = LinkedHashMap<String, List<fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem>>()

            groupedByCategory.toSortedMap().forEach { (catName, catRecords) ->
                val list = mutableListOf<fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem>()
                val productGroups = catRecords.groupBy { it.name }
                productGroups.forEach { (itemName, group) ->
                    val totalQty = group.sumOf { it.quantity }
                    val totalPrice = group.sumOf { it.priceAtSale }
                    list.add(
                        fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem(
                            name = itemName,
                            quantity = totalQty,
                            price = totalPrice
                        )
                    )
                }
                categoryGroups[catName] = list
            }

            try {
                val shopName = orderDao.getSetting("shop_name")?.value ?: "Easy POS"
                val paperWidth = settingsRepository.paperWidth.first()
                val paperWidthPx = if (paperWidth == 48) 576 else 384
                val footerContact = fyi.copiercode.easypos.util.ReceiptBuilder.FooterContact(
                    tel = settingsRepository.footerTel.first(),
                    email = settingsRepository.footerEmail.first(),
                    loc1 = settingsRepository.footerLoc1.first(),
                    loc2 = settingsRepository.footerLoc2.first()
                )

                val receiptBitmap = fyi.copiercode.easypos.util.BitmapHelper.drawAuditReport(
                    lineWidth = paperWidthPx,
                    shopName = shopName,
                    periodStr = periodStr,
                    categoryGroups = categoryGroups,
                    totalRevenue = totalRevenue.value,
                    totalUnitsSold = totalUnitsSold.value,
                    totalCash = totalCashReceived,
                    totalChange = totalChangeGiven,
                    footerContact = footerContact
                )

                val printerType = settingsRepository.printerType.first()
                val isPrinted = withContext(Dispatchers.IO) {
                    when (printerType) {
                        "USB" -> {
                            fyi.copiercode.easypos.printing.UsbPrinterHelper(context).printBitmapUsb(receiptBitmap)
                        }
                        "NETWORK" -> {
                            val ip = settingsRepository.networkPrinterIp.first()
                            fyi.copiercode.easypos.printing.NetworkPrinterHelper().printBitmapNetwork(ip, 9100, receiptBitmap)
                        }
                        else -> {
                            fyi.copiercode.easypos.printing.BluetoothPrinterHelper().printBitmap(
                                context = context,
                                targetName = "InnerPrinter",
                                bitmap = receiptBitmap
                            )
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (isPrinted) {
                        android.widget.Toast.makeText(context, "Audit Printed Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onError("Print failed. Please check printer connection & settings.")
                    }
                }
            } catch (e: Exception) {
                Log.e("AuditViewModel", "Audit print failed", e)
                withContext(Dispatchers.Main) {
                    onError("Print Error: ${e.message}")
                }
            }
        }
    }

    private fun calculateTimeRange(type: DateFilterType, calendar: Calendar): Pair<Long, Long>? {
        if (type == DateFilterType.ALL) return null

        val start = calendar.clone() as Calendar
        val end = calendar.clone() as Calendar

        when (type) {
            DateFilterType.DAY -> {
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            DateFilterType.MONTH -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            DateFilterType.YEAR -> {
                start.set(Calendar.MONTH, 0)
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.set(Calendar.MONTH, 11)
                end.set(Calendar.DAY_OF_MONTH, 31)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            else -> return null
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }

    suspend fun generateCsvFile(context: Context, records: List<AuditRecord>): File? = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext null
        try {
            val file = File(context.cacheDir, "audit_report_${System.currentTimeMillis()}.csv")
            BufferedWriter(OutputStreamWriter(FileOutputStream(file))).use { writer ->
                writer.write("Order ID,Timestamp,Item Name,Brand,Quantity,Unit Price,Total Price,Payment Method\n")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                records.forEach { record ->
                    val unitPrice = record.priceAtSale / record.quantity
                    writer.write("${record.orderId},")
                    writer.write("${sdf.format(Date(record.timestamp))},")
                    writer.write("\"${record.name.replace("\"", "\"\"")}\",")
                    writer.write("\"${record.brand.replace("\"", "\"\"")}\",")
                    writer.write("${record.quantity},")
                    writer.write("${String.format(Locale.US, "%.2f", unitPrice)},")
                    writer.write("${String.format(Locale.US, "%.2f", record.priceAtSale)},")
                    writer.write("${record.paymentMethod}\n")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportToCsv(context: Context, records: List<AuditRecord>): String? {
        if (records.isEmpty()) return null
        
        val fileName = "EasyPOE_Audit_Report_${System.currentTimeMillis()}.csv"

        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        // Brand, Quantity, Payment Method, Timing, Unit Price, Total Price
                        writer.write("Brand,Quantity,Payment Method,Timing,Unit Price,Total Price\n")
                        
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        records.forEach { record ->
                            val unitPrice = record.priceAtSale / record.quantity
                            writer.write("${record.brand.replace(",", ";")},")
                            writer.write("${record.quantity},")
                            writer.write("${record.paymentMethod},")
                            writer.write("${sdf.format(Date(record.timestamp))},")
                            writer.write("${String.format("%.2f", unitPrice)},")
                            writer.write("${String.format("%.2f", record.priceAtSale)}\n")
                        }
                    }
                }
                "Downloads/$fileName"
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
