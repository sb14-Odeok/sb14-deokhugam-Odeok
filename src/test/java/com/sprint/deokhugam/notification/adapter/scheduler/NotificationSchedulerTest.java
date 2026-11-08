package com.sprint.deokhugam.notification.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sprint.deokhugam.notification.application.provided.command.NotificationCleaner;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationSchedulerTest {
  @Test
  void 현재시각에서_5분을_뺀_기준으로_읽은_알림을_정리한다() {
    NotificationCleaner cleaner = mock(NotificationCleaner.class);
    Instant lower = Instant.now().minus(Duration.ofMinutes(5));
    new NotificationScheduler(cleaner).scheduledDelete();
    Instant upper = Instant.now().minus(Duration.ofMinutes(5));
    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(cleaner).deleteConfirmed(captor.capture());
    assertThat(captor.getValue()).isBetween(lower, upper);
  }
}
