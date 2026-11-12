package com.sortbot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlanTest {
    final Path root = Path.of("root").toAbsolutePath();

    private MoveFileAction move(String file, String targetFolder) {
        return new MoveFileAction(null, root.resolve(file), root.resolve(targetFolder).resolve(file));
    }

    @Test
    void editChangesWhereAFileWillGo() {
        Plan p = new Plan();
        MoveFileAction m = move("a.txt", "Docs");
        p.add(m);
        p.edit(m.id(), root.resolve("Other/a.txt"));
        assertEquals(root.resolve("Other/a.txt"), m.target());
    }

    @Test
    void editRejectsUnknownIdsAndNonMoveActions() {
        Plan p = new Plan();
        CreateFolderAction c = new CreateFolderAction(null, root.resolve("Docs"));
        p.add(c);
        assertThrows(IllegalArgumentException.class, () -> p.edit("nope", root));
        assertThrows(IllegalArgumentException.class, () -> p.edit(c.id(), root));
    }

    @Test
    void removeDropsOneActionAndReportsUnknownIds() {
        Plan p = new Plan();
        MoveFileAction m = move("a.txt", "Docs");
        p.add(m);
        assertFalse(p.remove("nope"));
        assertTrue(p.remove(m.id()));
        assertEquals(0, p.size());
    }

    @Test
    void renameFolderUpdatesFolderAndMovesInside() {
        Plan p = new Plan();
        CreateFolderAction c = new CreateFolderAction(null, root.resolve("Receipts"));
        MoveFileAction m = move("r.pdf", "Receipts");
        p.add(c);
        p.add(m);
        p.renameFolder(root.resolve("Receipts"), root.resolve("Finance/Receipts"));
        assertEquals(root.resolve("Finance/Receipts"), c.target());
        assertEquals(root.resolve("Finance/Receipts/r.pdf"), m.target());
    }

    @Test
    void approveDropsFoldersThatNoMoveUsesAnymore() {
        Plan p = new Plan();
        p.add(new CreateFolderAction(null, root.resolve("Used")));
        p.add(new CreateFolderAction(null, root.resolve("Unused")));
        p.add(move("a.txt", "Used"));
        p.approve();
        assertTrue(p.isApproved());
        assertEquals(2, p.size());
    }

    @Test
    void anApprovedPlanIsLocked() {
        Plan p = new Plan();
        MoveFileAction m = move("a.txt", "Docs");
        p.add(m);
        p.approve();
        assertThrows(IllegalStateException.class, () -> p.add(move("b.txt", "Docs")));
        assertThrows(IllegalStateException.class, () -> p.remove(m.id()));
        assertThrows(IllegalStateException.class, () -> p.edit(m.id(), root));
    }
}
