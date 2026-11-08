package com.sprint.deokhugam.book.adapter.api.kakao;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoClientConfig {

  // 키는 .env의 KAKAO_REST_API_KEY가 application.yaml의 kakao.rest-api-key로 들어온다
  @Bean
  public RestClient kakaoClient(@Value("${kakao.rest-api-key}") String restApiKey) {
    // 카카오가 느려도 요청 스레드가 오래 묶이지 않게 연결 3초, 응답 5초까지만 기다린다
    return RestClient.builder()
        .baseUrl("https://dapi.kakao.com")
        .defaultHeader("Authorization", "KakaoAK " + restApiKey)
        .requestFactory(timeoutFactory(Duration.ofSeconds(3), Duration.ofSeconds(5)))
        .build();
  }

  // 썸네일은 카카오 API가 아닌 이미지 서버에서 받으므로 인증 헤더 없는 클라이언트를 따로 둔다
  @Bean
  public RestClient thumbnailClient() {
    // 이 읽기 시간은 본문을 다 받을 때까지를 잰다
    return RestClient.builder()
        .requestFactory(timeoutFactory(Duration.ofSeconds(2), Duration.ofSeconds(3)))
        .build();
  }

  private JdkClientHttpRequestFactory timeoutFactory(Duration connect, Duration read) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connect).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(read);
    return factory;
  }
}
