package fyi.copiercode.easypos.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import fyi.copiercode.easypos.data.PairedDevice
import fyi.copiercode.easypos.data.SettingsRepository
import fyi.copiercode.easypos.data.dao.OrderDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class PosWebServer(
    private val context: Context,
    private val orderDao: OrderDao,
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
            <title>Easy POS - Enterprise Audit Bridge</title>
            <script src="https://cdn.tailwindcss.com"></script>
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;900&display=swap" rel="stylesheet">
            <style>
                body { font-family: 'Inter', sans-serif; }
            </style>
        </head>
        <body class="bg-slate-950 text-slate-100 min-h-screen flex flex-col justify-between selection:bg-indigo-500 selection:text-white">
            <header class="bg-slate-900 border-b border-slate-800 px-8 py-4 flex items-center justify-between shadow-xl sticky top-0 z-50">
                <div class="flex items-center space-x-3">
                    <div class="bg-gradient-to-tr from-indigo-600 to-violet-600 text-white p-2.5 rounded-2xl font-black text-xl tracking-wider shadow-lg shadow-indigo-600/30">POS</div>
                    <div>
                        <h1 class="text-lg font-bold text-white">Easy POS Enterprise Audit</h1>
                        <p class="text-xs text-slate-400">Windows PC Sync & Advanced Analytics Gateway</p>
                    </div>
                </div>
                <div id="connection-status" class="flex items-center space-x-2 bg-rose-500/10 text-rose-400 px-4 py-2 rounded-full text-xs font-semibold border border-rose-500/20 shadow-sm">
                    <span class="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse"></span>
                    <span>Unpaired Device</span>
                </div>
            </header>

            <main class="flex-1 max-w-7xl w-full mx-auto p-8">
                <!-- Pairing Card -->
                <div id="pair-card" class="max-w-md mx-auto bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl mt-16">
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
                            <input type="text" id="device-name-input" value="Windows Audit PC" required class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-sm text-white focus:outline-none focus:border-indigo-500 transition">
                        </div>
                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">4-Digit Pairing PIN</label>
                            <input type="text" id="pin-input" maxlength="4" placeholder="••••" required class="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-center text-2xl tracking-widest text-white focus:outline-none focus:border-indigo-500 transition font-mono shadow-inner">
                        </div>
                        <button type="submit" class="w-full bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white font-bold py-3.5 rounded-xl transition shadow-xl shadow-indigo-600/30">Authorize & Pair Device</button>
                        <p id="pair-error" class="text-xs text-rose-400 text-center hidden font-medium"></p>
                    </form>
                </div>

                <!-- Dashboard View -->
                <div id="dashboard-view" class="hidden space-y-8">
                    <!-- Top Bar & Controls -->
                    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div>
                            <h2 class="text-3xl font-black text-white tracking-tight">Audit & Sales Analytics</h2>
                            <p class="text-sm text-slate-400">Real-time filtering and professional reporting from SQLite terminal database.</p>
                        </div>
                        <div class="flex flex-wrap items-center gap-3">
                            <button onclick="fetchAuditData()" class="bg-slate-900 hover:bg-slate-800 text-slate-200 px-4 py-2.5 rounded-xl text-sm font-semibold transition flex items-center space-x-2 border border-slate-800 shadow-md">
                                <svg class="w-4 h-4 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/></svg>
                                <span>Refresh</span>
                            </button>
                            <button onclick="downloadJson()" class="bg-indigo-600 hover:bg-indigo-500 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition shadow-lg shadow-indigo-600/25 flex items-center space-x-2">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
                                <span>Export JSON</span>
                            </button>
                            <button onclick="downloadCsv()" class="bg-emerald-600 hover:bg-emerald-500 text-white px-4 py-2.5 rounded-xl text-sm font-semibold transition shadow-lg shadow-emerald-600/25 flex items-center space-x-2">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
                                <span>Export CSV</span>
                            </button>
                            <button onclick="unpair()" class="bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 px-4 py-2.5 rounded-xl text-sm font-semibold transition border border-rose-500/20">Disconnect</button>
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

                    <!-- Data Table -->
                    <div class="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
                        <div class="overflow-x-auto">
                            <table class="w-full text-left border-collapse">
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
                                    <!-- Populated dynamically -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </main>

            <footer class="bg-slate-900 border-t border-slate-800 py-4 px-8 text-center text-xs text-slate-500">
                Easy POS Enterprise Audit Bridge • Local Network Wi-Fi Synchronization
            </footer>

            <script>
                let cachedAuditData = [];

                window.addEventListener('DOMContentLoaded', () => {
                    const token = localStorage.getItem('bridge_token');
                    if (token) {
                        showDashboard();
                        fetchAuditData();
                    }
                });

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
                        // Date filter
                        if (dateFilter === 'today' && r.timestamp < todayMidnight) return false;
                        if (dateFilter === 'month' && r.timestamp < monthStart) return false;
                        if (dateFilter === 'year' && r.timestamp < yearStart) return false;
                        if (dateFilter === 'custom') {
                            if (startVal && r.timestamp < customStart) return false;
                            if (endVal && r.timestamp > customEnd) return false;
                        }

                        // Payment filter
                        if (paymentFilter !== 'all' && r.paymentMethod !== paymentFilter) return false;

                        // Brand filter
                        if (brandFilter !== 'all' && r.brand !== brandFilter) return false;

                        // Search query
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
