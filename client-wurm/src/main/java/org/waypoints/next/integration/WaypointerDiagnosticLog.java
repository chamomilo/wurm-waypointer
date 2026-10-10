package org.waypoints.next.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.waypoints.next.render.WaypointRenderProfiler;

/** Bounded persistent diagnostics survive a launcher restart that truncates client.log. */
public final class WaypointerDiagnosticLog {
    private static FileHandler handler;
    private static final Map<String, Long> GC_COUNTS = new HashMap<>();
    private static List<GarbageCollectorMXBean> collectors;
    private static long vmStartMillis;
    private static int ticks;
    private WaypointerDiagnosticLog() { }
    public static synchronized void start(Path directory, String version) {
        if(handler!=null)return;
        Logger logger=Logger.getLogger("WurmWaypointer");
        try {
            Files.createDirectories(directory);
            handler=new FileHandler(directory.resolve("waypointer-diagnostics-%g.log").toString(),
                    262144,3,true);
            handler.setFormatter(new SimpleFormatter());handler.setLevel(Level.INFO);handler.setEncoding("UTF-8");
            logger.setLevel(Level.INFO);
            logger.addHandler(handler);
            collectors=ManagementFactory.getGarbageCollectorMXBeans();
            vmStartMillis=ManagementFactory.getRuntimeMXBean().getStartTime();
            logger.info("Waypointer "+version+" render diagnostics started; Java="+System.getProperty("java.version"));
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread=new Thread(runnable,"wurm-waypointer-diagnostics");
                thread.setDaemon(true);return thread;
            }).scheduleWithFixedDelay(() -> sample(logger),1,1,TimeUnit.SECONDS);
        } catch(Exception failure) {
            logger.log(Level.WARNING,"Unable to open Waypointer diagnostics",failure);
        }
    }

    private static void sample(Logger logger) {
        try {
            String queues=WaypointRenderProfiler.pollSlowNativeQueues();
            String output=WaypointRenderProfiler.pollSlowFrameOutput();
            if(queues!=null)logger.info(queues);
            if(output!=null)logger.info(output);
            for(GarbageCollectorMXBean collector:collectors) {
                long count=collector.getCollectionCount();
                Long previous=GC_COUNTS.put(collector.getName(),count);
                if(previous!=null&&previous.longValue()==count)continue;
                if(collector instanceof com.sun.management.GarbageCollectorMXBean) {
                    com.sun.management.GcInfo info=((com.sun.management.GarbageCollectorMXBean)collector).getLastGcInfo();
                    if(info!=null&&info.getDuration()>=250)logger.info("Long GC: collector="+collector.getName()
                            +", durationMs="+info.getDuration()+", startedAtUtc="
                            +java.time.Instant.ofEpochMilli(vmStartMillis+info.getStartTime())
                            +", endedAtUtc="+java.time.Instant.ofEpochMilli(vmStartMillis+info.getEndTime())
                            +", count="+count);
                }
            }
            if(++ticks%30==0) {
                logger.info(WaypointRenderProfiler.summary(false));
                MemoryUsage heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                StringBuilder memory=new StringBuilder("JVM memory: heapUsedMiB=").append(heap.getUsed()/1048576)
                        .append(", heapMaxMiB=").append(heap.getMax()/1048576);
                for(GarbageCollectorMXBean collector:collectors)memory.append("; ").append(collector.getName())
                        .append(" count=").append(collector.getCollectionCount())
                        .append(", totalPauseMs=").append(collector.getCollectionTime());
                logger.info(memory.toString());
            }
        } catch(RuntimeException failure) {
            logger.log(Level.FINE,"Render diagnostic sample unavailable",failure);
        }
    }
}
