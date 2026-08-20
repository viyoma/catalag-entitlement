package com.lgi.catalog.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CURRENT STATE (legacy). Artwork blobs read straight off the NFS mount.
 *
 * AWS Transform target: Amazon S3. The filesystem Path resolution below becomes
 * an S3 key + presigned URL (or CloudFront URI). This is the "NFS -> object
 * storage" move called out in the target stack.
 */
public class ArtworkStore {

    private final Path root;

    public ArtworkStore(String nfsRoot) {
        this.root = Paths.get(nfsRoot);
    }

    /**
     * Resolve the on-disk artwork path for a title. Falls back to a default
     * placeholder file if the title-specific asset is missing on the mount.
     */
    public String artworkPath(String titleId) {
        Path p = root.resolve(titleId).resolve("poster.jpg");
        if (Files.exists(p)) {
            return p.toUri().toString();
        }
        return root.resolve("_default").resolve("poster.jpg").toUri().toString();
    }
}
