package org.simonegiusso.springweb.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

public final class JsonFixture {

    private static final String FIXTURE_ROOT = "fixtures/";

    private JsonFixture() {}

    public static String load(String fileName) {
        return load(fileName, Map.of());
    }

    public static String load(String fileName, Map<String, ?> placeholders) {
        String json = read(FIXTURE_ROOT + fileName);
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
