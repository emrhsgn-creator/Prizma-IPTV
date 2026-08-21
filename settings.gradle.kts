pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // FFmpeg yazılım kod çözücüleri (AC3 / E-AC3 / DTS / TrueHD) buradan geliyor.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Prizma IPTV"
include(":app")
