package me.gadse.antiseedcracker.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
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
        AtomicReference<Object> scheduledTaskRef = new AtomicReference<>();
        CancellableTask cancellableTask = new CancellableTask() {
            @Override
            public void cancel() {
                Object scheduledTask = scheduledTaskRef.get();
                if (scheduledTask != null) {
                    try {
                        Method cancelMethod = scheduledTask.getClass().getMethod("cancel");
                        cancelMethod.invoke(scheduledTask);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("Failed to cancel Folia scheduled task: cancel method not found", e);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to cancel Folia scheduled task: invocation error", e);
                    }
                }
            }
        };

        try {
            // Get the RegionScheduler via reflection: Bukkit.getRegionScheduler()
            Method getRegionSchedulerMethod = Bukkit.class.getMethod("getRegionScheduler");
            Object regionScheduler = getRegionSchedulerMethod.invoke(null);

            // Get the runAtFixedRate method and invoke it
            // Signature: ScheduledTask runAtFixedRate(Plugin plugin, Location location, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks)
            Method runAtFixedRateMethod = regionScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            Object scheduledTask = runAtFixedRateMethod.invoke(regionScheduler, plugin, location, (Consumer<?>) t -> task.accept(cancellableTask), initialDelay, period);
            scheduledTaskRef.set(scheduledTask);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to schedule Folia task: method not found - " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to schedule Folia task: invocation error - " + e.getMessage(), e);
        }

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
