plugins {
    id("campaignreach.java-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter)

    // reach may only talk to campaign through the shared kernel (event/config).
    // It must NOT depend on :campaign — enforced by the ArchUnit guard in :app.
    implementation(project(":shared"))
}
