# Keep LogAspect - called by ASM-injected bytecode
-keep class com.mimo.aoplog.runtime.LogAspect { *; }

# Keep LogManager - init() called by user code, accessed by LogAspect
-keep class com.mimo.aoplog.runtime.LogManager { *; }

# Keep LogHandler interface - implemented by user
-keep interface com.mimo.aoplog.runtime.LogHandler { *; }
-keep class * implements com.mimo.aoplog.runtime.LogHandler { *; }

# Keep @LogTrace annotation
-keep @interface com.mimo.aoplog.runtime.LogTrace

# Keep BaseLogHandler and all built-in handlers
-keep class com.mimo.aoplog.runtime.handler.** { *; }
-keep class com.mimo.aoplog.runtime.BaseLogHandler { *; }
-keep class com.mimo.aoplog.runtime.MethodPhase { *; }
