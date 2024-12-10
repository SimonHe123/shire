package com.phodal.shire.plantuml

import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.phodal.shirecore.provider.sketch.ExtensionLangSketch
import com.phodal.shirecore.provider.sketch.LanguageSketchProvider
import org.plantuml.idea.preview.PlantUmlPreviewPanel
import org.plantuml.idea.preview.editor.PlantUmlPreviewEditor
import org.plantuml.idea.preview.editor.PlantUmlSplitEditor
import org.plantuml.idea.preview.editor.SplitFileEditor
import org.plantuml.idea.rendering.LazyApplicationPoolExecutor
import org.plantuml.idea.rendering.RenderCommand
import org.plantuml.idea.settings.PlantUmlSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PlantUmlSketchProvider : LanguageSketchProvider {
    override fun isSupported(lang: String): Boolean {
        return true
    }

    override fun createSketch(project: Project, content: String): ExtensionLangSketch {
        val virtualFile = LightVirtualFile("plantuml.puml", content)
        return PlantUmlSketch(project, virtualFile)
    }
}

class PlantUmlSketch(private val project: Project, private val virtualFile: VirtualFile) : ExtensionLangSketch {
    private var mainPanel: JPanel = JPanel()
    private var umlPreviewEditor: PlantUmlPreviewEditor

    init {
        val editor = TextEditorProvider.getInstance().createEditor(project, virtualFile) as TextEditor
        umlPreviewEditor = PlantUmlPreviewEditor(virtualFile, project)
        umlPreviewEditor.editor = editor.editor
        val splitEditor = PlantUmlSplitEditor(editor, umlPreviewEditor)

        splitEditor.component.preferredSize = null

        mainPanel.add(splitEditor.component, BorderLayout.CENTER)

        PlantUmlSettings.getInstance().previewSettings.splitEditorLayout = SplitFileEditor.SplitEditorLayout.SECOND
    }

    override fun doneUpdateText(text: String) {
        (umlPreviewEditor.component as PlantUmlPreviewPanel).processRequest(LazyApplicationPoolExecutor.Delay.NOW, RenderCommand.Reason.FILE_SWITCHED)
    }

    override fun getExtensionName(): String {
        return "plantuml"
    }

    override fun getViewText(): String {
        return virtualFile.inputStream.bufferedReader().use { it.readText() }
    }

    override fun updateViewText(text: String) {
        virtualFile.setBinaryContent(text.toByteArray())
    }

    override fun getComponent(): JComponent {
        return mainPanel
    }

    override fun updateLanguage(language: Language?) {
    }

    override fun dispose() {
    }
}
