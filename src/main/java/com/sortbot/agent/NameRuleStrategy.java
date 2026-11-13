package com.sortbot.agent;

import com.sortbot.domain.Category;
import com.sortbot.domain.FileRecord;
import com.sortbot.domain.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic classification from file extensions and obvious name patterns. No AI involved. */
public class NameRuleStrategy implements ClassificationStrategy {
    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "heic", "webp", "bmp");
    private static final Set<String> INSTALLERS = Set.of("exe", "msi", "dmg", "pkg", "apk", "deb");
    private static final Set<String> ARCHIVES = Set.of("zip", "rar", "7z", "tar", "gz");
    private static final Set<String> CODE = Set.of("java", "py", "js", "ts", "c", "cpp", "h", "cs", "go", "rs", "html", "css", "sql", "ipynb", "sh");
    private static final Pattern RECEIPT = Pattern.compile("receipt|invoice|payment|order[ _-]?confirmation", Pattern.CASE_INSENSITIVE);
    private static final Pattern LECTURE = Pattern.compile("\\blec(ture)?[ _-]?\\d*|slides?|week[ _-]?\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGNMENT = Pattern.compile("assignment|homework|\\bhw[ _-]?\\d*|\\blab[ _-]?\\d+|\\bproject", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DOCS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "txt", "md", "odt");

    @Override public String name() { return "name-rules"; }

    @Override
    public List<FileRecord> classify(List<FileRecord> pending, Inventory inventory) {
        List<FileRecord> unresolved = new ArrayList<>();
        for (FileRecord r : pending) {
            if (!apply(r)) unresolved.add(r);
        }
        return unresolved;
    }

    private boolean apply(FileRecord r) {
        String name = r.getName();
        String ext = r.getExtension();
        if (IMAGES.contains(ext)) {
            boolean shot = name.toLowerCase().contains("screenshot") || name.toLowerCase().contains("screen shot");
            r.classify(shot ? Category.SCREENSHOTS : Category.PHOTOS, 0.9, "Image file" + (shot ? " named like a screenshot" : ""));
            return true;
        }
        if (INSTALLERS.contains(ext)) { r.classify(Category.INSTALLERS, 0.95, "Installer file type ." + ext); return true; }
        if (ARCHIVES.contains(ext)) { r.classify(Category.ARCHIVES, 0.9, "Archive file type ." + ext); return true; }
        if (CODE.contains(ext)) { r.classify(Category.CODE, 0.9, "Source or code file type ." + ext); return true; }
        if (DOCS.contains(ext)) {
            if (RECEIPT.matcher(name).find()) { r.classify(Category.RECEIPTS, 0.8, "Name looks like a receipt or invoice"); return true; }
            if (ASSIGNMENT.matcher(name).find()) { r.classify(Category.ASSIGNMENTS, 0.75, "Name looks like an assignment"); return true; }
            if (LECTURE.matcher(name).find()) { r.classify(Category.LECTURE_NOTES, 0.75, "Name looks like lecture material"); return true; }
        }
        return false;
    }
}
