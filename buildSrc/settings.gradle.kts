// https://stackoverflow.com/questions/67795324/gradle7-version-catalog-how-to-use-it-with-buildsrc
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
