package com.focusbuddy.domain.mascot

import com.focusbuddy.domain.model.*

/** Bundled, original code-native illustrations. IDs are persisted, labels may change. */
enum class MascotVariant(val id: String, val label: String, val detail: String) {
    Mallow("bunny", "Mallow", "A soft-eared companion"),
    Luma("owl", "Luma", "A thoughtful night owl"),
    Pip("sprout", "Pip", "A little room to grow");
    companion object { fun from(id: String) = entries.firstOrNull { it.id == id } ?: Mallow }
}
enum class MascotMoment { Resting, UpcomingReminder, TaskCompleted, DayCompleted }
fun completionMoment(completed: Int, total: Int, isToday: Boolean): MascotMoment =
    if (isToday && total > 0 && completed == total) MascotMoment.DayCompleted else MascotMoment.TaskCompleted

/** Fixed local templates, with no generated or remote content. */
fun mascotMessage(personality: MascotPersonality, moment: MascotMoment): String = when (moment) {
    MascotMoment.DayCompleted -> when (personality) {
        MascotPersonality.Calm -> "Your day has room to breathe."
        MascotPersonality.Friendly -> "You did it. Enjoy a little time for you."
        MascotPersonality.Playful -> "All done. Tiny victory dance!"
        MascotPersonality.Focused -> "Today's tasks are complete. Time to recharge."
    }
    MascotMoment.TaskCompleted -> when (personality) {
        MascotPersonality.Calm -> "One small step, gently done."
        MascotPersonality.Friendly -> "A little win. I'm cheering for you."
        MascotPersonality.Playful -> "One down. A little high five!"
        MascotPersonality.Focused -> "Task complete. Keep your own pace."
    }
    MascotMoment.UpcomingReminder -> when (personality) {
        MascotPersonality.Calm -> "A gentle nudge for what matters."
        MascotPersonality.Friendly -> "Here when you need a little reminder."
        MascotPersonality.Playful -> "A little tap on the shoulder."
        MascotPersonality.Focused -> "Your next intention is ready."
    }
    MascotMoment.Resting -> when (personality) {
        MascotPersonality.Calm -> "One small step at a time."
        MascotPersonality.Friendly -> "Good company for your everyday plans."
        MascotPersonality.Playful -> "Small steps. Plenty of possibility."
        MascotPersonality.Focused -> "Make space for what matters next."
    }
}
