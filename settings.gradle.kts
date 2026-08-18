pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BlindfoldTrainer"

// --- Jezgro ---------------------------------------------------------------
// core:chess i core:model su čist Kotlin (bez Androida) da bi se testovi
// vrteli u sekundama, bez emulatora.
include(":core:model")
include(":core:chess")
include(":core:moduleapi")
include(":core:designsystem")
include(":core:audio")
include(":core:engine")
include(":core:progress")
include(":core:data")

// --- Moduli za trening ----------------------------------------------------
// Dovoljno je dodati modul ovde i na spisak zavisnosti :app-a; meni i
// navigacija se dalje generišu iz registra.
include(":feature:geometry")
include(":feature:pairs")
include(":feature:endgame")
include(":feature:knightpath")
include(":feature:minefield")
include(":feature:recall")
include(":feature:followgame")
include(":feature:dictation")

include(":app")
