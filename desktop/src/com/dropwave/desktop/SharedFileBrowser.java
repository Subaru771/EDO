package com.dropwave.desktop;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Local-only file management, scoped to one canonical shared root. Never follows symbolic links. */
public final class SharedFileBrowser {
    public static final class Entry {
        public final Path path;
        public final boolean directory;
        public final boolean link;
        public final long size;
        public final FileTime modified;
        Entry(Path path, boolean directory, boolean link, long size, FileTime modified) {
            this.path = path; this.directory = directory; this.link = link;
            this.size = size; this.modified = modified;
        }
    }
    private final Path root;
    public SharedFileBrowser(Path folder) throws IOException {
        root = folder.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Choose an existing folder to share.");
    }
    public Path root() { return root; }
    public boolean isRoot(Path path) { return root.equals(path.toAbsolutePath().normalize()); }
    private Path inside(Path requested, boolean mustExist) throws IOException {
        Path p = requested.toAbsolutePath().normalize();
        if (!p.startsWith(root)) throw new IOException("This item is outside your Edo sharing folder.");
        if (mustExist) {
            if (Files.isSymbolicLink(p)) throw new IOException("Symbolic links cannot be opened or modified here.");
            Path real = p.toRealPath();
            if (!real.startsWith(root)) throw new IOException("This item points outside the shared folder.");
            return real;
        }
        Path parent = p.getParent();
        if (parent == null || !parent.toRealPath().startsWith(root))
            throw new IOException("The destination is outside the shared folder.");
        return p;
    }
    public List<Entry> list(Path folder) throws IOException {
        Path dir = inside(folder,true);
        if (!Files.isDirectory(dir)) throw new IOException("Not a folder.");
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                BasicFileAttributes attrs = Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                entries.add(new Entry(p,attrs.isDirectory(),attrs.isSymbolicLink(),attrs.isRegularFile()?attrs.size():-1,attrs.lastModifiedTime()));
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> !e.directory)
            .thenComparing(e -> e.path.getFileName().toString(),String.CASE_INSENSITIVE_ORDER));
        return entries;
    }
    public Path enter(Path folder) throws IOException {
        Path path = inside(folder,true);
        if (!Files.isDirectory(path)) throw new IOException("Not a folder.");
        return path;
    }
    private String cleanName(String name) throws IOException {
        String value = name.trim();
        if (value.isBlank() || value.equals(".") || value.equals("..") || value.matches(".*[<>:\"/\\\\|?*].*"))
            throw new IOException("Enter one valid file or folder name (no paths or special characters).");
        if (value.endsWith(".") || value.endsWith(" ")) throw new IOException("Names cannot end with a dot or space.");
        String base = value.split("\\.",2)[0].toUpperCase(Locale.ROOT);
        if (base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IOException("That filename is reserved on Windows.");
        return value;
    }
    public Path newFolder(Path folder, String name) throws IOException {
        Path dir = enter(folder);
        Path dest = inside(dir.resolve(cleanName(name)),false);
        return Files.createDirectory(dest);
    }
    public Path rename(Path source, String newName) throws IOException {
        Path src = inside(source,true);
        if (src.equals(root)) throw new IOException("Cannot rename the shared root folder.");
        Path target = inside(src.resolveSibling(cleanName(newName)),false);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        return Files.move(src,target);
    }
    public Path unique(Path destination) throws IOException {
        Path target = inside(destination,false);
        if (!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) return target;
        String name = target.getFileName().toString();
        int dot=name.lastIndexOf('.');
        String stem=(dot>0)?name.substring(0,dot):name;
        String suffix=(dot>0)?name.substring(dot):"";
        for(int i=1;i<10000;i++) {
            Path next = inside(target.resolveSibling(stem+" ("+i+")"+suffix),false);
            if (!Files.exists(next,LinkOption.NOFOLLOW_LINKS)) return next;
        }
        throw new IOException("Too many items with the same name.");
    }
    /** Copy imported files or directories. External sources are allowed for local importing only. */
    public List<Path> importFiles(List<Path> sources, Path folder) throws IOException {
        Path dest = enter(folder);
        List<Path> result = new ArrayList<>();
        for (Path source : sources) {
            Path src = source.toAbsolutePath().normalize();
            if (!Files.exists(src,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(src))
                throw new IOException("Cannot import missing files or symbolic links: "+src);
            Path target = unique(dest.resolve(src.getFileName()));
            if (Files.isDirectory(src) && target.startsWith(src.toRealPath()))
                throw new IOException("Cannot copy a folder into itself.");
            copyTree(src,target);
            result.add(target);
        }
        return result;
    }
    public List<Path> paste(List<Path> sources, Path folder, boolean move) throws IOException {
        Path dest = enter(folder);
        List<Path> results = new ArrayList<>();
        for (Path source : sources) {
            Path src = inside(source,true);
            if (src.equals(root)) throw new IOException("Cannot move the shared root.");
            if (src.getParent().equals(dest) && move) { results.add(src); continue; }
            if (dest.startsWith(src)) throw new IOException("Cannot place a folder inside itself.");
            Path target = unique(dest.resolve(src.getFileName()));
            if (move) Files.move(src,target);
            else copyTree(src,target);
            results.add(target);
        }
        return results;
    }
    private static void copyTree(Path source, Path target) throws IOException {
        // Never traverse source symbolic links, including links nested inside a directory.
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) throw new IOException("Cannot copy a symbolic link.");
                Files.createDirectory(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile()) throw new IOException("Unsupported file type: "+file);
                Files.copy(file,target.resolve(source.relativize(file)),StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public void delete(List<Path> items) throws IOException {
        for (Path item : items) {
            Path path = inside(item,true);
            if (path.equals(root)) throw new IOException("Cannot delete the shared root folder.");
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir,IOException exception) throws IOException {
                    if (exception != null) throw exception;
                    Files.delete(dir);return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
