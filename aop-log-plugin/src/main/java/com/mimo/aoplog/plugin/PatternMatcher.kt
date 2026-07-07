package com.mimo.aoplog.plugin

/**
 * Pattern 匹配工具。
 *
 * 支持以下通配符语法：
 * - `com.example.*`          → 包下所有类的所有方法
 * - `com.example.**`         → 包及子包所有类的所有方法
 * - `com.example.Foo.*`      → Foo 类所有方法
 * - `com.example.Foo.bar`    → 精确匹配某个方法
 *
 * 匹配时 className 使用点分隔格式（com.example.Foo），
 * methodName 为简单方法名（如 "doSomething"）。
 */
object PatternMatcher {

    /**
     * 判断给定的类名+方法名是否匹配任意一个 pattern。
     *
     * @param className   类名（点分隔），如 "com.example.Foo"
     * @param methodName  方法名，如 "doSomething"；匹配类级别时可传 null
     * @param patterns    pattern 列表
     * @return 匹配返回 true
     */
    fun matches(className: String, methodName: String?, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { matchesSingle(className, methodName, it) }
    }

    /**
     * 判断给定的类名是否匹配任意一个 pattern（类级别匹配，不关心方法名）。
     * 会从 pattern 中剥离方法名部分（最后一个 .* 或 .** 或 .methodName），
     * 只保留类名部分进行匹配。
     */
    fun matchesClass(className: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { pattern ->
            val classPattern = extractClassPattern(pattern)
            matchesClassSingle(className, classPattern)
        }
    }

    /**
     * 从完整 pattern 中提取类名部分。
     * - "com.example.*.*"        → "com.example.*"
     * - "com.example.**"         → "com.example.**"
     * - "com.example.Foo.bar"    → "com.example.Foo"
     * - "com.example.Foo"        → "com.example.Foo"
     */
    private fun extractClassPattern(pattern: String): String {
        if (pattern.endsWith(".**")) return pattern
        if (pattern.endsWith(".*")) {
            val withoutLast = pattern.dropLast(2)
            if (withoutLast.endsWith(".*") || withoutLast.endsWith(".**")) return withoutLast
            val lastDot = withoutLast.lastIndexOf('.')
            val lastSeg = if (lastDot >= 0) withoutLast.substring(lastDot + 1) else withoutLast
            if (lastSeg.contains("*")) return withoutLast
            if (lastSeg.isNotEmpty() && lastSeg[0].isUpperCase()) return withoutLast
            return pattern
        }
        val lastDot = pattern.lastIndexOf('.')
        if (lastDot >= 0) {
            val seg = pattern.substring(lastDot + 1)
            if (seg.isNotEmpty() && seg[0].isLowerCase()) return pattern.substring(0, lastDot)
        }
        return pattern
    }

    private fun matchesSingle(className: String, methodName: String?, pattern: String): Boolean {
        val lastDot = pattern.lastIndexOf('.')
        if (lastDot < 0) return false

        val classPart = pattern.substring(0, lastDot)
        val methodPart = pattern.substring(lastDot + 1)

        // 先匹配类名
        if (!matchesClassSingle(className, classPart)) return false

        // 再匹配方法名（如果 pattern 指定了方法）
        if (methodName == null) return true
        if (methodPart == "*") return true
        if (methodPart == "**") return true
        return methodPart == methodName
    }

    private fun matchesClassSingle(className: String, classPattern: String): Boolean {
        return matchesClassSinglePublic(className, classPattern)
    }

    /** 供外部调用的类名匹配方法 */
    fun matchesClassSinglePublic(className: String, classPattern: String): Boolean {
        return when {
            // ** 递归匹配：com.example.** → com.example 及其所有子包
            classPattern.endsWith(".**") -> {
                val prefix = classPattern.removeSuffix(".**")
                className == prefix || className.startsWith("$prefix.")
            }
            // * 单层匹配：com.example.* → 包下所有类（不递归子包）
            classPattern.endsWith(".*") -> {
                val prefix = classPattern.removeSuffix(".*")
                // className 必须是 prefix 的直接子包下的类
                if (!className.startsWith("$prefix.")) return false
                val remainder = className.removePrefix("$prefix.")
                // 直接子包：remainder 中不再有 "."
                !remainder.contains('.')
            }
            // 精确匹配
            else -> className == classPattern
        }
    }
}
