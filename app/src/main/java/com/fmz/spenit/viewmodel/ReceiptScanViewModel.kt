package com.fmz.spenit.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenit.SpenItApp
import com.fmz.spenit.data.db.entity.Receipt
import com.fmz.spenit.data.db.entity.ReceiptItem
import com.fmz.spenit.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class ReceiptScanViewModel : ViewModel() {

    private val container = SpenItApp.instance.container
    private val receiptRepo = container.receiptRepository
    private val preferences = container.preferences
    private val aiCore = container.aiCoreService

    private val _imagePath = MutableStateFlow<String?>(null)
    val imagePath: StateFlow<String?> = _imagePath

    private val _hasImage = MutableStateFlow(false)
    val hasImage: StateFlow<Boolean> = _hasImage

    private val _merchant = MutableStateFlow("")
    val merchant: StateFlow<String> = _merchant

    private val _date = MutableStateFlow(DateUtils.today())
    val date: StateFlow<String> = _date

    private val _total = MutableStateFlow(0.0)
    val total: StateFlow<Double> = _total

    private val _taxAmount = MutableStateFlow(0.0)
    val taxAmount: StateFlow<Double> = _taxAmount

    private val _currency = MutableStateFlow("$")
    val currency: StateFlow<String> = _currency

    private val _category = MutableStateFlow("General")
    val category: StateFlow<String> = _category

    private val _notes = MutableStateFlow<String?>(null)
    val notes: StateFlow<String?> = _notes

    private val _isTaxDeductible = MutableStateFlow(false)
    val isTaxDeductible: StateFlow<Boolean> = _isTaxDeductible

    private val _taxCategory = MutableStateFlow<String?>(null)
    val taxCategory: StateFlow<String?> = _taxCategory

    private val _tagsInput = MutableStateFlow("")
    val tagsInput: StateFlow<String> = _tagsInput

    private val _items = MutableStateFlow<List<ReceiptItem>>(emptyList())
    val items: StateFlow<List<ReceiptItem>> = _items

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private var editReceiptId = 0

    init {
        initCurrency()
    }

    private fun initCurrency() {
        viewModelScope.launch {
            _currency.value = preferences.getDefaultCurrency()
        }
    }

    fun setImage(imagePath: String) {
        _imagePath.value = imagePath
        _hasImage.value = true
    }

    fun setImageUri(uri: Uri) {
        _imagePath.value = uri.toString()
        _hasImage.value = true
    }

    fun extractReceiptData() {
        viewModelScope.launch {
            val path = _imagePath.value ?: return@launch
            _isProcessing.value = true
            _isBusy.value = true
            try {
                val result = aiCore.extractReceiptData(path, _currency.value)
                if (result != null) {
                    if (result.merchant.isNotBlank()) _merchant.value = result.merchant
                    if (result.total > 0) _total.value = result.total
                    if (result.taxAmount > 0) _taxAmount.value = result.taxAmount
                    if (result.category.isNotBlank() && _category.value == "General") _category.value = result.category
                    if (!result.notes.isNullOrBlank() && _notes.value.isNullOrBlank()) _notes.value = result.notes
                    if (result.items.isNotEmpty()) {
                        _items.value = result.items.map { item ->
                            ReceiptItem(
                                description = item.description,
                                quantity = item.quantity,
                                unitPrice = item.unitPrice,
                                total = item.total
                            )
                        }
                    }
                }
            } finally {
                _isProcessing.value = false
                _isBusy.value = false
            }
        }
    }

    fun prepareEdit(receiptId: Int) {
        viewModelScope.launch {
            editReceiptId = receiptId
            val receipt = receiptRepo.getReceiptById(receiptId) ?: return@launch
            _merchant.value = receipt.merchant
            _date.value = receipt.date
            _total.value = receipt.total
            _taxAmount.value = receipt.taxAmount
            _currency.value = receipt.currency
            _category.value = receipt.category
            _notes.value = receipt.notes
            _isTaxDeductible.value = receipt.isTaxDeductible
            _taxCategory.value = receipt.taxCategory
            _imagePath.value = receipt.imagePath
            _hasImage.value = !receipt.imagePath.isNullOrEmpty()
            _items.value = receiptRepo.getReceiptItems(receiptId)

            val tags = try {
                Json.decodeFromString<List<String>>(receipt.tagsJson)
            } catch (e: Exception) { emptyList() }
            _tagsInput.value = tags.joinToString(", ")
        }
    }

    fun saveReceipt() {
        viewModelScope.launch {
            if (_merchant.value.isBlank()) return@launch
            _isBusy.value = true
            try {
                val tags = _tagsInput.value
                    .split(",", ";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val tagsJson = Json.encodeToString(tags)

                val receipt = Receipt(
                    merchant = _merchant.value,
                    date = _date.value,
                    total = _total.value,
                    taxAmount = _taxAmount.value,
                    currency = _currency.value,
                    category = _category.value,
                    imagePath = _imagePath.value,
                    notes = _notes.value?.trim()?.ifBlank { null },
                    isTaxDeductible = _isTaxDeductible.value,
                    taxYear = if (_isTaxDeductible.value) _date.value.substring(0, 4) else null,
                    taxCategory = if (_isTaxDeductible.value) _taxCategory.value else null,
                    tagsJson = tagsJson
                )

                receiptRepo.saveReceipt(receipt)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun removeItem(item: ReceiptItem) {
        val current = _items.value.toMutableList()
        if (current.remove(item)) {
            _items.value = current
        }
    }

    fun setMerchant(v: String) { _merchant.value = v }
    fun setDate(v: String) { _date.value = v }
    fun setTotal(v: Double) { _total.value = v }
    fun setTaxAmount(v: Double) { _taxAmount.value = v }
    fun setCategory(v: String) { _category.value = v }
    fun setNotes(v: String?) { _notes.value = v }
    fun setIsTaxDeductible(v: Boolean) { _isTaxDeductible.value = v }
    fun setTaxCategory(v: String?) { _taxCategory.value = v }
    fun setTagsInput(v: String) { _tagsInput.value = v }
}
