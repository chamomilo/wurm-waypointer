# Render diagnostics

Waypointer 1.6.17 retains the render and map diagnostics introduced in 1.6.15–1.6.16. Logging runs on a background worker; render hooks record counters and timestamps without writing files.

## Files and measurements

Logs are stored outside the installed mod folder, under `WurmLauncher/wurm-waypointer-data/waypointer-diagnostics-*.log`. Rotation keeps three bounded files. Preserve them soon after a reported event so later runs do not replace the evidence. `/wp perf` displays the current performance counters in the client.

- Map preparation, contour rasterization and dynamic texture upload counters measure their respective CPU-side operations.
- Native legacy/modern render queues are measured separately from `LwjglClient.update`, which includes window/frame output and event processing.
- Native queue/output samples of at least 250 ms are collected with UTC event times. Multiple samples can be combined in one log entry; the entry records the count and latest event.
- A once-per-second background sample records newly observed long GC pauses with their start/end times. Periodic summaries include heap use, GC counts and total collection time.

The measurements are deliberately bounded. Several pauses between samples can be coalesced, and the GC management API exposes the collector's latest event rather than a complete tracing history. Absence of a logged event does not prove that the JVM or GPU never stalled.

## Reporting a freeze or black screen

1. Record the time of the event, your timezone and its approximate duration. Describe whether the screen went black, an older scene reappeared, or only movement stopped.
2. Record whether the mini-map was enabled, its GROUND/CAVE layer, contour interval and whether the full map was open. Note any reconnect or layer/contour change near the event.
3. Preserve the Waypointer diagnostic logs and normal Wurm client logs covering that time. Include the Waypointer version.
4. If practical, compare a session with the mini-map disabled using otherwise similar settings. This is useful evidence of correlation, not proof of the underlying cause.

## Interpretation and current limits

Small map-preparation counters do not exclude native GPU or frame-output stalls. Queue timings cover the client's shared rendering work, including the world and other mods; they cannot attribute a delay to Waypointer alone. CPU elapsed time can also include scheduling or GC pauses. A thread snapshot taken after a freeze does not identify the call that caused it.

Investigation of 1.6.15 established a 1.29-second full GC pause and independent connection failures with automatic reconnects. Later reported freezes occurred without a new full GC. The changes retain the unchanged published map and terrain index across reconnects, consume completed native texture requests on the worker, skip obsolete queued jobs, reuse dynamic texture storage and upload at the HUD queue boundary. These address established unnecessary work; they do not establish that every black screen or connection failure is fixed.

These diagnostics do not request a heap dump, force GC, change driver settings or modify the game installation.
