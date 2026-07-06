# AOP Log Tool

基于 Gradle Plugin + ASM（AGP Instrumentation API）的 Android AOP 日志工具。

在编译期通过字节码插桩，为目标方法自动注入日志回调，**零侵入业务代码**。

> 专为调试排查设计，不用于生产环境。

---

## 功能特性

- **零侵入** — 不改业务代码，通过包名/类名规则或注解匹配目标方法
- **编译期插桩** — 基于 AGP Instrumentation API + ASM，运行时零额外开销
- **自定义输出** — 实现 `LogHandler` 接口即可，内部可用任意第三方库（Gson/OkHttp/Room 等）
- **完整上下文** — enter/exit/throw 回调均包含类名、方法名、参数、实例对象
- **耗时统计** — 自动记录方法执行耗时
- **异常捕获** — 方法抛出异常时自动回调（在异常传播前）
- **内置 Handler** — Logcat 格式化输出、文件写入、调用计数、过滤器，支持组合使用
- **重入保护** — 编译期排除 + 运行期 ThreadLocal 双重保护，无死循环风险

---

## 项目结构

```
aop-log-tool/
├── aop-log-runtime/    ← Android Library：接口 + 内置 Handler
├── aop-log-plugin/     ← Gradle Plugin：编译期 ASM 字节码插桩
└── README.md
```

---

## 集成方式

### 1. 引入项目

将 `aop-log-tool` 目录放在你的项目旁边，通过 `includeBuild` 引入：

```kotlin
// settings.gradle.kts
includeBuild("../aop-log-tool")
```

### 2. 应用插件

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("com.mimo.aoplog")
}

dependencies {
    // 只在 debug 构建引入 runtime
    debugImplementation("com.mimo.aoplog:aop-log-runtime:1.0.0")
}
```

### 3. 配置插桩规则

```kotlin
// app/build.gradle.kts
aopLog {
    enabled = true  // release 构建设为 false

    // 指定你的 Handler 类（必须实现 LogHandler 接口）
    handlerClass = "com.example.DebugLogHandler"

    // 方式一：注解匹配
    matchAnnotations = listOf("com.mimo.aoplog.runtime.LogTrace")

    // 方式二：包名/类名 Pattern 匹配
    matchPatterns = listOf(
        "com.gatherad.sdk.source.*.*",   // source 包下所有类的所有方法
        "com.gatherad.sdk.net.**",       // net 包及子包所有方法
        "com.example.Foo.doSomething"    // 精确到某个方法
    )

    // 方式三：类名精确匹配
    matchClasses = listOf("com.example.MyClass")

    // 排除规则（优先级最高）
    excludePatterns = listOf("com.mimo.aoplog.**", "*.toString")

    // 可选配置
    logArgs = true          // 传递参数给 Handler
    logReturnValue = true   // 传递返回值给 Handler
    computeCost = true      // 计算耗时
}
```

### Pattern 语法

| Pattern | 含义 |
|---------|------|
| `com.example.*` | `com.example` 包下所有类所有方法（不递归子包） |
| `com.example.**` | `com.example` 及其所有子包 |
| `com.example.Foo.*` | `Foo` 类的所有方法 |
| `com.example.Foo.bar` | 精确匹配 `Foo.bar` |

---

## 自定义 Handler

### 核心接口

```kotlin
interface LogHandler {
    fun onEnter(className: String, methodName: String, descriptor: String,
                isStatic: Boolean, instance: Any?, args: Array<Any?>?)

    fun onExit(className: String, methodName: String, descriptor: String,
               isStatic: Boolean, instance: Any?, args: Array<Any?>?,
               returnValue: Any?, costMs: Long)

    fun onThrow(className: String, methodName: String, descriptor: String,
                isStatic: Boolean, instance: Any?, args: Array<Any?>?,
                throwable: Throwable, costMs: Long)
}
```

### 最简实现

```kotlin
class DebugLogHandler : BaseLogHandler() {
    override fun onEnter(className: String, methodName: String, descriptor: String,
                         isStatic: Boolean, instance: Any?, args: Array<Any?>?) {
        Log.d("AopLog", "[ENTER] $className#$methodName args=${args?.contentDeepToString()}")
    }

    override fun onExit(className: String, methodName: String, descriptor: String,
                        isStatic: Boolean, instance: Any?, args: Array<Any?>?,
                        returnValue: Any?, costMs: Long) {
        Log.d("AopLog", "[EXIT]  $className#$methodName result=$returnValue cost=${costMs}ms")
    }

    override fun onThrow(className: String, methodName: String, descriptor: String,
                         isStatic: Boolean, instance: Any?, args: Array<Any?>?,
                         throwable: Throwable, costMs: Long) {
        Log.e("AopLog", "[THROW] $className#$methodName error=$throwable")
    }
}
```

### 初始化（Application.onCreate）

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogManager.init(DebugLogHandler())
    }
}
```

### 高级用法

```kotlin
// Logcat + 文件 + 计数 同时输出
LogManager.init(CompositeHandler(
    LogcatLogHandler(LogcatLogHandler.Config(showCallStack = true)),
    FileLogHandler(this),
    CountLogHandler()
))

// 过滤：只关注特定包，且耗时 > 10ms 才输出
LogManager.init(FilterHandler(
    LogcatLogHandler(),
    FilterHandler.Config(
        includePackages = listOf("com.gatherad.sdk"),
        minCostMs = 10
    )
))

// 完全自定义（内部可用任意库）
LogManager.init(object : LogHandler {
    private val gson = Gson()
    // ... 自由实现
})
```

---

## 内置 Handler

| Handler | 功能 | 场景 |
|---------|------|------|
| `LogcatLogHandler` | 格式化输出到 Logcat | 日常调试，支持调用栈打印 |
| `FileLogHandler` | 写入本地文件 | 长时间调试、Monkey 测试 |
| `CountLogHandler` | 调用次数/耗时统计 | 排查重复调用、性能瓶颈 |
| `FilterHandler` | 过滤器包装器 | 按包名/类名/耗时过滤 |
| `CompositeHandler` | 多 Handler 组合 | 同时输出到多个目标 |

---

## 输出效果

```
D/AopLog: [ENTER] CsjSplashAdapter#loadAd | args: [slotId=1, size=1080x1920] | T: main
D/AopLog: [EXIT]  CsjSplashAdapter#loadAd | args: [...] | result: AdInfo{ecpm=500} | cost: 234ms
D/AopLog: [THROW] GdtSplashAdapter#loadAd | args: [slotId=2] | error: TimeoutException
    at com.gatherad.sdk.source.gdt.GdtSplashAdapter.loadAd(GdtSplashAdapter.kt:45)
    at com.gatherad.sdk.AdManager.loadSplash(AdManager.kt:112)
```

---

## 构建

```bash
cd aop-log-tool
./gradlew :aop-log-runtime:assembleRelease
./gradlew :aop-log-plugin:build
```

---

## 版本要求

| 依赖 | 最低版本 |
|------|---------|
| AGP | 7.4.2 |
| Gradle | 7.5 |
| Kotlin | 1.8.x |
| compileSdk | 34 |
| minSdk | 21 |

---

## 已知限制

- **Kotlin suspend 函数**：按普通方法处理，exit 在挂起点触发而非协程完成时
- **Instrumentation API**：依赖 AGP 内部 API，AGP 大版本升级时可能需要适配
- **InstrumentationScope.PROJECT**：默认只插桩项目代码，不处理第三方库（可在插件中改为 `ALL`）
