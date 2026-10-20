package com.focusbuddy.domain

import com.focusbuddy.domain.mascot.*
import com.focusbuddy.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MascotBehaviorTest {
    @Test fun completionIsContextualAndEmptyDaysDoNotCelebrate() {
        assertEquals(MascotMoment.TaskCompleted, completionMoment(0, 0, true))
        assertEquals(MascotMoment.TaskCompleted, completionMoment(1, 2, true))
        assertEquals(MascotMoment.TaskCompleted, completionMoment(2, 2, false))
        assertEquals(MascotMoment.DayCompleted, completionMoment(2, 2, true))
    }
    @Test fun everyPersonalityHasBundledMessagesForEveryVisualState() {
        MascotMoment.entries.forEach { moment ->
            val messages = MascotPersonality.entries.map { mascotMessage(it, moment) }
            assertTrue(messages.all { it.isNotBlank() }); assertEquals(4, messages.toSet().size)
        }
        assertEquals(MascotVariant.Mallow, MascotVariant.from("missing-asset"))
        assertEquals(3, MascotVariant.entries.map { it.id }.toSet().size)
    }
}
