plugins {
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    // AGP API — Gradle 运行时已有，compileOnly 避免打包
    compileOnly("com.android.tools.build:gradle:7.3.1")
    compileOnly("com.android.tools.build:gradle-api:7.3.1")

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
