
object Config {
    val AGP = System.getenv("VERSION_AGP") ?: "9.4.0"
    val kotlinStdLib = "stdlib-jdk8"
    val kotlinStdLibVersionAndroid = "1.9.24"
    val kotlinTestJunit = "test-junit"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:$AGP"
    }

    object Android {
        val abiFilters = listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a")

        // Debug variants are disabled everywhere. Unit tests run against the release
        // variant, so building the debug variant would only add overhead.
        fun shouldSkipDebugVariant(name: String?): Boolean {
            return name == "debug"
        }
    }

    object Libs {
        val springWeb = "org.springframework:spring-webmvc"
        val springWebflux = "org.springframework:spring-webflux"
        val springSecurityWeb = "org.springframework.security:spring-security-web"
        val springSecurityConfig = "org.springframework.security:spring-security-config"
        val springAop = "org.springframework:spring-aop"
        val aspectj = "org.aspectj:aspectjweaver"

        val kotlinReflect = "org.jetbrains.kotlin:kotlin-reflect"
        val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib"
    }

    object AnnotationProcessors {
        val springBootAutoConfigure = "org.springframework.boot:spring-boot-autoconfigure-processor"
        val springBootConfiguration = "org.springframework.boot:spring-boot-configuration-processor"
    }

    object QualityPlugins {
        // this can be removed when we upgrade to Gradle 8, which allows us to use a getter for the plugin ID
        val detektPlugin = "io.gitlab.arturbosch.detekt"
    }

    object IchnoisCore {
        val ICHNOIS_JAVA_SDK_NAME = "ichnois.java"
        val ICHNOIS_ANDROID_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.android"
        val ICHNOIS_TIMBER_SDK_NAME = "$ICHNOIS_ANDROID_SDK_NAME.timber"
        val ICHNOIS_LOGBACK_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.logback"
        val ICHNOIS_JUL_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.jul"
        val ICHNOIS_LOG4J2_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.log4j2"
        val ICHNOIS_SPRING_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring"
        val ICHNOIS_SPRING_JAKARTA_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring.jakarta"
        val ICHNOIS_SPRING_7_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-7"
        val ICHNOIS_SPRING_BOOT_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-boot"
        val ICHNOIS_SPRING_BOOT_STARTER_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-boot-starter"
        val ICHNOIS_SPRING_BOOT_JAKARTA_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-boot.jakarta"
        val ICHNOIS_SPRING_BOOT_STARTER_JAKARTA_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-boot-starter.jakarta"
        val ICHNOIS_SPRING_BOOT_4_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-boot-4"
        val ICHNOIS_SPRING_BOOT_4_STARTER_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.spring-boot-4-starter"
        val ICHNOIS_OPENTELEMETRY_BOOTSTRAP_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.bootstrap"
        val ICHNOIS_OPENTELEMETRY_CORE_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.core"
        val ICHNOIS_OPENTELEMETRY_OTLP_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.otlp"
        val ICHNOIS_OPENTELEMETRY_OTLP_SPRING_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.otlp-spring"
        val ICHNOIS_OPENTELEMETRY_AGENT_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.agent"
        val ICHNOIS_OPENTELEMETRY_AGENTLESS_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.agentless"
        val ICHNOIS_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.agentless-spring"
        val ICHNOIS_OPENTELEMETRY_AGENTCUSTOMIZATION_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.opentelemetry.agentcustomization"
        val ICHNOIS_OPENFEIGN_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.openfeign"
        val ICHNOIS_APOLLO3_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.apollo3"
        val ICHNOIS_APOLLO4_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.apollo4"
        val ICHNOIS_APOLLO5_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.apollo5"
        val ICHNOIS_APOLLO_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.apollo"
        val ICHNOIS_GRAPHQL_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.graphql"
        val ICHNOIS_GRAPHQL_CORE_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.graphql-core"
        val ICHNOIS_GRAPHQL22_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.graphql22"
        val ICHNOIS_JCACHE_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.jcache"
        val ICHNOIS_QUARTZ_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.quartz"
        val ICHNOIS_JDBC_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.jdbc"
        val ICHNOIS_KAFKA_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.kafka"
        val ICHNOIS_OPENFEATURE_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.openfeature"
        val ICHNOIS_LAUNCHDARKLY_SERVER_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.launchdarkly-server"
        val ICHNOIS_LAUNCHDARKLY_ANDROID_SDK_NAME = "$ICHNOIS_ANDROID_SDK_NAME.launchdarkly"
        val ICHNOIS_SERVLET_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.servlet"
        val ICHNOIS_SERVLET_JAKARTA_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.servlet.jakarta"
        val ICHNOIS_COMPOSE_HELPER_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.compose.helper"
        val ICHNOIS_OKHTTP_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.okhttp"
        val ICHNOIS_REACTOR_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.reactor"
        val ICHNOIS_KOTLIN_EXTENSIONS_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.kotlin-extensions"
        val ICHNOIS_ASYNC_PROFILER_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.async-profiler"
        val ICHNOIS_KTOR_CLIENT_SDK_NAME = "$ICHNOIS_JAVA_SDK_NAME.ktor-client"
        val group = "io.github.ichnois"
        val description = "SDK for ichnois.io"
        val versionNameProp = "versionName"
    }

    object BuildScript {
        val androidLibs = setOf(
            "ichnois-android-core",
            "ichnois-android-ndk",
            "ichnois-android-fragment",
            "ichnois-android-navigation",
            "ichnois-android-navigation3",
            "ichnois-android-timber",
            "ichnois-compose-android",
            "ichnois-android-sqlite",
            "ichnois-android-replay"
        )

        val androidXLibs = listOf(
            "androidx.core:core"
        )
    }
}
