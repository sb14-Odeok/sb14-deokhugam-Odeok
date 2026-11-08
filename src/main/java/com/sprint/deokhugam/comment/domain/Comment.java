package com.sprint.deokhugam.comment.domain;

import com.sprint.deokhugam.common.entity.BaseSoftDeletableEntity;
import com.sprint.deokhugam.common.exception.BusinessException;
import com.sprint.deokhugam.common.exception.CommonErrorCode;
import com.sprint.deokhugam.review.domain.Review;
import com.sprint.deokhugam.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "comments",
    indexes = {
      // 리뷰별 댓글 조회 시
      @Index(name = "idx_comments_review_id_created_at", columnList = "review_id, created_at"),
      @Index(name = "idx_comments_user_id", columnList = "user_id")
    })
public class Comment extends BaseSoftDeletableEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "review_id")
  Review review;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  User user;

  @Column(name = "content")
  String content;

  public static Comment create(Review review, User user, String content) {
    return new Comment(review, user, content);
  }

  public void update(String newContent) {
    if (newContent == null || newContent.trim().isEmpty()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
    this.content = newContent;
  }

  public boolean isWrittenBy(UUID userId) {
    return this.getUser().getId().equals(userId);
  }
}
