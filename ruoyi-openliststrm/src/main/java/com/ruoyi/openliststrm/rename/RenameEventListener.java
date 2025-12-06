package com.ruoyi.openliststrm.rename;

import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.nio.file.Path;

/**
 * Callback interface for rename events emitted by FileMonitorService.
 */
public interface RenameEventListener {
    /**
     * Called after a file has been copied/renamed to the destination.
     *
     * @param original absolute path of the original file
     * @param dest absolute path of the destination file
     * @param info parsed MediaInfo for the file
     * @param mediaType "movie" or "tv"
     */
    void onRename(Path original, Path dest, MediaInfo info, String mediaType);

    /**
     * Called when a file is NOT processed because of a failure (e.g. tmdbId not found).
     * Implementations should persist failure details if needed. The file will NOT be copied.
     *
     * @param original absolute path of the original file
     * @param info parsed MediaInfo (may be partial)
     * @param mediaType "movie" or "tv"
     * @param reason short reason message
     */
    void onRenameFailed(Path original, MediaInfo info, String mediaType, String reason);
}
