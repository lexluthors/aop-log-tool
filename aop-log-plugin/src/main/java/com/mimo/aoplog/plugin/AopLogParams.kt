package com.mimo.aoplog.plugin

import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * ASM 插桩参数，通过 Gradle Property API 在 plugin 和 factory 之间传递配置。
 * 所有字段使用 @Input 标注，确保 Gradle 增量构建缓存能正确识别配置变化。
 */
interface AopLogParams : InstrumentationParameters {

    @get:Input
    val matchAnnotations: ListProperty<String>

    @get:Input
    val matchPatterns: ListProperty<String>

    @get:Input
    val matchClasses: ListProperty<String>

    @get:Input
    val excludePatterns: ListProperty<String>

    @get:Input
    val handlerClass: Property<String>

    @get:Input
    val logArgs: Property<Boolean>

    @get:Input
    val logReturnValue: Property<Boolean>

    @get:Input
    val computeCost: Property<Boolean>
}
