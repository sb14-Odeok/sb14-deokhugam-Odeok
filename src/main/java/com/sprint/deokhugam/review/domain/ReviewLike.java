package com.sprint.deokhugam.review.domain;

import com.sprint.deokhugam.common.entity.BaseUpdatableEntity;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(
    name = "review_likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "user_id"}),
    indexes = @Index(name = "idx_review_likes_user_id", columnList = "user_id"))
public class ReviewLike extends BaseUpdatableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "review_id", nullable = false)
  Review review;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  User user;

  @Builder.Default boolean liked = true;

  public static ReviewLike init(Review review, User user) {
    return builder().review(review).user(user).build();
  }

  public boolean toggle() {
    liked = !liked;
    return liked;
  }
}
