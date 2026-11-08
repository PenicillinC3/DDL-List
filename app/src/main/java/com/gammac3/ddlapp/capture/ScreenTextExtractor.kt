package com.gammac3.ddlapp.capture

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 把当前活动窗口的无障碍节点树压成多行文本。
 *
 * 约束：只在用户点悬浮球时调用；跳过本应用、不可见节点、密码框；
 * 有节点数/深度/字符上限；不截图、不读剪贴板、不写任何持久化存储。
 */
object ScreenTextExtractor {

    private const val MAX_NODES = 2_000
    private const val MAX_DEPTH = 40
    private const val MAX_CHARS = 12_000

    fun extract(root: AccessibilityNodeInfo?, ownPackage: String): String {
        if (root == null) return ""

        val lines = mutableListOf<String>()
        var visited = 0
        var chars = 0
        var lastLine: String? = null

        fun append(raw: CharSequence?) {
            if (raw.isNullOrBlank()) return
            raw.toString().lineSequence().forEach { piece ->
                if (chars >= MAX_CHARS) return
                val line = piece.trim()
                if (line.isEmpty() || line == lastLine) return@forEach
                val remaining = MAX_CHARS - chars
                val clipped = if (line.length > remaining) line.take(remaining) else line
                if (clipped.isNotEmpty()) {
                    lines += clipped
                    chars += clipped.length + 1
                    lastLine = line
                }
            }
        }

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= MAX_NODES || depth > MAX_DEPTH || chars >= MAX_CHARS) return
            visited++

            // 密码框连同子树一起跳过，避免把输入内容带进解析器
            if (node.isPassword) return

            if (node.isVisibleToUser && node.packageName?.toString() != ownPackage) {
                append(node.text)
                append(node.contentDescription)
            }

            for (index in 0 until node.childCount) {
                if (visited >= MAX_NODES || chars >= MAX_CHARS) return
                val child = node.getChild(index) ?: continue
                visit(child, depth + 1)
            }
        }

        visit(root, 0)
        return lines.joinToString("\n")
    }
}
