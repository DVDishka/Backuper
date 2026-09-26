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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

public class ScheduleManager {

    private org.quartz.Scheduler quartzScheduler;
    private AsyncExecutor mainExecutorService;
    private volatile boolean stopping = true;

    public void init() {
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

        this.mainExecutorService = new AsyncExecutor();
        this.stopping = false;
    }

    public ScheduledTask runGlobalRegionDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (isStopping()) throw new RejectedExecutionException("Backuper is stopping");
        Runnable guarded = () -> { if (!isStopping()) task.run(); };
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (scheduledTask) -> guarded.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, guarded, delayTicks);
        }
        return null;
    }

    public ScheduledTask runGlobalRegionRepeatingTask(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isStopping()) throw new RejectedExecutionException("Backuper is stopping");
        Runnable guarded = () -> { if (!isStopping()) task.run(); };
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (scheduledTask) -> guarded.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, guarded, delayTicks, periodTicks);
        }
        return null;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        if (isStopping()) throw new RejectedExecutionException("Backuper is stopping");
        return mainExecutorService.submit(task);
    }

    public boolean isStopping() {
        return stopping || mainExecutorService == null || mainExecutorService.isStopping();
    }

    public boolean destroy(Plugin plugin) {
        stopping = true;
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
            if (this.quartzScheduler != null) this.quartzScheduler.shutdown(false);
        } catch (SchedulerException e) {
            Backuper.getInstance().getLogManager().warn("Failed to shutdown Quartz Scheduler");
            Backuper.getInstance().getLogManager().warn(e);
        }
        return mainExecutorService == null || mainExecutorService.stop(Duration.ofMinutes(3));
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
            jobDetail.getJobDataMap().put("backuperScheduler", this);
            jobDetail.getJobDataMap().put("backuperAutoBackup", Backuper.getInstance().getAutoBackupScheduleManager());
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
