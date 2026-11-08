package com.sprint.deokhugam.book.adapter.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;

class S3ClientConfigTest {

  @Test
  @DisplayName("S3Client에 호출 전체 10초, 시도 한 번 3초 타임아웃이 걸려 있다")
  void s3ClientHasCallTimeouts() {
    try (S3Client s3Client = new S3ClientConfig().s3Client("ap-northeast-2")) {
      ClientOverrideConfiguration config =
          s3Client.serviceClientConfiguration().overrideConfiguration();

      assertThat(config.apiCallTimeout()).contains(Duration.ofSeconds(10));
      assertThat(config.apiCallAttemptTimeout()).contains(Duration.ofSeconds(3));
    }
  }
}
