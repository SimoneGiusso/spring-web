package org.simonegiusso.springweb.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

enum FileUtils {
    ;

    private static final String ASSERTION_FILES_ROOT = "assertion-files/";

    static String load(String fileName, Map<String, ?> placeholders) {
        String json = read(ASSERTION_FILES_ROOT + fileName);
        for (Map.Entry<String, ?> placeholder : placeholders.entrySet()) {
            json = json.replace("${" + placeholder.getKey() + "}", String.valueOf(placeholder.getValue()));
        }
        return json;
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read fixture " + path, exception);
        }
    }
}
