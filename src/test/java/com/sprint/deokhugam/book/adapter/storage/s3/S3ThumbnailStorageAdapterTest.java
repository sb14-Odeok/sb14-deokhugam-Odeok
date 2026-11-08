package com.sprint.deokhugam.book.adapter.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// 실제 S3 대신 S3Client mock이 받는다. URL 만들기만 진짜 S3Utilities를 쓴다
class S3ThumbnailStorageAdapterTest {

  private static final byte[] IMAGE = {1, 2, 3};

  private final S3Client s3Client = mock(S3Client.class);
  private final S3ThumbnailStorageAdapter adapter =
      new S3ThumbnailStorageAdapter(s3Client, "test-bucket");

  @Test
  @DisplayName("버킷, 키, Content-Type을 넣어 올리고 그 객체의 URL을 돌려준다")
  void upload() {
    when(s3Client.utilities())
        .thenReturn(S3Utilities.builder().region(Region.AP_NORTHEAST_2).build());

    String url = adapter.upload("books/a.png", IMAGE, "image/png");

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
    assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
    assertThat(captor.getValue().key()).isEqualTo("books/a.png");
    assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    assertThat(url).isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/books/a.png");
  }

  @Test
  @DisplayName("S3가 실패하면 THUMBNAIL_UPLOAD_FAILED 예외로 바꾸고 원인을 남긴다")
  void uploadFails() {
    SdkClientException cause = SdkClientException.create("연결 실패");
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(cause);

    assertThatThrownBy(() -> adapter.upload("books/a.png", IMAGE, "image/png"))
        .isInstanceOf(BusinessException.class)
        .hasCause(cause)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.THUMBNAIL_UPLOAD_FAILED);
  }

  @Test
  @DisplayName("키로 객체를 지운다")
  void delete() {
    adapter.delete("books/a.png");

    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(captor.capture());
    assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
    assertThat(captor.getValue().key()).isEqualTo("books/a.png");
  }

  @Test
  @DisplayName("지우기가 실패해도 예외를 던지지 않는다")
  void deleteFailsQuietly() {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(SdkClientException.create("연결 실패"));

    assertThatCode(() -> adapter.delete("books/a.png")).doesNotThrowAnyException();
  }
}
