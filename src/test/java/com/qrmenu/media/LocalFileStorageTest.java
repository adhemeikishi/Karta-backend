package com.qrmenu.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitaire (sans Spring) du stockage disque : aller-retour et refus du
 * path traversal.
 */
class LocalFileStorageTest {

    private static final byte[] CONTENT = "%PDF-1.4 contenu".getBytes(StandardCharsets.UTF_8);

    @Test
    void storeReadDeleteRoundTrip(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString());

        storage.store("r1/a1.pdf", CONTENT);

        assertThat(storage.exists("r1/a1.pdf")).isTrue();
        assertThat(storage.read("r1/a1.pdf")).isEqualTo(CONTENT);

        storage.delete("r1/a1.pdf");
        assertThat(storage.exists("r1/a1.pdf")).isFalse();
    }

    @Test
    void deleteIsSilentWhenFileAbsent(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString());

        storage.delete("r1/absent.pdf"); // ne doit pas lever
    }

    @Test
    void rejectsPathTraversalKeys(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.toString());

        assertThatThrownBy(() -> storage.read("../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store("../evil.pdf", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.exists("r1/../../secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
