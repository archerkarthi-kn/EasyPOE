package fyi.copiercode.easypos.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PairedDevice(
    val token: String,
    val name: String,
    val pairedAt: Long = System.currentTimeMillis()
)

private val Context.dataStore by preferencesDataStore(name = "pos_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val TEL = stringPreferencesKey("footer_tel")
        val EMAIL = stringPreferencesKey("footer_email")
        val LOC1 = stringPreferencesKey("footer_loc1")
        val LOC2 = stringPreferencesKey("footer_loc2")
        val PAYMENT_BYPASS = booleanPreferencesKey("payment_bypass")
        val BILL_PREFIX = stringPreferencesKey("bill_prefix")
        val BILL_COUNTER = intPreferencesKey("bill_counter")
        val PAPER_WIDTH = intPreferencesKey("paper_width")
        val CACHED_IS_APPROVED = booleanPreferencesKey("cached_is_approved")
        val LAST_BILL_DATE = stringPreferencesKey("last_bill_date")
        val BRIDGE_PIN = stringPreferencesKey("bridge_pin")
        val BRIDGE_TOKEN = stringPreferencesKey("bridge_token")
        val BRIDGE_PAIRED_DEVICES = stringPreferencesKey("bridge_paired_devices")
    }

    val footerTel: Flow<String> = context.dataStore.data.map { it[Keys.TEL] ?: "" }
    val footerEmail: Flow<String> = context.dataStore.data.map { it[Keys.EMAIL] ?: "" }
    val footerLoc1: Flow<String> = context.dataStore.data.map { it[Keys.LOC1] ?: "" }
    val footerLoc2: Flow<String> = context.dataStore.data.map { it[Keys.LOC2] ?: "" }
    val paymentBypass: Flow<Boolean> = context.dataStore.data.map { it[Keys.PAYMENT_BYPASS] ?: false }
    val billPrefix: Flow<String> = context.dataStore.data.map { it[Keys.BILL_PREFIX] ?: "Taj" }
    val billCounter: Flow<Int> = context.dataStore.data.map { it[Keys.BILL_COUNTER] ?: 1 }
    val paperWidth: Flow<Int> = context.dataStore.data.map { it[Keys.PAPER_WIDTH] ?: 48 }
    val isApproved: Flow<Boolean?> = context.dataStore.data.map { it[Keys.CACHED_IS_APPROVED] }
    val bridgePin: Flow<String> = context.dataStore.data.map { it[Keys.BRIDGE_PIN] ?: "1234" }
    val bridgeToken: Flow<String> = context.dataStore.data.map { it[Keys.BRIDGE_TOKEN] ?: "" }
    
    val pairedDevices: Flow<List<PairedDevice>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.BRIDGE_PAIRED_DEVICES] ?: "[]"
        try {
            Json.decodeFromString<List<PairedDevice>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun setBridgePin(pin: String) {
        context.dataStore.edit { it[Keys.BRIDGE_PIN] = pin }
    }

    suspend fun setBridgeToken(token: String) {
        context.dataStore.edit { it[Keys.BRIDGE_TOKEN] = token }
    }

    suspend fun addPairedDevice(device: PairedDevice) {
        context.dataStore.edit { prefs ->
            val jsonStr = prefs[Keys.BRIDGE_PAIRED_DEVICES] ?: "[]"
            val list = try {
                Json.decodeFromString<MutableList<PairedDevice>>(jsonStr)
            } catch (e: Exception) {
                mutableListOf()
            }
            list.removeAll { it.token == device.token }
            list.add(device)
            prefs[Keys.BRIDGE_PAIRED_DEVICES] = Json.encodeToString(list)
        }
    }

    suspend fun removePairedDevice(token: String) {
        context.dataStore.edit { prefs ->
            val jsonStr = prefs[Keys.BRIDGE_PAIRED_DEVICES] ?: "[]"
            val list = try {
                Json.decodeFromString<MutableList<PairedDevice>>(jsonStr)
            } catch (e: Exception) {
                mutableListOf()
            }
            list.removeAll { it.token == token }
            prefs[Keys.BRIDGE_PAIRED_DEVICES] = Json.encodeToString(list)
        }
    }

    suspend fun isDevicePaired(token: String): Boolean {
        val list = pairedDevices.first()
        return list.any { it.token == token }
    }

    suspend fun saveFooterDetails(tel: String, email: String, loc1: String, loc2: String) {
        context.dataStore.edit {
            it[Keys.TEL] = tel
            it[Keys.EMAIL] = email
            it[Keys.LOC1] = loc1
            it[Keys.LOC2] = loc2
        }
    }

    suspend fun setPaymentBypass(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PAYMENT_BYPASS] = enabled }
    }

    suspend fun setBillPrefix(prefix: String) {
        context.dataStore.edit { it[Keys.BILL_PREFIX] = prefix }
    }

    suspend fun resetCounter() {
        context.dataStore.edit { it[Keys.BILL_COUNTER] = 1 }
    }

    suspend fun setPaperWidth(width: Int) {
        context.dataStore.edit { it[Keys.PAPER_WIDTH] = width }
    }

    suspend fun setApproved(approved: Boolean) {
        context.dataStore.edit { it[Keys.CACHED_IS_APPROVED] = approved }
    }

    suspend fun getNextBillNumber(): String {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        var prefix = "Taj"
        var finalCounter = 1

        context.dataStore.edit { preferences ->
            val lastDate = preferences[Keys.LAST_BILL_DATE] ?: ""
            prefix = preferences[Keys.BILL_PREFIX] ?: "Taj"
            val currentCounter = preferences[Keys.BILL_COUNTER] ?: 1

            if (today != lastDate) {
                // Strictly new day: Always start at 1
                finalCounter = 1
                preferences[Keys.LAST_BILL_DATE] = today
                preferences[Keys.BILL_COUNTER] = 2 // Next bill will be 002
            } else {
                // Same day: Use current counter and increment
                finalCounter = currentCounter
                preferences[Keys.BILL_COUNTER] = currentCounter + 1
            }
        }
        
        return "$prefix ${finalCounter.toString().padStart(3, '0')}"
    }
}
