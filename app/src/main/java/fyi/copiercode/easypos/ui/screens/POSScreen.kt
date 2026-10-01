package fyi.copiercode.easypos.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fyi.copiercode.easypos.data.entity.ProductEntity
import fyi.copiercode.easypos.viewmodel.CartViewModel
import fyi.copiercode.easypos.viewmodel.AdminViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSScreen(
    viewModel: CartViewModel,
    adminViewModel: AdminViewModel = hiltViewModel(),
    onNavigateToAdmin: () -> Unit
) {
    val context = LocalContext.current
    val products by adminViewModel.filteredProducts.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val shopName by adminViewModel.shopName.collectAsState()
    val billFooter by adminViewModel.billFooter.collectAsState()
    val logoUri by adminViewModel.receiptLogoUri.collectAsState()
    val categories by adminViewModel.categories.collectAsState()
    val searchQuery by adminViewModel.searchQuery.collectAsState()
    
    val paymentBypass by adminViewModel.paymentBypass.collectAsState()
    
    val isProcessing by viewModel.isProcessing.collectAsState()
    val heldOrders by viewModel.heldOrders.collectAsState()
    
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showHeldOrdersDialog by remember { mutableStateOf(false) }
    var pendingResumeHeldOrder by remember { mutableStateOf<fyi.copiercode.easypos.data.entity.HeldOrderEntity?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        BarcodeScannerScreen(
            onBarcodeScanned = { barcode ->
                viewModel.addByBarcode(barcode) {
                    Toast.makeText(context, "Product not found: $barcode", Toast.LENGTH_SHORT).show()
                }
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }

    val filteredProducts = remember(products, selectedCategoryId) {
        if (selectedCategoryId == null) products else products.filter { it.categoryId == selectedCategoryId }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: Catalog & Categories
        Column(modifier = Modifier.weight(0.65f).fillMaxHeight().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = shopName, 
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { adminViewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search...") },
                    modifier = Modifier.width(200.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                FilledTonalIconButton(onClick = { showScanner = true }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = { showHeldOrdersDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (heldOrders.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(Icons.Default.PauseCircle, contentDescription = "Held Orders", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hold (${heldOrders.size})", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))
                
                FilledIconButton(
                    onClick = onNavigateToAdmin,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Admin")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Category List
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    CategoryChip(
                        name = "All Products",
                        isSelected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null }
                    )
                }
                items(categories) { category ->
                    CategoryChip(
                        name = category.name,
                        isSelected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Products Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProducts) { product ->
                    ProductCard(product = product, onClick = { viewModel.addToCart(product) })
                }
            }
        }

        // Right Column: Cart
        Surface(
            modifier = Modifier.weight(0.35f).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Current Order", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(cartItems) { item ->
                        CartItemRow(item = item, 
                            onIncrease = { viewModel.addToCart(item.product) },
                            onDecrease = { viewModel.removeFromCart(item.product) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Summary
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", style = MaterialTheme.typography.bodyLarge)
                        Text("$${String.format("%.2f", totalAmount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { 
                            if (cartItems.isNotEmpty()) {
                                if (paymentBypass) {
                                    viewModel.setPaymentMethod("DIRECT")
                                    viewModel.checkout(context, shopName, billFooter, logoUri) {
                                        Toast.makeText(context, "Order Completed (Direct)!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    showCheckoutDialog = true 
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = cartItems.isNotEmpty() && !isProcessing
                    ) {
                        Text("PAY $${String.format("%.2f", totalAmount)}", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.holdCurrentOrder()
                                Toast.makeText(context, "Order Held!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = cartItems.isNotEmpty() && !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("HOLD")
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearCart() },
                            modifier = Modifier.weight(1f),
                            enabled = cartItems.isNotEmpty() && !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CLEAR")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onNavigateToAdmin, modifier = Modifier.fillMaxWidth()) {
                        Text("Admin Settings", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutDialog(
            viewModel = viewModel,
            isProcessing = isProcessing,
            onDismiss = { if (!isProcessing) showCheckoutDialog = false },
            onConfirm = {
                viewModel.checkout(context, shopName, billFooter, logoUri) {
                    showCheckoutDialog = false
                    Toast.makeText(context, "Order Completed!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showHeldOrdersDialog) {
        HeldOrdersDialog(
            heldOrders = heldOrders,
            onDismiss = { showHeldOrdersDialog = false },
            onResume = { heldOrder ->
                if (cartItems.isNotEmpty()) {
                    pendingResumeHeldOrder = heldOrder
                } else {
                    viewModel.resumeHeldOrder(heldOrder)
                    showHeldOrdersDialog = false
                    Toast.makeText(context, "Order Resumed!", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { heldOrderId ->
                viewModel.deleteHeldOrder(heldOrderId)
                Toast.makeText(context, "Held Order Discarded", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (pendingResumeHeldOrder != null) {
        val heldOrderToResume = pendingResumeHeldOrder!!
        AlertDialog(
            onDismissRequest = { pendingResumeHeldOrder = null },
            title = { Text("Active Cart Notice", fontWeight = FontWeight.Bold) },
            text = { Text("Your cart is not empty. Would you like to hold your current cart before resuming this order, or replace the current cart?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.holdCurrentOrder()
                        viewModel.resumeHeldOrder(heldOrderToResume)
                        pendingResumeHeldOrder = null
                        showHeldOrdersDialog = false
                        Toast.makeText(context, "Current order held & selected order resumed!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("HOLD CURRENT & RESUME")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resumeHeldOrder(heldOrderToResume)
                        pendingResumeHeldOrder = null
                        showHeldOrdersDialog = false
                        Toast.makeText(context, "Order Resumed!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("REPLACE CART", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
fun HeldOrdersDialog(
    heldOrders: List<fyi.copiercode.easypos.data.entity.HeldOrderEntity>,
    onDismiss: () -> Unit,
    onResume: (fyi.copiercode.easypos.data.entity.HeldOrderEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sdf = remember { java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.7f).padding(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Held Orders (${heldOrders.size})", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            if (heldOrders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No orders are currently on hold.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    items(heldOrders, key = { it.id }) { heldOrder ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = heldOrder.label.ifBlank { "Hold #${heldOrder.id}" },
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = sdf.format(java.util.Date(heldOrder.timestamp)),
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${heldOrder.totalQuantity} items • Total: $${String.format("%.2f", heldOrder.totalAmount)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedIconButton(
                                        onClick = { onDelete(heldOrder.id) },
                                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }

                                    Button(
                                        onClick = { onResume(heldOrder) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESUME", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun CategoryChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ProductCard(product: ProductEntity, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (product.imageUri != null) {
                    AsyncImage(
                        model = product.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                // Pulsing Combo Badge
                if (product.comboQty > 0) {
                    Surface(
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart).alpha(alpha),
                        color = Color(0xFFFFD700), // Bright Gold
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${product.comboQty} for $${product.comboPrice}", 
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
                            color = Color.Black, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(product.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("$${String.format("%.2f", product.basePrice)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun CartItemRow(item: fyi.copiercode.easypos.model.CartItem, onIncrease: () -> Unit, onDecrease: () -> Unit) {
    Surface(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text("$${String.format("%.2f", item.totalPrice)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) { 
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp)) 
                }
                Text("${item.quantity}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp), fontSize = 18.sp)
                IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))) { 
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) 
                }
            }
        }
    }
}

@Composable
fun CheckoutDialog(viewModel: CartViewModel, isProcessing: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val totalAmount by viewModel.totalAmount.collectAsState()
    val paymentMethod by viewModel.paymentMethod.collectAsState()
    val amountGiven by viewModel.amountGiven.collectAsState()
    val balanceAmount by viewModel.balanceAmount.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.6f).padding(24.dp),
        title = { 
            Text("Complete Checkout", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Payable", style = MaterialTheme.typography.labelLarge)
                        Text("$${String.format("%.2f", totalAmount)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Payment Method", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.setPaymentMethod("CASH") },
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (paymentMethod == "CASH") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMethod == "CASH") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("CASH", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Button(
                            onClick = { viewModel.setPaymentMethod("CARD") },
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (paymentMethod == "CARD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMethod == "CARD") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("CARD", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Button(
                            onClick = { viewModel.setPaymentMethod("SCAN") },
                            modifier = Modifier.weight(1f).height(60.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (paymentMethod == "SCAN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMethod == "SCAN") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("SCAN", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                    }
                }

                if (paymentMethod == "CASH") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = amountGiven,
                            onValueChange = { viewModel.updateAmountGiven(it) },
                            label = { Text("Amount Handed Over") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (amountGiven.isNotBlank()) {
                            Surface(
                                color = if (balanceAmount >= 0) Color(0xFFE8F5E9) else Color(0xFFFDECEA),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (balanceAmount >= 0) "Return Change:" else "Remaining:", fontWeight = FontWeight.Bold)
                                    Text("$${String.format("%.2f", Math.abs(balanceAmount))}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = if (balanceAmount >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isProcessing && (paymentMethod == "CARD" || paymentMethod == "SCAN" || (amountGiven.isNotBlank() && balanceAmount >= 0))
            ) { 
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Print, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "PRINTING..." else "PRINT RECEIPT & FINISH", fontWeight = FontWeight.Bold, fontSize = 18.sp) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Go Back", color = MaterialTheme.colorScheme.outline) }
        }
    )
}
