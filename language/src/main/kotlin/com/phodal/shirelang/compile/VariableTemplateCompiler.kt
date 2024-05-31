package com.phodal.shirelang.compile

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import java.io.StringWriter

class VariableTemplateCompiler(
    val language: Language,
    val file: PsiFile,
    val element: PsiElement?,
    val editor: Editor,
    val selectedText: String = "",
) {
    private val log = logger<VariableTemplateCompiler>()
    private val velocityContext = VelocityContext()

    init {
        this.set(CustomVariable.SELECTION.variable, editor.selectionModel.selectedText ?: selectedText)
        this.set(CustomVariable.BEFORE_CURSOR.variable, file.text.substring(0, editor.caretModel.offset))
        this.set(CustomVariable.AFTER_CURSOR.variable, file.text.substring(editor.caretModel.offset))
        this.set(CustomVariable.ALL.variable, file.text)
    }

    fun set(key: String, value: String) {
        velocityContext.put(key, value)
    }

    fun compile(template: String): String {
        velocityContext.put(CustomVariable.FILE_NAME.variable, file.name)
        velocityContext.put(CustomVariable.FILE_PATH.variable, file.virtualFile?.path ?: "")
        velocityContext.put(
            CustomVariable.METHOD_NAME.variable, when (element) {
                is PsiNameIdentifierOwner -> element.nameIdentifier?.text ?: ""
                else -> ""
            }
        )

        configForLanguage()

        val oldContextClassLoader = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(VariableTemplateCompiler::class.java.getClassLoader())

        val sw = StringWriter()
        try {
            Velocity.evaluate(velocityContext, sw, "#" + this.javaClass.name, template)
        } catch (e: Exception) {
            log.error("Failed to compile template: $template", e)
            sw.write(template)
        }

        Thread.currentThread().setContextClassLoader(oldContextClassLoader)
        return sw.toString()
    }

    private fun configForLanguage() {
        velocityContext.put(CustomVariable.LANGUAGE.variable, language.displayName)
        velocityContext.put(
            CustomVariable.COMMENT_SYMBOL.variable, when (language.displayName.lowercase()) {
                "java", "kotlin" -> "//"
                "python" -> "#"
                "javascript" -> "//"
                "typescript" -> "//"
                "go" -> "//"
                "c", "c++", "c#" -> "//"
                "rust" -> "//"
                "ruby" -> "#"
                "shell" -> "#"
                else -> "-"
            }
        )
    }
}