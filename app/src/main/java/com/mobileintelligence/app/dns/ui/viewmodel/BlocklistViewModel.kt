package com.mobileintelligence.app.dns.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.dns.data.entity.BlocklistEntity
import com.mobileintelligence.app.dns.filter.BlocklistRepository
import com.mobileintelligence.app.dns.filter.DomainFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Blocklist Management screen.
 * Handles CRUD, download, and toggle operations for custom and pre-configured blocklists.
 */
class BlocklistViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BlocklistRepository(application)
    private val domainFilter by lazy { DomainFilter(application) }

    // ─── UI State ───────────────────────────────────────────────

    val allBlocklists: StateFlow<List<BlocklistEntity>> = repository.allBlocklists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledCount: StateFlow<Int> = repository.enabledCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalEnabledDomains: StateFlow<Int> = repository.totalEnabledDomains
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadingIds: StateFlow<Set<Long>> = _downloadingIds.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editingBlocklist = MutableStateFlow<BlocklistEntity?>(null)
    val editingBlocklist: StateFlow<BlocklistEntity?> = _editingBlocklist.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // ─── Filter state ───────────────────────────────────────────

    private val _filterCategory = MutableStateFlow<String?>(null)
    val filterCategory: StateFlow<String?> = _filterCategory.asStateFlow()

    val filteredBlocklists: StateFlow<List<BlocklistEntity>> = combine(
        allBlocklists,
        _filterCategory
    ) { lists, category ->
        if (category == null) lists
        else lists.filter { it.category == category }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Initialization ─────────────────────────────────────────

    init {
        viewModelScope.launch {
            repository.seedPreConfiguredLists()
        }
    }

    // ─── CRUD Actions ───────────────────────────────────────────

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    fun startEditing(blocklist: BlocklistEntity) { _editingBlocklist.value = blocklist }
    fun stopEditing() { _editingBlocklist.value = null }

    fun setFilterCategory(category: String?) { _filterCategory.value = category }

    fun addCustomBlocklist(name: String, url: String, description: String = "", category: String = "custom") {
        viewModelScope.launch {
            try {
                val id = repository.addCustomBlocklist(name, url, description, category)
                if (id > 0) {
                    _toastMessage.tryEmit("Added: $name")
                    _showAddDialog.value = false
                } else {
                    _toastMessage.tryEmit("Blocklist already exists")
                }
            } catch (e: Exception) {
                _toastMessage.tryEmit("Error: ${e.message}")
            }
        }
    }

    fun addFromFile(name: String, uri: Uri, description: String = "", category: String = "custom") {
        viewModelScope.launch {
            try {
                val id = repository.addFromLocalFile(name, uri, description, category)
                if (id > 0) {
                    _toastMessage.tryEmit("Imported: $name")
                    _showAddDialog.value = false
                } else {
                    _toastMessage.tryEmit("Import failed")
                }
            } catch (e: Exception) {
                _toastMessage.tryEmit("Error: ${e.message}")
            }
        }
    }

    fun updateBlocklist(id: Long, name: String, url: String, description: String, category: String) {
        viewModelScope.launch {
            try {
                repository.updateBlocklist(id, name, url, description, category)
                _toastMessage.tryEmit("Updated: $name")
                _editingBlocklist.value = null
            } catch (e: Exception) {
                _toastMessage.tryEmit("Error: ${e.message}")
            }
        }
    }

    fun deleteBlocklist(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteBlocklist(id)
                _toastMessage.tryEmit("Deleted")
            } catch (e: Exception) {
                _toastMessage.tryEmit("Error: ${e.message}")
            }
        }
    }

    fun toggleBlocklist(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleBlocklist(id, enabled)
                // Refresh DomainFilter custom set after toggle
                reloadCustomDomains()
            } catch (e: Exception) {
                _toastMessage.tryEmit("Error: ${e.message}")
            }
        }
    }

    // ─── Download Actions ───────────────────────────────────────

    fun downloadBlocklist(id: Long) {
        viewModelScope.launch {
            _downloadingIds.update { it + id }
            try {
                repository.downloadBlocklist(id)
                reloadCustomDomains()
            } catch (e: Exception) {
                _toastMessage.tryEmit("Download failed: ${e.message}")
            } finally {
                _downloadingIds.update { it - id }
            }
        }
    }

    fun refreshAllEnabled() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshAllEnabled()
                reloadCustomDomains()
                _toastMessage.tryEmit("All blocklists refreshed")
            } catch (e: Exception) {
                _toastMessage.tryEmit("Refresh failed: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ─── Domain filter integration ──────────────────────────────

    private suspend fun reloadCustomDomains() {
        try {
            val domains = repository.loadEnabledDomains()
            domainFilter.replaceCustomBlocklistDomains(domains)
        } catch (e: Exception) {
            // Non-fatal — filter continues with previous state
        }
    }
}
