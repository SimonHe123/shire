package com.phodal.shirelang.provider

import com.intellij.openapi.project.Project
import com.phodal.shirecore.config.ShireActionLocation
import com.phodal.shirecore.provider.ide.ShirePromptBuilder
import com.phodal.shirecore.provider.streaming.OnStreamingService
import com.phodal.shirelang.actions.base.DynamicShireActionService
import com.phodal.shirelang.run.runner.ShireRunner
import kotlinx.coroutines.runBlocking

class ShireActionPromptBuilder : ShirePromptBuilder {
    override fun build(project: Project, actionLocation: String, originPrompt: String): String {
        val location: ShireActionLocation = ShireActionLocation.valueOf(actionLocation)

        val action = DynamicShireActionService.getInstance(project).getActions(location)
            .firstOrNull() ?: return originPrompt

        val initVariables = mapOf("chatPrompt" to originPrompt)
        val finalPrompt = runBlocking {
            val runnerContext = ShireRunner.compileOnly(project, action.shireFile, initVariables, null)

            val service = project.getService(OnStreamingService::class.java)
            service?.onStart(project, runnerContext.finalPrompt)

            runnerContext
        }.finalPrompt

        return finalPrompt
    }
}
