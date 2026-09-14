package dev.rafaelbrauner.flowvoice.ui.screens.dictionary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary

class DictionaryScreenState(private val dictionary: PersonalDictionary) {
    var pending by mutableStateOf(dictionary.pending().map { it.surface })
        private set
    var approved by mutableStateOf(dictionary.approved().map { it.surface })
        private set
    var draft by mutableStateOf("")
        private set

    val pendingLabel: String
        get() = pendingLabel(pending.size)

    fun refresh() {
        pending = dictionary.pending().map { it.surface }
        approved = dictionary.approved().map { it.surface }
    }

    fun approve(surface: String) {
        if (surface.isBlank()) return
        dictionary.approve(surface)
        refresh()
    }

    fun discard(surface: String) {
        dictionary.reject(surface)
        refresh()
    }

    fun updateDraft(value: String) {
        draft = value
    }

    fun addDraft(): Boolean {
        val term = draft.trim()
        if (term.isEmpty()) return false
        dictionary.approve(term)
        draft = ""
        refresh()
        return true
    }

    companion object {
        fun pendingLabel(count: Int): String = when {
            count <= 0 -> "Nada pendente"
            count == 1 -> "1 termo aguardando"
            else -> "$count termos aguardando"
        }
    }
}
