package me.gadse.antiseedcracker.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Utility class to provide scheduler compatibility between Folia and Bukkit/Paper servers.
 */
public class FoliaScheduler {

    private static final boolean FOLIA;

    static {
        boolean isFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
        FOLIA = isFolia;
    }

    /**
     * Checks if the server is running Folia.
     *
     * @return true if running on Folia, false otherwise
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Schedules a repeating task at the given location.
     * On Folia, this uses the region scheduler for the location's region.
     * On Bukkit/Paper, this uses the global scheduler.
     *
     * @param plugin       the plugin scheduling the task
     * @param location     the location determining the region (Folia) or ignored (Bukkit)
     * @param task         the task to execute, receives a CancellableTask that can be cancelled
     * @param initialDelay the initial delay in ticks
     * @param period       the period between executions in ticks
     * @return a CancellableTask that can be used to cancel the task
     */
    public static CancellableTask runAtLocationTimer(Plugin plugin, Location location, Consumer<CancellableTask> task, long initialDelay, long period) {
        if (FOLIA) {
            return runAtLocationTimerFolia(plugin, location, task, initialDelay, period);
        } else {
            return runAtLocationTimerBukkit(plugin, task, initialDelay, period);
        }
    }

    private static CancellableTask runAtLocationTimerFolia(Plugin plugin, Location location, Consumer<CancellableTask> task, long initialDelay, long period) {
        AtomicReference<io.papermc.paper.threadedregions.scheduler.ScheduledTask> scheduledTaskRef = new AtomicReference<>();
        CancellableTask cancellableTask = new CancellableTask() {
            @Override
            public void cancel() {
                io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = scheduledTaskRef.get();
                if (scheduledTask != null) {
                    scheduledTask.cancel();
                }
            }
        };

        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(
                plugin,
                location,
                t -> task.accept(cancellableTask),
                initialDelay,
                period
        );
        scheduledTaskRef.set(scheduledTask);

        return cancellableTask;
    }

    private static CancellableTask runAtLocationTimerBukkit(Plugin plugin, Consumer<CancellableTask> task, long initialDelay, long period) {
        AtomicReference<org.bukkit.scheduler.BukkitTask> bukkitTaskRef = new AtomicReference<>();
        CancellableTask cancellableTask = new CancellableTask() {
            @Override
            public void cancel() {
                org.bukkit.scheduler.BukkitTask bukkitTask = bukkitTaskRef.get();
                if (bukkitTask != null) {
                    bukkitTask.cancel();
                }
            }
        };

        org.bukkit.scheduler.BukkitTask bukkitTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> task.accept(cancellableTask),
                initialDelay,
                period
        );
        bukkitTaskRef.set(bukkitTask);

        return cancellableTask;
    }

    /**
     * Represents a task that can be cancelled.
     */
    public interface CancellableTask {
        /**
         * Cancels this task.
         */
        void cancel();
    }
}
