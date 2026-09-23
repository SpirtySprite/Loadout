package com.kirugoldzzzz.loadout;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LanguageFilesTest {

    private static final Path LANG = Path.of("src", "main", "resources", "lang");

    private static Set<String> keys(String file) throws Exception {
        try (Reader reader = Files.newBufferedReader(LANG.resolve(file), StandardCharsets.UTF_8)) {
            return new TreeSet<>(YamlConfiguration.loadConfiguration(reader).getKeys(true));
        }
    }

    @Test
    void everyLanguageHasTheSameMessageKeys() throws Exception {
        assertEquals(keys("messages_fr.yml"), keys("messages_en.yml"));
    }

    @Test
    void englishInterfaceDictionaryIsAFlatMapOfNonBlankValues() throws Exception {
        try (Reader reader = Files.newBufferedReader(LANG.resolve("en.yml"), StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            assertNotNull(loaded);
            Map<?, ?> map = (Map<?, ?>) loaded;
            assertFalse(map.isEmpty());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                assertFalse(String.valueOf(entry.getValue()).isBlank(), "blank translation for " + entry.getKey());
            }
        }
    }
}
