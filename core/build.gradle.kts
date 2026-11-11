plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation("com.github.houbb:opencc4j:1.14.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { useJUnit(); testLogging { events("passed", "failed") } }
