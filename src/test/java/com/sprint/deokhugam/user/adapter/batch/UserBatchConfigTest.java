package com.sprint.deokhugam.user.adapter.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.user.application.provided.command.UserCleanupUseCase;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 실제 Batch Job/Step/청크를 실행하되 DB 자원과 비즈니스 삭제는 대체한다. */
class UserBatchConfigTest {
  private final UserBatchConfig config = new UserBatchConfig();
  private final UserCleanupUseCase cleanup = mock(UserCleanupUseCase.class);

  private JobExecution execute(List<UUID> ids) throws Exception {
    var repository = new ResourcelessJobRepository();
    Step step =
        config.userCleanupStep(
            repository, new ResourcelessTransactionManager(), new ListItemReader<>(ids), cleanup);
    Job job = config.userCleanupJob(repository, step);
    JobParameters parameters = new JobParameters();
    var instance = repository.createJobInstance(job.getName(), parameters);
    JobExecution execution =
        repository.createJobExecution(instance, parameters, new ExecutionContext());
    job.execute(execution);
    return execution;
  }

  @Test
  void 한_명마다_트랜잭션을_커밋한_뒤_다음_유저를_처리한다() throws Exception {
    List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    AtomicInteger committed = new AtomicInteger();
    List<Integer> commitsBeforeWrite = new ArrayList<>();
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              commitsBeforeWrite.add(committed.get());
              TransactionSynchronizationManager.registerSynchronization(
                  new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                      committed.incrementAndGet();
                    }
                  });
              return null;
            })
        .when(cleanup)
        .cleanupUserWithRelatedData(any());
    JobExecution execution = execute(ids);
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(commitsBeforeWrite).containsExactly(0, 1, 2);
    assertThat(committed).hasValue(3);
    var order = inOrder(cleanup);
    for (UUID id : ids) order.verify(cleanup).cleanupUserWithRelatedData(id);
  }

  @Test
  void 모의_삭제_예외는_Writer에서_기록하고_후속_유저를_처리한다() throws Exception {
    UUID first = UUID.randomUUID();
    UUID failed = UUID.randomUUID();
    UUID next = UUID.randomUUID();
    doThrow(new IllegalStateException("test deletion failure"))
        .when(cleanup)
        .cleanupUserWithRelatedData(failed);
    JobExecution execution = execute(List.of(first, failed, next));
    // 삭제 서비스를 mock으로 대체해 실제 DB의 rollback-only 처리는 발생하지 않는다.
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    var step = execution.getStepExecutions().iterator().next();
    assertThat(step.getWriteCount()).isEqualTo(3);
    assertThat(step.getWriteSkipCount()).isZero();
    var order = inOrder(cleanup);
    order.verify(cleanup).cleanupUserWithRelatedData(first);
    order.verify(cleanup).cleanupUserWithRelatedData(failed);
    order.verify(cleanup).cleanupUserWithRelatedData(next);
  }

  @Test
  void Reader는_최대_20명을_한번_조회해서_한명씩_반환한다() throws Exception {
    UserFinder finder = mock(UserFinder.class);
    List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
    when(finder.findCleanupIds(any(), eq(20))).thenReturn(ids);
    Instant before = Instant.now().minusSeconds(300);
    var reader = config.userCleanupReader(finder);
    Instant after = Instant.now().minusSeconds(300);
    assertThat(reader.read()).isEqualTo(ids.get(0));
    assertThat(reader.read()).isEqualTo(ids.get(1));
    assertThat(reader.read()).isNull();
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(finder).findCleanupIds(cutoff.capture(), eq(20));
    assertThat(cutoff.getValue()).isBetween(before, after);
    verifyNoMoreInteractions(finder);
  }
}
