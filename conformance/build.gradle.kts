// Real-server conformance runner. This is a placeholder until T-M1-11.
plugins {
    base
}

tasks.register("smoke") {
    group = "verification"
    description = "Boots one real cell and checks a round trip. Placeholder until T-M1-11."
    doLast {
        val message = "smoke: not implemented until T-M1-11"
        // CI sets CI_REQUIRE_SMOKE=true once a real smoke run exists. Until then, failing here keeps
        // a placeholder from passing silently. Outside CI the placeholder only prints.
        if (System.getenv("CI_REQUIRE_SMOKE") == "true") {
            throw GradleException(message)
        }
        logger.lifecycle(message)
    }
}
