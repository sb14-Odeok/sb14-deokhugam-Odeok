package com.sprint.deokhugam.book;

import com.sprint.deokhugam.book.application.required.ThumbnailStoragePort;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;

// 테스트에서 S3 대신 쓴다. 올린 키와 지운 키를 기록한다
public class FakeThumbnailStoragePort implements ThumbnailStoragePort {

  public static final String BASE_URL = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";

  private final List<String> uploadedKeys = new ArrayList<>();
  private final List<String> deletedKeys = new ArrayList<>();
  private boolean failing;

  public void setFailing(boolean failing) {
    this.failing = failing;
  }

  public List<String> uploadedKeys() {
    return uploadedKeys;
  }

  public List<String> deletedKeys() {
    return deletedKeys;
  }

  @Override
  public String upload(String key, byte[] bytes, String contentType) {
    if (failing) {
      throw new BusinessException(BookErrorCode.THUMBNAIL_UPLOAD_FAILED);
    }
    uploadedKeys.add(key);
    return BASE_URL + key;
  }

  @Override
  public void delete(String key) {
    deletedKeys.add(key);
  }
}
