package com.mobileintelligence.app.dns.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.dns.data.DnsRepository
import com.mobileintelligence.app.dns.data.entity.DnsDailyStats
import com.mobileintelligence.app.dns.data.dao.DnsQueryDao
import com.mobileintelligence.app.dns.filter.BlocklistManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for DNS statistics screen.
 * Shows historical data, top trackers, app network behavior.
 */
class DnsStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)

    // ─── Daily History ──────────────────────────────────────────

    val dailyStats: StateFlow<List<DnsDailyStats>> = repository.getDailyStats(14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Top Trackers ───────────────────────────────────────────

    val topBlockedDomains: StateFlow<List<DnsQueryDao.DomainCount>> =
        repository.getTodayTopBlockedDomains(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Top Apps ───────────────────────────────────────────────

    val topApps: StateFlow<List<DnsQueryDao.AppQueryCount>> =
        repository.getTodayTopApps(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Blocklist Info ─────────────────────────────────────────

    private val _blocklistInfo = MutableStateFlow<List<BlocklistManager.BlocklistInfo>>(emptyList())
    val blocklistInfo: StateFlow<List<BlocklistManager.BlocklistInfo>> = _blocklistInfo

    init {
        refreshBlocklistInfo()
    }

    fun refreshBlocklistInfo() {
        _blocklistInfo.value = repository.getBlocklistInfo()
    }

    fun getAppLabel(packageName: String): String = repository.getAppLabel(packageName)
}
