package com.mobileintelligence.app.dns.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.dns.data.DnsRepository
import com.mobileintelligence.app.dns.filter.BlocklistManager
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for DNS settings screen.
 * Manages DNS provider, block mode, categories, blocklists, and data.
 */
class DnsSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)
    private val blocklistManager = BlocklistManager(application)

    // ─── VPN State ──────────────────────────────────────────────

    val vpnEnabled: StateFlow<Boolean> = repository.vpnEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val vpnAutoStart: StateFlow<Boolean> = repository.vpnAutoStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ─── Block Mode ─────────────────────────────────────────────

    val blockMode: StateFlow<String> = repository.blockMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zero_ip")

    // ─── DNS Provider ───────────────────────────────────────────

    val dnsProvider: StateFlow<String> = repository.dnsProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "cloudflare")

    val customDnsPrimary: StateFlow<String> = repository.customDnsPrimary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val customDnsSecondary: StateFlow<String> = repository.customDnsSecondary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // ─── Filter Categories ──────────────────────────────────────

    val blockAds: StateFlow<Boolean> = repository.blockAds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockTrackers: StateFlow<Boolean> = repository.blockTrackers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockMalware: StateFlow<Boolean> = repository.blockMalware
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ─── Logging ────────────────────────────────────────────────

    val loggingEnabled: StateFlow<Boolean> = repository.loggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val logRetentionDays: StateFlow<Int> = repository.logRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    // ─── Blocklist Info ─────────────────────────────────────────

    private val _blocklistInfo = MutableStateFlow<List<BlocklistManager.BlocklistInfo>>(emptyList())
    val blocklistInfo: StateFlow<List<BlocklistManager.BlocklistInfo>> = _blocklistInfo

    // ─── Dialog States ──────────────────────────────────────────

    private val _showClearDataDialog = MutableStateFlow(false)
    val showClearDataDialog: StateFlow<Boolean> = _showClearDataDialog

    init {
        refreshBlocklistInfo()
    }

    // ─── Actions ────────────────────────────────────────────────

    fun setVpnEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVpnEnabled(enabled) }
    }

    fun setVpnAutoStart(autoStart: Boolean) {
        viewModelScope.launch { repository.setVpnAutoStart(autoStart) }
    }

    fun setBlockMode(mode: String) {
        viewModelScope.launch { repository.setBlockMode(mode) }
    }

    fun setDnsProvider(provider: String) {
        viewModelScope.launch { repository.setDnsProvider(provider) }
    }

    fun setCustomDns(primary: String, secondary: String) {
        viewModelScope.launch { repository.setCustomDns(primary, secondary) }
    }

    fun setBlockAds(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockAds(enabled) }
    }

    fun setBlockTrackers(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockTrackers(enabled) }
    }

    fun setBlockMalware(enabled: Boolean) {
        viewModelScope.launch { repository.setBlockMalware(enabled) }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLoggingEnabled(enabled) }
    }

    fun setLogRetentionDays(days: Int) {
        viewModelScope.launch { repository.setLogRetentionDays(days) }
    }

    fun updateBlocklists() {
        repository.triggerBlocklistUpdate()
    }

    fun refreshBlocklistInfo() {
        _blocklistInfo.value = repository.getBlocklistInfo()
    }

    fun showClearDataDialog() { _showClearDataDialog.value = true }
    fun hideClearDataDialog() { _showClearDataDialog.value = false }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllDnsData()
            _showClearDataDialog.value = false
        }
    }
}
