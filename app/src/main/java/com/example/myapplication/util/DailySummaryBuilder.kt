package com.example.myapplication.util

import com.example.myapplication.data.entities.DeviationEventEntity
import com.example.myapplication.data.entities.DeviationEventType
import com.example.myapplication.data.entities.RecognitionLogEntity
import com.example.myapplication.data.entities.RecognitionOutcome
import com.example.myapplication.data.entities.RoutineItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailySummaryBuilder {

    fun build(
        routines: List<RoutineItemEntity>,
        recognitionLogs: List<RecognitionLogEntity>,
        events: List<DeviationEventEntity>
    ): String {
        val parts = mutableListOf<String>()

        parts += buildIntro(routines, recognitionLogs, events)

        buildRoutineSummary(routines)?.let { parts += it }
        buildRecognitionSummary(recognitionLogs)?.let { parts += it }
        buildDeviationSummary(events)?.let { parts += it }

        parts += buildClosing(events, recognitionLogs)

        return parts.joinToString("\n\n")
    }

    private fun buildIntro(
        routines: List<RoutineItemEntity>,
        recognitionLogs: List<RecognitionLogEntity>,
        events: List<DeviationEventEntity>
    ): String {
        return when {
            routines.isEmpty() && recognitionLogs.isEmpty() && events.isEmpty() ->
                "It has been a quiet day so far. Nothing important has been recorded yet."

            routines.isNotEmpty() && recognitionLogs.isEmpty() && events.isEmpty() ->
                "Here is what your day looks like so far."

            else ->
                "Here is a summary of what has happened today."
        }
    }

    private fun buildRoutineSummary(routines: List<RoutineItemEntity>): String? {
        if (routines.isEmpty()) return null

        val sorted = routines.sortedBy { it.timeMinutes }
        val lines = mutableListOf<String>()

        lines += "Planned activities for today:"

        sorted.take(6).forEach { item ->
            val start = formatMinutes(item.timeMinutes)
            val end = formatMinutes(item.endTimeMinutes ?: (item.timeMinutes + 60))
            val place = item.expectedLocationLabel?.takeIf { it.isNotBlank() }?.let { " at $it" } ?: ""
            lines += "• ${item.label} from $start to $end$place."
        }

        if (sorted.size > 6) {
            lines += "• There were also ${sorted.size - 6} more scheduled activities."
        }

        return lines.joinToString("\n")
    }

    private fun buildRecognitionSummary(logs: List<RecognitionLogEntity>): String? {
        if (logs.isEmpty()) return null

        val recognized = logs.count { it.outcome == RecognitionOutcome.RECOGNIZED }
        val unknown = logs.count { it.outcome == RecognitionOutcome.UNKNOWN }
        val noFace = logs.count { it.outcome == RecognitionOutcome.NO_FACE }
        val errors = logs.count {
            it.outcome == RecognitionOutcome.ERROR ||
                    it.outcome == RecognitionOutcome.IMAGE_DECODE_FAIL
        }

        val lines = mutableListOf<String>()
        lines += "People recognition activity:"

        if (recognized > 0) {
            lines += "• $recognized ${pluralize("person", recognized)} ${pluralize("was", recognized)} recognized successfully."
        }

        if (unknown > 0) {
            lines += "• $unknown ${pluralize("person", unknown)} could not be recognized."
        }

        if (noFace > 0) {
            lines += "• $noFace recognition ${pluralize("attempt", noFace)} did not capture a clear face."
        }

        if (errors > 0) {
            lines += "• $errors recognition ${pluralize("attempt", errors)} had a capture or processing problem."
        }

        val latest = logs.maxByOrNull { it.ts }
        if (latest != null) {
            lines += "• The latest recognition activity was at ${formatClock(latest.ts)}."
        }

        return if (lines.size == 1) null else lines.joinToString("\n")
    }

    private fun buildDeviationSummary(events: List<DeviationEventEntity>): String? {
        if (events.isEmpty()) return null

        val unknownSaved = events.count { it.eventType == DeviationEventType.UNKNOWN_PERSON_SAVED }
        val prompts = events.count { it.eventType == DeviationEventType.DEVIATION_PROMPT }
        val escalations = events.count { it.eventType == DeviationEventType.DEVIATION_ESCALATION }
        val repeatEscalations = events.count { it.eventType == DeviationEventType.DEVIATION_REPEAT_ESCALATION }
        val caregiverCalls = events.count { it.eventType == DeviationEventType.CAREGIVER_CALL }

        val lines = mutableListOf<String>()
        lines += "Important events today:"

        if (unknownSaved > 0) {
            lines += "• $unknownSaved unknown ${pluralize("encounter", unknownSaved)} ${pluralize("was", unknownSaved)} saved for caregiver review."
        }

        if (prompts > 0) {
            lines += "• $prompts reminder ${pluralize("was", prompts)} shown when you were away from an expected place."
        }

        if (escalations > 0) {
            lines += "• $escalations deviation ${pluralize("event", escalations)} needed extra attention."
        }

        if (repeatEscalations > 0) {
            lines += "• $repeatEscalations repeated alert ${pluralize("was", repeatEscalations)} sent because the deviation continued."
        }

        if (caregiverCalls > 0) {
            lines += "• A caregiver call was started $caregiverCalls ${pluralize("time", caregiverCalls)}."
        }

        val latest = events.maxByOrNull { it.ts }
        if (latest != null) {
            val routinePart = latest.routineLabel?.takeIf { it.isNotBlank() }?.let { " during $it" } ?: ""
            lines += "• The latest important event happened at ${formatClock(latest.ts)}$routinePart."
        }

        return if (lines.size == 1) null else lines.joinToString("\n")
    }

    private fun buildClosing(
        events: List<DeviationEventEntity>,
        logs: List<RecognitionLogEntity>
    ): String {
        val seriousDeviation = events.any {
            it.eventType == DeviationEventType.DEVIATION_ESCALATION ||
                    it.eventType == DeviationEventType.DEVIATION_REPEAT_ESCALATION
        }

        val uncertainty = events.any { it.eventType == DeviationEventType.UNKNOWN_PERSON_SAVED } ||
                logs.any { it.outcome == RecognitionOutcome.UNKNOWN }

        return when {
            seriousDeviation ->
                "Some moments today needed extra support, and the app kept track of them for you."

            uncertainty ->
                "There were a few uncertain moments today, but they were recorded to make things easier later."

            else ->
                "Overall, your day has been going smoothly."
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val h24 = (minutes / 60) % 24
        val m = minutes % 60
        val ampm = if (h24 < 12) "AM" else "PM"
        val h12 = when (val v = h24 % 12) {
            0 -> 12
            else -> v
        }
        val mm = if (m < 10) "0$m" else "$m"
        return "$h12:$mm $ampm"
    }

    private fun formatClock(ts: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun pluralize(word: String, count: Int): String {
        return if (count == 1) word else "${word}s"
    }
}
