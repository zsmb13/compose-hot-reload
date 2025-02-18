@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.analysis

import org.jetbrains.compose.reload.core.withClosure
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode


private const val functionKeyMetaConstructorDescriptor = "Landroidx/compose/runtime/internal/FunctionKeyMeta;"


data class RuntimeInfo(val scopes: List<RuntimeScopeInfo>) {
    /**
     * Index from [MethodId] to the 'root scopes' belonging to this method id
     */
    val methods: Map<MethodId, List<RuntimeScopeInfo>> = scopes.groupBy { info -> info.methodId }

    /**
     * Index from [ComposeGroupKey] to *all* scopes that are associated with this group.
     * (null as key means 'no known group')
     */
    val groups: Map<ComposeGroupKey?, List<RuntimeScopeInfo>> =
        scopes.withClosure<RuntimeScopeInfo> { info -> info.children }
            .groupBy { info -> info.group }
}

data class MethodId(
    val classId: ClassId,
    val methodName: String,
    val methodDescriptor: String,
) {
    override fun toString(): String {
        return "$classId.$methodName $methodDescriptor"
    }
}

@JvmInline
value class ClassId(val value: String) : Comparable<ClassId> {
    override fun compareTo(other: ClassId): Int {
        return value.compareTo(other.value)
    }

    override fun toString(): String {
        return value
    }
}

data class RuntimeScopeInfo(
    val methodId: MethodId,
    val type: RuntimeScopeType,
    val group: ComposeGroupKey?,
    val parentGroup: ComposeGroupKey?,
    val hash: RuntimeScopeHash,
    val children: List<RuntimeScopeInfo>,
    val dependencies: List<MethodId>
)

enum class RuntimeScopeType {
    Method, RestartGroup, ReplaceGroup, SourceInformationMarker,
}

fun RuntimeInfo(bytecode: ByteArray): RuntimeInfo? {
    val reader = ClassReader(bytecode)
    val node = ClassNode(ASM9)
    reader.accept(node, 0)

    return RuntimeInfo(node)
}

internal fun RuntimeInfo(classNode: ClassNode): RuntimeInfo? {
    if (isIgnoredClassId(classNode.name)) return null
    return RuntimeInfo(classNode.methods.mapNotNull { methodNode -> RuntimeScopeInfo(classNode, methodNode) })
}

internal fun RuntimeScopeInfo(classNode: ClassNode, methodNode: MethodNode): RuntimeScopeInfo? {
    val context = RuntimeMethodAnalysisContext(classNode, methodNode)

    val functionKey = methodNode.visibleAnnotations.orEmpty().find { annotationNode ->
        annotationNode.desc == functionKeyMetaConstructorDescriptor
    }?.values?.zipWithNext()?.find { (name, _) -> name == "key" }?.second as? Int

    if (functionKey != null) {
        context.pushFunctionKeyAnnotation(ComposeGroupKey(functionKey))
    }

    methodNode.instructions.forEach { instructionNode ->
        RuntimeInstructionAnalyzer.analyze(context, instructionNode)
    }

    return RuntimeScopeInfo(context.end() ?: return null)
}

/**
 * Creates a new [RuntimeInfo] by updating [this] RuntimeInfo with 'new' scopes from [other]
 */
operator fun RuntimeInfo?.plus(other: RuntimeInfo?): RuntimeInfo {
    if (this == null) return other ?: RuntimeInfo(emptyList())
    if (other == null) return this
    val newMethods = this.methods + other.methods
    val newScopes = newMethods.values.flatten()
    return RuntimeInfo(newScopes)
}