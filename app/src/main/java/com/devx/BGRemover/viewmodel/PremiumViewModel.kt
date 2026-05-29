package com.devx.BGRemover.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumViewModel : ViewModel() {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isCheckingStatus = MutableStateFlow(true)
    val isCheckingStatus: StateFlow<Boolean> = _isCheckingStatus.asStateFlow()

    private val customerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
        handleCustomerInfo(customerInfo, source = "listener")
    }

    init {
        Purchases.sharedInstance.updatedCustomerInfoListener = customerInfoListener
        checkPremiumStatus()
    }

    // ✅ FIX: called when paywall is dismissed without any action
    fun resetCheckingStatus() {
        _isCheckingStatus.value = false
    }
    fun checkPremiumStatus() {
        _isCheckingStatus.value = true
        Purchases.sharedInstance.getCustomerInfo(
            object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    handleCustomerInfo(customerInfo, source = "getCustomerInfo")
                }
                override fun onError(error: PurchasesError) {
                    Log.e("PremiumViewModel", "getCustomerInfo ERROR: ${error.message}")
                    _isPremium.value = false
                    _isCheckingStatus.value = false
                }
            }
        )
    }

    fun updateFromCustomerInfo(customerInfo: CustomerInfo) {
        handleCustomerInfo(customerInfo, source = "updateFromCustomerInfo")
    }

    private fun handleCustomerInfo(customerInfo: CustomerInfo, source: String) {

        val active = customerInfo.entitlements["premium"]?.isActive == true

        _isPremium.value = active
        _isCheckingStatus.value = false

    }
    override fun onCleared() {
        super.onCleared()
        Purchases.sharedInstance.updatedCustomerInfoListener = null
        _isCheckingStatus.value = false
    }

}