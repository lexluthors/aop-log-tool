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
        // 以 .** 结尾 → 递归子包通配，整体是类名 pattern
        if (pattern.endsWith(".**")) return pattern
        // 以 .* 结尾 → 需要判断是"类名通配"还是"方法名通配"
        // 规则：如果 .* 前面还有 * 或通配符，说明最后 .* 是方法名部分
        if (pattern.endsWith(".*")) {
            val withoutLast = pattern.dropLast(2) // 去掉最后的 .*
            // 如果去掉最后 .* 后以 .* 或 .** 结尾，说明最后 .* 是方法名通配
            if (withoutLast.endsWith(".*") || withoutLast.endsWith(".**")) {
                return withoutLast
            }
            // 如果去掉最后 .* 后的剩余部分不含 "/" 且不含大写开头（非类名），
            // 也即最后 .* 前面的部分像包名 → 最后 .* 是类名通配
            // 否则最后 .* 是方法名通配
            // 简单策略：检查最后 .* 前面的段是否含有通配符
            val lastDot = withoutLast.lastIndexOf('.')
            if (lastDot >= 0) {
                val lastSegment = withoutLast.substring(lastDot + 1)
                if (lastSegment.contains("*")) {
                    // 最后 .* 前面是通配符（如 *.），说明 .* 是方法名部分
                    return withoutLast
                }
            }
            // 最后 .* 前面是具体类名（如 Foo），说明这个 .* 是方法名部分
            // 此时类名部分就是 withoutLast（即 com.example.Foo）
            return withoutLast
        }
        // 不含通配符 → 可能是完整类名或 类名.方法名
        // 判断最后一段：大写开头 → 类名；小写开头 → 方法名
        val lastDot = pattern.lastIndexOf('.')
        if (lastDot >= 0) {
            val lastSegment = pattern.substring(lastDot + 1)
            if (lastSegment.isNotEmpty() && lastSegment[0].isLowerCase()) {
                // 最后一段小写开头 → 是方法名，剥离
                return pattern.substring(0, lastDot)
            }
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
