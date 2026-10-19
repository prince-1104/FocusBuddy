package com.focusbuddy.data.repository

import com.focusbuddy.data.local.FocusBuddyDatabase
import com.focusbuddy.domain.model.Category
import com.focusbuddy.domain.model.FocusSession
import com.focusbuddy.domain.repository.CategoryRepository
import com.focusbuddy.domain.repository.FocusSessionRepository
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomCategoryRepository(database: FocusBuddyDatabase) : CategoryRepository {
    private val categories = database.categories()
    override fun observeAll() = categories.observeAll().map { rows -> rows.map { it.model() } }
    override suspend fun create(category: Category) { categories.insert(category.entity()) }
    override suspend fun update(category: Category) {
        require(categories.update(category.entity()) == 1) { "Category does not exist" }
    }
    override suspend fun delete(id: UUID) { categories.delete(id) }
}

class RoomFocusSessionRepository(database: FocusBuddyDatabase) : FocusSessionRepository {
    private val sessions = database.focusSessions()
    override fun observeAll() = sessions.observeAll().map { rows -> rows.map { it.model() } }
    override suspend fun get(id: UUID) = sessions.get(id)?.model()
    override suspend fun create(session: FocusSession) { sessions.insert(session.entity()) }
    override suspend fun update(session: FocusSession) {
        require(sessions.update(session.entity()) == 1) { "Focus session does not exist" }
    }
    override suspend fun delete(id: UUID) { sessions.delete(id) }
}
