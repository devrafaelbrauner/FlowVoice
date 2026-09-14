package dev.rafaelbrauner.flowvoice.shared.model

import dev.rafaelbrauner.flowvoice.shared.http.sharedJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenRouterModelsResponseTest {

    @Test
    fun decodesContextLengthFromSnakeCase() {
        val response = sharedJson.decodeFromString(OpenRouterModelsResponse.serializer(), MODELS_JSON)

        assertEquals(2, response.data.size)
        val first = response.data[0]
        assertEquals("openai/gpt-4o-mini-transcribe", first.id)
        assertEquals("OpenAI: GPT-4o mini Transcribe", first.name)
        assertEquals(16000, first.contextLength)
        assertNull(response.data[1].contextLength)
    }

    private companion object {
        const val MODELS_JSON = """
        {
          "data": [
            {
              "id": "openai/gpt-4o-mini-transcribe",
              "canonical_slug": "openai/gpt-4o-mini-transcribe",
              "hugging_face_id": null,
              "name": "OpenAI: GPT-4o mini Transcribe",
              "created": 1742428800,
              "description": "Speech-to-text model.",
              "context_length": 16000,
              "architecture": {
                "modality": "audio->text",
                "input_modalities": ["audio"],
                "output_modalities": ["text"],
                "tokenizer": "GPT"
              },
              "pricing": { "prompt": "0.00000125", "completion": "0.000005" },
              "top_provider": { "context_length": 16000, "max_completion_tokens": 2000, "is_moderated": true },
              "per_request_limits": null,
              "supported_parameters": ["language", "prompt"]
            },
            {
              "id": "deepgram/nova-3",
              "name": "Deepgram: Nova 3",
              "context_length": null,
              "architecture": { "input_modalities": ["audio"], "output_modalities": ["text"] }
            }
          ]
        }
        """
    }
}
