package fyi.copiercode.easypos.presentation

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fyi.copiercode.easypos.ui.screens.CustomerScreen
import fyi.copiercode.easypos.ui.theme.EasyPOETheme
import fyi.copiercode.easypos.viewmodel.CartViewModel
import fyi.copiercode.easypos.viewmodel.AdminViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class CustomerPresentation(
    outerContext: Context,
    display: Display,
    private val cartViewModel: CartViewModel,
    private val adminViewModel: AdminViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner
) : Presentation(outerContext, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val composeView = ComposeView(context)
        
        // Essential for Compose in Presentation
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        composeView.setContent {
            val isDarkMode by adminViewModel.isDarkMode.collectAsState()
            val shopName by adminViewModel.shopName.collectAsState()
            val customerBanner by adminViewModel.customerBanner.collectAsState()
            val products by adminViewModel.products.collectAsState()
            
            EasyPOETheme(darkTheme = isDarkMode) {
                CustomerScreen(
                    cartViewModel = cartViewModel,
                    shopName = shopName,
                    bannerText = customerBanner,
                    products = products
                )
            }
        }
        
        setContentView(composeView)
    }
}
