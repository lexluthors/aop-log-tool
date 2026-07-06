plugins {
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // AGP API — Gradle 运行时已有，compileOnly 避免打包
    compileOnly("com.android.tools.build:gradle:7.4.2")
    compileOnly("com.android.tools.build:gradle-api:7.4.2")

    // ASM — 编译期字节码操作
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
}

gradlePlugin {
    plugins {
        create("aopLog") {
            id = "com.mimo.aoplog"
            implementationClass = "com.mimo.aoplog.plugin.AopLogPlugin"
        }
    }
}
