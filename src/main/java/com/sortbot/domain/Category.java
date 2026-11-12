package com.sortbot.domain;

/** Categories a file can be assigned to. The folder name is where files of this category normally go. */
public enum Category {
    LECTURE_NOTES("Lecture Notes"), ASSIGNMENTS("Assignments"), RECEIPTS("Receipts"), DOCUMENTS("Documents"),
    PHOTOS("Photos"), SCREENSHOTS("Screenshots"), INSTALLERS("Installers"), ARCHIVES("Archives"),
    CODE("Code"), OTHER("Other"), UNCLASSIFIED("Unclassified");

    private final String folderName;

    Category(String folderName) {
        this.folderName = folderName;
    }

    public String folderName() {
        return folderName;
    }

    /** Case-insensitive lookup that accepts the enum name or the folder name; null if unknown. */
    public static Category fromText(String text) {
        if (text == null) return null;
        String t = text.trim();
        for (Category c : values()) {
            if (c.name().equalsIgnoreCase(t) || c.folderName.equalsIgnoreCase(t)) return c;
        }
        return null;
    }
}
