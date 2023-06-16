package health.flo.network.gradle

object Environment {

    val isCI: Boolean
        get() = System.getenv()["CI"] != null
}

fun isReleaseBranch(): Boolean {
    val branch = System.getenv("GITHUB_HEAD_REF")

    return branch != null && (branch == "main")
}
