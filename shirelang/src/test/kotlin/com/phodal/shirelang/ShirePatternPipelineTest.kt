package com.phodal.shirelang

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.phodal.shirecore.middleware.post.PostProcessorContext
import com.phodal.shirelang.compiler.execute.PatternActionProcessor
import com.phodal.shirelang.compiler.parser.ShireSyntaxAnalyzer
import com.phodal.shirelang.compiler.template.ShireVariableTemplateCompiler
import com.phodal.shirelang.psi.ShireFile
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language

class ShirePatternPipelineTest : BasePlatformTestCase() {
    fun testShouldSupportForTee() {
        @Language("Shire")
        val code = """
            ---
            name: Summary
            description: "Generate Summary"
            interaction: AppendCursor
            data: ["a", "b"]
            when: ${'$'}fileName.matches("/.*.java/")
            variables:
              "var2": /.*ple.shire/ { cat | find("fileName") | sort }
            onStreamingEnd: { append(${'$'}var2) | saveFile("summary.md") }
            ---
            
            Summary webpage: ${'$'}fileName
        """.trimIndent()

        val file = myFixture.addFileToProject("sample.shire", code)

        myFixture.openFileInEditor(file.virtualFile)

        val compile = ShireSyntaxAnalyzer(project, file as ShireFile, myFixture.editor).parseAndExecuteLocalCommand()
        val hole = compile.config!!

        val context = PostProcessorContext(
            genText = "User prompt:\n\n",
        )

        runBlocking {
            val shireTemplateCompiler = ShireVariableTemplateCompiler(project, hole, compile.variableTable, code, myFixture.editor)
            val compiledVariables =
                shireTemplateCompiler.compileVariable(myFixture.editor, mutableMapOf())

            context.compiledVariables = compiledVariables

            hole.variables.mapValues {
                PatternActionProcessor(project, hole, mutableMapOf()).execute(it.value)
            }

            hole.setupStreamingEndProcessor(project, context = context)
            hole.executeStreamingEndProcessor(project, null, context = context, compiledVariables)
        }

        assertEquals("User prompt:\n\n" +
                "  \"var2\": /.*ple.shire/ { cat | find(\"fileName\") | sort }\n" +
                "Summary webpage: \$fileName\n" +
                "when: \$fileName.matches(\"/.*.java/\")", context.genText)
    }

    fun testShouldSupportAfterStreamingPattern() {
        @Language("Shire")
        val code = """
            ---
            name: Summary
            description: "Generate Summary"
            interaction: AppendCursor
            variables:
              "var2": "sample"
            afterStreaming: { 
                case condition {
                  default { print(${'$'}output) }
                }
            }
            ---
            
            Summary webpage: ${'$'}fileName
        """.trimIndent()

        val file = myFixture.addFileToProject("sample.shire", code)

        myFixture.openFileInEditor(file.virtualFile)

        val compile = ShireSyntaxAnalyzer(project, file as ShireFile, myFixture.editor).parseAndExecuteLocalCommand()
        val hole = compile.config!!

        val context = PostProcessorContext(
            genText = "User prompt:\n\n",
        )

        runBlocking {
            val shireTemplateCompiler = ShireVariableTemplateCompiler(project, hole, compile.variableTable, code, myFixture.editor)
            val compiledVariables =
                shireTemplateCompiler.compileVariable(myFixture.editor, mutableMapOf())

            context.compiledVariables = compiledVariables

            hole.variables.mapValues {
                PatternActionProcessor(project, hole, mutableMapOf()).execute(it.value)
            }

            hole.setupStreamingEndProcessor(project, context = context)
            hole.executeAfterStreamingProcessor(project, null, context = context)
        }

        assertEquals("User prompt:\n\n", context.lastTaskOutput)
    }

    fun testShouldUseSedReplaceContentInVariables() {
        @Language("Shire")
        val code = """
            ---
            name: Summary
            description: "Generate Summary"
            interaction: AppendCursor
            variables:
              "openai": "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"
              "var2": /.*ple.shire/ { cat | find("openai") | sed("(?i)\b(sk-[a-zA-Z0-9]{20}T3BlbkFJ[a-zA-Z0-9]{20})(?:['|\"|\n|\r|\s|\x60|;]|${'$'})", "sk-***") }
            ---
            
            Summary webpage: ${'$'}var2
        """.trimIndent()

        val file = myFixture.addFileToProject("sample.shire", code)

        myFixture.openFileInEditor(file.virtualFile)

        val compile = ShireSyntaxAnalyzer(project, file as ShireFile, myFixture.editor).parseAndExecuteLocalCommand()
        val hole = compile.config!!

        val context = PostProcessorContext(
            genText = "User prompt:\n\n",
        )

        runBlocking {
            val shireTemplateCompiler = ShireVariableTemplateCompiler(project, hole, compile.variableTable, code, myFixture.editor)
            val compiledVariables =
                shireTemplateCompiler.compileVariable(myFixture.editor, mutableMapOf())

            context.compiledVariables = compiledVariables

            hole.variables.mapValues {
                PatternActionProcessor(project, hole, mutableMapOf()).execute(it.value)
            }

            hole.setupStreamingEndProcessor(project, context = context)
            hole.executeAfterStreamingProcessor(project, null, context = context)
        }

        assertEquals("  \"openai\": \"sk-***\n" +
                "  \"var2\": /.*ple.shire/ { cat | find(\"openai\") | sed(\"(?i)\\b(sk-[a-zA-Z0-9]{20}T3BlbkFJ[a-zA-Z0-9]{20})(?:['|\\\"|\\n|\\r|\\s|\\x60|;]|\$)\", \"sk-***\") }", context.compiledVariables["var2"]
        )
    }
}
