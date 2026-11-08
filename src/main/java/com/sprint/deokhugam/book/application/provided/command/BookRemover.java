package com.sprint.deokhugam.book.application.provided.command;

import java.util.UUID;

public interface BookRemover {

  // 논리 삭제. 리뷰, 댓글, 썸네일 파일은 그대로 둔다
  void delete(UUID bookId);

  // 물리 삭제. 리뷰와 그 아래 데이터, 썸네일 파일까지 지운다. UI에는 없고 테스트로만 부른다
  void hardDelete(UUID bookId);
}
