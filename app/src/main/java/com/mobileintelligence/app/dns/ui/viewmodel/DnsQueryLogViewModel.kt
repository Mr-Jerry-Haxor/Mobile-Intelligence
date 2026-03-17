package com.mobileintelligence.app.dns.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobileintelligence.app.dns.data.DnsRepository
import com.mobileintelligence.app.dns.data.entity.DnsQueryEntity
import kotlinx.coroutines.flow.*

/**
 * ViewModel for live DNS query log screen.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DnsQueryLogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DnsRepository(application)

    // Filter state
    private val _showBlockedOnly = MutableStateFlow(false)
    val showBlockedOnly: StateFlow<Boolean> = _showBlockedOnly

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Query log
    val queries: StateFlow<List<DnsQueryEntity>> = combine(
        _showBlockedOnly,
        _searchQuery
    ) { blockedOnly, search ->
        Pair(blockedOnly, search)
    }.flatMapLatest { (blockedOnly, search) ->
        when {
            search.isNotBlank() -> repository.searchQueries(search)
            blockedOnly -> repository.getRecentBlockedQueries(200)
            else -> repository.getRecentQueries(200)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBlockedOnly(blocked: Boolean) {
        _showBlockedOnly.value = blocked
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getAppLabel(packageName: String): String = repository.getAppLabel(packageName)
}
