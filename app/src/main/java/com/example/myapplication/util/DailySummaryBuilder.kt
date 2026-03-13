package com.example.myapplication.util

import com.example.myapplication.data.entities.DeviationEventEntity
import com.example.myapplication.data.entities.DeviationEventType
import com.example.myapplication.data.entities.RecognitionLogEntity
import com.example.myapplication.data.entities.RecognitionOutcome
import com.example.myapplication.data.entities.RoutineItemEntity
import java.time.LocalTime

object DailySummaryBuilder {

    fun build(
        routines: List<RoutineItemEntity>,
        recognitionLogs: List<RecognitionLogEntity>,
        events: List<DeviationEventEntity>
    ): String {

        val lines = mutableListOf<String>()

        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute

        // routines completed so far
        val completedRoutines = routines
            .filter { it.timeMinutes <= nowMinutes }
            .map { it.label }

        if (completedRoutines.isNotEmpty()) {
            lines += "Today you completed: ${completedRoutines.joinToString()}."
        }

        // recognized people
        val recognizedPeople = recognitionLogs
            .filter { it.outcome == RecognitionOutcome.RECOGNIZED }
            .mapNotNull { it.personId }
            .distinct()

        if (recognizedPeople.isNotEmpty()) {
            lines += "You recognized ${recognizedPeople.size} familiar person(s) today."
        }

        // unknown person saved
        if (events.any { it.eventType == DeviationEventType.UNKNOWN_PERSON_SAVED }) {
            lines += "You met someone new today and the app saved them for caregiver review."
        }

        // deviations
        if (events.any {
                it.eventType == DeviationEventType.DEVIATION_ESCALATION ||
                        it.eventType == DeviationEventType.DEVIATION_REPEAT_ESCALATION
            }) {
            lines += "The app helped guide you back to your routine today."
        }

        // caregiver calls
        if (events.any { it.eventType == DeviationEventType.CAREGIVER_CALL }) {
            lines += "You contacted your caregiver today."
        }

        if (lines.isEmpty()) {
            return "You have had a calm and smooth day so far."
        }

        lines += "You are doing well today."

        return lines.joinToString("\n\n")
    }
}