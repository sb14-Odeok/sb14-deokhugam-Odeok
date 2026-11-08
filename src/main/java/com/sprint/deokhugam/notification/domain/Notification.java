package com.sprint.deokhugam.notification.domain;

import static jakarta.persistence.FetchType.LAZY;

import com.sprint.deokhugam.common.entity.BaseSoftDeletableEntity;
import com.sprint.deokhugam.dashboard.review.domain.PopularReviewPeriod;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "notifications",
    indexes = {
      @Index(
          name = "idx_notifications_user_created_at_id",
          columnList = "user_id, created_at DESC, id DESC"),
      @Index(name = "idx_notifications_review_id", columnList = "review_id"),
      @Index(
          name = "idx_notifications_confirmed_at",
          columnList = "confirmed_at",
          options = "WHERE confirmed = true")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseSoftDeletableEntity {

  // 알림을 받는사람
  @JoinColumn(name = "user_id", nullable = false)
  @ManyToOne(fetch = LAZY)
  private User user;

  // 알림 대상 리뷰 (알림을 누르면 이동할 리뷰)
  @JoinColumn(name = "review_id", nullable = false)
  @ManyToOne(fetch = LAZY)
  private Review review;

  // 알림 생성 시점의 리뷰 내용 (리뷰가 수정돼도 그대로 유지)
  @Column(name = "review_content")
  private String reviewContent;

  // 알림 종류
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false) // 필수조건
  private NotificationType type;

  // 알림 문구 (닉네임 고정, 추후 닉네임 변경해도 동일)
  @Column(name = "message", nullable = false)
  private String message;

  // 읽었어?
  @Column(name = "confirmed")
  private boolean confirmed;

  // 읽은 시간 ( 추후 읽은 후 1주일 뒤 삭제 구현 예정)
  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  private Notification(
      User user, Review review, String reviewContent, NotificationType type, String value) {
    this.user = user;
    this.review = review;
    this.message = type.format(value);
    this.reviewContent = reviewContent;
    this.type = type;
    this.confirmed = false;
  }

  public static Notification createLike(
      User user, Review review, String reviewContent, String actorNickname) {
    return new Notification(user, review, reviewContent, NotificationType.LIKE, actorNickname);
  }

  public static Notification createComment(
      User user, Review review, String reviewContent, String actorNickname) {
    return new Notification(user, review, reviewContent, NotificationType.COMMENT, actorNickname);
  }

  public static Notification createRanked(
      User user, Review review, String reviewContent, PopularReviewPeriod period, long rank) {
    String periodName =
        switch (period) {
          case DAILY -> "일간";
          case WEEKLY -> "주간";
          case MONTHLY -> "월간";
          case ALL_TIME -> "전체 기간";
        };
    String description = "%s 인기 리뷰 %d위".formatted(periodName, rank);

    return new Notification(user, review, reviewContent, NotificationType.RANKED, description);
  }

  public void confirm() {
    if (confirmed) {
      return;
    }
    this.confirmed = true;
    this.confirmedAt = Instant.now();
  }
}
