package com.phonapps.qrscanner.data.repo

import com.phonapps.qrscanner.data.db.ScanDao
import com.phonapps.qrscanner.data.db.ScanEntity
import com.phonapps.qrscanner.data.db.ScanSource
import com.phonapps.qrscanner.data.model.QrClassifier
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for scan-history persistence.
 *
 * Duplicate suppression: a scan whose payload + source matches a record saved
 * within [DUPLICATE_WINDOW_MS] reuses the existing row instead of inserting a
 * new one. Callers that want to force a second record (explicit re-scan) pass
 * [forceNew] = true.
 */
class ScanRepository(private val dao: ScanDao) {

    fun observeAll(newestFirst: Boolean): Flow<List<ScanEntity>> =
        if (newestFirst) dao.observeAllDesc() else dao.observeAllAsc()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeById(id: Long): Flow<ScanEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): ScanEntity? = dao.getById(id)

    suspend fun recordScan(raw: String, source: ScanSource, forceNew: Boolean = false): Long {
        val content = QrClassifier.classify(raw)
        val now = System.currentTimeMillis()
        if (!forceNew) {
            val dupe = dao.findRecentDuplicate(raw, source.name, now - DUPLICATE_WINDOW_MS)
            if (dupe != null) return dupe.id
        }
        return dao.insert(
            ScanEntity(
                rawValue = raw,
                contentType = content.kind.name,
                title = content.title.take(TITLE_MAX_LEN),
                timestamp = now,
                source = source.name
            )
        )
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.deleteAll()

    companion object {
        private const val DUPLICATE_WINDOW_MS = 60_000L
        private const val TITLE_MAX_LEN = 160
    }
}
