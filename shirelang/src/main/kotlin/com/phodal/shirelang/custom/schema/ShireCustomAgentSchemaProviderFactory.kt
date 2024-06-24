package com.phodal.shirelang.custom.schema

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

class ShireCustomAgentSchemaProviderFactory : JsonSchemaProviderFactory  {
    override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
        return mutableListOf(CustomAgentSchemaFileProvider(project))
    }
}
