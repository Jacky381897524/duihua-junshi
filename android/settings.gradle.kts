pluginManagement {
    repositories {
        System.getenv("LOVEADVISOR_MAVEN_CACHE")?.let { maven { url=uri(it) } }
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroup("com.google.testing.platform") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        System.getenv("LOVEADVISOR_MAVEN_CACHE")?.let { maven { url=uri(it) } }
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google\\.(mlkit|android|firebase).*"); includeGroup("com.google.testing.platform") } }
        mavenCentral()
    }
}
rootProject.name = "LoveAdvisor"
include(":app", ":core")
