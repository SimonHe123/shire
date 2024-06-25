package com.phodal.shire.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.phodal.shire.settings.ShireSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class OpenAIProvider : LlmProvider {
    private val logger: Logger = logger<OpenAIProvider>()
//    private val timeout = (defaultTimeout).toDuration(DurationUnit.SECONDS)
    private val modelName: String
        get() = ShireSettingsState.getInstance().modelName
    private val openAiKey: String
        get() = ShireSettingsState.getInstance().apiToken
    private val maxTokenLength: Int get() = 16 * 1024
    private val messages: MutableList<ChatMessage> = ArrayList()
    private var historyMessageLength: Int = 0

    private val service: OpenAI
        get() {
            if (openAiKey.isEmpty()) {
                throw IllegalStateException("You LLM server Key is empty")
            }

            var openAiProxy = ShireSettingsState.getInstance().apiHost
            return if (openAiProxy.isEmpty()) {
                val openAIConfig = OpenAIConfig(
                    token = openAiKey,
//                    timeout = Timeout(socket = timeout)
                )

                OpenAI(openAIConfig)
            } else {
                if (!openAiProxy.endsWith("/")) {
                    openAiProxy += "/"
                }

                val config = OpenAIConfig(
                    token = openAiKey,
//                    timeout = Timeout(socket = timeout),
                    host = OpenAIHost(baseUrl = openAiProxy)
                )

                OpenAI(config)
            }
        }

    override fun isApplicable(project: Project): Boolean {
        return openAiKey.isNotEmpty() && modelName.isNotEmpty()
    }

    override fun clearMessage() {
        messages.clear()
        historyMessageLength = 0
    }

    override fun stream(promptText: String, systemPrompt: String, keepHistory: Boolean): Flow<String> {
        if (!keepHistory) {
            clearMessage()
        }

        var output = ""
        val completionRequest = prepareRequest(promptText, systemPrompt)

        return callbackFlow {
            withContext(Dispatchers.IO) {
                service.chatCompletions(completionRequest)
                    .onEach {
                        if (it.choices.isNotEmpty()) {
                            val content = it.choices[0].delta?.content
                            if (content != null ) {
                                output += content
                                trySend(content)
                            }
                        }
                    }
                    .onCompletion { println() }
                    .catch { error ->
                        logger.error("Error in stream", error)
                        trySend(error.message ?: "Error occurs")
                        error.printStackTrace()
                    }

                if (!keepHistory) {
                    clearMessage()
                }

                close()
            }
        }
    }

    private fun prepareRequest(promptText: String, systemPrompt: String): ChatCompletionRequest {
        if (systemPrompt.isNotEmpty()) {
            val systemMessage = ChatMessage(ChatRole.System, systemPrompt)
            messages.add(systemMessage)
        }

        val userMessage = ChatMessage(ChatRole.User, promptText)

        historyMessageLength += promptText.length
        if (historyMessageLength > maxTokenLength) {
            messages.clear()
        }

        messages.add(userMessage)
        logger.info("messages length: ${messages.size}")

        val chatCompletionRequest = ChatCompletionRequest (
            model = ModelId(modelName),
            messages = listOf(userMessage),
            temperature = 0.0
        )

        return chatCompletionRequest
    }
}
