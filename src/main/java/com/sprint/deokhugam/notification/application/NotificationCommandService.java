package com.sprint.deokhugam.notification.application;

import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.notification.application.dto.NotificationDto;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCleaner;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCommand;
import com.sprint.deokhugam.notification.application.required.NotificationPersistencePort;
import com.sprint.deokhugam.notification.domain.Notification;
import com.sprint.deokhugam.notification.domain.exception.NotificationErrorCode;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationCommandService implements NotificationCommand, NotificationCleaner {

  private final NotificationPersistencePort notificationPersistencePort;

  @Override
  public NotificationDto createLike(Review review, User user, String actorNickname) {
    String content = review.getContent();
    Notification notification = Notification.createLike(user, review, content, actorNickname);
    Notification save = notificationPersistencePort.save(notification);
    NotificationDto notificationDto =
        NotificationDto.to(
            notification.getId(),
            user.getId(),
            review.getId(),
            content,
            notification.getMessage(),
            notification.isConfirmed(),
            notification.getCreatedAt(),
            notification.getUpdatedAt());
    return notificationDto;
  }

  @Override
  public NotificationDto createComment(Review review, User user, String actorNickname) {
    String content = review.getContent();
    Notification notification = Notification.createComment(user, review, content, actorNickname);
    Notification save = notificationPersistencePort.save(notification);
    NotificationDto notificationDto =
        NotificationDto.to(
            notification.getId(),
            user.getId(),
            review.getId(),
            content,
            notification.getMessage(),
            notification.isConfirmed(),
            notification.getCreatedAt(),
            notification.getUpdatedAt());
    return notificationDto;
  }

  @Override
  public void createRank(Review review, User user, PopularReviewPeriod period, long rank) {
    Notification notification =
        Notification.createRanked(user, review, review.getContent(), period, rank);

    notificationPersistencePort.save(notification);
  }

  @Override
  public NotificationDto confirm(UUID notificationId, UUID userId) {
    Notification find =
        notificationPersistencePort
            .findById(notificationId)
            .filter(notification -> !notification.isDeleted())
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOT_FOUND));

    if (!find.getUser().getId().equals(userId)) {
      throw new BusinessException(NotificationErrorCode.FORBIDDEN);
    }

    find.confirm();
    return NotificationDto.to(
        find.getId(),
        find.getUser().getId(),
        find.getReview().getId(),
        find.getReviewContent(),
        find.getMessage(),
        find.isConfirmed(),
        find.getCreatedAt(),
        find.getUpdatedAt());
  }

  @Override
  public void confirmAll(UUID userId) {
    List<Notification> allByUser = notificationPersistencePort.findAllByUser(userId);
    /*
     *  시간날 때 리팩터링 예정.
     * */
    allByUser.forEach(Notification::confirm);
  }

  @Override
  public void deleteConfirmed(Instant cutoff) {
    notificationPersistencePort.deleteConfirmed(cutoff);
  }

  @Override
  public void deleteByUser(UUID userId) {
    notificationPersistencePort.deleteByUser(userId);
  }

  @Override
  public void deleteByReview(UUID reviewId) {
    notificationPersistencePort.deleteByReview(reviewId);
  }

  @Override
  public void deleteByReviewIds(List<UUID> reviewIds) {}
}
