package com.phodal.shirelang.debugger

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import com.phodal.shirelang.debugger.snapshot.VariableSnapshotRecorder
import org.jetbrains.concurrency.Promise

class ShireStackFrame(
    val process: ShireDebugProcess,
    val project: Project,
) : XStackFrame(), Disposable {
    private val myPosition: XSourcePosition? = null

    override fun customizePresentation(component: ColoredTextContainer) {
        VariableSnapshotRecorder.getInstance(project).all().forEach {
            component.append(it.variableName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            component.append(" = ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            component.append(it.value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            component.append("\n", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
//        VariableSnapshotRecorder.getInstance(project).addListener(object : VariableSnapshotListener {
//            override fun onSnapshot(variableName: String, value: String, operations: List<VariableOperation>) {
//                component.append(variableName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
//                component.append(" = ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
//                component.append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES)
//                component.append("\n", SimpleTextAttributes.REGULAR_ATTRIBUTES)
//            }
//        })
    }

    override fun computeChildren(node: XCompositeNode) {
        super.computeChildren(node)
    }

    override fun getEvaluator(): XDebuggerEvaluator? {
        return ShireDebugEvaluator()
    }

    override fun dispose() {

    }
}

class ShireDebugEvaluator : XDebuggerEvaluator() {
    override fun evaluate(expr: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val expression = expr.trim()
            if (expression.isEmpty()) {
                callback.evaluated(getNone())
                return@executeOnPooledThread
            }


            val value = ShireDebugValue(expression, "String", expression)
            callback.evaluated(value)
        }
    }

    private fun getNone(): XValue = ShireDebugValue("", "None", "")
}

class ShireDebugValue(
    private val myName: String,
    val type: String = "String",
    val value: String,
) : XNamedValue(myName) {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        node.setPresentation(AllIcons.Debugger.Value, myName, myName, true)
    }

    override fun calculateEvaluationExpression(): Promise<XExpression> {
        return super.calculateEvaluationExpression()
    }
}

class ShireSuspendContext(val process: ShireDebugProcess, project: Project) : XSuspendContext() {
    private val executionStacks: Array<XExecutionStack> = arrayOf(
        ExecutionStack(process, project)
    )

    override fun getActiveExecutionStack(): XExecutionStack? = executionStacks.firstOrNull()
    override fun getExecutionStacks(): Array<XExecutionStack> = executionStacks
}

class ExecutionStack(private val process: ShireDebugProcess, project: Project) :
    XExecutionStack("Custom variables") {
    private val stackFrames: List<ShireStackFrame> = listOf(
        ShireStackFrame(process, project)
    )

    override fun getTopFrame(): XStackFrame? = stackFrames.firstOrNull()

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
        container.addStackFrames(stackFrames, true)
    }
}
