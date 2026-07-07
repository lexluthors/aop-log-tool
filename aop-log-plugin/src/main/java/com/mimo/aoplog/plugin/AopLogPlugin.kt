package com.mimo.aoplog.plugin

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * AOP 日志 Gradle 插件入口。
 *
 * 注册 DSL（aopLog {}），并在 AGP 的 Android 构建流程中注册 ASM 字节码插桩。
 * 使用 AGP 7.4+ 的 Instrumentation API（替代已废弃的 Transform API）。
 */
class AopLogPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("aopLog", AopLogExtension::class.java)

        project.plugins.withId("com.android.application") {
            configureWithAndroid(project, extension)
        }
        project.plugins.withId("com.android.library") {
            configureWithAndroid(project, extension)
        }
    }

    private fun configureWithAndroid(project: Project, extension: AopLogExtension) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            if (!extension.enabled) return@onVariants

            variant.instrumentation.apply {
                // 注册 ASM ClassVisitor 工厂
                // transformClassesWith 的 scope 参数决定插桩范围：
                //   PROJECT           → 只插桩项目自身代码（推荐）
                //   ALL               → 包含第三方库
                //   EXTERNAL_LIBRARIES → 只插桩第三方库
                transformClassesWith(
                    LogClassVisitorFactory::class.java,
                    InstrumentationScope.ALL
                ) { params ->
                    params.matchAnnotations.set(extension.matchAnnotations)
                    params.matchPatterns.set(extension.matchPatterns)
                    params.matchClasses.set(extension.matchClasses)
                    params.excludePatterns.set(extension.excludePatterns)
                    params.handlerClass.set(extension.handlerClass)
                    params.logArgs.set(extension.logArgs)
                    params.logReturnValue.set(extension.logReturnValue)
                    params.computeCost.set(extension.computeCost)
                }

                setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
            }
        }
    }
}
