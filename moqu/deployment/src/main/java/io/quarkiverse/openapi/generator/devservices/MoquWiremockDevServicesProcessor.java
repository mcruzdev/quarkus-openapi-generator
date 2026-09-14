package io.quarkiverse.openapi.generator.devservices;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.openapi.generator.items.MoquBuildItem;
import io.quarkiverse.openapi.generator.moqu.MoquConfig;
import io.quarkiverse.openapi.moqu.marshall.ObjectMapperFactory;
import io.quarkiverse.openapi.moqu.wiremock.mapper.WiremockMapper;
import io.quarkiverse.openapi.moqu.wiremock.model.WiremockMapping;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class MoquWiremockDevServicesProcessor {

    static final String WIREMOCK_FILES_MAPPING_PROPERTY = "quarkus.wiremock.devservices.files-mapping";
    static final String DEFAULT_WIREMOCK_FILES_MAPPING = "src/test/resources";

    private static final String WIREMOCK_CAPABILITY = "io.quarkiverse.wiremock";
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String DEFAULT_TARGET_SUBDIR = "moqu-wiremock";
    private static final String MAPPINGS_DIR = "mappings";
    private static final String FILE_PREFIX = "moqu-";

    @BuildStep
    void generateWiremockStubs(List<MoquBuildItem> mocks, OutputTargetBuildItem outputTarget,
            Capabilities capabilities, MoquConfig moquConfig,
            BuildProducer<GeneratedResourceBuildItem> generatedResources) {

        if (capabilities.isMissing(WIREMOCK_CAPABILITY)) {
            return;
        }

        String wiremockFilesMapping = ConfigProvider.getConfig()
                .getOptionalValue(WIREMOCK_FILES_MAPPING_PROPERTY, String.class)
                .orElse(DEFAULT_WIREMOCK_FILES_MAPPING);

        Path mappingsDir = resolveMappingsDir(outputTarget, moquConfig.wiremock().outputDir(), wiremockFilesMapping);

        clearMoquGeneratedFiles(mappingsDir);

        if (mocks.isEmpty()) {
            return;
        }

        WiremockMapper wiremockMapper = new WiremockMapper();
        ObjectMapper objMapper = ObjectMapperFactory.getInstance();

        try {
            Files.createDirectories(mappingsDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (MoquBuildItem mock : mocks) {
            List<WiremockMapping> wiremockMappings = wiremockMapper.map(mock.getMoqu());

            int index = 0;
            for (WiremockMapping mapping : wiremockMappings) {
                try {
                    byte[] content = objMapper.writeValueAsBytes(mapping);
                    String filename = String.format("%s%s-%s-%d.json", FILE_PREFIX, mock.getFilename(),
                            mock.getExtension(), index++);

                    Files.write(mappingsDir.resolve(filename), content);
                    generatedResources.produce(new GeneratedResourceBuildItem(
                            String.format("%s/%s/%s", DEFAULT_TARGET_SUBDIR, MAPPINGS_DIR, filename), content));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    static Path resolveMappingsDir(OutputTargetBuildItem outputTarget, Optional<String> explicitOutputDir,
            String wiremockFilesMapping) {

        if (explicitOutputDir.isPresent()) {
            return resolveAgainstProjectRoot(outputTarget, explicitOutputDir.get()).resolve(MAPPINGS_DIR);
        }

        boolean customized = !DEFAULT_WIREMOCK_FILES_MAPPING.equals(wiremockFilesMapping);
        boolean classpathMode = wiremockFilesMapping.startsWith(CLASSPATH_PREFIX);
        if (customized && !classpathMode) {
            return resolveAgainstProjectRoot(outputTarget, wiremockFilesMapping).resolve(MAPPINGS_DIR);
        }

        return outputTarget.getOutputDirectory().resolve(DEFAULT_TARGET_SUBDIR).resolve(MAPPINGS_DIR);
    }

    private static Path resolveAgainstProjectRoot(OutputTargetBuildItem outputTarget, String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path;
        }
        return outputTarget.getOutputDirectory().getParent().resolve(path);
    }

    private void clearMoquGeneratedFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> existing = Files.list(dir)) {
            for (Path path : existing.toList()) {
                if (path.getFileName().toString().startsWith(FILE_PREFIX)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
