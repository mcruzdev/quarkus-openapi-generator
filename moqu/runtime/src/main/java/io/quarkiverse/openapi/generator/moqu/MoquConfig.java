package io.quarkiverse.openapi.generator.moqu;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.openapi-generator.moqu")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface MoquConfig {

    String DEFAULT_RESOURCE_DIR = "openapi";

    /**
     * Path to the Moqu OpenAPI files, relative to the <code>src/main/resources</code> directory.
     */
    @WithDefault(DEFAULT_RESOURCE_DIR)
    String resourceDir();

    /**
     * WireMock Dev Service integration settings.
     */
    Wiremock wiremock();

    interface Wiremock {

        /**
         * Directory where Moqu writes the generated WireMock stub files for the WireMock Dev Service
         * to serve live in Dev mode and tests. Relative paths are resolved against the project root,
         * the same way <code>quarkus.wiremock.devservices.files-mapping</code> is.
         * <p>
         * If unset, Moqu tries to reuse <code>quarkus.wiremock.devservices.files-mapping</code> when
         * that property has been customized away from its own default; if that is also unset, Moqu
         * falls back to <code>target/moqu-wiremock</code>.
         */
        Optional<String> outputDir();
    }
}
