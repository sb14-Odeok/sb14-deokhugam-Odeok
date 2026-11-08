package com.sprint.deokhugam.notification.adapter.scheduler;

import com.sprint.deokhugam.notification.application.provided.command.NotificationCleaner;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

  private final NotificationCleaner notificationCleaner;

  @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
  public void scheduledDelete() {
    Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
    notificationCleaner.deleteConfirmed(cutoff);
  }
}
