package com.phodal.shirecore.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBUI
import com.phodal.shirecore.utils.markdown.CodeFence
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean

class CodeView(
    project: Project,
    text: String,
) : JBPanel<CodeView>(), DataProvider, Disposable {
    init {
        val disposable = Disposer.newDisposable()
        val editor = createCodeViewerEditor(project, text, disposable)

        val toolbarActionGroup = ActionUtil.getActionGroup("Shire.ToolWindow.Toolbar")
        toolbarActionGroup?.let {
            val toolbar: ActionToolbarImpl =
                object : ActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, toolbarActionGroup, true) {
                    override fun updateUI() {
                        super.updateUI()
                        editor.component.setBorder(JBUI.Borders.empty())
                    }
                }

            toolbar.setBackground(editor.backgroundColor)
            toolbar.setOpaque(true)
            toolbar.targetComponent = editor.contentComponent
            editor.headerComponent = toolbar

            val connect = project.messageBus.connect(disposable)
            val topic: Topic<EditorColorsListener> = EditorColorsManager.TOPIC
            connect.subscribe(topic, EditorColorsListener {
                toolbar.setBackground(editor.backgroundColor)
            })
        }

        editor.scrollPane.setBorder(JBUI.Borders.empty())
        editor.component.setBorder(JBUI.Borders.empty())

        add(editor.component, BorderLayout.CENTER)
    }

    override fun getData(dataId: String): Any? {
        return null
    }

    companion object {
        private fun createCodeViewerEditor(project: Project, text: String, disposable: Disposable): EditorEx {
            val language = CodeFence.findLanguage("markdown")
            val file = LightVirtualFile("", language, text)
            val document: Document =
                file.findDocument() ?: throw IllegalStateException("Document not found")

            return createCodeViewerEditor(project, file, document, disposable)
        }

        private fun createCodeViewerEditor(
            project: Project,
            file: LightVirtualFile,
            document: Document,
            disposable: Disposable,
        ): EditorEx {
            val editor: EditorEx = ReadAction.compute<EditorEx, Throwable> {
                EditorFactory.getInstance()
                    .createViewer(document, project, EditorKind.PREVIEW) as EditorEx
            }

            disposable.whenDisposed(disposable) {
                EditorFactory.getInstance().releaseEditor(editor)
            }

            editor.setFile(file)
            editor.setCaretEnabled(true)
            val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)

            editor.highlighter = highlighter

            val markupModel: MarkupModelEx = editor.markupModel
            (markupModel as EditorMarkupModel).isErrorStripeVisible = false

            val settings = editor.settings.also {
                it.isDndEnabled = false
                it.isLineNumbersShown = false
                it.additionalLinesCount = 0
                it.isLineMarkerAreaShown = false
                it.isFoldingOutlineShown = false
                it.isRightMarginShown = false
                it.isShowIntentionBulb = false
                it.isUseSoftWraps = true
                it.isRefrainFromScrolling = true
                it.isAdditionalPageAtBottom = false
                it.isCaretRowShown = false
            }

            editor.addFocusListener(object : FocusChangeListener {
                override fun focusGained(focusEditor: Editor) {
                    settings.isCaretRowShown = true
                }

                override fun focusLost(focusEditor: Editor) {
                    settings.isCaretRowShown = false
                    editor.markupModel.removeAllHighlighters()
                }
            })

            return editor
        }
    }

    override fun dispose() {
        // do nothing
    }
}

@RequiresReadLock
fun VirtualFile.findDocument(): Document? {
    return ReadAction.compute<Document, Throwable> {
        FileDocumentManager.getInstance().getDocument(this)
    }
}

fun Disposable.whenDisposed(listener: () -> Unit) {
    Disposer.register(this) { listener() }
}

fun Disposable.whenDisposed(
    parentDisposable: Disposable,
    listener: () -> Unit,
) {
    val isDisposed = AtomicBoolean(false)

    val disposable = Disposable {
        if (isDisposed.compareAndSet(false, true)) {
            listener()
        }
    }

    Disposer.register(this, disposable)

    Disposer.register(parentDisposable, Disposable {
        if (isDisposed.compareAndSet(false, true)) {
            Disposer.dispose(disposable)
        }
    })
}