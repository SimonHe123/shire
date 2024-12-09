package com.phodal.shirecore.ui.viewer

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDefaultExecutor
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.JBUI
import com.phodal.shirecore.ShireCoreBundle
import com.phodal.shirecore.ShirelangNotifications
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*


class DiffPatchViewer(
    private val myProject: Project,
    private val filepath: VirtualFile,
    private val patchContent: String,
) : SketchViewer {
    private val mainPanel: JPanel = JPanel(VerticalLayout(5))
    private val myHeaderPanel: JPanel = JPanel(BorderLayout())
    private val shelfExecutor = ApplyPatchDefaultExecutor(myProject)
    private val myReader = PatchReader(patchContent).also {
        try {
            it.parseAllPatches()
        } catch (e: Exception) {
            ShirelangNotifications.error(myProject, "Failed to parse patch: ${e.message}")
        }
    }


    init {
        myHeaderPanel.add(createHeaderAction(), BorderLayout.EAST)

        val contentPanel = JPanel(BorderLayout())
        val fileIcon = JLabel(filepath.fileType.icon)

        val filepathComponent = JBLabel(filepath.name).apply {
            foreground = JBColor(0x888888, 0x888888)
            background = JBColor(0xF5F5F5, 0x333333)

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    foreground = JBColor(0x0000FF, 0x0000FF)
                }

                override fun mouseClicked(e: MouseEvent?) {
                    FileEditorManager.getInstance(myProject).openFile(filepath, true)
                }

                override fun mouseExited(e: MouseEvent) {
                    foreground = JBColor(0x888888, 0x888888)
                }
            })
        }

        val actions = JLabel(AllIcons.Actions.Rollback)

        val filePanel = panel {
            row {
                cell(fileIcon).align(AlignX.LEFT)
                cell(filepathComponent).align(AlignX.LEFT)
                cell(actions).align(AlignX.RIGHT)
            }
        }.also {
            it.background = JBColor(0xF5F5F5, 0x333333)
            it.border = JBUI.Borders.empty(10)
        }

        contentPanel.add(filePanel, BorderLayout.CENTER)

        mainPanel.add(myHeaderPanel)
        mainPanel.add(contentPanel)
    }

    private fun createHeaderAction(): JComponent {
        val acceptButton = JButton(ShireCoreBundle.message("sketch.patch.action.accept")).apply {
            icon = AllIcons.Actions.SetDefault
            toolTipText = ShireCoreBundle.message("sketch.patch.action.accept.tooltip")
            addActionListener {
                handleAcceptAction()
            }
        }

        val rejectButton = JButton(ShireCoreBundle.message("sketch.patch.action.reject")).apply {
            icon = AllIcons.Actions.Rollback
            toolTipText = ShireCoreBundle.message("sketch.patch.action.reject.tooltip")
            addActionListener {
                handleRejectAction()
            }
        }

        val viewDiffButton = JButton(ShireCoreBundle.message("sketch.patch.action.viewDiff")).apply {
            toolTipText = ShireCoreBundle.message("sketch.patch.action.viewDiff.tooltip")
            icon = AllIcons.Actions.ListChanges
            addActionListener {
                handleViewDiffAction()
            }
        }

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.add(acceptButton)
        panel.add(rejectButton)
        panel.add(viewDiffButton)

        panel.background = JBColor(0xF5F5F5, 0x333333)

        return panel
    }

    private fun handleAcceptAction() {
        val filePatches: MutableList<FilePatch> = myReader.allPatches
        ApplicationManager.getApplication().invokeAndWait {
            val patchGroups = MultiMap<VirtualFile, AbstractFilePatchInProgress<*>>()
            MatchPatchPaths(myProject).execute(filePatches, true).forEach { patchInProgress ->
                patchGroups.putValue(patchInProgress.base, patchInProgress)
            }

            if (filePatches.isEmpty()) {
                ShirelangNotifications.error(myProject, "PatchProcessor: no patches found")
                return@invokeAndWait
            }

            val additionalInfo = myReader.getAdditionalInfo(ApplyPatchDefaultExecutor.pathsFromGroups(patchGroups))
            shelfExecutor.apply(filePatches, patchGroups, null, filepath.name, additionalInfo)
        }
    }

    private fun handleRejectAction() {
        println("Reject action triggered")
    }

    private fun handleViewDiffAction() {
        val content: String = filepath.contentsToByteArray().toString(Charsets.UTF_8)
        val contentFactory = DiffContentFactory.getInstance()

        val oldContent: DocumentContent = contentFactory.create(content, filepath)
        val newContent: DocumentContent = contentFactory.create(patchContent, filepath)

        val request = SimpleDiffRequest(null, oldContent, newContent, "Before", "After")

        val diffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, null)
        diffPanel.putContextHints(DiffUserDataKeys.PLACE, "ExtractSignature")
        diffPanel.setRequest(request)

        val panel = JPanel(BorderLayout())
        panel.add(diffPanel.component, BorderLayout.CENTER)
        panel.border = IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(5))

        class SampleDialogWrapper : DialogWrapper(true) {
            init {
                title = "Shire - Diff Viewer"
                init()
            }

            override fun createCenterPanel(): JComponent = panel
        }

        SampleDialogWrapper().show()
    }

    override fun getComponent(): JPanel {
        return mainPanel
    }

    override fun getViewText(): String {
        return ""
    }

    override fun updateViewText(text: String) {
        return
    }

    override fun dispose() {
        mainPanel.removeAll()
    }
}
