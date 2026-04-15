package buildlogic.mm

// license files (see also mm-dmjsons-conventions.gradle for corresponding config for npm artifacts):
tasks.withType<AbstractArchiveTask>().configureEach {
    //exclude any third party license files, third party license files are handled by our THIRD_PARTY_NOTICES file
    exclude("META-INF/*LICENSE*")
    exclude("META-INF/*NOTICE*")
    exclude("META-INF/*THIRD_PARTY_NOTICES*")

    //include our licenses license files as meta info
    from(rootProject.rootDir) {
        include("LICENSE")
        include("NOTICE")
        include("THIRD_PARTY_NOTICES")
        include("licenses/**")
        into("META-INF")
    }
}
