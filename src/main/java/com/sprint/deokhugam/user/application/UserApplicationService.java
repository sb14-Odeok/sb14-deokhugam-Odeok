package com.sprint.deokhugam.user.application;

import com.sprint.deokhugam.comment.application.provided.CommentUseCase;
import com.sprint.deokhugam.notification.application.provided.command.NotificationCleaner;
import com.sprint.deokhugam.review.application.ReviewCleanupUseCase;
import com.sprint.deokhugam.review.application.provided.command.ReviewLikeCommand;
import com.sprint.deokhugam.user.application.provided.command.UserCleanupUseCase;
import com.sprint.deokhugam.user.application.provided.command.UserCommand;
import com.sprint.deokhugam.user.application.provided.query.UserFinder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserApplicationService implements UserCleanupUseCase {

  private final UserFinder userFinder;
  private final UserCommand userCommand;
  private final NotificationCleaner notificationCleaner;
  private final CommentUseCase commentUseCase;
  private final ReviewCleanupUseCase reviewCleanupUseCase;
  private final ReviewLikeCommand reviewLikeCommand;

  @Override
  @Transactional
  public void cleanupUserWithRelatedData(UUID targetId) {

    notificationCleaner.deleteByUser(targetId);
    reviewLikeCommand.deleteLikesByUserId(targetId);
    commentUseCase.deleteCommentsByUser(targetId);
    reviewCleanupUseCase.deleteReviewsWithRelatedDataByUserId(targetId);
    userCommand.delete(targetId);
    // 유저 아이디로 삭제 메서드 요청해야함 지금은
  }
}
