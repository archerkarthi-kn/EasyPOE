package fyi.copiercode.easypos.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import fyi.copiercode.easypos.BuildConfig
import androidx.lifecycle.viewModelScope
import fyi.copiercode.easypos.data.ThemePreferences
import fyi.copiercode.easypos.data.SettingsRepository
import fyi.copiercode.easypos.data.dao.OrderDao
import fyi.copiercode.easypos.data.dao.ProductDao
import fyi.copiercode.easypos.data.entity.*
import fyi.copiercode.easypos.printing.PrinterHelper
import fyi.copiercode.easypos.util.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val themePreferences: ThemePreferences,
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // New Settings Flows
    val footerTel = settingsRepository.footerTel.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val footerEmail = settingsRepository.footerEmail.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val footerLoc1 = settingsRepository.footerLoc1.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val footerLoc2 = settingsRepository.footerLoc2.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val paymentBypass = settingsRepository.paymentBypass.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val billPrefix = settingsRepository.billPrefix.stateIn(viewModelScope, SharingStarted.Lazily, "Taj")
    val billCounter = settingsRepository.billCounter.stateIn(viewModelScope, SharingStarted.Lazily, 1)
    val paperWidth = settingsRepository.paperWidth.stateIn(viewModelScope, SharingStarted.Lazily, 48)
    val bridgePin = settingsRepository.bridgePin.stateIn(viewModelScope, SharingStarted.Lazily, "1234")
    val bridgeToken = settingsRepository.bridgeToken.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val pairedDevices = settingsRepository.pairedDevices.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val printerType = settingsRepository.printerType.stateIn(viewModelScope, SharingStarted.Lazily, "BLUETOOTH")
    val networkPrinterIp = settingsRepository.networkPrinterIp.stateIn(viewModelScope, SharingStarted.Lazily, "192.168.1.100")
    val manualBillingPassword = settingsRepository.manualBillingPassword.stateIn(viewModelScope, SharingStarted.Lazily, "11111")
    val printTwoSlips = settingsRepository.printTwoSlips.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val enableTokenNumber = settingsRepository.enableTokenNumber.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val tokenCounter = settingsRepository.tokenCounter.stateIn(viewModelScope, SharingStarted.Lazily, 1)

    fun setPrinterType(type: String) {
        viewModelScope.launch { settingsRepository.setPrinterType(type) }
    }

    fun setNetworkPrinterIp(ip: String) {
        viewModelScope.launch { settingsRepository.setNetworkPrinterIp(ip) }
    }

    fun setManualBillingPassword(pwd: String) {
        viewModelScope.launch { settingsRepository.setManualBillingPassword(pwd) }
    }

    fun setPrintTwoSlips(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPrintTwoSlips(enabled) }
    }

    fun setEnableTokenNumber(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEnableTokenNumber(enabled) }
    }

    fun resetTokenCounter() {
        viewModelScope.launch { settingsRepository.resetTokenCounter() }
    }

    fun createManualOrder(
        context: Context,
        billNo: String,
        items: List<fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem>,
        paymentMethod: String,
        amountGiven: Double,
        change: Double,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val totalAmount = items.sumOf { it.price }
                
                // Manual bills are printed ONLY and excluded from database/audit logs as requested
                val paperWidth = settingsRepository.paperWidth.first()
                val footerContact = fyi.copiercode.easypos.util.ReceiptBuilder.FooterContact(
                    tel = settingsRepository.footerTel.first(),
                    email = settingsRepository.footerEmail.first(),
                    loc1 = settingsRepository.footerLoc1.first(),
                    loc2 = settingsRepository.footerLoc2.first()
                )
                val shopName = orderDao.getSetting("shop_name")?.value ?: "Easy POS"
                val logoPath = orderDao.getSetting("receipt_logo_uri")?.value

                val logoBitmap = if (!logoPath.isNullOrEmpty()) {
                    try {
                        if (logoPath.startsWith("content://") || logoPath.startsWith("file://") || logoPath.startsWith("android.resource://")) {
                            val uri = Uri.parse(logoPath)
                            context.contentResolver.openInputStream(uri)?.use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        } else {
                            val file = File(logoPath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            } else null
                        }
                    } catch (e: Exception) { null }
                } else null

                val paperWidthPx = if (paperWidth == 48) 576 else 384
                val receiptBitmap = fyi.copiercode.easypos.util.BitmapHelper.drawFancyReceipt(
                    lineWidth = paperWidthPx,
                    logo = logoBitmap,
                    shopName = shopName,
                    billNo = billNo,
                    items = items,
                    total = totalAmount,
                    paymentMethod = paymentMethod,
                    amountReceived = amountGiven,
                    change = change,
                    footerContact = footerContact
                )

                val isCash = paymentMethod == "CASH"
                val pType = settingsRepository.printerType.first()
                withContext(Dispatchers.IO) {
                    when (pType) {
                        "USB" -> {
                            fyi.copiercode.easypos.printing.UsbPrinterHelper(context).printBitmapUsb(receiptBitmap, openCashDrawer = isCash)
                        }
                        "NETWORK" -> {
                            val ip = settingsRepository.networkPrinterIp.first()
                            fyi.copiercode.easypos.printing.NetworkPrinterHelper().printBitmapNetwork(ip, 9100, receiptBitmap, openCashDrawer = isCash)
                        }
                        else -> {
                            fyi.copiercode.easypos.printing.BluetoothPrinterHelper().printFancyOrder(
                                context = context,
                                logoUri = logoPath,
                                shopName = shopName,
                                billNo = billNo,
                                items = items,
                                total = totalAmount,
                                paymentMethod = paymentMethod,
                                amountReceived = amountGiven,
                                change = change,
                                footerContact = footerContact
                            )
                        }
                    }
                }
                onComplete(true, null)
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Manual order failed", e)
                onComplete(false, e.message)
            }
        }
    }

    fun removePairedDevice(token: String) {
        viewModelScope.launch {
            settingsRepository.removePairedDevice(token)
        }
    }

    fun generateNewPin() {
        viewModelScope.launch {
            val randomPin = (1000..9999).random().toString()
            settingsRepository.setBridgePin(randomPin)
        }
    }

    fun getDeviceIpAddress(): String {
        try {
            val ipList = mutableListOf<String>()
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val name = intf.name.lowercase()
                if (name.contains("tun") || name.contains("ppp") || name.contains("p2p")) continue

                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        ipList.add(hostAddr)
                    }
                }
            }
            // Prioritize 192.168.x.x, then 10.x.x.x, then 172.x.x.x
            ipList.firstOrNull { it.startsWith("192.168.") }?.let { return it }
            ipList.firstOrNull { it.startsWith("10.") }?.let { return it }
            ipList.firstOrNull { it.startsWith("172.") }?.let { return it }
            return ipList.firstOrNull() ?: "127.0.0.1"
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun saveFooterDetails(tel: String, email: String, loc1: String, loc2: String) {
        viewModelScope.launch { settingsRepository.saveFooterDetails(tel, email, loc1, loc2) }
    }

    fun setPaymentBypass(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPaymentBypass(enabled) }
    }

    fun setBillPrefix(prefix: String) {
        viewModelScope.launch { settingsRepository.setBillPrefix(prefix) }
    }

    fun resetCounter() {
        viewModelScope.launch { settingsRepository.resetCounter() }
    }

    fun setPaperWidth(width: Int) {
        viewModelScope.launch { settingsRepository.setPaperWidth(width) }
    }

    val products = productDao.getAllProducts().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredProducts = combine(products, searchQuery) { productsList, query ->
        if (query.isBlank()) {
            productsList
        } else {
            productsList.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories = productDao.getAllCategories().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isDarkMode = themePreferences.isDarkMode.stateIn(viewModelScope, SharingStarted.Lazily, true)

    val billFooter = orderDao.getSettingFlow("bill_footer").map { it?.value ?: "Thank you for your visit!" }.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val receiptLogoUri = orderDao.getSettingFlow("receipt_logo_uri").map { it?.value }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val shopName = orderDao.getSettingFlow("shop_name").map { it?.value ?: "Easy POS" }.stateIn(viewModelScope, SharingStarted.Lazily, "Easy POS")
    val customerBanner = orderDao.getSettingFlow("customer_banner").map { it?.value ?: "Welcome to our Shop!" }.stateIn(viewModelScope, SharingStarted.Lazily, "Welcome to our Shop!")

    private val _selectedPrinterMac = MutableStateFlow("")
    val selectedPrinterMac = _selectedPrinterMac.asStateFlow()

    private val _updateResult = MutableStateFlow<UpdateManager.UpdateResult?>(null)
    val updateResult = _updateResult.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateResult.value = updateManager.checkForUpdates()
        }
    }

    fun startDownload(url: String) {
        updateManager.downloadAndInstall(url)
    }

    init {
        initializeOrderIndices()
    }

    private fun initializeOrderIndices() {
        viewModelScope.launch {
            // Get current list once to check
            val list = productDao.getAllProductsList()
            // If any index is 0 or there are duplicates, re-index everything sequentially
            val indices = list.map { it.orderIndex }
            if (list.isNotEmpty() && (indices.contains(0) || indices.size != indices.distinct().size)) {
                list.forEachIndexed { index, product ->
                    productDao.updateProduct(product.copy(orderIndex = index + 1))
                }
            }
        }
    }

    fun initPrinter(context: Context) {
        // No-op, using stateless BluetoothPrinterHelper
    }

    fun saveDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.toggleDarkMode(enabled)
        }
    }

    fun saveBillFooter(text: String) {
        viewModelScope.launch {
            orderDao.insertSetting(AppSetting("bill_footer", text))
        }
    }

    fun saveReceiptLogo(context: Context, uriString: String?) {
        viewModelScope.launch {
            val finalUri = if (uriString != null && uriString.startsWith("content://")) {
                saveImageToInternalStorage(context, Uri.parse(uriString), "logo_${System.currentTimeMillis()}")
            } else {
                uriString
            }
            orderDao.insertSetting(AppSetting("receipt_logo_uri", finalUri ?: ""))
        }
    }

    fun saveShopName(name: String) {
        viewModelScope.launch {
            orderDao.insertSetting(AppSetting("shop_name", name))
        }
    }

    fun saveCustomerBanner(banner: String) {
        viewModelScope.launch {
            orderDao.insertSetting(AppSetting("customer_banner", banner))
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveProduct(context: Context, product: ProductEntity) {
        viewModelScope.launch {
            var finalProduct = if (product.imageUri != null && product.imageUri.startsWith("content://")) {
                val internalPath = saveImageToInternalStorage(context, Uri.parse(product.imageUri), "prod_${System.currentTimeMillis()}")
                product.copy(imageUri = internalPath)
            } else {
                product
            }
            
            // Simplified logic: Ensure orderIndex is assigned correctly for new products
            if (finalProduct.id == 0L) {
                val maxIndex = productDao.getMaxOrderIndex() ?: 0
                finalProduct = finalProduct.copy(orderIndex = maxIndex + 1)
            }
            
            productDao.insertProduct(finalProduct)
        }
    }

    fun moveProduct(fromProduct: ProductEntity, toProduct: ProductEntity) {
        viewModelScope.launch {
            productDao.swapProductOrder(
                id1 = fromProduct.id,
                index1 = fromProduct.orderIndex,
                id2 = toProduct.id,
                index2 = toProduct.orderIndex
            )
        }
    }

    fun moveProductUp(product: ProductEntity) {
        viewModelScope.launch {
            // Get the FRESH full list ordered by orderIndex
            val allProducts = productDao.getAllProductsList()
            val currentIndex = allProducts.indexOfFirst { it.id == product.id }
            if (currentIndex > 0) {
                val previousProduct = allProducts[currentIndex - 1]
                // SWAP: Use indices from the current list to avoid collisions
                productDao.swapProductOrder(
                    id1 = product.id,
                    index1 = product.orderIndex,
                    id2 = previousProduct.id,
                    index2 = previousProduct.orderIndex
                )
            }
        }
    }

    fun moveProductDown(product: ProductEntity) {
        viewModelScope.launch {
            // Get the FRESH full list ordered by orderIndex
            val allProducts = productDao.getAllProductsList()
            val currentIndex = allProducts.indexOfFirst { it.id == product.id }
            if (currentIndex >= 0 && currentIndex < allProducts.size - 1) {
                val nextProduct = allProducts[currentIndex + 1]
                // SWAP: Continuous swapping across the entire list
                productDao.swapProductOrder(
                    id1 = product.id,
                    index1 = product.orderIndex,
                    id2 = nextProduct.id,
                    index2 = nextProduct.orderIndex
                )
            }
        }
    }

    private fun saveImageToInternalStorage(context: Context, uri: Uri, fileName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "$fileName.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch { productDao.deleteProduct(product) }
    }

    // Category Management
    fun saveCategory(name: String) {
        viewModelScope.launch {
            productDao.insertCategory(CategoryEntity(name = name))
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            productDao.updateCategory(category)
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            productDao.deleteCategory(category)
        }
    }

    @Serializable
    data class BackupMetadata(
        val version: String,
        val versionCode: Int,
        val timestamp: Long
    )

    suspend fun fullSystemBackup(context: Context): String? = withContext(Dispatchers.IO) {
        val dbPath = context.getDatabasePath("easypos_database")
        val dbShm = File(dbPath.path + "-shm")
        val dbWal = File(dbPath.path + "-wal")
        val filesDir = context.filesDir
        
        val zipFileName = "EasyPOS_FullBackup_${System.currentTimeMillis()}.zip"
        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val zipFile = File(downloadsFolder, zipFileName)
        
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                // Metadata
                val metadata = BackupMetadata(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, System.currentTimeMillis())
                val metadataJson = Json.encodeToString(metadata)
                val metadataEntry = ZipEntry("backup_metadata.json")
                zos.putNextEntry(metadataEntry)
                zos.write(metadataJson.toByteArray())
                zos.closeEntry()

                listOf(dbPath, dbShm, dbWal).forEach { file ->
                    if (file.exists()) {
                        addToZip(file, "database/${file.name}", zos)
                    }
                }
                // Recursively add all files and subdirectories (e.g. datastore preferences)
                addFolderToZip(filesDir, "files", zos)
            }
            zipFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun addFolderToZip(folder: File, baseZipPath: String, zos: ZipOutputStream) {
        folder.listFiles()?.forEach { file ->
            val zipPath = "$baseZipPath/${file.name}"
            if (file.isDirectory) {
                addFolderToZip(file, zipPath, zos)
            } else if (file.isFile) {
                addToZip(file, zipPath, zos)
            }
        }
    }

    private fun copyDirectoryRecursively(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { child ->
                copyDirectoryRecursively(child, File(target, child.name))
            }
        } else if (source.isFile) {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    private fun addToZip(file: File, zipPath: String, zos: ZipOutputStream) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(zipPath)
            zos.putNextEntry(entry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    suspend fun fullSystemRestore(context: Context, zipUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dbPath = context.getDatabasePath("easypos_database")
        val filesDir = context.filesDir
        val tempDir = File(context.cacheDir, "restore_temp")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            // Step 1: Extract to temp and check metadata
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val targetFile = File(tempDir, entry.name)
                        targetFile.parentFile?.mkdirs()
                        if (!entry.isDirectory) {
                            FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Validate Metadata
            val metadataFile = File(tempDir, "backup_metadata.json")
            if (metadataFile.exists()) {
                val metadata = Json.decodeFromString<BackupMetadata>(metadataFile.readText())
                Log.d("Restore", "Restoring from version: ${metadata.version}")
            }

            // Step 2: Close DB
            withContext(Dispatchers.Main) {
                fyi.copiercode.easypos.data.database.AppDatabase.closeDatabase()
            }

            // Step 3: Overwrite Database and Files
            val dbTempDir = File(tempDir, "database")
            if (dbTempDir.exists()) {
                dbTempDir.listFiles()?.forEach { file ->
                    file.copyTo(File(dbPath.parentFile, file.name), overwrite = true)
                }
            }
            
            val filesTempDir = File(tempDir, "files")
            if (filesTempDir.exists()) {
                copyDirectoryRecursively(filesTempDir, filesDir)
            }

            tempDir.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e("Restore", "Restore failed", e)
            tempDir.deleteRecursively()
            false
        }
    }

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        exitProcess(0)
    }
}
