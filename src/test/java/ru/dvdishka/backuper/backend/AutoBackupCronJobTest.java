package ru.dvdishka.backuper.backend;

import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import ru.dvdishka.backuper.backend.autobackup.AutoBackupCronJob;
import ru.dvdishka.backuper.backend.autobackup.AutoBackupScheduleManager;

import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutoBackupCronJobTest {
    @Test
    void callbackOfStoppedGenerationCannotStartAnotherBackup() {
        var scheduler = mock(ScheduleManager.class);
        var autoBackup = mock(AutoBackupScheduleManager.class);
        when(scheduler.runAsync(any())).thenThrow(new RejectedExecutionException("Stopped generation"));
        var detail = JobBuilder.newJob(AutoBackupCronJob.class).build();
        detail.getJobDataMap().put("backuperScheduler", scheduler);
        detail.getJobDataMap().put("backuperAutoBackup", autoBackup);
        var context = mock(JobExecutionContext.class);
        when(context.getJobDetail()).thenReturn(detail);
        new AutoBackupCronJob().execute(context);
        verify(scheduler).runAsync(any());
        verifyNoInteractions(autoBackup);
    }
}
