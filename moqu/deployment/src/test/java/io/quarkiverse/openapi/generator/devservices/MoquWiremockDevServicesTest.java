package io.quarkiverse.openapi.generator.devservices;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.Matchers.containsString;

class MoquWiremockDevServicesTest {

    private static final int APP_PORT = 8183;
    private static final String WIREMOCK_MAPPINGS_JSON_PATH = "/q/moqu/yaml/openapi/wiremock-mappings.json?mode=see";

    @RegisterExtension
    static final QuarkusDevModeTest unitTest = new QuarkusDevModeTest()
            .withApplicationRoot(javaArchive -> javaArchive
                    .addAsResource("devservices-api.yaml", "openapi/openapi.yaml")
                    .addAsResource(new StringAsset("quarkus.http.port=" + APP_PORT + "\n"), "application.properties"));

    @Test
    void resolvesConcreteUrlFromExample() {
        RestAssured.given()
                .port(APP_PORT)
                .when().get(WIREMOCK_MAPPINGS_JSON_PATH)
                .then()
                .statusCode(200)
                .body(containsString("\"url\":\"/users/1\""))
                .body(containsString("80"));
    }

    @Test
    void updatesGeneratedStubOnHotReload() {
        RestAssured.given()
                .port(APP_PORT)
                .when().get(WIREMOCK_MAPPINGS_JSON_PATH)
                .then()
                .statusCode(200)
                .body(containsString("80"));

        unitTest.modifyResourceFile("openapi/openapi.yaml", content -> content.replace("80", "77"));

        RestAssured.given()
                .port(APP_PORT)
                .when().get(WIREMOCK_MAPPINGS_JSON_PATH)
                .then()
                .statusCode(200)
                .body(containsString("77"));
    }
}
