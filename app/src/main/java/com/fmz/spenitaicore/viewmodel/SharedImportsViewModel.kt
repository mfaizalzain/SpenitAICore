package com.fmz.spenitaicore.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmz.spenitaicore.data.db.entity.SharedImportItem
import com.fmz.spenitaicore.data.db.entity.SharedImportKind
import com.fmz.spenitaicore.data.db.entity.SharedImportStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SharedImportsViewModel : ViewModel() {

    private val _imports = MutableStateFlow<List<SharedImportItem>>(emptyList())
    val imports: StateFlow<List<SharedImportItem>> = _imports

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    fun addFiles(filePaths: List<String>, displayNames: List<String>) {
        val newItems = filePaths.zip(displayNames).map { (path, name) ->
            SharedImportItem(
                id = UUID.randomUUID().toString(),
                filePath = path,
                displayName = name,
                kind = SharedImportKind.Unknown,
                status = SharedImportStatus.NeedsReview
            )
        }
        _imports.value = _imports.value + newItems
        refreshSummary()
    }

    fun setImportKind(item: SharedImportItem, kind: SharedImportKind) {
        _imports.value = _imports.value.map {
            if (it.id == item.id) it.copy(kind = kind) else it
        }
        refreshSummary()
    }

    fun processImport(item: SharedImportItem) {
        viewModelScope.launch {
            _imports.value = _imports.value.map {
                if (it.id == item.id) it.copy(status = SharedImportStatus.Processing) else it
            }
            refreshSummary()

            try {
                // In a real implementation, this would call the appropriate
                // OCR service and save to the database
                kotlinx.coroutines.delay(1500)
                _imports.value = _imports.value.map {
                    if (it.id == item.id) it.copy(status = SharedImportStatus.Completed) else it
                }
            } catch (e: Exception) {
                _imports.value = _imports.value.map {
                    if (it.id == item.id) it.copy(
                        status = SharedImportStatus.Failed,
                        statusMessage = e.message ?: "Failed"
                    ) else it
                }
            }
            refreshSummary()
        }
    }

    fun retryImport(item: SharedImportItem) {
        viewModelScope.launch {
            _imports.value = _imports.value.map {
                if (it.id == item.id) it.copy(
                    status = SharedImportStatus.NeedsReview,
                    statusMessage = null,
                    retryCount = it.retryCount + 1
                ) else it
            }
            processImport(item)
        }
    }

    fun removeImport(item: SharedImportItem) {
        _imports.value = _imports.value.filter { it.id != item.id }
        refreshSummary()
    }

    fun clearCompleted() {
        _imports.value = _imports.value.filter { it.status != SharedImportStatus.Completed }
        refreshSummary()
    }

    private fun refreshSummary() {
        _pendingCount.value = _imports.value.size
    }
}
