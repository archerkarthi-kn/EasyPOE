package fyi.copiercode.easypos.server

import android.content.Context
import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fyi.copiercode.easypos.data.PairedDevice
import fyi.copiercode.easypos.data.SettingsRepository
import fyi.copiercode.easypos.data.dao.OrderDao
import fyi.copiercode.easypos.data.dao.ProductDao
import fyi.copiercode.easypos.data.entity.CategoryEntity
import fyi.copiercode.easypos.data.entity.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class PosWebServer(
    private val context: Context,
    private val orderDao: OrderDao,
    private val productDao: ProductDao,
    private val settingsRepository: SettingsRepository,
    port: Int = 8080
) : NanoHTTPD(port) {

    @Serializable
    data class PairResponse(
        val status: String,
        val token: String? = null,
        val message: String? = null
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        val files = HashMap<String, String>()
        if (session.method == Method.POST) {
            try {
                session.parseBody(files)
            } catch (e: Exception) {
                Log.e("PosWebServer", "Failed to parse POST body", e)
            }
        }
        val params = session.parameters

        when (uri) {
            "/pair" -> {
                val pinParam = params["pin"]?.firstOrNull() ?: ""
                val deviceName = params["name"]?.firstOrNull() ?: "Windows Audit PC"
                val currentPin = runBlocking { settingsRepository.bridgePin.first() }

                if (pinParam == currentPin) {
                    val newToken = UUID.randomUUID().toString()
                    val newDevice = PairedDevice(token = newToken, name = deviceName)
                    runBlocking {
                        settingsRepository.setBridgeToken(newToken)
                        settingsRepository.addPairedDevice(newDevice)
                    }
                    
                    val responseJson = Json.encodeToString(PairResponse(status = "success", token = newToken))
                    return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
                } else {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Invalid PIN"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }
            }
            "/api/image" -> {
                val path = params["path"]?.firstOrNull() ?: ""
                if (path.isNotBlank()) {
                    val file = File(path)
                    if (file.exists()) {
                        try {
                            val fis = FileInputStream(file)
                            return newFixedLengthResponse(Response.Status.OK, "image/jpeg", fis, file.length())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Image not found")
            }
            "/api/audit" -> {
                val tokenParam = params["token"]?.firstOrNull() ?: ""
                val isPaired = runBlocking { settingsRepository.isDevicePaired(tokenParam) }

                if (tokenParam.isBlank() || !isPaired) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Unauthorized"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }

                val records = runBlocking {
                    orderDao.getFilteredAuditRecords(null, null, null, null, null).first()
                }

                val responseJson = Json.encodeToString(records)
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
            }
            "/api/products" -> {
                val tokenParam = params["token"]?.firstOrNull() ?: ""
                val isPaired = runBlocking { settingsRepository.isDevicePaired(tokenParam) }

                if (tokenParam.isBlank() || !isPaired) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Unauthorized"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }

                val productsList = runBlocking { productDao.getAllProductsList() }
                val responseJson = Json.encodeToString(productsList)
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
            }
            "/api/categories" -> {
                val tokenParam = params["token"]?.firstOrNull() ?: ""
                val isPaired = runBlocking { settingsRepository.isDevicePaired(tokenParam) }

                if (tokenParam.isBlank() || !isPaired) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Unauthorized"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }

                val categoriesList = runBlocking { productDao.getAllCategories().first() }
                val responseJson = Json.encodeToString(categoriesList)
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
            }
            "/api/categories/save" -> {
                val tokenParam = params["token"]?.firstOrNull() ?: ""
                val isPaired = runBlocking { settingsRepository.isDevicePaired(tokenParam) }

                if (tokenParam.isBlank() || !isPaired) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Unauthorized"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }

                val name = params["name"]?.firstOrNull() ?: ""
                if (name.isNotBlank()) {
                    runBlocking { productDao.insertCategory(CategoryEntity(name = name)) }
                }

                val responseJson = Json.encodeToString(PairResponse(status = "success", message = "Category saved"))
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
            }
            "/api/products/save" -> {
                val tokenParam = params["token"]?.firstOrNull() ?: ""
                val isPaired = runBlocking { settingsRepository.isDevicePaired(tokenParam) }

                if (tokenParam.isBlank() || !isPaired) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Unauthorized"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }

                val id = params["id"]?.firstOrNull()?.toLongOrNull() ?: 0L
                val name = params["name"]?.firstOrNull() ?: ""
                val brand = params["brand"]?.firstOrNull() ?: "General"
                val price = params["price"]?.firstOrNull()?.toDoubleOrNull() ?: 0.0
                val categoryId = params["categoryId"]?.firstOrNull()?.toLongOrNull() ?: 0L
                val barcode = params["barcode"]?.firstOrNull()
                val imageBase64 = params["imageBase64"]?.firstOrNull() ?: files["postData"]

                if (name.isBlank()) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Name is required"))
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", responseJson)
                }

                runBlocking {
                    val existing = if (id != 0L) productDao.getProductById(id) else null
                    var newImageUri: String? = existing?.imageUri

                    if (!imageBase64.isNullOrBlank() && imageBase64.contains("base64,")) {
                        try {
                            val base64Data = imageBase64.substringAfter("base64,")
                            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                            val imgFile = File(context.filesDir, "web_img_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(imgFile).use { it.write(decodedBytes) }
                            newImageUri = imgFile.absolutePath
                        } catch (e: Exception) {
                            Log.e("PosWebServer", "Failed to save Base64 image", e)
                        }
                    }

                    val product = existing?.copy(
                        name = name,
                        brand = brand,
                        basePrice = price,
                        categoryId = categoryId,
                        barcode = barcode,
                        imageUri = newImageUri
                    ) ?: ProductEntity(
                        id = id,
                        name = name,
                        brand = brand,
                        basePrice = price,
                        categoryId = categoryId,
                        barcode = barcode,
                        imageUri = newImageUri
                    )
                    productDao.insertProduct(product)
                }

                val responseJson = Json.encodeToString(PairResponse(status = "success", message = "Product saved"))
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
            }
            "/api/products/delete" -> {
                val tokenParam = params["token"]?.firstOrNull() ?: ""
                val isPaired = runBlocking { settingsRepository.isDevicePaired(tokenParam) }

                if (tokenParam.isBlank() || !isPaired) {
                    val responseJson = Json.encodeToString(PairResponse(status = "error", message = "Unauthorized"))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", responseJson)
                }

                val id = params["id"]?.firstOrNull()?.toLongOrNull() ?: 0L
                if (id != 0L) {
                    runBlocking {
                        val product = productDao.getProductById(id)
                        if (product != null) productDao.deleteProduct(product)
                    }
                }

                val responseJson = Json.encodeToString(PairResponse(status = "success", message = "Product deleted"))
                return newFixedLengthResponse(Response.Status.OK, "application/json", responseJson)
            }
            "/", "/index.html" -> {
                val html = getProfessionalWebUiHtml()
                return newFixedLengthResponse(Response.Status.OK, "text/html", html)
            }
            else -> {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "<h3>404 Not Found</h3>")
            }
        }
    }

    private fun getProfessionalWebUiHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Easy POS - Enterprise Admin & Audit Gateway</title>
            <script src="https://cdn.tailwindcss.com"></script>
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;900&display=swap" rel="stylesheet">
            <style>
                body { font-family: 'Inter', sans-serif; }
            </style>
        </head>
        <body class="bg-slate-950 text-slate-100 min-h-screen flex flex-col justify-between selection:bg-indigo-500 selection:text-white">
            <header class="bg-slate-900 border-b border-slate-800 px-4 md:px-8 py-4 flex items-center justify-between shadow-xl sticky top-0 z-50">
                <div class="flex items-center space-x-3">
                    <div class="bg-gradient-to-tr from-indigo-600 to-violet-600 text-white p-2.5 rounded-2xl font-black text-xl tracking-wider shadow-lg shadow-indigo-600/30">POS</div>
                    <div>
                        <h1 class="text-lg font-bold text-white">Easy POS Web Admin</h1>
                        <p class="text-xs text-slate-400">PC Product & Price Manager • Audit Gateway</p>
                    </div>
                </div>
                <div id="connection-status" class="flex items-center space-x-2 bg-rose-500/10 text-rose-400 px-4 py-2 rounded-full text-xs font-semibold border border-rose-500/20 shadow-sm">
                    <span class="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse"></span>
                    <span>Unpaired Device</span>
                </div>
            </header>

            <main class="flex-1 max-w-7xl w-full mx-auto p-4 md:p-8">
                <!-- Pairing Card -->
                <div id="pair-card" class="max-w-md mx-auto bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl mt-12">
                    <div class="text-center mb-6">
                        <div class="inline-flex p-4 bg-indigo-500/10 text-indigo-400 rounded-2xl mb-3 shadow-inner">
                            <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/></svg>
                        </div>
                        <h2 class="text-2xl font-black text-white">Terminal Pairing</h2>
                        <p class="text-sm text-slate-400 mt-1">Enter your 4-digit PIN from Android POS Admin Settings to pair this PC.</p>
                    </div>
                    <form id="pair-form" onsubmit="handlePair(event)" class="space-y-5">
                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">Device Name / PC ID</label>
                            <input type="text" id="device-name-input" value="Windows Admin PC" required class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                        </div>
                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">4-Digit Pairing PIN</label>
                            <input type="text" id="pin-input" maxlength="4" placeholder="••••" required class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-center text-2xl tracking-widest text-white focus:outline-none focus:border-indigo-500 transition font-mono shadow-inner">
                        </div>
                        <button type="submit" class="w-full bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white font-bold py-3.5 rounded-xl transition shadow-xl shadow-indigo-600/30">Authorize & Pair Device</button>
                        <p id="pair-error" class="text-xs text-rose-400 text-center hidden font-medium"></p>
                    </form>
                </div>

                <!-- Main Dashboard View -->
                <div id="dashboard-view" class="hidden space-y-8">
                    <!-- Tab Switcher & Disconnect -->
                    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                        <div class="flex space-x-2 bg-slate-900 p-1.5 rounded-2xl border border-slate-800">
                            <button id="tab-btn-products" onclick="switchTab('products')" class="px-5 py-2.5 rounded-xl text-sm font-bold transition bg-indigo-600 text-white shadow-md">Products & Pricing</button>
                            <button id="tab-btn-audit" onclick="switchTab('audit')" class="px-5 py-2.5 rounded-xl text-sm font-bold transition text-slate-400 hover:text-white">Audit & Sales</button>
                        </div>
                        <div class="flex items-center space-x-3">
                            <button onclick="unpair()" class="bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 px-4 py-2.5 rounded-xl text-sm font-semibold transition border border-rose-500/20">Disconnect PC</button>
                        </div>
                    </div>

                    <!-- PRODUCTS & PRICING TAB -->
                    <div id="section-products" class="space-y-6">
                        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
                            <div>
                                <h2 class="text-2xl font-black text-white">Product & Price Manager</h2>
                                <p class="text-sm text-slate-400">Add products, edit prices, upload images, and choose categories directly from your computer.</p>
                            </div>
                            <div class="flex flex-wrap items-center gap-3">
                                <button onclick="openProductModal()" class="bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white px-5 py-2.5 rounded-xl text-sm font-bold shadow-lg shadow-indigo-600/30 flex items-center space-x-2">
                                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/></svg>
                                    <span>Add New Product</span>
                                </button>
                                <button onclick="fetchProducts(); fetchCategories();" class="bg-slate-900 hover:bg-slate-800 text-slate-200 px-4 py-2.5 rounded-xl text-sm font-semibold border border-slate-800">Refresh List</button>
                            </div>
                        </div>

                        <!-- Product Table Container with Horizontal Scroll -->
                        <div class="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
                            <div class="overflow-x-auto w-full">
                                <table class="w-full text-left border-collapse min-w-[700px]">
                                    <thead>
                                        <tr class="bg-slate-950/60 border-b border-slate-800 text-xs font-semibold uppercase tracking-wider text-slate-400">
                                            <th class="px-4 py-4 w-16 text-center">Image</th>
                                            <th class="px-6 py-4">Product Name</th>
                                            <th class="px-6 py-4">Category</th>
                                            <th class="px-6 py-4">Barcode</th>
                                            <th class="px-6 py-4 text-right">Base Price ($)</th>
                                            <th class="px-6 py-4 text-center">Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody id="products-table-body" class="divide-y divide-slate-800 text-sm text-slate-300">
                                        <!-- Loaded dynamically -->
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>

                    <!-- AUDIT & SALES TAB -->
                    <div id="section-audit" class="hidden space-y-6">
                        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
                            <div>
                                <h2 class="text-2xl font-black text-white">Audit & Sales Analytics</h2>
                                <p class="text-sm text-slate-400">Real-time SQLite database synchronization with Windows PC.</p>
                            </div>
                            <div class="flex flex-wrap items-center gap-3">
                                <button onclick="fetchAuditData()" class="bg-slate-900 hover:bg-slate-800 text-slate-200 px-4 py-2.5 rounded-xl text-sm font-semibold transition border border-slate-800">Refresh Logs</button>
                                <button onclick="downloadJson()" class="bg-indigo-600 hover:bg-indigo-500 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition shadow-lg shadow-indigo-600/25">Export JSON</button>
                                <button onclick="downloadCsv()" class="bg-emerald-600 hover:bg-emerald-500 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition shadow-lg shadow-emerald-600/25">Export CSV</button>
                            </div>
                        </div>

                        <!-- Filter Bar -->
                        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-4">
                            <div>
                                <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Date Filter</label>
                                <select id="filter-date" onchange="toggleDateFilter()" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                                    <option value="all">All Time</option>
                                    <option value="today">Today</option>
                                    <option value="month">This Month</option>
                                    <option value="year">This Year</option>
                                    <option value="custom">Custom Range</option>
                                </select>
                            </div>
                            <div id="custom-date-box" class="hidden sm:col-span-2 grid grid-cols-2 gap-2">
                                <div>
                                    <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Start Date</label>
                                    <input type="date" id="filter-start" onchange="applyFilters()" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                                </div>
                                <div>
                                    <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">End Date</label>
                                    <input type="date" id="filter-end" onchange="applyFilters()" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                                </div>
                            </div>
                            <div>
                                <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Payment Method</label>
                                <select id="filter-payment" onchange="applyFilters()" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                                    <option value="all">All Payment Types</option>
                                    <option value="CASH">Cash</option>
                                    <option value="CARD">Card</option>
                                </select>
                            </div>
                            <div>
                                <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Brand / Category</label>
                                <select id="filter-brand" onchange="applyFilters()" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                                    <option value="all">All Brands</option>
                                </select>
                            </div>
                            <div>
                                <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">Search Item</label>
                                <input type="text" id="filter-search" oninput="applyFilters()" placeholder="Search item name..." class="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2.5 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                            </div>
                            <div class="flex items-end">
                                <button onclick="resetFilters()" class="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold py-2.5 rounded-xl transition text-sm border border-slate-700">Reset Filters</button>
                            </div>
                        </div>

                        <!-- Stats Cards -->
                        <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
                            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                                <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Total Transactions</p>
                                <p id="stat-count" class="text-3xl font-black text-white mt-2">0</p>
                            </div>
                            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                                <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Total Revenue</p>
                                <p id="stat-revenue" class="text-3xl font-black text-emerald-400 mt-2">${'$'}0.00</p>
                            </div>
                            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                                <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Units Sold</p>
                                <p id="stat-units" class="text-3xl font-black text-indigo-400 mt-2">0</p>
                            </div>
                            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
                                <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Average Order Value</p>
                                <p id="stat-avg" class="text-3xl font-black text-violet-400 mt-2">${'$'}0.00</p>
                            </div>
                        </div>

                        <!-- Audit Data Table -->
                        <div class="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
                            <div class="overflow-x-auto w-full">
                                <table class="w-full text-left border-collapse min-w-[700px]">
                                    <thead>
                                        <tr class="bg-slate-950/50 border-b border-slate-800 text-xs font-semibold uppercase tracking-wider text-slate-400">
                                            <th class="px-6 py-4">Order ID</th>
                                            <th class="px-6 py-4">Timestamp</th>
                                            <th class="px-6 py-4">Item Name</th>
                                            <th class="px-6 py-4">Brand</th>
                                            <th class="px-6 py-4 text-center">Qty</th>
                                            <th class="px-6 py-4 text-right">Price</th>
                                            <th class="px-6 py-4">Payment</th>
                                        </tr>
                                    </thead>
                                    <tbody id="audit-table-body" class="divide-y divide-slate-800 text-sm text-slate-300">
                                        <!-- Loaded dynamically -->
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </main>

            <!-- Product Edit Modal -->
            <div id="product-modal" class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 hidden">
                <div class="bg-slate-900 border border-slate-800 rounded-3xl p-8 max-w-lg w-full shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
                    <h3 id="modal-title" class="text-2xl font-black text-white">Add / Edit Product</h3>
                    <input type="hidden" id="edit-product-id" value="0">
                    <div class="space-y-4">
                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">Product Name</label>
                            <input type="text" id="prod-name" placeholder="e.g. Bullet Beer 500ml" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500">
                        </div>

                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">Category (Machine Categories)</label>
                            <div class="flex space-x-2">
                                <select id="prod-category-select" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500">
                                    <option value="0">General</option>
                                </select>
                                <button type="button" onclick="promptNewCategory()" class="px-3 py-2.5 bg-slate-800 hover:bg-slate-700 text-indigo-400 text-xs font-bold rounded-xl border border-slate-700 whitespace-nowrap">+ New Cat</button>
                            </div>
                        </div>

                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">Selling Price ($)</label>
                            <input type="number" step="0.01" id="prod-price" placeholder="0.00" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500">
                        </div>

                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">Barcode (Optional)</label>
                            <input type="text" id="prod-barcode" placeholder="e.g. 89012345678" class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 font-mono">
                        </div>

                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">Product Image (Optional)</label>
                            <input type="file" id="prod-image-file" accept="image/*" onchange="handleImageSelect(event)" class="w-full text-xs text-slate-400 file:mr-3 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-600 file:text-white hover:file:bg-indigo-500 cursor-pointer">
                            <div id="image-preview-container" class="mt-2 hidden flex items-center space-x-3">
                                <img id="image-preview" src="" class="w-16 h-16 object-cover rounded-xl border border-slate-800 shadow">
                                <span class="text-xs text-slate-400">Image selected</span>
                            </div>
                        </div>
                    </div>
                    <div class="flex justify-end space-x-3 pt-2">
                        <button onclick="closeProductModal()" class="px-5 py-2.5 rounded-xl text-sm font-semibold text-slate-400 hover:bg-slate-800">Cancel</button>
                        <button onclick="saveProduct()" class="bg-indigo-600 hover:bg-indigo-500 text-white px-6 py-2.5 rounded-xl text-sm font-bold shadow-lg shadow-indigo-600/30">Save Product</button>
                    </div>
                </div>
            </div>

            <footer class="bg-slate-900 border-t border-slate-800 py-4 px-8 text-center text-xs text-slate-500">
                Easy POS Enterprise Web Admin • Local Network PC Management
            </footer>

            <script>
                let cachedAuditData = [];
                let cachedProducts = [];
                let cachedCategories = [];
                let currentBase64Image = null;

                window.addEventListener('DOMContentLoaded', () => {
                    const token = localStorage.getItem('bridge_token');
                    if (token) {
                        showDashboard();
                        fetchCategories();
                        fetchProducts();
                        fetchAuditData();
                    }
                });

                function switchTab(tab) {
                    const btnProd = document.getElementById('tab-btn-products');
                    const btnAudit = document.getElementById('tab-btn-audit');
                    const secProd = document.getElementById('section-products');
                    const secAudit = document.getElementById('section-audit');

                    if (tab === 'products') {
                        btnProd.className = "px-5 py-2.5 rounded-xl text-sm font-bold transition bg-indigo-600 text-white shadow-md";
                        btnAudit.className = "px-5 py-2.5 rounded-xl text-sm font-bold transition text-slate-400 hover:text-white";
                        secProd.classList.remove('hidden');
                        secAudit.classList.add('hidden');
                    } else {
                        btnAudit.className = "px-5 py-2.5 rounded-xl text-sm font-bold transition bg-indigo-600 text-white shadow-md";
                        btnProd.className = "px-5 py-2.5 rounded-xl text-sm font-bold transition text-slate-400 hover:text-white";
                        secAudit.classList.remove('hidden');
                        secProd.classList.add('hidden');
                    }
                }

                async function handlePair(e) {
                    e.preventDefault();
                    const pin = document.getElementById('pin-input').value;
                    const name = document.getElementById('device-name-input').value;
                    const errEl = document.getElementById('pair-error');
                    errEl.classList.add('hidden');

                    try {
                        const res = await fetch('/pair?pin=' + encodeURIComponent(pin) + '&name=' + encodeURIComponent(name));
                        const data = await res.json();
                        if (data.status === 'success' && data.token) {
                            localStorage.setItem('bridge_token', data.token);
                            showDashboard();
                            fetchCategories();
                            fetchProducts();
                            fetchAuditData();
                        } else {
                            errEl.textContent = data.message || 'Invalid Pairing PIN';
                            errEl.classList.remove('hidden');
                        }
                    } catch (err) {
                        errEl.textContent = 'Connection error. Please check network.';
                        errEl.classList.remove('hidden');
                    }
                }

                async function fetchCategories() {
                    const token = localStorage.getItem('bridge_token');
                    if (!token) return;

                    try {
                        const res = await fetch('/api/categories?token=' + encodeURIComponent(token));
                        if (res.status === 401) return unpair();
                        const data = await res.json();
                        cachedCategories = data;
                        renderCategoryDropdown(data);
                    } catch (err) {
                        console.error('Failed to fetch categories', err);
                    }
                }

                function renderCategoryDropdown(categories) {
                    const select = document.getElementById('prod-category-select');
                    select.innerHTML = '<option value="0">General</option>';
                    categories.forEach(c => {
                        const opt = document.createElement('option');
                        opt.value = c.id;
                        opt.textContent = c.name;
                        select.appendChild(opt);
                    });
                }

                async function promptNewCategory() {
                    const name = prompt("Enter New Category Name:");
                    if (!name) return;

                    const token = localStorage.getItem('bridge_token');
                    try {
                        const res = await fetch('/api/categories/save?token=' + encodeURIComponent(token) + '&name=' + encodeURIComponent(name), { method: 'POST' });
                        const data = await res.json();
                        if (data.status === 'success') {
                            await fetchCategories();
                        }
                    } catch (err) {
                        alert("Failed to save category");
                    }
                }

                async function fetchProducts() {
                    const token = localStorage.getItem('bridge_token');
                    if (!token) return;

                    try {
                        const res = await fetch('/api/products?token=' + encodeURIComponent(token));
                        if (res.status === 401) return unpair();
                        const data = await res.json();
                        cachedProducts = data;
                        renderProducts(data);
                    } catch (err) {
                        console.error('Failed to fetch products', err);
                    }
                }

                function renderProducts(products) {
                    const tbody = document.getElementById('products-table-body');
                    tbody.innerHTML = '';

                    if (!products.length) {
                        tbody.innerHTML = '<tr><td colspan="6" class="text-center py-8 text-slate-500 font-medium">No products found. Click "+ Add New Product" to create one.</td></tr>';
                        return;
                    }

                    products.forEach(p => {
                        const cat = cachedCategories.find(c => c.id === p.categoryId);
                        const catName = cat ? cat.name : (p.brand || "General");
                        const imgTag = p.imageUri 
                            ? `<img src="/api/image?path=${'$'}{encodeURIComponent(p.imageUri)}" class="w-10 h-10 object-cover rounded-lg border border-slate-800 mx-auto">`
                            : `<div class="w-10 h-10 bg-slate-800 text-slate-500 rounded-lg flex items-center justify-center text-xs font-bold mx-auto">POS</div>`;

                        const tr = document.createElement('tr');
                        tr.className = 'hover:bg-slate-800/50 transition';
                        tr.innerHTML = `
                            <td class="px-4 py-3 text-center">${'$'}{imgTag}</td>
                            <td class="px-6 py-3 font-bold text-white">${'$'}{p.name}</td>
                            <td class="px-6 py-3"><span class="px-2.5 py-1 bg-indigo-500/10 text-indigo-400 rounded-lg text-xs font-semibold border border-indigo-500/20">${'$'}{catName}</span></td>
                            <td class="px-6 py-3 font-mono text-xs text-slate-400">${'$'}{p.barcode || "—"}</td>
                            <td class="px-6 py-3 text-right font-mono font-bold text-emerald-400 text-base">$${'$'}{p.basePrice.toFixed(2)}</td>
                            <td class="px-6 py-3 text-center space-x-2">
                                <button onclick="editProduct(${'$'}{p.id})" class="px-3 py-1.5 bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 rounded-lg text-xs font-bold border border-indigo-500/20 transition">Edit</button>
                                <button onclick="deleteProduct(${'$'}{p.id})" class="px-3 py-1.5 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 rounded-lg text-xs font-bold border border-rose-500/20 transition">Delete</button>
                            </td>
                        `;
                        tbody.appendChild(tr);
                    });
                }

                function openProductModal() {
                    document.getElementById('edit-product-id').value = "0";
                    document.getElementById('prod-name').value = "";
                    document.getElementById('prod-category-select').value = "0";
                    document.getElementById('prod-price').value = "";
                    document.getElementById('prod-barcode').value = "";
                    document.getElementById('prod-image-file').value = "";
                    currentBase64Image = null;
                    document.getElementById('image-preview-container').classList.add('hidden');
                    document.getElementById('modal-title').textContent = "Add New Product";
                    document.getElementById('product-modal').classList.remove('hidden');
                }

                function editProduct(id) {
                    const p = cachedProducts.find(x => x.id === id);
                    if (!p) return;
                    document.getElementById('edit-product-id').value = p.id;
                    document.getElementById('prod-name').value = p.name;
                    document.getElementById('prod-category-select').value = p.categoryId || "0";
                    document.getElementById('prod-price').value = p.basePrice;
                    document.getElementById('prod-barcode').value = p.barcode || "";
                    document.getElementById('prod-image-file').value = "";
                    currentBase64Image = null;

                    if (p.imageUri) {
                        document.getElementById('image-preview').src = "/api/image?path=" + encodeURIComponent(p.imageUri);
                        document.getElementById('image-preview-container').classList.remove('hidden');
                    } else {
                        document.getElementById('image-preview-container').classList.add('hidden');
                    }

                    document.getElementById('modal-title').textContent = "Edit Product: " + p.name;
                    document.getElementById('product-modal').classList.remove('hidden');
                }

                function closeProductModal() {
                    document.getElementById('product-modal').classList.add('hidden');
                }

                function handleImageSelect(event) {
                    const file = event.target.files[0];
                    if (!file) return;
                    const reader = new FileReader();
                    reader.onload = function(e) {
                        currentBase64Image = e.target.result;
                        document.getElementById('image-preview').src = currentBase64Image;
                        document.getElementById('image-preview-container').classList.remove('hidden');
                    };
                    reader.readAsDataURL(file);
                }

                async function saveProduct() {
                    const token = localStorage.getItem('bridge_token');
                    const id = document.getElementById('edit-product-id').value;
                    const name = document.getElementById('prod-name').value;
                    const categoryId = document.getElementById('prod-category-select').value;
                    const catText = document.getElementById('prod-category-select').options[document.getElementById('prod-category-select').selectedIndex].text;
                    const price = document.getElementById('prod-price').value;
                    const barcode = document.getElementById('prod-barcode').value;

                    if (!name || !price) return alert("Product name and price are required!");

                    let url = `/api/products/save?token=${'$'}{encodeURIComponent(token)}&id=${'$'}{id}&name=${'$'}{encodeURIComponent(name)}&brand=${'$'}{encodeURIComponent(catText)}&price=${'$'}{price}&categoryId=${'$'}{categoryId}&barcode=${'$'}{encodeURIComponent(barcode)}`;
                    
                    let bodyData = null;
                    if (currentBase64Image) {
                        bodyData = "imageBase64=" + encodeURIComponent(currentBase64Image);
                    }

                    try {
                        const res = await fetch(url, { 
                            method: 'POST',
                            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                            body: bodyData
                        });
                        const data = await res.json();
                        if (data.status === 'success') {
                            closeProductModal();
                            await fetchProducts();
                        } else {
                            alert(data.message || "Failed to save product");
                        }
                    } catch (err) {
                        alert("Network error while saving product");
                    }
                }

                async function deleteProduct(id) {
                    if (!confirm("Are you sure you want to delete this product?")) return;
                    const token = localStorage.getItem('bridge_token');
                    try {
                        const res = await fetch(`/api/products/delete?token=${'$'}{encodeURIComponent(token)}&id=${'$'}{id}`, { method: 'POST' });
                        const data = await res.json();
                        if (data.status === 'success') {
                            await fetchProducts();
                        }
                    } catch (err) {
                        alert("Failed to delete product");
                    }
                }

                async function fetchAuditData() {
                    const token = localStorage.getItem('bridge_token');
                    if (!token) return;

                    try {
                        const res = await fetch('/api/audit?token=' + encodeURIComponent(token));
                        if (res.status === 401) {
                            unpair();
                            return;
                        }
                        const data = await res.json();
                        cachedAuditData = data;
                        populateBrandDropdown(data);
                        applyFilters();
                    } catch (err) {
                        console.error('Failed to fetch audit records', err);
                    }
                }

                function populateBrandDropdown(records) {
                    const brandSelect = document.getElementById('filter-brand');
                    const currentVal = brandSelect.value;
                    const brands = [...new Set(records.map(r => r.brand))].filter(Boolean).sort();
                    
                    brandSelect.innerHTML = '<option value="all">All Brands</option>';
                    brands.forEach(b => {
                        const opt = document.createElement('option');
                        opt.value = b;
                        opt.textContent = b;
                        if (b === currentVal) opt.selected = true;
                        brandSelect.appendChild(opt);
                    });
                }

                function toggleDateFilter() {
                    const val = document.getElementById('filter-date').value;
                    const box = document.getElementById('custom-date-box');
                    if (val === 'custom') {
                        box.classList.remove('hidden');
                    } else {
                        box.classList.add('hidden');
                        document.getElementById('filter-start').value = '';
                        document.getElementById('filter-end').value = '';
                    }
                    applyFilters();
                }

                function applyFilters() {
                    const dateFilter = document.getElementById('filter-date').value;
                    const startVal = document.getElementById('filter-start').value;
                    const endVal = document.getElementById('filter-end').value;
                    const paymentFilter = document.getElementById('filter-payment').value;
                    const brandFilter = document.getElementById('filter-brand').value;
                    const searchQuery = document.getElementById('filter-search').value.toLowerCase();

                    const now = new Date();
                    const todayMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
                    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
                    const yearStart = new Date(now.getFullYear(), 0, 1).getTime();

                    let customStart = startVal ? new Date(startVal).setHours(0, 0, 0, 0) : 0;
                    let customEnd = endVal ? new Date(endVal).setHours(23, 59, 59, 999) : Number.MAX_SAFE_INTEGER;

                    const filtered = cachedAuditData.filter(r => {
                        if (dateFilter === 'today' && r.timestamp < todayMidnight) return false;
                        if (dateFilter === 'month' && r.timestamp < monthStart) return false;
                        if (dateFilter === 'year' && r.timestamp < yearStart) return false;
                        if (dateFilter === 'custom') {
                            if (startVal && r.timestamp < customStart) return false;
                            if (endVal && r.timestamp > customEnd) return false;
                        }

                        if (paymentFilter !== 'all' && r.paymentMethod !== paymentFilter) return false;
                        if (brandFilter !== 'all' && r.brand !== brandFilter) return false;
                        if (searchQuery && !r.name.toLowerCase().includes(searchQuery) && !r.orderId.toLowerCase().includes(searchQuery)) return false;

                        return true;
                    });

                    renderDashboard(filtered);
                }

                function resetFilters() {
                    document.getElementById('filter-date').value = 'all';
                    document.getElementById('custom-date-box').classList.add('hidden');
                    document.getElementById('filter-start').value = '';
                    document.getElementById('filter-end').value = '';
                    document.getElementById('filter-payment').value = 'all';
                    document.getElementById('filter-brand').value = 'all';
                    document.getElementById('filter-search').value = '';
                    applyFilters();
                }

                function renderDashboard(records) {
                    const tbody = document.getElementById('audit-table-body');
                    tbody.innerHTML = '';

                    let totalRev = 0;
                    let totalUnits = 0;
                    const uniqueOrders = new Set();

                    records.forEach(r => {
                        totalRev += r.priceAtSale;
                        totalUnits += r.quantity;
                        uniqueOrders.add(r.orderId);

                        const dateStr = new Date(r.timestamp).toLocaleString();
                        const tr = document.createElement('tr');
                        tr.className = 'hover:bg-slate-800/50 transition';
                        tr.innerHTML = `
                            <td class="px-6 py-4 font-mono font-medium text-indigo-400">${'$'}{r.orderId}</td>
                            <td class="px-6 py-4 text-slate-400">${'$'}{dateStr}</td>
                            <td class="px-6 py-4 font-semibold text-white">${'$'}{r.name}</td>
                            <td class="px-6 py-4">${'$'}{r.brand}</td>
                            <td class="px-6 py-4 text-center">${'$'}{r.quantity}</td>
                            <td class="px-6 py-4 text-right font-mono font-semibold text-emerald-400">$${'$'}{r.priceAtSale.toFixed(2)}</td>
                            <td class="px-6 py-4"><span class="px-2.5 py-1 rounded-lg text-xs font-semibold ${'$'}{r.paymentMethod === "CASH" ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20'}">${'$'}{r.paymentMethod}</span></td>
                        `;
                        tbody.appendChild(tr);
                    });

                    const avgOrder = uniqueOrders.size > 0 ? totalRev / uniqueOrders.size : 0;

                    document.getElementById('stat-count').textContent = uniqueOrders.size;
                    document.getElementById('stat-revenue').textContent = '$' + totalRev.toFixed(2);
                    document.getElementById('stat-units').textContent = totalUnits;
                    document.getElementById('stat-avg').textContent = '$' + avgOrder.toFixed(2);
                }

                function showDashboard() {
                    document.getElementById('pair-card').classList.add('hidden');
                    document.getElementById('dashboard-view').classList.remove('hidden');
                    const statusEl = document.getElementById('connection-status');
                    statusEl.className = 'flex items-center space-x-2 bg-emerald-500/10 text-emerald-400 px-4 py-2 rounded-full text-xs font-semibold border border-emerald-500/20 shadow-sm';
                    statusEl.innerHTML = '<span class="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span><span>Connected & Secure</span>';
                }

                function unpair() {
                    localStorage.removeItem('bridge_token');
                    document.getElementById('dashboard-view').classList.add('hidden');
                    document.getElementById('pair-card').classList.remove('hidden');
                    const statusEl = document.getElementById('connection-status');
                    statusEl.className = 'flex items-center space-x-2 bg-rose-500/10 text-rose-400 px-4 py-2 rounded-full text-xs font-semibold border border-rose-500/20 shadow-sm';
                    statusEl.innerHTML = '<span class="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse"></span><span>Unpaired Device</span>';
                }

                function downloadJson() {
                    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(cachedAuditData, null, 2));
                    const downloadAnchor = document.createElement('a');
                    downloadAnchor.setAttribute("href", dataStr);
                    downloadAnchor.setAttribute("download", "easypos_audit_logs_" + Date.now() + ".json");
                    document.body.appendChild(downloadAnchor);
                    downloadAnchor.click();
                    downloadAnchor.remove();
                }

                function downloadCsv() {
                    if (!cachedAuditData.length) return;
                    let csv = 'Order ID,Timestamp,Item Name,Brand,Quantity,Price,Payment Method\n';
                    cachedAuditData.forEach(r => {
                        const dateStr = new Date(r.timestamp).toISOString();
                        csv += `"${'$'}{r.orderId}","${'$'}{dateStr}","${'$'}{r.name}","${'$'}{r.brand}",${'$'}{r.quantity},${'$'}{r.priceAtSale},"${'$'}{r.paymentMethod}"\n`;
                    });
                    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.setAttribute('href', url);
                    a.setAttribute('download', 'easypos_audit_report_' + Date.now() + '.csv');
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
