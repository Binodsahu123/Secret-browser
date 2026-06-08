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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "My Application"

include(":app")
include(":extension-engine")
include(":permission-engine")
include(":adblock-engine")
include(":browser-engine")
include(":tab-engine")
include(":search-engine")
include(":translate-engine")
include(":reader-engine")
include(":bookmark-engine")
include(":history-engine")
include(":download-engine")
include(":ai-engine")
include(":news-engine")
include(":media-engine")
include(":security-engine")
include(":settings-engine")
include(":notification-engine")
include(":backup-engine")
include(":database-core")
include(":network-core")
include(":analytics-core")
include(":ui-core")
