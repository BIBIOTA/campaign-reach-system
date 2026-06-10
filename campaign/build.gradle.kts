plugins {
    id("campaignreach.java-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter)

    // campaign may only talk to reach through the shared kernel (event/config).
    // It must NOT depend on :reach — enforced by the ArchUnit guard in :app.
    implementation(project(":shared"))
}
