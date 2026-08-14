package com.example.smsforwarder.data.repository

import com.example.smsforwarder.data.local.dao.FilterDao
import com.example.smsforwarder.data.local.dao.FilterMonthlySummaryDao
import com.example.smsforwarder.data.local.dao.LogDao
import com.example.smsforwarder.data.local.entity.FilterEntity
import com.example.smsforwarder.data.local.entity.FilterMonthlySummaryEntity
import com.example.smsforwarder.data.local.entity.LogEntity
import com.example.smsforwarder.domain.model.Filter
import com.example.smsforwarder.domain.model.ForwardLog
import com.example.smsforwarder.domain.model.MonthlySummaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ForwarderRepository {
    fun getAllFilters(): Flow<List<Filter>>
    suspend fun getActiveFilters(): List<Filter>
    suspend fun getFilterById(id: Long): Filter?
    suspend fun saveFilter(filter: Filter): Long
    suspend fun setFilterActive(filterId: Long, isActive: Boolean)
    suspend fun updateFilterOrders(orderList: List<Pair<Long, Int>>)
    suspend fun deleteFilter(filter: Filter)

    fun getAllLogs(): Flow<List<ForwardLog>>
    suspend fun addLog(log: ForwardLog): Long
    suspend fun clearLogs()

    fun getMonthlySummaries(): Flow<List<MonthlySummaryEntry>>
    suspend fun restoreMonthlySummaries(entries: List<MonthlySummaryEntry>)
}

class ForwarderRepositoryImpl(
    private val filterDao: FilterDao,
    private val logDao: LogDao,
    private val filterMonthlySummaryDao: FilterMonthlySummaryDao
) : ForwarderRepository {

    override fun getAllFilters(): Flow<List<Filter>> {
        return filterDao.getAllFilters().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getActiveFilters(): List<Filter> {
        return filterDao.getActiveFilters().map { it.toDomain() }
    }

    override suspend fun getFilterById(id: Long): Filter? {
        return filterDao.getFilterById(id)?.toDomain()
    }

    override suspend fun saveFilter(filter: Filter): Long {
        var targetFilter = filter
        if (targetFilter.id > 0) {
            val existingById = filterDao.getFilterById(targetFilter.id)
            if (existingById != null) {
                targetFilter = targetFilter.copy(id = existingById.id)
            }
        } else {
            val existingByName = filterDao.getFilterByName(targetFilter.name)
            if (existingByName != null) {
                targetFilter = targetFilter.copy(id = existingByName.id)
            }
        }
        val entity = FilterEntity.fromDomain(targetFilter)
        return filterDao.insertFilter(entity)
    }

    override suspend fun setFilterActive(filterId: Long, isActive: Boolean) {
        filterDao.updateActiveStatus(filterId, isActive)
    }

    override suspend fun updateFilterOrders(orderList: List<Pair<Long, Int>>) {
        filterDao.updateFilterOrders(orderList)
    }

    override suspend fun deleteFilter(filter: Filter) {
        if (filter.id > 0) {
            val entity = FilterEntity.fromDomain(filter)
            filterDao.deleteFilter(entity)
        } else {
            val existing = filterDao.getFilterByName(filter.name)
            if (existing != null) {
                filterDao.deleteFilter(existing)
            } else {
                val entity = FilterEntity.fromDomain(filter)
                filterDao.deleteFilter(entity)
            }
        }
    }

    override fun getAllLogs(): Flow<List<ForwardLog>> {
        return logDao.getAllLogs().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun addLog(log: ForwardLog): Long {
        val entity = LogEntity.fromDomain(log)
        return logDao.insertLog(entity)
    }

    override suspend fun clearLogs() {
        logDao.clearLogs()
    }

    override fun getMonthlySummaries(): Flow<List<MonthlySummaryEntry>> {
        return filterMonthlySummaryDao.getAllSummaries().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun restoreMonthlySummaries(entries: List<MonthlySummaryEntry>) {
        val entities = entries.map {
            FilterMonthlySummaryEntity(
                filterId = it.filterId,
                filterName = it.filterName,
                periodLabel = it.periodLabel,
                periodStartTimestamp = it.periodStartTimestamp,
                totalAmount = it.totalAmount
            )
        }
        filterMonthlySummaryDao.restoreSummaries(entities)
    }
}
