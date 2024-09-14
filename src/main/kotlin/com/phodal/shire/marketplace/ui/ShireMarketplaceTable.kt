package com.phodal.shire.marketplace.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.phodal.shire.ShireIdeaIcons
import com.phodal.shire.ShireMainBundle
import com.phodal.shire.marketplace.model.ShirePackage
import com.phodal.shire.marketplace.util.ShireDownloader
import com.phodal.shirecore.ShirelangNotifications
import java.awt.Component
import java.io.File
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class ShireMarketplaceTable(val project: Project) {
    val columns = arrayOf(
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.name")) {
            override fun valueOf(data: ShirePackage): String = data.name
        },
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.description")) {
            override fun valueOf(data: ShirePackage): String = data.description
        },
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.version")) {
            override fun valueOf(data: ShirePackage): String = data.version
        },
        object : ColumnInfo<ShirePackage, String>(ShireMainBundle.message("marketplace.column.author")) {
            override fun valueOf(data: ShirePackage): String = data.author
        },
        object : ColumnInfo<ShirePackage, ShirePackage>(ShireMainBundle.message("marketplace.column.action")) {
            override fun valueOf(item: ShirePackage?): ShirePackage? = item
            override fun isCellEditable(item: ShirePackage?): Boolean = true

            override fun getEditor(item: ShirePackage): TableCellEditor {
                return object : IconButtonTableCellEditor(item, ShireIdeaIcons.Download, "Download") {
                    init {
                        myButton.addActionListener {
                            ShirelangNotifications.info(project, "Downloading ${item.name}")
                            ShireDownloader(project, item).downloadAndUnzip()
                            ShirelangNotifications.info(project, "Success Downloaded ${item.name}")

                            // refresh .shire dir
                            val shireDir = File(project.basePath, ".shire")
                            LocalFileSystem.getInstance().refreshAndFindFileByPath(shireDir.path)
                            fireEditingStopped()
                        }
                    }
                }
            }

            override fun getRenderer(item: ShirePackage?): TableCellRenderer {
                return object : IconButtonTableCellRenderer(ShireIdeaIcons.Download, "Download") {
                    override fun getTableCellRendererComponent(
                        table: JTable,
                        value: Any,
                        selected: Boolean,
                        focused: Boolean,
                        viewRowIndex: Int,
                        viewColumnIndex: Int,
                    ): Component {
                        myButton.isEnabled = true

                        return super.getTableCellRendererComponent(
                            table,
                            value,
                            selected,
                            focused,
                            viewRowIndex,
                            viewColumnIndex
                        )
                    }
                }
            }
        }
    )
    var mainPanel: JPanel

    // Create a list to store the row data
    val dataList = listOf(
        ShirePackage(
            "基础 AI 辅助编程",
            "基础 AI 编码能力包：自动化单测、提交信息生成、代码重构、AI 终端命令生成、Java 注释生成。",
            "TODO",
            "Phodal Huang",
            "https://static.shire.run/package/basic-assistant.zip"
        ),
    )

    init {
        val model = ListTableModel(columns, dataList)
        val tableView = TableView(model)
        val scrollPane = JBScrollPane(tableView)

        val myReloadButton = JButton(AllIcons.Actions.Refresh)

        mainPanel = panel {
            /// add header
            row {
                cell(myReloadButton.apply {
                    addActionListener {
                        fetchData()
                    }
                })
            }

            row {
                cell(scrollPane).align(Align.FILL)
            }
        }
    }

    fun fetchData() {
        ShirelangNotifications.info(project, "Fetching data")
    }
}