package com.sprint.deokhugam.user.adapter.scheduler;

import com.sprint.deokhugam.user.application.provided.command.UserActivityCommand;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRankingScheduler {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final UserFinder userFinder;
  private final UserActivityCommand userCommand;

  @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
  public void closeRanking() {
    LocalDate today = LocalDate.now(SEOUL);
    List<UUID> targetIds = userFinder.findRankingTargetIds();

    for (UUID userId : targetIds) {
      try {
        userCommand.closeRanking(userId, today);
      } catch (RuntimeException e) {
        log.error("유저 순위 마감 실패. userId={}, date={}", userId, today, e);
      }
    }
  }
}
