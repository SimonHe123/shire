package com.phodal.shirelang.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.phodal.shirecore.sketch.highlight.CodeHighlightSketch
import com.phodal.shirelang.ShireFileType
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.run.runner.ShireRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel


@Service(Service.Level.PROJECT)
internal class ShireEditorScope(private val coroutineScope: CoroutineScope) {
    companion object {
        fun createChildScope(project: Project): CoroutineScope {
            return scope(project).childScope()
        }

        fun scope(project: Project): CoroutineScope {
            return project.service<ShireEditorScope>().coroutineScope
        }
    }
}


class ShireFileEditorProvider : WeighedFileEditorProvider() {
    override fun getEditorTypeId() = "shire-editor"
    private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
    private val previewProvider: FileEditorProvider = ShirePreviewEditorProvider()

    override fun accept(project: Project, file: VirtualFile) =
        FileTypeRegistry.getInstance().isFileOfType(file, ShireFileType.INSTANCE)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val editor = TextEditorProvider.getInstance().createEditor(project, file)
        if (editor.file is LightVirtualFile) {
            return editor
        }

        val mainEditor = mainProvider.createEditor(project, file) as TextEditor
        val preview = previewProvider.createEditor(project, file) as ShirePreviewEditor
        return ShireFileEditor(mainEditor, preview, project)
    }

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class ShirePreviewEditorProvider : WeighedFileEditorProvider(), AsyncFileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return FileTypeRegistry.getInstance().isFileOfType(file, ShireFileType.INSTANCE)
    }

    override fun createEditor(project: Project, virtualFile: VirtualFile): FileEditor {
        return ShirePreviewEditor(project, virtualFile)
    }

    override fun getEditorTypeId(): String = "markdown-preview-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

/**
 * Display shire file render prompt and have a sample file as view
 */
open class ShirePreviewEditor(
    val project: Project,
    val virtualFile: VirtualFile,
) : UserDataHolder by UserDataHolderBase(), FileEditor {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
    private var mainEditor = MutableStateFlow<Editor?>(null)
    private val visualPanel: JPanel = JPanel(BorderLayout()).apply {
        /// show preview of the shire file
    }

    private var highlightSketch: CodeHighlightSketch? = null

    init {
        val corePanel = panel {
            row {
                highlightSketch = CodeHighlightSketch(project, "", null).apply {
                    initEditor(virtualFile.readText())
                }

                updateOutput()
                cell(highlightSketch!!).align(Align.FILL)
            }
        }

        visualPanel.add(corePanel)

        mainEditor.value?.document?.addDocumentListener(ReparseContentDocumentListener())
    }


    private inner class ReparseContentDocumentListener : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            runInEdt {
                updateOutput()
            }
        }
    }

    fun updateOutput() {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? ShireFile ?: return
        val finalPrompt = runBlocking {
            ShireRunner.compileFileContext(project, psiFile, mapOf())
        }.finalPrompt

        highlightSketch?.updateViewText(finalPrompt)
        highlightSketch?.repaint()
    }


    fun setMainEditor(editor: Editor) {
        check(mainEditor.value == null)
        mainEditor.value = editor
    }

    override fun getComponent(): JComponent = visualPanel
    override fun getName(): String = "Shire Prompt Preview"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = virtualFile
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
}

class ShireFileEditor(
    private val ourEditor: TextEditor,
    private val preview: ShirePreviewEditor,
    private val project: Project,
) : TextEditorWithPreview(ourEditor, preview) {
    val virtualFile: VirtualFile = ourEditor.file

    init {
        editor.contentComponent.putClientProperty(ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, true)
        preview.setMainEditor(editor)
    }

    override fun dispose() {
        TextEditorProvider.getInstance().disposeEditor(ourEditor)
    }

    override fun createToolbar(): ActionToolbar {
        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, createActionGroup(project, virtualFile, editor), true)
            .also {
                it.targetComponent = editor.contentComponent
            }
    }

    private fun createActionGroup(project: Project, virtualFile: VirtualFile, editor: Editor): ActionGroup {
        return DefaultActionGroup(
            createHelpAction(project),
            Separator()
        )
    }

    private fun createHelpAction(project: Project): AnAction {
        val idleIcon = AllIcons.Actions.Help
        return object : AnAction("Help", "Help", idleIcon) {
            override fun actionPerformed(e: AnActionEvent) {
                val url = "https://shire.phodal.com/"
                com.intellij.ide.BrowserUtil.browse(url)
            }
        }
    }
}
