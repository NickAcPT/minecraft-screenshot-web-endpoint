package io.github.nickacpt.minecraftscreenshotendpoint;

import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry;
import org.jetbrains.annotations.Nullable;

public interface ScreenshotTaskHolder {
    @Nullable
    ScreenshotQueueEntry mse$getCurrentScreenshotEntryTask();

    void mse$setCurrentScreenshotEntryTask(ScreenshotQueueEntry task);
}
