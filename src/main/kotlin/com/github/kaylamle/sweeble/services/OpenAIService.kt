package com.github.kaylamle.sweeble.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.openapi.components.Service
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import org.json.JSONObject
import org.json.JSONArray
import com.intellij.openapi.diagnostic.Logger

@Service
open class OpenAIService {
    companion object {
        private val LOG = Logger.getInstance(OpenAIService::class.java)
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        fun getInstance(project: Project): OpenAIService {
            return project.getService(OpenAIService::class.java)
        }
    }

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun getCompletions(project: Project, file: PsiFile, editor: Editor, offset: Int): List<Completion> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isNullOrBlank()) {
                LOG.warn("OpenAI API key not configured")
                return emptyList()
            }
            val context = buildContext(file, editor, offset)
            val response = callOpenAI(apiKey, context)
            parseCompletions(response)
        } catch (e: Exception) {
            LOG.error("Error getting completions", e)
            emptyList()
        }
    }

    private fun getApiKey(): String? {
        // TODO: Get from settings/configuration
        return System.getenv("OPENAI_API_KEY")
    }

    private fun buildContext(file: PsiFile, editor: Editor, offset: Int): String {
        val document = editor.document
        val fileContent = document.text
        val beforeCursor = fileContent.substring(0, offset)
        val afterCursor = fileContent.substring(offset)
        return """
            File: ${file.name}
            Language: ${file.language.displayName}

            Context before cursor:
            $beforeCursor

            Context after cursor:
            $afterCursor

            Please provide relevant code completions that would make sense at this position.
            Return only the completion text, one per line.
        """.trimIndent()
    }

    // Change from protected open to open internal for mocking
    open internal fun callOpenAI(apiKey: String, context: String): String {
        val requestBody = JSONObject().apply {
            put("model", "gpt-4")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a helpful coding assistant. Provide concise, relevant code completions.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", context)
                })
            })
            put("max_tokens", 150)
            put("temperature", 0.3)
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(OPENAI_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    private fun parseCompletions(response: String): List<Completion> {
        val jsonResponse = JSONObject(response)
        val choices = jsonResponse.getJSONArray("choices")
        if (choices.length() == 0) return emptyList()
        val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
        return content.lines()
            .filter { it.isNotBlank() }
            .map { Completion(it.trim()) }
    }
}

data class Completion(val text: String) 