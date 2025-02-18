package org.jetbrains.compose.reload.analysis

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

private const val lambdaMetaFactoryClassId = "java/lang/invoke/LambdaMetafactory"
private const val metafactoryMethodName = "metafactory"

internal object RuntimeScopeMethodDependencyAnalyzer : RuntimeInstructionAnalyzer {
    override fun analyze(
        context: RuntimeMethodAnalysisContext, instructionNode: AbstractInsnNode
    ) {
        val scope = context.scope ?: return

        if (instructionNode is MethodInsnNode && instructionNode.opcode == Opcodes.INVOKESTATIC) {
            if (isIgnoredClassId(instructionNode.owner)) return
            scope.attachDependency(MethodId(instructionNode))
        }

        if (instructionNode is InvokeDynamicInsnNode &&
            instructionNode.bsm.owner == lambdaMetaFactoryClassId &&
            instructionNode.bsm.name == metafactoryMethodName
        ) {
            val handle = instructionNode.bsmArgs.getOrNull(1) as? Handle ?: return
            scope.attachDependency(MethodId(handle))
        }
    }
}
