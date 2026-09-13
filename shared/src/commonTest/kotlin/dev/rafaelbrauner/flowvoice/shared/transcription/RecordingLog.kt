package dev.rafaelbrauner.flowvoice.shared.transcription

internal const val SECRET_KEY = "sk-or-v1-super-secret-test-key"

internal class RecordingLog : TranscriptionEventLog {
    data class Event(val event: String, val metadata: Map<String, String>)

    val events = mutableListOf<Event>()

    override fun log(event: String, metadata: Map<String, String>) {
        events += Event(event, metadata)
    }

    fun containsSecret(): Boolean {
        val haystack = buildString {
            events.forEach { event ->
                append(event.event)
                event.metadata.forEach { (key, value) ->
                    append(key)
                    append(value)
                }
            }
        }
        return haystack.contains(SECRET_KEY) || haystack.contains("Bearer")
    }
}
