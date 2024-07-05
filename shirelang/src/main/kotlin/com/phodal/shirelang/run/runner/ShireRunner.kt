package com.phodal.shirelang.run.runner

import com.intellij.execution.console.ConsoleViewWrapperBase
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.phodal.shirecore.config.ShireActionLocation
import com.phodal.shirecore.config.interaction.PostFunction
import com.phodal.shirecore.llm.LlmProvider
import com.phodal.shirecore.middleware.PostCodeHandleContext
import com.phodal.shirecore.provider.action.TerminalLocationExecutor
import com.phodal.shirecore.provider.context.ActionLocationEditor
import com.phodal.shirelang.ShireBundle
import com.phodal.shirelang.compiler.SHIRE_ERROR
import com.phodal.shirelang.compiler.ShireParsedResult
import com.phodal.shirelang.compiler.ShireTemplateCompiler
import com.phodal.shirelang.compiler.hobbit.HobbitHole
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.run.ShireConfiguration
import com.phodal.shirelang.run.ShireConsoleView
import com.phodal.shirelang.run.ShireProcessHandler
import com.phodal.shirelang.run.executor.CustomRemoteAgentLlmExecutor
import com.phodal.shirelang.run.executor.ShireDefaultLlmExecutor
import com.phodal.shirelang.run.executor.ShireLlmExecutor
import com.phodal.shirelang.run.executor.ShireLlmExecutorContext
import com.phodal.shirelang.run.flow.ShireConversationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ShireRunner(
    private val shireFile: ShireFile,
    private val project: Project,
    private val console: ShireConsoleView,
    private val configuration: ShireConfiguration,
    private val userInput: String,
    private val processHandler: ShireProcessHandler
) {
    private val terminalLocationExecutor = TerminalLocationExecutor.provide(project)

    fun execute(parsedResult: ShireParsedResult) {
        val runnerContext = processTemplateCompile(parsedResult)
        if (runnerContext.hasError) return

        project.getService(ShireConversationService::class.java)
            .createConversation(configuration.getScriptPath(), runnerContext.compileResult)

        if (runnerContext.hole?.actionLocation == ShireActionLocation.TERMINAL_MENU) {
            executeTerminalTask(runnerContext) { response, textRange ->
                executePostFunction(runnerContext, runnerContext.hole, response, textRange)
            }
        } else {
            executeLlmTask(runnerContext)
        }
    }

    private fun executeTerminalTask(context: ShireRunnerContext, postFunction: PostFunction) {
        CoroutineScope(Dispatchers.Main).launch {
            val handler = terminalLocationExecutor?.bundler(project, userInput)
            if (handler == null) {
                console.print("Terminal not found", ConsoleViewContentType.ERROR_OUTPUT)
                processHandler.destroyProcess()
                return@launch
            }

            val llmResult = StringBuilder()
            runBlocking {
                LlmProvider.provider(project)?.stream(context.finalPrompt, "", false)?.collect {
                    llmResult.append(it)
                    handler?.onChunk?.invoke(it)
                } ?: console.print(
                    ShireBundle.message("shire.llm.notfound"),
                    ConsoleViewContentType.ERROR_OUTPUT
                )
            }

            val response = llmResult.toString()
            handler?.onFinish?.invoke(response)

            postFunction(response, null)
            processHandler.detachProcess()
        }
    }

    private fun processTemplateCompile(compileResult: ShireParsedResult): ShireRunnerContext {
        val hobbitHole = compileResult.config

        val templateCompiler =
            ShireTemplateCompiler(project, hobbitHole, compileResult.variableTable, compileResult.shireOutput)
        if (userInput.isNotEmpty()) {
            templateCompiler.putCustomVariable("input", userInput)
        }

        PostCodeHandleContext.getData()?.lastTaskOutput?.let {
            templateCompiler.putCustomVariable("output", it)
        }

        val promptTextTrim = templateCompiler.compile().trim()
        printCompiledOutput(console, promptTextTrim, configuration)

        var hasError = false

        if (promptTextTrim.isEmpty()) {
            console.print("No content to run", ConsoleViewContentType.ERROR_OUTPUT)
            processHandler.destroyProcess()
            hasError = true
        }

        if (promptTextTrim.contains(SHIRE_ERROR)) {
            processHandler.exitWithError()
            hasError = true
        }

        return ShireRunnerContext(
            hobbitHole,
            editor = ActionLocationEditor.provide(project, hobbitHole?.actionLocation),
            compileResult,
            promptTextTrim,
            hasError
        )
    }

    fun executeLlmTask(runData: ShireRunnerContext) {
        val agent = runData.compileResult.executeAgent
        val hobbitHole = runData.hole

        val shireLlmExecutorContext = ShireLlmExecutorContext(
            configuration = configuration,
            processHandler = processHandler,
            console = console,
            myProject = project,
            hole = hobbitHole,
            prompt = runData.finalPrompt,
            editor = runData.editor,
        )
        val shireLlmExecutor: ShireLlmExecutor = when {
            agent != null -> {
                CustomRemoteAgentLlmExecutor(shireLlmExecutorContext, agent)
            }

            else -> {
                val isLocalMode = runData.compileResult.isLocalCommand
                ShireDefaultLlmExecutor(shireLlmExecutorContext, isLocalMode)
            }
        }

        shireLlmExecutor.prepareTask()
        shireLlmExecutor.execute { response, textRange ->
            executePostFunction(runData, hobbitHole, response, textRange)
        }
    }

    private fun executePostFunction(
        runData: ShireRunnerContext,
        hobbitHole: HobbitHole?,
        response: String?,
        textRange: TextRange?,
    ) {
        val currentFile = runData.editor?.virtualFile?.let {
            runReadAction { PsiManager.getInstance(project).findFile(it) }
        }

        val context = PostCodeHandleContext(
            selectedEntry = hobbitHole?.pickupElement(project, runData.editor),
            currentLanguage = currentFile?.language,
            currentFile = currentFile,
            genText = response,
            modifiedTextRange = textRange,
            editor = runData.editor,
            lastTaskOutput = response,
        )

        hobbitHole?.executeStreamingEndProcessor(project, console, context)
        hobbitHole?.executeAfterStreamingProcessor(project, console, context)

        processHandler.detachProcess()
    }

    private fun printCompiledOutput(
        console: ConsoleViewWrapperBase,
        promptText: String,
        shireConfiguration: ShireConfiguration,
    ) {
        console.print("Shire Script: ${shireConfiguration.getScriptPath()}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print("Shire Script Compile output:\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        promptText.split("\n").forEach {
            when {
                it.contains(SHIRE_ERROR) -> {
                    console.print(it, ConsoleViewContentType.LOG_ERROR_OUTPUT)
                }

                else -> {
                    console.print(it, ConsoleViewContentType.USER_INPUT)
                }
            }
            console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
        }

        console.print("\n--------------------\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }
}
