package com.phodal.shirecore.ui.input

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.phodal.shirecore.ShireCoreBundle
import com.phodal.shirecore.provider.psi.RelatedClassesProvider
import com.phodal.shirecore.provider.shire.FileCreateService
import com.phodal.shirecore.provider.shire.FileRunService
import com.phodal.shirecore.relativePath
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

data class modelWrapper(val psiElement: PsiElement, var panel: JPanel? = null, var namePanel: JPanel? = null)

class ShireChatBoxInput(val project: Project) : JPanel(BorderLayout()), Disposable {
    private var scratchFile: VirtualFile? = null
    private val listModel = DefaultListModel<modelWrapper>()
    private val elementsList = JBList(listModel)
    private var inputSection: ShireInputSection

    init {
        setupElementsList()
        inputSection = ShireInputSection(project, this)
        inputSection.addListener(object : ShireInputListener {
            override fun onStop(component: ShireInputSection) {
                inputSection.showSendButton()
            }

            override fun onSubmit(component: ShireInputSection, trigger: ShireInputTrigger) {
                val prompt = component.text
                component.text = ""

                if (prompt.isEmpty() || prompt.isBlank()) {
                    component.showTooltip(ShireCoreBundle.message("chat.input.empty.tips"))
                    return
                }

                val virtualFile = createShireFile(prompt)
                this@ShireChatBoxInput.scratchFile = virtualFile

                FileRunService.provider(project, virtualFile!!)
                    ?.runFile(project, virtualFile, null)

                listModel.clear()
                elementsList.clearSelection()
            }
        })

        this.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        this.add(inputSection, BorderLayout.CENTER)
        this.add(elementsList, BorderLayout.NORTH)

        setupEditorListener()
        setupRelatedListener()
    }

    private fun setupEditorListener() {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile ?: return
                    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
                    RelatedClassesProvider.provide(psiFile.language) ?: return
                    ApplicationManager.getApplication().invokeLater {
                        listModel.addIfAbsent(psiFile)
                    }
                }
            }
        )
    }

    private fun setupRelatedListener() {
        project.messageBus.connect(this)
            .subscribe(LookupManagerListener.TOPIC, ShireInputLookupManagerListener(project) {
                ApplicationManager.getApplication().invokeLater {
                    val relatedElements = RelatedClassesProvider.provide(it.language)?.lookup(it)
                    updateElements(relatedElements)
                }
            })
    }

    private fun setupElementsList() {
        elementsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        elementsList.layoutOrientation = JList.HORIZONTAL_WRAP
        elementsList.visibleRowCount = 2
        elementsList.cellRenderer = ElementListCellRenderer()
        elementsList.setEmptyText("")
        
        val scrollPane = JBScrollPane(elementsList)
        scrollPane.preferredSize = Dimension(-1, 80)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

        elementsList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val list = e.source as JBList<*>
                val index = list.locationToIndex(e.point)
                if (index != -1) {
                    val wrapper = listModel.getElementAt(index)
                    val cellBounds = list.getCellBounds(index, index)
                    wrapper.panel?.components?.firstOrNull { it.contains(e.x - cellBounds.x - it.x, it.height - 1) }?.let {
                        when {
                            it is JPanel -> {
                                listModel.removeElement(wrapper)
                                wrapper.psiElement.containingFile?.let { psiFile ->
                                    val relativePath = psiFile.virtualFile.relativePath(project)
                                    inputSection.appendText("\n/" + "file" + ":${relativePath}")
                                    listModel.indexOf(wrapper.psiElement).takeIf { it != -1 }?.let { listModel.remove(it) }
                                    val relatedElements = RelatedClassesProvider.provide(psiFile.language)?.lookup(psiFile)
                                    updateElements(relatedElements)
                                }
                            }
                            it is JLabel && it.icon == AllIcons.Actions.Close -> listModel.removeElement(wrapper)
                            else -> list.clearSelection()
                        }
                    } ?: list.clearSelection()
                }
            }
        })

        add(scrollPane, BorderLayout.NORTH)
    }

    private fun updateElements(elements: List<PsiElement>?) {
        elements?.forEach { listModel.addIfAbsent(it) }
    }

    private fun createShireFile(prompt: String): VirtualFile? {
        val findLanguageByID = Language.findLanguageByID("Shire")
            ?: throw IllegalStateException("Shire language not found")
        val provide = FileCreateService.provide(findLanguageByID)
            ?: throw IllegalStateException("FileCreateService not found")

        return provide.createFile(prompt, project)
    }

    override fun dispose() {
        scratchFile?.delete(this)
    }
}

private fun DefaultListModel<modelWrapper>.addIfAbsent(psiFile: PsiElement) {
    val isValid = when (psiFile) {
        is PsiFile -> psiFile.isValid
        else -> true
    }
    if (!isValid) return

    if (elements().asIterator().asSequence().none { it.psiElement == psiFile }) {
        addElement(modelWrapper(psiFile))
    }
}

private class ElementListCellRenderer : ListCellRenderer<modelWrapper> {
    override fun getListCellRendererComponent(
        list: JList<out modelWrapper>,
        value: modelWrapper,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val psiElement = value.psiElement
        val panel = value.panel ?: JPanel(FlowLayout(FlowLayout.LEFT, 3, 0)).apply {
            accessibleContext.accessibleName = "Element Panel"

            border = JBUI.Borders.empty(2, 5)

            val namePanel = JPanel(layout)
            val iconLabel = JLabel(psiElement.containingFile?.fileType?.icon ?: AllIcons.FileTypes.Unknown)
            namePanel.add(iconLabel)

            val nameLabel = JLabel(psiElement.containingFile?.name ?: "Unknown")
            namePanel.add(nameLabel)

            add(namePanel)
            val closeLabel = JLabel(AllIcons.Actions.Close)
            closeLabel.border = JBUI.Borders.empty()
            add(closeLabel, BorderLayout.EAST)

            value.panel = this
            value.namePanel = namePanel
        }
        val namePanel = value.namePanel
        if (isSelected) {
            namePanel?.background = list.selectionBackground
            namePanel?.foreground = list.selectionForeground
        } else {
            namePanel?.background = list.background
            namePanel?.foreground = list.foreground
        }

        return panel
    }
}