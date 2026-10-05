package fyi.copiercode.easypos.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import fyi.copiercode.easypos.data.entity.CategoryEntity
import fyi.copiercode.easypos.data.entity.ProductEntity
import fyi.copiercode.easypos.viewmodel.AdminViewModel
import fyi.copiercode.easypos.viewmodel.AuditViewModel
import fyi.copiercode.easypos.viewmodel.DateFilterType
import fyi.copiercode.easypos.util.UpdateManager
import fyi.copiercode.easypos.util.AnalyticsHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(
    adminViewModel: AdminViewModel,
    auditViewModel: AuditViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Products", "Categories", "Offers", "Audit", "Manual Bill", "Settings")
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        adminViewModel.initPrinter(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ProductManagement(adminViewModel)
                1 -> CategoryManagement(adminViewModel)
                2 -> OfferManagement(adminViewModel)
                3 -> AuditScreen(auditViewModel)
                4 -> ManualBillingScreen(adminViewModel)
                5 -> SettingsScreen(adminViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductManagement(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val products by viewModel.filteredProducts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    
    val listState = rememberLazyListState()

    // Group products by category
    val groupedProducts = remember(products, categories) {
        products.groupBy { product ->
            categories.find { it.id == product.categoryId }?.name ?: "No Category"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Product Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Search Products by Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedProducts.forEach { (categoryName, productsInCategory) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = categoryName,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    items(productsInCategory, key = { product -> 
                        if (product.id != 0L) product.id else "new_${product.name}_${product.brand}"
                    }) { product ->
                        var itemOffset by remember { mutableStateOf(0f) }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .offset { IntOffset(0, itemOffset.roundToInt()) }
                                .pointerInput(product) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { /* Optional: visual feedback */ },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            itemOffset += dragAmount.y
                                            
                                            // Simple Swap Logic: If dragged significantly, trigger ViewModel move
                                            if (itemOffset > 50f) {
                                                viewModel.moveProductDown(product)
                                                itemOffset = 0f
                                            } else if (itemOffset < -50f) {
                                                viewModel.moveProductUp(product)
                                                itemOffset = 0f
                                            }
                                        },
                                        onDragEnd = { itemOffset = 0f },
                                        onDragCancel = { itemOffset = 0f }
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Default.DragHandle, 
                                        contentDescription = "Long press to drag", 
                                        modifier = Modifier.padding(end = 12.dp), 
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Column {
                                        Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Price: $${String.format(Locale.US, "%.2f", product.basePrice)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.moveProductUp(product) }) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                    }
                                    IconButton(onClick = { viewModel.moveProductDown(product) }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                    }
                                    IconButton(onClick = {
                                        selectedProduct = product
                                        showDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { viewModel.deleteProduct(product) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                selectedProduct = null
                showDialog = true
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product")
        }

        if (showDialog) {
            ProductEditDialog(
                product = selectedProduct,
                categories = categories,
                onDismiss = { showDialog = false },
                onSave = { name, brand, price, imageUri, categoryId, barcode ->
                    val newProduct = selectedProduct?.copy(
                        name = name, 
                        brand = brand, 
                        basePrice = price,
                        imageUri = imageUri,
                        categoryId = categoryId,
                        barcode = barcode
                    ) ?: ProductEntity(
                        name = name, 
                        brand = brand, 
                        basePrice = price,
                        imageUri = imageUri,
                        categoryId = categoryId,
                        barcode = barcode
                    )
                    viewModel.saveProduct(context, newProduct)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun CategoryManagement(viewModel: AdminViewModel) {
    val categories by viewModel.categories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryName by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text("Category Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(categories) { category ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium)
                        Row {
                            IconButton(onClick = {
                                selectedCategory = category
                                categoryName = category.name
                                showDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteCategory(category) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                selectedCategory = null
                categoryName = ""
                showDialog = true
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Category")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(if (selectedCategory == null) "Add Category" else "Edit Category") },
                text = {
                    TextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (selectedCategory == null) {
                            viewModel.saveCategory(categoryName)
                        } else {
                            viewModel.updateCategory(selectedCategory!!.copy(name = categoryName))
                        }
                        showDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun OfferManagement(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val products by viewModel.filteredProducts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Manage Combo Offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search Products for Offers") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(products) { product ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (product.comboQty > 0) {
                                Text("Current: ${product.comboQty} for $${String.format("%.2f", product.comboPrice)}", 
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            } else {
                                Text("No offer set", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Button(
                            onClick = {
                                selectedProduct = product
                                showDialog = true
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (product.comboQty > 0) "Edit Offer" else "Add Offer")
                        }
                    }
                }
            }
        }
    }

    if (showDialog && selectedProduct != null) {
        OfferEditDialog(
            product = selectedProduct!!,
            onDismiss = { showDialog = false },
            onSave = { qty, price ->
                viewModel.saveProduct(context, selectedProduct!!.copy(comboQty = qty, comboPrice = price))
                showDialog = false
            },
            onRemove = {
                viewModel.saveProduct(context, selectedProduct!!.copy(comboQty = 0, comboPrice = 0.0))
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(viewModel: AuditViewModel) {
    val records by viewModel.filteredRecords.collectAsState()
    val brands by viewModel.brands.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val selectedPaymentMethod by viewModel.selectedPaymentMethod.collectAsState()
    val dateFilterType by viewModel.dateFilterType.collectAsState()
    val totalRevenue by viewModel.totalRevenue.collectAsState()
    val totalUnitsSold by viewModel.totalUnitsSold.collectAsState()
    val calendar by viewModel.selectedCalendar.collectAsState()
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Business Audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { 
                        viewModel.printAudit(context) { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        } 
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print Audit")
                    }
                    OutlinedButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Export")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    val currentCatName = categories.find { it.id == selectedCategoryId }?.name ?: "All Categories"
                    OutlinedButton(onClick = { categoryDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(currentCatName, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = categoryDropdownExpanded, onDismissRequest = { categoryDropdownExpanded = false }) {
                        DropdownMenuItem(text = { Text("All Categories") }, onClick = { viewModel.setCategoryFilter(null); categoryDropdownExpanded = false })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { viewModel.setCategoryFilter(category.id); categoryDropdownExpanded = false })
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { brandDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedBrand ?: "All Brands", maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = brandDropdownExpanded, onDismissRequest = { brandDropdownExpanded = false }) {
                        DropdownMenuItem(text = { Text("All Brands") }, onClick = { viewModel.setBrandFilter(null); brandDropdownExpanded = false })
                        brands.forEach { brand ->
                            DropdownMenuItem(text = { Text(brand) }, onClick = { viewModel.setBrandFilter(brand); brandDropdownExpanded = false })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = selectedPaymentMethod == null, onClick = { viewModel.setPaymentFilter(null) }, label = { Text("All Pay") })
                FilterChip(selected = selectedPaymentMethod == "CASH", onClick = { viewModel.setPaymentFilter("CASH") }, label = { Text("Cash") })
                FilterChip(selected = selectedPaymentMethod == "CARD", onClick = { viewModel.setPaymentFilter("CARD") }, label = { Text("Card") })
                FilterChip(selected = selectedPaymentMethod == "SCAN", onClick = { viewModel.setPaymentFilter("SCAN") }, label = { Text("Scan") })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = dateFilterType == DateFilterType.ALL, onClick = { viewModel.setDateFilterType(DateFilterType.ALL) }, label = { Text("All") })
                FilterChip(selected = dateFilterType == DateFilterType.DAY, onClick = { viewModel.setDateFilterType(DateFilterType.DAY) }, label = { Text("Daily") })
                FilterChip(selected = dateFilterType == DateFilterType.MONTH, onClick = { viewModel.setDateFilterType(DateFilterType.MONTH) }, label = { Text("Monthly") })
                FilterChip(selected = dateFilterType == DateFilterType.RANGE, onClick = { viewModel.setDateFilterType(DateFilterType.RANGE) }, label = { Text("Range") })
                
                if (dateFilterType == DateFilterType.DAY || dateFilterType == DateFilterType.MONTH) {
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { 
                            val sdf = when(dateFilterType) {
                                DateFilterType.DAY -> SimpleDateFormat("dd MMM", Locale.getDefault())
                                else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                            }
                            Text(sdf.format(calendar.time))
                        },
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                } else if (dateFilterType == DateFilterType.RANGE) {
                    AssistChip(
                        onClick = { showRangePicker = true },
                        label = {
                            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                            val start = customStartDate?.let { sdf.format(Date(it)) } ?: "Start"
                            val end = customEndDate?.let { sdf.format(Date(it)) } ?: "End"
                            Text("$start - $end")
                        },
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(records) { record ->
                    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val unitPrice = record.priceAtSale / record.quantity
                    ListItem(
                        headlineContent = { Text(record.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { 
                            Column {
                                Text("${record.paymentMethod} • ${sdf.format(Date(record.timestamp))}", style = MaterialTheme.typography.bodySmall)
                                if (record.paymentMethod == "CASH") {
                                    Text("Given: $${String.format("%.2f", record.amountGiven)} | Change: $${String.format("%.2f", record.balanceAmount)}", 
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$${String.format("%.2f", record.priceAtSale)}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Text("${record.quantity} x $${String.format("%.2f", unitPrice)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (selectedBrand == null) "TOTAL REVENUE" else "TOTAL FOR $selectedBrand", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("$totalUnitsSold UNITS SOLD", style = MaterialTheme.typography.labelMedium)
                }
                Text("$${String.format("%.2f", totalRevenue)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it
                        viewModel.updateCalendar(cal)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        viewModel.setCustomRange(start, end)
                        showRangePicker = false
                    }
                }) { Text("OK") }
            }
        ) {
            DateRangePicker(state = dateRangePickerState, modifier = Modifier.weight(1f).padding(16.dp))
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Audit Data") },
            text = { Text("Would you like to download the CSV file to your device or share it via another application?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val path = viewModel.exportToCsv(context, records)
                            if (path != null) {
                                Toast.makeText(context, "CSV Exported to: $path", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                            }
                            showExportDialog = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = viewModel.generateCsvFile(context, records)
                            if (file != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Audit CSV"))
                            } else {
                                Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                            }
                            showExportDialog = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showComplaintDialog by remember { mutableStateOf(false) }
    
    val billFooter by viewModel.billFooter.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val customerBanner by viewModel.customerBanner.collectAsState()
    val receiptLogoUri by viewModel.receiptLogoUri.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // New Settings
    val footerTel by viewModel.footerTel.collectAsState()
    val footerEmail by viewModel.footerEmail.collectAsState()
    val footerLoc1 by viewModel.footerLoc1.collectAsState()
    val footerLoc2 by viewModel.footerLoc2.collectAsState()
    val paymentBypass by viewModel.paymentBypass.collectAsState()
    val billPrefix by viewModel.billPrefix.collectAsState()
    val billCounter by viewModel.billCounter.collectAsState()
    val paperWidth by viewModel.paperWidth.collectAsState()
    val printerType by viewModel.printerType.collectAsState()
    val networkPrinterIp by viewModel.networkPrinterIp.collectAsState()
    val printTwoSlips by viewModel.printTwoSlips.collectAsState()
    val enableTokenNumber by viewModel.enableTokenNumber.collectAsState()
    val tokenCounter by viewModel.tokenCounter.collectAsState()

    var footerText by remember(billFooter) { mutableStateOf(billFooter) }
    var shopNameText by remember(shopName) { mutableStateOf(shopName) }
    var bannerText by remember(customerBanner) { mutableStateOf(customerBanner) }
    
    var telText by remember(footerTel) { mutableStateOf(footerTel) }
    var emailText by remember(footerEmail) { mutableStateOf(footerEmail) }
    var loc1Text by remember(footerLoc1) { mutableStateOf(footerLoc1) }
    var loc2Text by remember(footerLoc2) { mutableStateOf(footerLoc2) }
    var prefixText by remember(billPrefix) { mutableStateOf(billPrefix) }
    var ipText by remember(networkPrinterIp) { mutableStateOf(networkPrinterIp) }

    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.saveReceiptLogo(context, it.toString()) }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = viewModel.fullSystemRestore(context, it)
                if (success) {
                    Toast.makeText(context, "Restore Successful! Restarting...", Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(2000)
                    viewModel.restartApp(context)
                } else {
                    Toast.makeText(context, "Restore Failed!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (!bluetoothPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            launcher.launch(bluetoothPermissions.toTypedArray())
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            WifiBridgeCard(viewModel)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Printer & Receipt Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Column {
                        Text("Connection Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = printerType == "BLUETOOTH",
                                onClick = { viewModel.setPrinterType("BLUETOOTH") },
                                label = { Text("Bluetooth / Internal") }
                            )
                            FilterChip(
                                selected = printerType == "NETWORK",
                                onClick = { viewModel.setPrinterType("NETWORK") },
                                label = { Text("Wi-Fi / LAN") }
                            )
                            FilterChip(
                                selected = printerType == "USB",
                                onClick = { viewModel.setPrinterType("USB") },
                                label = { Text("USB (Xprinter)") }
                            )
                        }
                    }

                    if (printerType == "NETWORK") {
                        OutlinedTextField(
                            value = ipText,
                            onValueChange = { ipText = it },
                            label = { Text("Printer IP Address (e.g. 192.168.1.100)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.setNetworkPrinterIp(ipText) }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save IP")
                                }
                            }
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    // Two Slips Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Two Slips (Duplicate Receipt)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Print 2 slips per order (Cashier & Customer)", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = printTwoSlips,
                            onCheckedChange = { viewModel.setPrintTwoSlips(it) }
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    // Token Number Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Token Number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Print prominent TOKEN #001 header on receipts", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = enableTokenNumber,
                            onCheckedChange = { viewModel.setEnableTokenNumber(it) }
                        )
                    }

                    if (enableTokenNumber) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Token: #$tokenCounter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = { viewModel.resetTokenCounter() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Token Counter")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Terminal Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Switch between light and dark interface", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.saveDarkModeEnabled(it) }
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Payment Screen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("If disabled, orders finalize as 'DIRECT'", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = !paymentBypass,
                            onCheckedChange = { viewModel.setPaymentBypass(!it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    Column {
                        Text("Paper Width", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = paperWidth == 32,
                                onClick = { viewModel.setPaperWidth(32) },
                                label = { Text("58mm (32ch)") }
                            )
                            FilterChip(
                                selected = paperWidth == 48,
                                onClick = { viewModel.setPaperWidth(48) },
                                label = { Text("80mm (48ch)") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Receipt Header & Branding", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Receipt Logo", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (receiptLogoUri != null) {
                            AsyncImage(
                                model = receiptLogoUri,
                                contentDescription = "Receipt Logo",
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No Logo", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { logoLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Upload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (receiptLogoUri == null) "Upload Logo" else "Change Logo")
                            }
                            if (receiptLogoUri != null) {
                                TextButton(onClick = { viewModel.saveReceiptLogo(context, null) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = shopNameText,
                    onValueChange = { shopNameText = it },
                    label = { Text("Shop Name") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.saveShopName(shopNameText) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                )

                OutlinedTextField(
                    value = bannerText,
                    onValueChange = { bannerText = it },
                    label = { Text("Customer Display Welcome Message") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.saveCustomerBanner(bannerText) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bill Numbering", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prefixText,
                        onValueChange = { prefixText = it },
                        label = { Text("Prefix (e.g. Taj)") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.setBillPrefix(prefixText) },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Set")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Number: $billPrefix${billCounter.toString().padStart(3, '0')}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.resetCounter() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Counter")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Footer Contact Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                OutlinedTextField(value = telText, onValueChange = { telText = it }, label = { Text("Telephone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emailText, onValueChange = { emailText = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = loc1Text, onValueChange = { loc1Text = it }, label = { Text("Location 1") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = loc2Text, onValueChange = { loc2Text = it }, label = { Text("Location 2") }, modifier = Modifier.fillMaxWidth())
                
                Button(
                    onClick = { viewModel.saveFooterDetails(telText, emailText, loc1Text, loc2Text) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Contact Details")
                }
            }
        }
        
        item { AboutCard() }

        item {
            Button(
                onClick = { showComplaintDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit Complaint / Error")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        scope.launch {
                            val path = viewModel.fullSystemBackup(context)
                            if (path != null) {
                                Toast.makeText(context, "Full Backup saved to: $path", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Backup Failed!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Full System Backup (ZIP)")
                }

                OutlinedButton(
                    onClick = { zipLauncher.launch("application/zip") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore System from ZIP")
                }
            }
        }

        item { UpdateSection(viewModel) }
    }

    if (showComplaintDialog) {
        SubmitComplaintDialog(
            onDismiss = { showComplaintDialog = false },
            onSubmit = { text ->
                AnalyticsHelper.sendComplaint(context, text)
                Toast.makeText(context, "Complaint Submitted Successfully", Toast.LENGTH_SHORT).show()
                showComplaintDialog = false
            }
        )
    }
}

@Composable
fun WifiBridgeCard(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val bridgePin by viewModel.bridgePin.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val ipAddress = remember { viewModel.getDeviceIpAddress() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Windows Audit & Browser Bridge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("PORT 8080", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Text("Connect your Windows PC audit software or web browser on the same Wi-Fi gateway network.", style = MaterialTheme.typography.bodySmall)

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Server Address:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("http://$ipAddress:8080", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Pairing PIN:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(bridgePin, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                            IconButton(onClick = { viewModel.generateNewPin() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "New PIN", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Active Sessions:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("${pairedDevices.size} device(s) paired", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (pairedDevices.isNotEmpty()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline)
                    }
                }
            }

            if (pairedDevices.isNotEmpty()) {
                Text("Paired Devices:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pairedDevices.forEach { device ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Paired: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(device.pairedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                TextButton(
                                    onClick = { viewModel.removePairedDevice(device.token) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Revoke")
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$ipAddress:8080"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Web UI Dashboard")
            }
        }
    }
}

@Composable
fun SubmitComplaintDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Complaint / Error") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Please describe the issue in detail. Your device ID and timestamp will be sent automatically.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("Describe here...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(text) },
                enabled = text.isNotBlank()
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun UpdateSection(viewModel: AdminViewModel) {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateResult by viewModel.updateResult.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("App Updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Button(
            onClick = { 
                viewModel.checkForUpdates()
                showUpdateDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Check for Updates")
        }
    }

    if (showUpdateDialog && updateResult != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Status") },
            text = {
                when (val result = updateResult!!) {
                    is UpdateManager.UpdateResult.NoUpdate -> Text("Your app is up to date!")
                    is UpdateManager.UpdateResult.UpdateAvailable -> {
                        Column {
                            Text("A new version is available!\n\nVersion: ${result.version} (Build ${result.versionCode})\n\nDo you want to update now?")
                        }
                    }
                    is UpdateManager.UpdateResult.Error -> Text("Error checking for updates: ${result.message}")
                }
            },
            confirmButton = {
                if (updateResult is UpdateManager.UpdateResult.UpdateAvailable) {
                    Button(onClick = {
                        val res = updateResult as UpdateManager.UpdateResult.UpdateAvailable
                        viewModel.startDownload(res.url)
                        showUpdateDialog = false
                    }) {
                        Text("Download & Install")
                    }
                } else {
                    Button(onClick = { showUpdateDialog = false }) { Text("OK") }
                }
            }
        )
    }
}

@Composable
fun AboutCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("About App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Easy POS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Easy POS is a modern, high-performance Point of Sale (POS) system engineered for speed and efficiency. Designed specifically for dual-screen Android terminals, it features real-time dynamic pricing, seamless receipt printing, and comprehensive offline auditing capabilities to streamline daily shop operations.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Support:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "archerkarthi@gmail.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:archerkarthi@gmail.com")
                        }
                        context.startActivity(intent)
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enquiry:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "www.Enquiry.copiercode.fyi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.Enquiry.copiercode.fyi"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun ProductEditDialog(
    product: ProductEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String?, Long, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(product?.name ?: "") }
    var price by remember { mutableStateOf(product?.basePrice?.toString() ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var imageUri by remember { mutableStateOf(product?.imageUri) }
    var selectedCategoryId by remember { mutableStateOf(product?.categoryId ?: (categories.firstOrNull()?.id ?: 0L)) }
    var expanded by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri = it.toString() }
    }

    if (showCameraScanner) {
        BarcodeScannerScreen(
            onBarcodeScanned = { 
                barcode = it
                showCameraScanner = false
            },
            onDismiss = { showCameraScanner = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (product == null) "Create New Product" else "Edit Product",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Product Name") }, 
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                
                OutlinedTextField(
                    value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (Scan or Type)") }, 
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showCameraScanner = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Scan with Camera")
                        }
                    }
                )

                OutlinedTextField(
                    value = price, onValueChange = { price = it }, label = { Text("Selling Price ($)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true
                )
                
                Column {
                    Text("Select Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        ) {
                            val catName = categories.find { it.id == selectedCategoryId }?.name ?: "No Category Selected"
                            Text(catName)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUri != null) {
                            AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { imageLauncher.launch("image/*") }) {
                        Text(if (imageUri == null) "Add Product Image" else "Change Image")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val categoryName = categories.find { it.id == selectedCategoryId }?.name ?: "General"
                    onSave(name, categoryName, price.toDoubleOrNull() ?: 0.0, imageUri, selectedCategoryId, barcode) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && price.isNotBlank()
            ) { Text("SAVE & ASSIGN INDEX") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = MaterialTheme.colorScheme.outline) }
        }
    )
}

@Composable
fun OfferEditDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Double) -> Unit,
    onRemove: () -> Unit
) {
    var qty by remember { mutableStateOf(if (product.comboQty > 0) product.comboQty.toString() else "") }
    var price by remember { mutableStateOf(if (product.comboPrice > 0.0) product.comboPrice.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Combo Offer for ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = qty, onValueChange = { qty = it }, label = { Text("Trigger Quantity (e.g. 3)") })
                TextField(value = price, onValueChange = { price = it }, label = { Text("Combo Price (e.g. 10.50)") })
            }
        },
        confirmButton = {
            Column {
                Button(onClick = { onSave(qty.toIntOrNull() ?: 0, price.toDoubleOrNull() ?: 0.0) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save Offer")
                }
                if (product.comboQty > 0) {
                    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Remove Offer")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ManualBillingScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val storedPassword by viewModel.manualBillingPassword.collectAsState()

    var isAuthenticated by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showChangePwdDialog by remember { mutableStateOf(false) }
    var newPwdInput by remember { mutableStateOf("") }

    var billNoText by remember { mutableStateOf("MANUAL-${System.currentTimeMillis().toString().takeLast(6)}") }
    var itemNameText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var amountGivenText by remember { mutableStateOf("") }

    val manualItems = remember { mutableStateListOf<fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem>() }

    if (!isAuthenticated) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.width(360.dp).padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Protected Feature", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Please enter the admin PIN to access Manual Billing.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Enter PIN (default: admin)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )

                    Button(
                        onClick = {
                            if (passwordInput == storedPassword) {
                                isAuthenticated = true
                            } else {
                                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("UNLOCK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        val totalBillAmount = manualItems.sumOf { it.price }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Manual Bill Entry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showChangePwdDialog = true }) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change PIN")
                    }
                }
            }

            // Card 1: Add Item Inputs
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Add Item to Bill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = billNoText,
                            onValueChange = { billNoText = it },
                            label = { Text("Bill Number / Order ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = itemNameText,
                            onValueChange = { itemNameText = it },
                            label = { Text("Item Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { quantityText = it },
                                label = { Text("Quantity") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it },
                                label = { Text("Total Price ($)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                            )
                        }

                        Button(
                            onClick = {
                                val qty = quantityText.toIntOrNull() ?: 1
                                val price = priceText.toDoubleOrNull() ?: 0.0
                                if (itemNameText.isBlank() || price <= 0) {
                                    Toast.makeText(context, "Please enter valid item name and price", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                manualItems.add(
                                    fyi.copiercode.easypos.util.ReceiptBuilder.ReceiptItem(
                                        name = itemNameText,
                                        quantity = qty,
                                        price = price
                                    )
                                )
                                itemNameText = ""
                                priceText = ""
                                quantityText = "1"
                                Toast.makeText(context, "Item added to manual bill", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = itemNameText.isNotBlank() && priceText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ADD ITEM TO BILL", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Card 2: Added Items List (Multi-item support)
            if (manualItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Bill Items (${manualItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { manualItems.clear() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text("Clear All")
                                }
                            }

                            manualItems.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${index + 1}. ${item.name}", fontWeight = FontWeight.Bold)
                                        Text("Qty: ${item.quantity}  •  Unit: $${String.format(Locale.US, "%.2f", item.price / item.quantity)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text("$${String.format(Locale.US, "%.2f", item.price)}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { manualItems.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Bill Amount:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$${String.format(Locale.US, "%.2f", totalBillAmount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Card 3: Payment Method & Print Action
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text("Payment Method", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(selected = paymentMethod == "CASH", onClick = { paymentMethod = "CASH" }, label = { Text("Cash") })
                                    FilterChip(selected = paymentMethod == "CARD", onClick = { paymentMethod = "CARD" }, label = { Text("Card") })
                                    FilterChip(selected = paymentMethod == "SCAN", onClick = { paymentMethod = "SCAN" }, label = { Text("Scan") })
                                }
                            }

                            if (paymentMethod == "CASH") {
                                val givenVal = amountGivenText.toDoubleOrNull() ?: totalBillAmount
                                val changeVal = givenVal - totalBillAmount

                                OutlinedTextField(
                                    value = amountGivenText,
                                    onValueChange = { amountGivenText = it },
                                    label = { Text("Amount Handed Over (Optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                                )
                                if (amountGivenText.isNotBlank()) {
                                    Text("Change to Return: $${String.format(Locale.US, "%.2f", changeVal)}", fontWeight = FontWeight.Bold, color = if (changeVal >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                }
                            }

                            Button(
                                onClick = {
                                    val given = amountGivenText.toDoubleOrNull() ?: totalBillAmount
                                    val change = if (paymentMethod == "CASH") given - totalBillAmount else 0.0

                                    viewModel.createManualOrder(
                                        context = context,
                                        billNo = billNoText,
                                        items = manualItems.toList(),
                                        paymentMethod = paymentMethod,
                                        amountGiven = given,
                                        change = change
                                    ) { success, error ->
                                        if (success) {
                                            Toast.makeText(context, "Manual Bill Printed!", Toast.LENGTH_SHORT).show()
                                            manualItems.clear()
                                            billNoText = "MANUAL-${System.currentTimeMillis().toString().takeLast(6)}"
                                            amountGivenText = ""
                                        } else {
                                            Toast.makeText(context, "Print Error: $error", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PRINT MANUAL BILL ($${String.format(Locale.US, "%.2f", totalBillAmount)})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangePwdDialog) {
        AlertDialog(
            onDismissRequest = { showChangePwdDialog = false },
            title = { Text("Change Manual Billing PIN") },
            text = {
                OutlinedTextField(
                    value = newPwdInput,
                    onValueChange = { newPwdInput = it },
                    label = { Text("New 4-6 Digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPwdInput.isNotBlank()) {
                        viewModel.setManualBillingPassword(newPwdInput)
                        Toast.makeText(context, "PIN Updated Successfully!", Toast.LENGTH_SHORT).show()
                        showChangePwdDialog = false
                        newPwdInput = ""
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePwdDialog = false }) { Text("Cancel") }
            }
        )
    }
}
