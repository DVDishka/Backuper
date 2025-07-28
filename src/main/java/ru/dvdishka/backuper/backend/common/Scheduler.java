package ru.dvdishka.backuper.backend.common;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.util.concurrent.TimeUnit;

public class Scheduler {

    private org.quartz.Scheduler quartzScheduler;

    private static Scheduler instance;

    public static Scheduler getInstance() {
        if (instance == null) {
            instance = new Scheduler();
            try {
                if (DirectSchedulerFactory.getInstance().getAllSchedulers().stream().noneMatch(scheduler -> {
                    try {
                        return scheduler.getSchedulerName().equals("main");
                    } catch (SchedulerException e) {
                        throw new RuntimeException(e);
                    }
                })) {
                    DirectSchedulerFactory.getInstance().createScheduler("main", "main", new SimpleThreadPool(1, 5), new RAMJobStore());
                }

                instance.quartzScheduler = DirectSchedulerFactory.getInstance().getScheduler("main");
                instance.quartzScheduler.start();
            } catch (Exception e) {
                Logger.getLogger().warn("Failed to initialize Quartz Scheduler, automatic backups will not work");
                Logger.getLogger().warn(Scheduler.class, e);
            }
        }
        return instance;
    }

    public ScheduledTask runSync(Plugin plugin, Runnable task) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().run(plugin, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
        return null;
    }

    public ScheduledTask runSyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
        return null;
    }

    public ScheduledTask runSyncRepeatingTask(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (scheduledTask) -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
        }
        return null;
    }

    public ScheduledTask runAsync(Plugin plugin, Runnable task) {
        if (Utils.isFolia) {
            return Bukkit.getAsyncScheduler().runNow(plugin, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public ScheduledTask runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (Utils.isFolia) {
            return Bukkit.getAsyncScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), delayTicks * 20, TimeUnit.SECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
        return null;
    }

    public void destroy(Plugin plugin) {
        if (Utils.isFolia) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
        try {
            this.quartzScheduler.shutdown(false);
        } catch (SchedulerException e) {
            Logger.getLogger().warn("Failed to shutdown Quartz Scheduler");
            Logger.getLogger().warn(Scheduler.class, e);
        }
        instance = null;
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
            Logger.getLogger().warn("Failed to run Cron Scheduled Job");
            Logger.getLogger().warn(Scheduler.class, e);
            return null;
        }
    }
}
