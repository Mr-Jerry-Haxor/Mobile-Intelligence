package com.mobileintelligence.app.dns.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.dns.data.DnsRepository
import com.mobileintelligence.app.dns.data.entity.DnsQueryEntity
import com.mobileintelligence.app.dns.data.dao.DnsQueryDao
import com.mobileintelligence.app.dns.service.LocalDnsVpnService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the DNS Protection Dashboard.
 * Shows real-time stats, top blocked domains, and protection status.
 */
class DnsProtectionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)

    // ─── VPN State ──────────────────────────────────────────────

    val isVpnRunning: StateFlow<Boolean> = repository.isVpnRunning
    val vpnEnabled: StateFlow<Boolean> = repository.vpnEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ─── Live Counters ──────────────────────────────────────────

    val todayQueries: StateFlow<Int> = repository.todayQueries
    val todayBlocked: StateFlow<Int> = repository.todayBlocked

    val todayTotalFromDb: StateFlow<Int> = repository.getTodayTotalQueries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayBlockedFromDb: StateFlow<Int> = repository.getTodayBlockedQueries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCached: StateFlow<Int> = repository.getTodayCachedQueries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val avgResponseTime: StateFlow<Double?> = repository.getTodayAvgResponseTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val blockedPercentage: StateFlow<Float> = repository.getTodayBlockedPercentage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // ─── Lifetime Stats ─────────────────────────────────────────

    val totalQueriesLifetime: StateFlow<Long> = repository.totalQueriesLifetime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalBlockedLifetime: StateFlow<Long> = repository.totalBlockedLifetime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // ─── Top Blocked Domains ────────────────────────────────────

    val topBlockedDomains: StateFlow<List<DnsQueryDao.DomainCount>> =
        repository.getTodayTopBlockedDomains(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topDomains: StateFlow<List<DnsQueryDao.DomainCount>> =
        repository.getTodayTopDomains(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Top Apps ───────────────────────────────────────────────

    val topApps: StateFlow<List<DnsQueryDao.AppQueryCount>> =
        repository.getTodayTopApps(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Actions ────────────────────────────────────────────────

    fun toggleVpn() {
        viewModelScope.launch {
            val current = vpnEnabled.value
            repository.setVpnEnabled(!current)
            if (current) {
                LocalDnsVpnService.stop(getApplication())
            }
            // Starting is handled by the UI (needs VPN permission intent)
        }
    }

    fun getAppLabel(packageName: String): String = repository.getAppLabel(packageName)
}
