import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless")
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        googleJavaFormat()
        targetExclude("src/**/java/io/ichnois/vendor/**")
    }
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
        ktfmt().googleStyle()
        targetExclude("src/test/java/io/ichnois/apollo4/generated/**", "src/test/java/io/ichnois/apollo3/adapter/**")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt().googleStyle()
    }
}

