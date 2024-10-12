package com.phodal.shire.openrewrite

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openRewrite.recipe.OpenRewriteType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.phodal.shirecore.provider.shire.FileRunService


class OpenRewriteFileRunService : FileRunService {
    override fun isApplicable(project: Project, file: VirtualFile): Boolean {
        return isOpenWriteFile(project, file)
    }

    private fun isOpenWriteFile(
        project: Project,
        file: VirtualFile,
    ): Boolean {
        try {
            val clazz = Class.forName("com.intellij.openRewrite.OpenRewriteFileService")
            val getInstanceMethod = clazz.getDeclaredMethod("getInstance")
            val isRecipeMethod = clazz.getDeclaredMethod("isRecipe", PsiFile::class.java)

            val fileService = getInstanceMethod.invoke(null)
            val psiFile = runReadAction {
                PsiManager.getInstance(project).findFile(file)
            } ?: return false

            isRecipeMethod.isAccessible = true

            val result = isRecipeMethod.invoke(fileService, psiFile) as Boolean
            return result
        } catch (e: Exception) {
            return false
        }
    }

    override fun runConfigurationClass(project: Project): Class<out RunProfile>? = null

    override fun runFile(project: Project, virtualFile: VirtualFile, psiElement: PsiElement?): String {
        if (!isOpenWriteFile(project, virtualFile)) {
            return ""
        }

        val runManager = RunManager.getInstance(project)
        val allSettings = runManager.allSettings

        val workingPath = virtualFile.parent.path

        var settings = allSettings.firstOrNull { it ->
            val config = it.configuration
            val configClass = config::class.java

            if (configClass.name == "com.intellij.openRewrite.run.OpenRewriteRunConfiguration") {
                val getExpandedWorkingDirectoryMethod = configClass.getMethod("getExpandedWorkingDirectory")
                val expandedWorkingDirectory = getExpandedWorkingDirectoryMethod.invoke(config) as? String
                expandedWorkingDirectory == workingPath
            } else {
                false
            }
        }

        // use relfect to get .com.intellij.openRewrite.recipeOpenRewriteRecipeService access getInstance(project)
        val clazz = Class.forName("com.intellij.openRewrite.recipe.OpenRewriteRecipeService")
        val getInstanceMethod = clazz.getDeclaredMethod("getInstance", Project::class.java)
        val openRewriteRecipeService = getInstanceMethod.invoke(null, project)
        // call getLocalDescriptors in openRewriteRecipeService getLocalDescriptors(psiFile, OpenRewriteType.RECIPE)
        val getLocalDescriptorsMethod = clazz.getDeclaredMethod("getLocalDescriptors", PsiFile::class.java, OpenRewriteType::class.java)
        getLocalDescriptorsMethod.isAccessible = true

        val psiFile = runReadAction {
            PsiManager.getInstance(project).findFile(virtualFile)
        } ?: return ""

        val list = runReadAction {
            getLocalDescriptorsMethod.invoke(openRewriteRecipeService, psiFile, OpenRewriteType.RECIPE) as LinkedHashMap<*, *>
        }
        if (list.isEmpty()) {
            return ""
        }

        if (settings == null) {
            // ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
            val configurationType = ConfigurationTypeUtil.findConfigurationType("OpenRewriteRunConfigurationType")!!
            settings = runManager.createConfiguration("", configurationType.configurationFactories[0])
            val configuration = settings.configuration
            if (configuration.javaClass.name == "com.intellij.openRewrite.run.OpenRewriteRunConfiguration") {
                val directoryMethod =
                    configuration::class.java.getMethod("setWorkingDirectory", String::class.java)
                val projectRoot = project.basePath ?: ""
                directoryMethod.invoke(configuration, projectRoot)

                val setConfigLocationMethod =
                    configuration::class.java.getMethod("setConfigLocation", String::class.java)
                setConfigLocationMethod.invoke(configuration, virtualFile.path)

                // setActiveRecipes(string) from lists.first
                val setActiveRecipesMethod =
                    configuration::class.java.getMethod("setActiveRecipes", String::class.java)
                setActiveRecipesMethod.invoke(configuration, list.keys.first())

                val nameMethod = configuration::class.java.getMethod("setGeneratedName")
                nameMethod.invoke(configuration)
            }
            runManager.setUniqueNameIfNeeded(settings)
            runManager.setTemporaryConfiguration(settings)
        }

        val builder = ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings)
        builder?.let {
            ExecutionManager.getInstance(project).restartRunProfile(it.build())
        }

        return ""
    }
}