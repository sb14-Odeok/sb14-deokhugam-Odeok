package com.sprint.deokhugam.book.adapter.storage.s3;

import com.sprint.deokhugam.book.application.required.ThumbnailStoragePort;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
public class S3ThumbnailStorageAdapter implements ThumbnailStoragePort {

  private final S3Client s3Client;
  private final String bucket;

  public S3ThumbnailStorageAdapter(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
  }

  @Override
  public String upload(String key, byte[] bytes, String contentType) {
    PutObjectRequest request =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
    try {
      s3Client.putObject(request, RequestBody.fromBytes(bytes));
    } catch (SdkException e) {
      // S3 쪽 문제라 500. 원인을 넘겨 로그에 스택이 남는다
      throw new BusinessException(BookErrorCode.THUMBNAIL_UPLOAD_FAILED, e);
    }
    GetUrlRequest urlRequest = GetUrlRequest.builder().bucket(bucket).key(key).build();
    return s3Client.utilities().getUrl(urlRequest).toString();
  }

  @Override
  public void delete(String key) {
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (SdkException e) {
      // 삭제 실패는 되돌릴 게 없어서 로그만 남긴다
      log.warn("썸네일 삭제 실패: {} ({})", key, e.getMessage());
    }
  }
}
