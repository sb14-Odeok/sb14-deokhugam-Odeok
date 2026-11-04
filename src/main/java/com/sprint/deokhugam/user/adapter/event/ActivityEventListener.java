package com.sprint.deokhugam.user.adapter.event;

import com.sprint.deokhugam.user.application.provided.command.UserActivityCommand;
import com.sprint.deokhugam.user.domain.ranking.ActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityEventListener {

  private final UserActivityCommand userActivityCommand;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(ActivityEvent event) {
    log.info("event={}", event);
    userActivityCommand.apply(event);
  }
}
