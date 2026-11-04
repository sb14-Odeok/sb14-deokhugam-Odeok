package com.sprint.deokhugam.notification;

import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCommand;
import com.sprint.deokhugam.review.application.provided.query.ReviewFinder;
import com.sprint.deokhugam.review.domain.Review;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class NotificationApplicationService {

  private final ReviewFinder reviewFinder;
  private final NotificationCommand notificationCommand;

  public void notifyComment(UUID reviewId, String actorName) {
    Review review = reviewFinder.getByIdOrThrow(reviewId);

    notificationCommand.createComment(review, review.getUser(), actorName);
  }

  public void notifyLike(UUID reviewId, String nickName) {
    Review review = reviewFinder.getByIdOrThrow(reviewId);
    notificationCommand.createLike(review, review.getUser(), nickName);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void notifyRanked(UUID reviewId, PopularReviewPeriod period, long rank) {
    Review review = reviewFinder.getByIdOrThrow(reviewId);

    notificationCommand.createRank(review, review.getUser(), period, rank);
  }
}
