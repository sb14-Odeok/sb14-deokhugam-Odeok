package com.sprint.deokhugam.notification.adapter.event;

import com.sprint.deokhugam.notification.NotificationApplicationService;
import com.sprint.deokhugam.notification.domain.NotifyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationApplicationService notificationApplicationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(NotifyEvent event) {
    switch (event) {
      case NotifyEvent.Comment e ->
          notificationApplicationService.notifyComment(e.reviewId(), e.actorName());
      case NotifyEvent.Like e ->
          notificationApplicationService.notifyLike(e.reviewId(), e.actorName());
      case NotifyEvent.Ranked e ->
          notificationApplicationService.notifyRanked(e.reviewId(), e.period(), e.rank());
    }
  }
}
