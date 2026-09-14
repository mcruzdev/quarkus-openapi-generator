package io.quarkiverse.openapi.generator.devservices;

import static io.quarkiverse.openapi.generator.devservices.MoquWiremockDevServicesProcessor.DEFAULT_WIREMOCK_FILES_MAPPING;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.openapi.generator.items.MoquBuildItem;
import io.quarkiverse.openapi.generator.moqu.MoquConfig;
import io.quarkiverse.openapi.moqu.Moqu;
import io.quarkiverse.openapi.moqu.OpenAPIMoquImporter;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class MoquWiremockDevServicesProcessorTest {

    private static final String OPENAPI_YAML = """
            openapi: 3.0.3
            servers:
              - url: http://localhost:8888
            info:
              version: 999-SNAPSHOT
              title: Users API
            paths:
              /users/{id}:
                get:
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: number
                      examples:
                        alice:
                          value: 1
                  responses:
                    "200":
                      description: Ok
                      content:
                        "application/json":
                          examples:
                            alice:
                              value:
                                '{"name": "Alice", "age": 80}'
            """;

    private final MoquWiremockDevServicesProcessor processor = new MoquWiremockDevServicesProcessor();

    // --- resolveMappingsDir(...): pure resolution logic, no augmentation or config lookup needed ---

    @Test
    void resolvesToDefaultTargetSubdirWhenNothingIsConfigured(@TempDir Path outputDir) {
        Path resolved = MoquWiremockDevServicesProcessor.resolveMappingsDir(
                outputTarget(outputDir), Optional.empty(), DEFAULT_WIREMOCK_FILES_MAPPING);

        assertThat(resolved).isEqualTo(outputDir.resolve("moqu-wiremock").resolve("mappings"));
    }

    @Test
    void followsCustomizedWiremockFilesMapping(@TempDir Path projectRoot) {
        Path outputDir = projectRoot.resolve("target");

        Path resolved = MoquWiremockDevServicesProcessor.resolveMappingsDir(
                outputTarget(outputDir), Optional.empty(), "custom-wiremock-dir");

        assertThat(resolved).isEqualTo(projectRoot.resolve("custom-wiremock-dir").resolve("mappings"));
    }

    @Test
    void ignoresClasspathFilesMappingAndFallsBackToDefault(@TempDir Path outputDir) {
        Path resolved = MoquWiremockDevServicesProcessor.resolveMappingsDir(
                outputTarget(outputDir), Optional.empty(), "classpath:wiremock");

        assertThat(resolved).isEqualTo(outputDir.resolve("moqu-wiremock").resolve("mappings"));
    }

    @Test
    void explicitMoquOutputDirTakesPrecedenceOverFilesMapping(@TempDir Path projectRoot) {
        Path outputDir = projectRoot.resolve("target");

        Path resolved = MoquWiremockDevServicesProcessor.resolveMappingsDir(
                outputTarget(outputDir), Optional.of("moqu-stubs"), "some-other-dir");

        assertThat(resolved).isEqualTo(projectRoot.resolve("moqu-stubs").resolve("mappings"));
    }

    // --- generateWiremockStubs(...): end-to-end behavior ---

    @Test
    void doesNothingWhenWiremockCapabilityIsMissing(@TempDir Path outputDir) {
        RecordingProducer producer = new RecordingProducer();

        processor.generateWiremockStubs(List.of(moquBuildItem()), outputTarget(outputDir),
                new Capabilities(Set.of()), moquConfig(Optional.empty()), producer);

        assertThat(producer.produced).isEmpty();
        assertThat(outputDir.resolve("moqu-wiremock")).doesNotExist();
    }

    @Test
    void writesStubsWhenWiremockCapabilityIsPresent(@TempDir Path outputDir) throws IOException {
        RecordingProducer producer = new RecordingProducer();

        processor.generateWiremockStubs(List.of(moquBuildItem()), outputTarget(outputDir),
                new Capabilities(Set.of("io.quarkiverse.wiremock")), moquConfig(Optional.empty()), producer);

        assertThat(producer.produced).hasSize(1);
        assertSingleStubWritten(outputDir.resolve("moqu-wiremock").resolve("mappings"));
    }

    @Test
    void onlyDeletesItsOwnFilesOnRegeneration(@TempDir Path outputDir) throws IOException {
        Path mappingsDir = outputDir.resolve("moqu-wiremock").resolve("mappings");
        Files.createDirectories(mappingsDir);
        Path handWrittenStub = mappingsDir.resolve("hand-written-stub.json");
        Files.writeString(handWrittenStub, "{\"request\":{},\"response\":{}}");

        processor.generateWiremockStubs(List.of(moquBuildItem()), outputTarget(outputDir),
                new Capabilities(Set.of("io.quarkiverse.wiremock")), moquConfig(Optional.empty()), new RecordingProducer());

        assertThat(handWrittenStub).exists();
        assertSingleStubWritten(mappingsDir);
    }

    private static void assertSingleStubWritten(Path mappingsDir) throws IOException {
        assertThat(mappingsDir).isDirectory();
        try (var files = Files.list(mappingsDir)) {
            List<Path> written = files.filter(p -> p.getFileName().toString().startsWith("moqu-")).toList();
            assertThat(written).hasSize(1);
            String content = Files.readString(written.get(0), StandardCharsets.UTF_8);
            assertThat(content).contains("\"url\":\"/users/1\"").contains("Alice");
        }
    }

    private static MoquBuildItem moquBuildItem() {
        Moqu moqu = new OpenAPIMoquImporter().parse(OPENAPI_YAML);
        return new MoquBuildItem("openapi", "yaml", moqu);
    }

    private static OutputTargetBuildItem outputTarget(Path outputDir) {
        return new OutputTargetBuildItem(outputDir, "test", false, new Properties(), Optional.empty());
    }

    private static MoquConfig moquConfig(Optional<String> outputDir) {
        return new MoquConfig() {
            @Override
            public String resourceDir() {
                return DEFAULT_RESOURCE_DIR;
            }

            @Override
            public Wiremock wiremock() {
                return () -> outputDir;
            }
        };
    }

    private static final class RecordingProducer implements BuildProducer<GeneratedResourceBuildItem> {
        private final List<GeneratedResourceBuildItem> produced = new ArrayList<>();

        @Override
        public void produce(GeneratedResourceBuildItem item) {
            produced.add(item);
        }
    }
}
