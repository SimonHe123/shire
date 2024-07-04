package com.phodal.shirelang.run.runner

import com.intellij.execution.console.ConsoleViewWrapperBase
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.phodal.shirecore.config.ShireActionLocation
import com.phodal.shirecore.middleware.PostCodeHandleContext
import com.phodal.shirecore.provider.context.ActionLocationEditor
import com.phodal.shirelang.compiler.SHIRE_ERROR
import com.phodal.shirelang.compiler.ShireParsedResult
import com.phodal.shirelang.compiler.ShireSyntaxAnalyzer
import com.phodal.shirelang.compiler.ShireTemplateCompiler
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.run.ShireConfiguration
import com.phodal.shirelang.run.ShireConsoleView
import com.phodal.shirelang.run.ShireProcessHandler
import com.phodal.shirelang.run.executor.CustomRemoteAgentLlmExecutor
import com.phodal.shirelang.run.executor.ShireDefaultLlmExecutor
import com.phodal.shirelang.run.executor.ShireLlmExecutor
import com.phodal.shirelang.run.executor.ShireLlmExecutorContext
import com.phodal.shirelang.run.flow.ShireConversationService

class ShireRunner(
    private val shireFile: ShireFile,
    private val project: Project,
    private val console: ShireConsoleView,
    private val configuration: ShireConfiguration,
    private val userInput: String,
    private val processHandler: ShireProcessHandler
) {
    fun execute() {
        val syntaxAnalyzer = ShireSyntaxAnalyzer(project, shireFile, ActionLocationEditor.defaultEditor(project))
        val parsedResult = syntaxAnalyzer.parse()

        val runnerContext = processTemplateCompile(parsedResult)
        if (runnerContext.hasError) return

        project.getService(ShireConversationService::class.java)
            .createConversation(configuration.getScriptPath(), runnerContext.compileResult)

        if (runnerContext.hole?.actionLocation == ShireActionLocation.TERMINAL_MENU) {
            //
        } else {
            executeLlmTask(runnerContext)
        }
    }

    private fun processTemplateCompile(compileResult: ShireParsedResult): ShireRunnerContext {
        val hobbitHole = compileResult.config

        val templateCompiler =
            ShireTemplateCompiler(project, hobbitHole, compileResult.variableTable, compileResult.shireOutput)
        if (userInput.isNotEmpty()) {
            templateCompiler.putCustomVariable("input", userInput)
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
            var currentFile: PsiFile? = null
            runData.editor?.virtualFile?.also {
                currentFile = runReadAction { PsiManager.getInstance(project).findFile(it) }
            }

            val context = PostCodeHandleContext(
                selectedEntry = hobbitHole?.pickupElement(project, runData.editor),
                currentLanguage = currentFile?.language,
                currentFile = currentFile,
                genText = response,
                modifiedTextRange = textRange,
                editor = runData.editor,
            )

            hobbitHole?.executeStreamingEndProcessor(project, console, context)
            hobbitHole?.executeAfterStreamingProcessor(project, console, context)
        }
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
