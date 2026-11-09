package com.sprint.deokhugam.comment.adapter.persistence;

import com.sprint.deokhugam.comment.domain.Comment;
import com.sprint.deokhugam.comment.domain.exception.CommentErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentJpaRepository extends JpaRepository<Comment, UUID> {

  Optional<Comment> findByIdAndDeletedAtIsNull(UUID commentId);

  default Comment findByIdOrThrow(UUID commentId) {
    return findByIdAndDeletedAtIsNull(commentId)
        .orElseThrow(() -> new BusinessException(CommentErrorCode.NOT_FOUND));
  }

  // 물리 삭제용: 논리 삭제된 댓글도 찾는다
  default Comment findByIdIncludingDeletedOrThrow(UUID commentId) {
    return findById(commentId).orElseThrow(() -> new BusinessException(CommentErrorCode.NOT_FOUND));
  }
}
