package com.phodal.shirelang.compiler.variable.resolver

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiManager
import com.phodal.shirecore.provider.variable.PsiContextVariableProvider
import com.phodal.shirecore.provider.variable.impl.DefaultPsiContextVariableProvider
import com.phodal.shirecore.provider.variable.model.PsiContextVariable
import com.phodal.shirelang.compiler.variable.resolver.base.VariableResolver
import com.phodal.shirelang.compiler.variable.resolver.base.VariableResolverContext

/**
 * Include ToolchainVariableProvider and PsiContextVariableProvider
 */
class PsiContextVariableResolver(private val context: VariableResolverContext) : VariableResolver {
    private val variableProvider: PsiContextVariableProvider

    init {
        val psiFile = runReadAction {
            PsiManager.getInstance(context.myProject).findFile(context.editor.virtualFile ?: return@runReadAction null)
        }

        variableProvider = if (psiFile?.language != null) {
            PsiContextVariableProvider.provide(psiFile.language)
        } else {
            DefaultPsiContextVariableProvider()
        }
    }

    override suspend fun resolve(initVariables: Map<String, Any>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        context.variableTable.getAllVariables().forEach {
            val psiContextVariable = PsiContextVariable.from(it.key)
            if (psiContextVariable != null) {
                result[it.key] = try {
                    runReadAction {
                        variableProvider.resolve(psiContextVariable, context.myProject, context.editor, context.element)
                    }
                } catch (e: Exception) {
                    logger<CompositeVariableResolver>().error("Failed to resolve variable: ${it.key}", e)
                    ""
                }

                return@forEach
            }
        }

        return result
    }
}