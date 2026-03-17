package com.mobileintelligence.app.engine.features

import com.mobileintelligence.app.engine.intelligence.IntelligenceEngine
import com.mobileintelligence.app.engine.intelligence.PatternDetector
import kotlinx.coroutines.flow.*

/**
 * Smart Suggestions — AI-driven actionable tips.
 *
 * Analyzes current scores, detected patterns, and historical trends
 * to produce personalized, prioritized suggestions for the user.
 *
 * Categories:
 * - REDUCE: Reduce harmful behaviors (doom scrolling, late-night use)
 * - IMPROVE: Improve positive metrics (focus, screen-free time)
 * - PROTECT: Enhance privacy (tighten DNS tier, enable DoH detection)
 * - CELEBRATE: Positive reinforcement for good habits
 */
class SmartSuggestions {

    /**
     * Generate suggestions based on current intelligence scores and detected patterns.
     */
    fun generate(
        scores: IntelligenceEngine.IntelligenceScores,
        patterns: List<IntelligenceEngine.DetectedPattern>,
        focusModeActive: Boolean = false
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        // ── Addiction Index Suggestions ──────────────────────

        when {
            scores.addictionIndex > 80 -> suggestions.add(
                Suggestion(
                    id = "dai_critical",
                    category = SuggestionCategory.REDUCE,
                    priority = Priority.URGENT,
                    title = "High Addiction Pattern Detected",
                    description = "Your phone usage shows strong addictive patterns. " +
                            "Consider enabling Focus Mode to break the cycle.",
                    actionLabel = "Enable Focus Mode",
                    actionId = "enable_focus_mode"
                )
            )
            scores.addictionIndex > 60 -> suggestions.add(
                Suggestion(
                    id = "dai_high",
                    category = SuggestionCategory.REDUCE,
                    priority = Priority.HIGH,
                    title = "Moderate Addiction Signals",
                    description = "You're checking your phone more frequently than usual. " +
                            "Try placing it face-down during work hours.",
                    actionLabel = "Schedule Focus Time",
                    actionId = "schedule_focus"
                )
            )
            scores.addictionIndex < 20 -> suggestions.add(
                Suggestion(
                    id = "dai_great",
                    category = SuggestionCategory.CELEBRATE,
                    priority = Priority.LOW,
                    title = "Healthy Usage Pattern!",
                    description = "Your phone usage is well-controlled. Keep it up!",
                    actionLabel = null,
                    actionId = null
                )
            )
        }

        // ── Focus Score Suggestions ─────────────────────────

        when {
            scores.focusScore < 30 -> suggestions.add(
                Suggestion(
                    id = "fss_low",
                    category = SuggestionCategory.IMPROVE,
                    priority = Priority.HIGH,
                    title = "Focus Needs Improvement",
                    description = "Your sessions are short and interrupted. " +
                            "Try the Pomodoro technique — 25 minutes of focused work, 5 minute break.",
                    actionLabel = "Start Pomodoro",
                    actionId = "start_pomodoro"
                )
            )
            scores.focusScore > 70 && !focusModeActive -> suggestions.add(
                Suggestion(
                    id = "fss_good",
                    category = SuggestionCategory.CELEBRATE,
                    priority = Priority.LOW,
                    title = "Great Focus Today!",
                    description = "You've maintained long, uninterrupted sessions. " +
                            "Your focus stability is above average.",
                    actionLabel = null,
                    actionId = null
                )
            )
        }

        // ── Privacy Suggestions ─────────────────────────────

        when {
            scores.privacyExposure > 70 -> suggestions.add(
                Suggestion(
                    id = "pes_high",
                    category = SuggestionCategory.PROTECT,
                    priority = Priority.HIGH,
                    title = "High Privacy Exposure",
                    description = "Many trackers are contacting your device. " +
                            "Consider upgrading your DNS blocking tier to Aggressive.",
                    actionLabel = "Upgrade DNS Tier",
                    actionId = "upgrade_dns_tier"
                )
            )
            scores.privacyExposure > 40 -> suggestions.add(
                Suggestion(
                    id = "pes_moderate",
                    category = SuggestionCategory.PROTECT,
                    priority = Priority.MEDIUM,
                    title = "Moderate Tracker Activity",
                    description = "Some apps are sending data to tracking services. " +
                            "Check the tracker simulation for details.",
                    actionLabel = "View Simulation",
                    actionId = "view_tracker_sim"
                )
            )
            scores.privacyExposure < 15 -> suggestions.add(
                Suggestion(
                    id = "pes_clean",
                    category = SuggestionCategory.CELEBRATE,
                    priority = Priority.LOW,
                    title = "Strong Privacy Posture",
                    description = "Very few trackers are getting through. Your DNS firewall " +
                            "is working effectively.",
                    actionLabel = null,
                    actionId = null
                )
            )
        }

        // ── Pattern-Based Suggestions ───────────────────────

        patterns.forEach { pattern ->
            when (pattern.type) {
                IntelligenceEngine.PatternType.DOOM_SCROLLING -> suggestions.add(
                    Suggestion(
                        id = "pattern_doom_${pattern.detectedAt}",
                        category = SuggestionCategory.REDUCE,
                        priority = Priority.HIGH,
                        title = "Doom Scrolling Detected",
                        description = "You've been scrolling continuously for a long time. " +
                                "Take a 5-minute break — look away from the screen, stretch, or walk.",
                        actionLabel = "Take a Break",
                        actionId = "take_break"
                    )
                )
                IntelligenceEngine.PatternType.COMPULSIVE_CHECKING -> suggestions.add(
                    Suggestion(
                        id = "pattern_compulsive_${pattern.detectedAt}",
                        category = SuggestionCategory.REDUCE,
                        priority = Priority.MEDIUM,
                        title = "Compulsive Phone Checking",
                        description = "You've unlocked your phone multiple times in a short period. " +
                                "Try leaving your phone in another room for 30 minutes.",
                        actionLabel = "Start Focus Mode",
                        actionId = "enable_focus_mode"
                    )
                )
                IntelligenceEngine.PatternType.NIGHT_DISRUPTION -> suggestions.add(
                    Suggestion(
                        id = "pattern_night_${pattern.detectedAt}",
                        category = SuggestionCategory.REDUCE,
                        priority = Priority.HIGH,
                        title = "Late-Night Phone Use",
                        description = "Using your phone late at night disrupts sleep. " +
                                "Consider scheduling Silent Mode after 10 PM.",
                        actionLabel = "Schedule Silent Mode",
                        actionId = "schedule_silent"
                    )
                )
                IntelligenceEngine.PatternType.EXTENDED_CONTINUOUS_USE -> suggestions.add(
                    Suggestion(
                        id = "pattern_extended_${pattern.detectedAt}",
                        category = SuggestionCategory.REDUCE,
                        priority = Priority.MEDIUM,
                        title = "Extended Screen Session",
                        description = "You've been using your phone for over 2 hours straight. " +
                                "The 20-20-20 rule helps: every 20 min, look 20ft away for 20 sec.",
                        actionLabel = null,
                        actionId = null
                    )
                )
                IntelligenceEngine.PatternType.RAPID_APP_SWITCHING -> suggestions.add(
                    Suggestion(
                        id = "pattern_switch_${pattern.detectedAt}",
                        category = SuggestionCategory.IMPROVE,
                        priority = Priority.LOW,
                        title = "Rapid App Switching",
                        description = "Frequently switching between apps reduces focus. " +
                                "Try using one app at a time and practice single-tasking.",
                        actionLabel = null,
                        actionId = null
                    )
                )
                else -> {}
            }
        }

        // ── Overall Health Suggestion ───────────────────────

        when {
            scores.overallHealth >= 80 -> suggestions.add(
                Suggestion(
                    id = "health_excellent",
                    category = SuggestionCategory.CELEBRATE,
                    priority = Priority.LOW,
                    title = "Excellent Digital Health!",
                    description = "Your overall digital wellness score is ${scores.overallHealth.toInt()}/100. " +
                            "You're maintaining great habits.",
                    actionLabel = null,
                    actionId = null
                )
            )
            scores.overallHealth < 40 -> suggestions.add(
                Suggestion(
                    id = "health_poor",
                    category = SuggestionCategory.REDUCE,
                    priority = Priority.URGENT,
                    title = "Digital Health Needs Attention",
                    description = "Your overall score is ${scores.overallHealth.toInt()}/100. " +
                            "Multiple areas need improvement. Start with the highest-priority suggestion above.",
                    actionLabel = "View Intelligence Console",
                    actionId = "view_console"
                )
            )
        }

        // Sort by priority and deduplicate
        return suggestions
            .distinctBy { it.id.substringBefore("_") + it.category }
            .sortedBy { it.priority.ordinal }
            .take(8)
    }

    // ── Data Classes ────────────────────────────────────────────

    data class Suggestion(
        val id: String,
        val category: SuggestionCategory,
        val priority: Priority,
        val title: String,
        val description: String,
        val actionLabel: String?,
        val actionId: String?
    )

    enum class SuggestionCategory(val displayName: String, val emoji: String) {
        REDUCE("Reduce", "📉"),
        IMPROVE("Improve", "📈"),
        PROTECT("Protect", "🛡\uFE0F"),
        CELEBRATE("Celebrate", "🎉")
    }

    enum class Priority {
        URGENT,
        HIGH,
        MEDIUM,
        LOW
    }
}
