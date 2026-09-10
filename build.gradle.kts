plugins {
    application
    java
}

group = "org.tavall.community"
version = "0.2.0-SNAPSHOT"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    val githubToken = providers.environmentVariable("GITHUB_TOKEN").orNull
    if (!githubToken.isNullOrBlank()) {
        maven("https://maven.pkg.github.com/TavallStudios/function-catalog") {
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orElse("github").get()
                password = githubToken
            }
        }
        maven("https://maven.pkg.github.com/TavallStudios/tavall-di") {
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orElse("github").get()
                password = githubToken
            }
        }
    }
}

val functionCatalogVersion = providers.gradleProperty("functionCatalogVersion").orElse("1.0.1")
val tavallDiVersion = providers.gradleProperty("tavallDiVersion").orElse("1.0.0")

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation("org.tavall:ai-core:${functionCatalogVersion.get()}")
    implementation("org.tavall:agent-runtime:${functionCatalogVersion.get()}")
    implementation("org.tavall:strands-agent-provider:${functionCatalogVersion.get()}")
    implementation("org.tavall:mcp-server:${functionCatalogVersion.get()}")
    implementation("org.tavall:tavall-di:${tavallDiVersion.get()}")
    implementation("org.apache.tomcat.embed:tomcat-embed-core:11.0.20")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.tavall.community.CommunityAgentApplication"
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}
