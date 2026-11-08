package com.sprint.deokhugam.book.adapter.storage.s3;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3ClientConfig {

  // 자격 증명은 SDK가 찾는다. 로컬은 aws login, 서버는 인스턴스 역할
  @Bean
  public S3Client s3Client(@Value("${aws.s3.region}") String region) {
    // S3가 멈춰도 요청 스레드가 묶이지 않게 재시도까지 합쳐 10초, 한 번 시도는 3초까지만 기다린다
    ClientOverrideConfiguration timeouts =
        ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(10))
            .apiCallAttemptTimeout(Duration.ofSeconds(3))
            .build();
    return S3Client.builder().region(Region.of(region)).overrideConfiguration(timeouts).build();
  }
}
