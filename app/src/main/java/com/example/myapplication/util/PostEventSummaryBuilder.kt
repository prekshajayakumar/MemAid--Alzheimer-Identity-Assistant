package com.example.myapplication.util

import com.example.myapplication.data.entities.DeviationEventEntity
import com.example.myapplication.data.entities.DeviationEventType

object PostEventSummaryBuilder {

    fun build(events: List<DeviationEventEntity>): String {
        if (events.isEmpty()) return "Everything went as planned."

        val lines = mutableListOf<String>()

        val firstRoutineLabel = events.firstNotNullOfOrNull { it.routineLabel }
        if (!firstRoutineLabel.isNullOrBlank()) {
            lines += "Planned activity: $firstRoutineLabel."
        }

        if (events.any { it.eventType == DeviationEventType.UNKNOWN_PERSON_SAVED }) {
            lines += "You met someone the app could not identify, and that person was saved for caregiver review."
        }

        if (events.any { it.eventType == DeviationEventType.DEVIATION_PROMPT }) {
            lines += "You moved away from the expected place for a while, and a gentle reminder was shown."
        }

        if (events.any {
                it.eventType == DeviationEventType.DEVIATION_ESCALATION ||
                        it.eventType == DeviationEventType.DEVIATION_REPEAT_ESCALATION
            }) {
            lines += "The deviation continued, so extra attention was requested."
        }

        if (events.any {
                it.eventType == DeviationEventType.DEVIATION_SMS_5MIN ||
                        it.eventType == DeviationEventType.DEVIATION_SMS_15MIN ||
                        it.eventType == DeviationEventType.DEVIATION_SMS_20MIN ||
                        it.eventType == DeviationEventType.DEVIATION_SMS_30MIN ||
                        it.eventType == DeviationEventType.DEVIATION_SMS_REPEAT
            }) {
            lines += "A caregiver text update with location details was sent for extra support."
        }

        if (events.any { it.eventType == DeviationEventType.CAREGIVER_CALL }) {
            lines += "A caregiver call was started to provide support."
        }

        lines += "You are safe now."

        return lines.joinToString(separator = "\n\n")
    }
}