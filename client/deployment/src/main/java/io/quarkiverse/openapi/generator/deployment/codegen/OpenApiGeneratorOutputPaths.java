package io.quarkiverse.openapi.generator.deployment.codegen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class OpenApiGeneratorOutputPaths {

    public static final String OPENAPI_PATH = "open-api";
    public static final String STREAM_PATH = "open-api-stream";

    private static final Collection<String> rootPaths = List.of(STREAM_PATH);

    public static Path getRelativePath(Path path) {
        List<String> paths = new ArrayList<>();
        Path currentPath = path;
        while (currentPath != null && currentPath.getFileName() != null) {
            if (rootPaths.contains(currentPath.getFileName().toString())) {
                Iterator<String> iter = paths.iterator();
                Path result = Path.of(iter.next());
                while (iter.hasNext()) {
                    result = result.resolve(iter.next());
                }
                return result;
            } else {
                paths.add(0, currentPath.getFileName().toString());
                currentPath = currentPath.getParent();
            }
        }
        return path.getFileName();
    }
}
