package fyi.copiercode.easypos.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fyi.copiercode.easypos.data.SettingsRepository
import fyi.copiercode.easypos.util.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthState {
    LOADING,
    AUTHORIZED,
    UNAUTHORIZED,
    NETWORK_ERROR_INITIAL
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.LOADING)
    val authState = _authState.asStateFlow()

    fun checkAuthorization(context: Context) {
        _authState.value = AuthState.LOADING
        
        AnalyticsHelper.sendDeviceHeartbeat(context) { isApproved ->
            viewModelScope.launch {
                if (isApproved != null) {
                    // Successful network check
                    settingsRepository.setApproved(isApproved)
                    _authState.value = if (isApproved) AuthState.AUTHORIZED else AuthState.UNAUTHORIZED
                } else {
                    // Network failure - Check cache
                    val cachedStatus = settingsRepository.isApproved.first()
                    if (cachedStatus == true) {
                        _authState.value = AuthState.AUTHORIZED
                    } else {
                        _authState.value = AuthState.NETWORK_ERROR_INITIAL
                    }
                }
            }
        }
    }
}
