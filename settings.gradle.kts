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
        // Opt-in private updater repository. No credentials are embedded in the app.
        if (providers.gradleProperty("useSharedUpdater").orNull == "true" || providers.gradleProperty("useSharedTheme").orNull == "true") {
            require(!providers.environmentVariable("N2K02_PACKAGES_TOKEN").orNull.isNullOrBlank()) {
                "Shared libraries require N2K02_PACKAGES_TOKEN"
            }
            maven {
                name = "N2K02PrivateUpdater"
                url = uri("https://maven.pkg.github.com/N2K02/n2k02updater")
                credentials {
                    username = "N2K02"
                    password = providers.environmentVariable("N2K02_PACKAGES_TOKEN").orNull
                }
                content { includeModule("dev.n2k02", "updater") }
            }
            if (providers.gradleProperty("useSharedTheme").orNull == "true") {
                maven {
                    name = "N2K02PrivateTheme"
                    url = uri("https://maven.pkg.github.com/N2K02/n2k02theme")
                    credentials {
                        username = "N2K02"
                        password = providers.environmentVariable("N2K02_PACKAGES_TOKEN").orNull
                    }
                    content { includeModule("dev.n2k02", "theme") }
                }
            }
        }
    }
}

rootProject.name = "MRadio"
include(":app")
