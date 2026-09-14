package io.quarkiverse.openapi.generator.devservices;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.containsString;

class MoquWiremockDevServicesUnitTest {

    private static final int WIREMOCK_PORT = 8198;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(javaArchive -> javaArchive
                    .addAsResource("devservices-api.yaml", "openapi/openapi.yaml"))
            .overrideConfigKey("quarkus.wiremock.devservices.files-mapping", "target/moqu-wiremock")
            .overrideConfigKey("quarkus.wiremock.devservices.port", String.valueOf(WIREMOCK_PORT));

    @Test
    void servesMoquGeneratedStubLiveInTestMode() {
        RestAssured.given()
                .baseUri("http://localhost:" + WIREMOCK_PORT)
                .when().get("/users/1")
                .then()
                .statusCode(200)
                .body(containsString("Alice"));
    }
}
