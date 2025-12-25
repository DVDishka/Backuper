package ru.dvdishka.backuper.backend;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.Utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleManager {

    private org.quartz.Scheduler quartzScheduler;
    private final ExecutorService mainExecutorService;

    public ScheduleManager() {
        try {
            if (DirectSchedulerFactory.getInstance().getAllSchedulers().stream().noneMatch(scheduler -> {
                try {
                    return scheduler.getSchedulerName().equals("backuper");
                } catch (SchedulerException e) {
                    throw new RuntimeException(e);
                }
            })) {
                DirectSchedulerFactory.getInstance().createScheduler("backuper", "main", new SimpleThreadPool(1, 5), new RAMJobStore());
            }

            this.quartzScheduler = DirectSchedulerFactory.getInstance().getScheduler("backuper");
            this.quartzScheduler.start();
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to initialize Quartz Scheduler, automatic backups will not work");
            Backuper.getInstance().getLogManager().warn(e);
        }

        if (Backuper.getInstance().getConfigManager().getServerConfig().getThreadNumber() == 0) {
            this.mainExecutorService = Executors.newWorkStealingPool();
        } else {
            this.mainExecutorService = Executors.newWorkStealingPool(Backuper.getInstance().getConfigManager().getServerConfig().getThreadNumber());
        }
    }

    public ScheduledTask runGlobalRegionDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
        return null;
    }

    public ScheduledTask runGlobalRegionRepeatingTask(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (scheduledTask) -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
        }
        return null;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, mainExecutorService);
    }

    public void destroy(Plugin plugin) {
        try {
            if (Utils.isFolia) {
                Bukkit.getAsyncScheduler().cancelTasks(plugin);
                Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            } else {
                Bukkit.getScheduler().cancelTasks(plugin);
            }
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to cancel scheduler tasks");
            Backuper.getInstance().getLogManager().warn(e);
        }
        try {
            this.quartzScheduler.shutdown(false);
        } catch (SchedulerException e) {
            Backuper.getInstance().getLogManager().warn("Failed to shutdown Quartz Scheduler");
            Backuper.getInstance().getLogManager().warn(e);
        }
        this.mainExecutorService.shutdownNow();
    }

    /***
     * Doesn't guarantee being executed sync or async
     * @param job
     * @param jobName
     * @param jobGroup
     * @param cronExpression
     */
    public CronTrigger runCronScheduledJob(Class<? extends Job> job, String jobName, String jobGroup, CronExpression cronExpression) {
        try {

            JobDetail jobDetail = JobBuilder.newJob(job).withIdentity(jobName, jobGroup).build();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobName, jobGroup)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .forJob(jobDetail)
                    .build();
            quartzScheduler.scheduleJob(jobDetail, trigger);
            return trigger;

        } catch (SchedulerException e) {
            Backuper.getInstance().getLogManager().warn("Failed to run Cron Scheduled Job");
            Backuper.getInstance().getLogManager().warn(e);
            return null;
        }
    }
}
