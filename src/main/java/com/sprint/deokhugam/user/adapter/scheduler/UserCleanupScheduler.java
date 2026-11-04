package com.sprint.deokhugam.user.adapter.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserCleanupScheduler {

  private final JobOperator jobOperator;
  private final Job userCleanupJob;

  @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
  public void cleanup() {
    JobParameters parameters =
        new JobParametersBuilder()
            .addLong("requestedAt", System.currentTimeMillis())
            .toJobParameters();

    try {
      jobOperator.start(userCleanupJob, parameters);
    } catch (Exception e) {
      log.error("유저 삭제 실패 ", e);
    }
  }
}
