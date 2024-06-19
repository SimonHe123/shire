package com.phodal.shirelang.compiler.variable

import com.intellij.psi.PsiNameIdentifierOwner
import com.phodal.shirelang.completion.dataprovider.ContextVariable
import com.phodal.shirelang.completion.dataprovider.ContextVariable.*

class ContextVariableResolver(
    private val context: VariableResolverContext
): VariableResolver {
    fun all(): List<ContextVariable> = values().toList()

    override fun resolve(): Map<String, String> {
        val file = context.element?.containingFile

        val caretModel = context.editor.caretModel

        return all().associate {
            it.variable to when (it) {
                SELECTION -> context.editor.selectionModel.selectedText ?: ""
                BEFORE_CURSOR -> file?.text?.substring(0, caretModel.offset) ?: ""
                AFTER_CURSOR -> file?.text?.substring(caretModel.offset) ?: ""
                FILE_NAME -> file?.name ?: ""
                FILE_PATH -> file?.virtualFile?.path ?: ""
                METHOD_NAME -> when (context.element) {
                    is PsiNameIdentifierOwner -> (context.element as PsiNameIdentifierOwner).nameIdentifier?.text ?: ""
                    else -> ""
                }

                LANGUAGE -> context.element?.language?.displayName ?: ""
                COMMENT_SYMBOL -> when (context.element?.language?.displayName?.lowercase()) {
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

                ALL -> file?.text ?: ""
            }
        }
    }
}