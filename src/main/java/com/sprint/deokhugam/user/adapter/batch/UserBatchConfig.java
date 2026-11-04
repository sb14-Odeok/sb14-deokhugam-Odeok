package com.sprint.deokhugam.user.adapter.batch;

import com.sprint.deokhugam.user.application.provided.command.UserCleanupUseCase;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Slf4j
public class UserBatchConfig {

  @Bean
  public Job userCleanupJob(JobRepository jobRepository, Step userCleanupStep) {
    return new JobBuilder("userCleanupJob", jobRepository).start(userCleanupStep).build();
  }

  @Bean
  public Step userCleanupStep(
      JobRepository jonRepository,
      PlatformTransactionManager tm,
      ItemReader<UUID> userCleanupReader,
      UserCleanupUseCase userCleanupUseCase) {
    return new StepBuilder("userCleanupStep", jonRepository)
        .<UUID, UUID>chunk(1)
        .transactionManager(tm)
        .reader(userCleanupReader)
        .writer(
            chunk -> {
              for (UUID userId : chunk.getItems()) {
                try {
                  userCleanupUseCase.cleanupUserWithRelatedData(userId);
                } catch (RuntimeException e) {
                  log.error("삭제 실패 유저 id = {}", userId, e);
                  throw e;
                }
              }
            })
        .build();
  }

  @Bean
  @StepScope
  public ListItemReader<UUID> userCleanupReader(UserFinder userFinder) {
    Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));

    List<UUID> targetIds = userFinder.findCleanupIds(cutoff, 20);
    return new ListItemReader<>(targetIds);
  }
}
