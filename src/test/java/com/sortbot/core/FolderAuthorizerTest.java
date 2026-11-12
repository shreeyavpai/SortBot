package com.sortbot.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderAuthorizerTest {
    @TempDir Path tmp;

    @Test
    void addedFolderAndItsChildrenAreAuthorized() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(d);
        assertTrue(a.isAuthorized(d));
        assertTrue(a.isAuthorized(d.resolve("sub/new.txt")), "paths that do not exist yet must still be checked");
        assertEquals(1, a.getAllowedFolders().size());
    }

    @Test
    void otherFoldersAreNotAuthorized() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        Path other = Files.createDirectory(tmp.resolve("Other"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(d);
        assertFalse(a.isAuthorized(other));
        assertFalse(a.isAuthorized(null));
        assertFalse(new FolderAuthorizer().isAuthorized(d), "empty allow-list authorizes nothing");
    }

    @Test
    void dotDotCannotEscapeAnAuthorizedFolder() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(d);
        assertFalse(a.isAuthorized(d.resolve("../secret.txt")));
        assertFalse(a.isAuthorized(d.resolve("sub/../../secret.txt")));
    }

    @Test
    void symlinkCannotEscapeAnAuthorizedFolder() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        Path outside = Files.createDirectory(tmp.resolve("Outside"));
        Path link = d.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Symlinks need extra rights on some systems; nothing to test there.
        }
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(d);
        assertFalse(a.isAuthorized(link.resolve("secret.txt")));
    }

    @Test
    void rejectsMissingFolder() {
        assertThrows(FolderRejectedException.class, () -> new FolderAuthorizer().add(tmp.resolve("nope")));
    }

    @Test
    void rejectsAFileAndNull() throws IOException {
        Path f = Files.writeString(tmp.resolve("a.txt"), "x");
        FolderAuthorizer a = new FolderAuthorizer();
        assertThrows(FolderRejectedException.class, () -> a.add(f));
        assertThrows(FolderRejectedException.class, () -> a.add(null));
    }

    @Test
    void rejectsFilesystemRootAndHomeFolder() {
        FolderAuthorizer a = new FolderAuthorizer();
        assertThrows(FolderRejectedException.class, () -> a.add(tmp.getRoot()));
        assertThrows(FolderRejectedException.class, () -> a.add(Path.of(System.getProperty("user.home"))));
    }

    @Test
    void rejectsDuplicateFolder() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(d);
        assertThrows(FolderRejectedException.class, () -> a.add(d));
    }

    @Test
    void removeStopsAuthorizingAndReportsUnknown() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        FolderAuthorizer a = new FolderAuthorizer();
        a.add(d);
        assertTrue(a.remove(d));
        assertFalse(a.isAuthorized(d));
        assertFalse(a.remove(d), "removing twice reports false");
    }

    @Test
    void allowListSurvivesARestart() throws IOException {
        Path d = Files.createDirectory(tmp.resolve("Downloads"));
        Path store = tmp.resolve("state/folders.txt");
        new FolderAuthorizer(store).add(d);
        FolderAuthorizer reloaded = new FolderAuthorizer(store);
        assertTrue(reloaded.isAuthorized(d));
        reloaded.remove(d);
        assertFalse(new FolderAuthorizer(store).isAuthorized(d));
    }
}
