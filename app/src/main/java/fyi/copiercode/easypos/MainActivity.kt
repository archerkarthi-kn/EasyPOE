package fyi.copiercode.easypos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.material3.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import fyi.copiercode.easypos.presentation.CustomerPresentation
import fyi.copiercode.easypos.ui.screens.AdminDashboard
import fyi.copiercode.easypos.ui.screens.POSScreen
import fyi.copiercode.easypos.ui.screens.UnauthorizedDeviceScreen
import fyi.copiercode.easypos.ui.theme.EasyPOETheme
import fyi.copiercode.easypos.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val cartViewModel: CartViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()
    private val auditViewModel: AuditViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    
    private var customerPresentation: CustomerPresentation? = null
    private var currentRoute by mutableStateOf("pos")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkOverlayPermission()
        checkBluetoothPermissions()
        setupCustomerDisplay()
        
        authViewModel.checkAuthorization(this)

        setContent {
            val isDarkMode by adminViewModel.isDarkMode.collectAsState()
            val authState by authViewModel.authState.collectAsState()
            var showExitDialog by remember { mutableStateOf(false) }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("Exit Application", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to exit the POS system?") },
                    confirmButton = {
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("YES, EXIT")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showExitDialog = false }) {
                            Text("NO, CANCEL")
                        }
                    }
                )
            }

            BackHandler {
                showExitDialog = true
            }

            EasyPOETheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (authState) {
                        AuthState.LOADING -> StartupLoadingScreen()
                        AuthState.AUTHORIZED -> {
                            val navController = rememberNavController()
                            
                            // Track route changes
                            navController.addOnDestinationChangedListener { _, destination, _ ->
                                currentRoute = destination.route ?: "pos"
                            }

                            NavHost(navController = navController, startDestination = "pos") {
                                composable("pos") {
                                    POSScreen(
                                        viewModel = cartViewModel,
                                        adminViewModel = adminViewModel,
                                        onNavigateToAdmin = { navController.navigate("admin") }
                                    )
                                }
                                composable("admin") {
                                    AdminDashboard(
                                        adminViewModel = adminViewModel,
                                        auditViewModel = auditViewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                        else -> {
                            UnauthorizedDeviceScreen(
                                state = authState,
                                onRetry = { authViewModel.checkAuthorization(this) }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StartupLoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("Verifying License...", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled, no further action needed
    }

    private fun checkBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            bluetoothPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupCustomerDisplay() {
        try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
            
            var displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            if (displays.isEmpty()) {
                displays = displayManager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }.toTypedArray()
            }

            if (displays.isNotEmpty()) {
                showCustomerPresentation(displays[0])
            }

            displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    val display = displayManager.getDisplay(displayId)
                    if (display != null && displayId != Display.DEFAULT_DISPLAY) {
                        showCustomerPresentation(display)
                    }
                }

                override fun onDisplayRemoved(displayId: Int) {
                    if (customerPresentation?.display?.displayId == displayId) {
                        customerPresentation?.dismiss()
                        customerPresentation = null
                    }
                }

                override fun onDisplayChanged(displayId: Int) {}
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCustomerPresentation(display: Display) {
        try {
            customerPresentation?.dismiss()
            customerPresentation = CustomerPresentation(
                outerContext = this,
                display = display,
                cartViewModel = cartViewModel,
                adminViewModel = adminViewModel,
                lifecycleOwner = this,
                savedStateRegistryOwner = this,
                viewModelStoreOwner = this
            )
            customerPresentation?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        customerPresentation?.dismiss()
        customerPresentation = null
    }

    // Priority Barcode Scanner Capture (Intercepts keys before UI steals them)
    private var barcodeBuffer = StringBuilder()
    private var lastKeyTime: Long = 0

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Only intercept KeyDown events for the buffer
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            
            // Timeout reset: Scanners are extremely fast. 
            // If > 500ms has passed, it's a new scan or manual typing.
            if (currentTime - lastKeyTime > 500) {
                barcodeBuffer.setLength(0)
            }
            lastKeyTime = currentTime

            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB -> {
                    val barcode = barcodeBuffer.toString().trim()
                    if (barcode.isNotEmpty()) {
                        // Priority processing
                        android.widget.Toast.makeText(this, "Scanned: $barcode", android.widget.Toast.LENGTH_SHORT).show()
                        cartViewModel.addByBarcode(barcode) {
                            // Already handled by Scanned Toast, but can add specific "Not Found" logic here
                        }
                        barcodeBuffer.setLength(0)
                        return true // Consume the terminator to stop it from clicking buttons or moving focus
                    }
                }
                else -> {
                    // Capture almost any printable character from the HID scanner
                    val char = event.unicodeChar
                    if (char != 0) {
                        val c = char.toChar()
                        if (!Character.isWhitespace(c) || c == ' ') {
                            barcodeBuffer.append(c)
                            // Only steal key events on the POS screen; do NOT steal when editing in Admin
                            if (currentRoute == "pos" && currentTime - lastKeyTime < 50) return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
