pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
    plugins {
        kotlin("jvm") version "2.2.21"  // 使用与libs.versions.toml中kotlin-plugin相同的版本
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven("https://maven.aliyun.com/repository/spring")
        mavenLocal {
            content {
                includeGroup("io.github.libxposed")
            }
        }
    }
    versionCatalogs {
        create("libs")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
include(":app")

