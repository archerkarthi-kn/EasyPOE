package fyi.copiercode.easypos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fyi.copiercode.easypos.util.AnalyticsHelper
import fyi.copiercode.easypos.viewmodel.AuthState

@Composable
fun UnauthorizedDeviceScreen(
    state: AuthState,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val deviceId = AnalyticsHelper.getDeviceId(context)
    val macAddress = AnalyticsHelper.getMacAddress()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon = if (state == AuthState.UNAUTHORIZED) Icons.Default.Lock else Icons.Default.WifiOff
        val iconColor = if (state == AuthState.UNAUTHORIZED) MaterialTheme.colorScheme.error else Color(0xFFFBC02D) // Yellow for network
        val title = if (state == AuthState.UNAUTHORIZED) "Device Not Authorized" else "Connection Required"
        val description = if (state == AuthState.UNAUTHORIZED) {
            "This device has not been approved for use. Please provide the details below to your administrator."
        } else {
            "An internet connection is required for initial activation. Please check your network and try again."
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = iconColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(label = "Device ID", value = deviceId)
                InfoRow(label = "MAC Address", value = macAddress)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("CHECK STATUS AGAIN", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
