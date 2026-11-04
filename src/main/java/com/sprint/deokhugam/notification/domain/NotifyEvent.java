package com.sprint.deokhugam.notification.domain;

import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import java.util.UUID;

public sealed interface NotifyEvent
    permits NotifyEvent.Like, NotifyEvent.Comment, NotifyEvent.Ranked {

  UUID reviewId();

  static NotifyEvent like(UUID reviewId, UUID userId, String actorName) {
    return new Like(reviewId, userId, actorName);
  }

  static NotifyEvent comment(UUID reviewId, UUID userId, String actorName) {
    return new Comment(reviewId, userId, actorName);
  }

  static NotifyEvent ranked(UUID reviewId, PopularReviewPeriod period, long rank) {
    return new Ranked(reviewId, period, rank);
  }

  record Ranked(UUID reviewId, PopularReviewPeriod period, long rank) implements NotifyEvent {}

  record Like(UUID reviewId, UUID userId, String actorName) implements NotifyEvent {}

  record Comment(UUID reviewId, UUID userId, String actorName) implements NotifyEvent {}
}
