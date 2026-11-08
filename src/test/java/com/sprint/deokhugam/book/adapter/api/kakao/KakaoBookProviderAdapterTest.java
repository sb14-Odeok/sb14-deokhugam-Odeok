package com.sprint.deokhugam.book.adapter.api.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 카카오 서버 대신 MockRestServiceServer가 응답한다. 실제 네트워크는 쓰지 않는다
class KakaoBookProviderAdapterTest {

  private static final String SEARCH_URL =
      "https://dapi.kakao.com/v3/search/book?target=isbn&query=9788966260959";

  private final RestClient.Builder builder =
      RestClient.builder()
          .baseUrl("https://dapi.kakao.com")
          .defaultHeader("Authorization", "KakaoAK test-key");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final KakaoBookProviderAdapter adapter =
      new KakaoBookProviderAdapter(builder.build(), new KakaoClientConfig().thumbnailClient());

  // 카카오 실제 응답 모양. price처럼 안 쓰는 필드도 섞어서 무시되는지 본다
  private static String kakaoJson(String thumbnail) {
    return kakaoJson(thumbnail, "8966260950 9788966260959");
  }

  private static String kakaoJson(String thumbnail, String isbn) {
    return """
        {
          "meta": {"is_end": true, "pageable_count": 1, "total_count": 1},
          "documents": [{
            "title": "클린 코드",
            "contents": "소개",
            "publisher": "인사이트",
            "authors": ["로버트 C. 마틴", "박재호"],
            "datetime": "2013-12-24T00:00:00.000+09:00",
            "isbn": "%s",
            "thumbnail": "%s",
            "price": 33000,
            "status": "정상판매"
          }]
        }
        """
        .formatted(isbn, thumbnail);
  }

  @Test
  @DisplayName("카카오 응답을 BookInfoDto로 바꾼다. 저자는 쉼표로 합치고 ISBN은 13자리를 고른다")
  void findByIsbn_mapsDocument() {
    server
        .expect(requestTo(SEARCH_URL))
        .andExpect(header("Authorization", "KakaoAK test-key"))
        .andRespond(withSuccess(kakaoJson(""), MediaType.APPLICATION_JSON));

    Optional<BookInfoDto> result = adapter.findByIsbn(Isbn.of("9788966260959"));

    assertThat(result).isPresent();
    BookInfoDto info = result.get();
    assertThat(info.title()).isEqualTo("클린 코드");
    assertThat(info.author()).isEqualTo("로버트 C. 마틴, 박재호");
    assertThat(info.description()).isEqualTo("소개");
    assertThat(info.publisher()).isEqualTo("인사이트");
    assertThat(info.publishedDate()).isEqualTo(LocalDate.of(2013, 12, 24));
    assertThat(info.isbn()).isEqualTo("9788966260959");
    assertThat(info.thumbnailImage()).isNull();
    server.verify();
  }

  @Test
  @DisplayName("공백으로 나뉜 isbn은 순서와 상관없이 13자리를 고르고, 10자리만 있으면 그대로 넘긴다")
  void findByIsbn_picksIsbn() {
    server
        .expect(requestTo(SEARCH_URL))
        .andRespond(
            withSuccess(kakaoJson("", "9788966260959 8966260950"), MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(SEARCH_URL))
        .andRespond(withSuccess(kakaoJson("", " 8966260950 "), MediaType.APPLICATION_JSON));

    assertThat(adapter.findByIsbn(Isbn.of("9788966260959")))
        .map(BookInfoDto::isbn)
        .contains("9788966260959");
    assertThat(adapter.findByIsbn(Isbn.of("9788966260959")))
        .map(BookInfoDto::isbn)
        .contains("8966260950");
  }

  @Test
  @DisplayName("documents가 비어 있으면 빈 Optional을 돌려준다")
  void findByIsbn_emptyDocuments() {
    server
        .expect(requestTo(SEARCH_URL))
        .andRespond(withSuccess("{\"documents\": []}", MediaType.APPLICATION_JSON));

    assertThat(adapter.findByIsbn(Isbn.of("9788966260959"))).isEmpty();
  }

  @Test
  @DisplayName("카카오가 5xx를 주면 예외를 잡지 않고 그대로 올린다")
  void findByIsbn_serverError() {
    server.expect(requestTo(SEARCH_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> adapter.findByIsbn(Isbn.of("9788966260959")))
        .isInstanceOf(RestClientException.class);
  }

  @Test
  @DisplayName("fname이 인코딩된 썸네일 URL을 그대로 요청해 이미지 바이트를 받는다 (두 번 인코딩하면 403)")
  void findByIsbn_downloadsThumbnailWithEncodedUrl() throws Exception {
    // 카카오 썸네일 서버 대신 받은 쿼리 문자열을 기록하고 이미지 바이트를 돌려주는 로컬 서버
    byte[] image = {1, 2, 3};
    AtomicReference<String> receivedQuery = new AtomicReference<>();
    HttpServer thumbServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    thumbServer.createContext(
        "/thumb/",
        exchange -> {
          receivedQuery.set(exchange.getRequestURI().getRawQuery());
          exchange.sendResponseHeaders(200, image.length);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write(image);
          }
        });
    thumbServer.start();
    try {
      String encodedQuery = "fname=http%3A%2F%2Ft1.daumcdn.net%2Flbook%2Fimage%2F545848";
      String thumbnailUrl =
          "http://127.0.0.1:" + thumbServer.getAddress().getPort() + "/thumb/?" + encodedQuery;
      server
          .expect(requestTo(SEARCH_URL))
          .andRespond(withSuccess(kakaoJson(thumbnailUrl), MediaType.APPLICATION_JSON));

      Optional<BookInfoDto> result = adapter.findByIsbn(Isbn.of("9788966260959"));

      assertThat(receivedQuery.get()).isEqualTo(encodedQuery);
      assertThat(result).isPresent();
      assertThat(result.get().thumbnailImage()).isEqualTo(image);
    } finally {
      thumbServer.stop(0);
    }
  }

  @Test
  @DisplayName("썸네일을 받지 못하면 thumbnailImage만 null이고 책 정보는 그대로 온다")
  void findByIsbn_thumbnailDownloadFails() {
    // 아무도 듣지 않는 포트라 연결이 바로 거절된다
    server
        .expect(requestTo(SEARCH_URL))
        .andRespond(
            withSuccess(kakaoJson("http://127.0.0.1:1/cover.jpg"), MediaType.APPLICATION_JSON));

    Optional<BookInfoDto> result = adapter.findByIsbn(Isbn.of("9788966260959"));

    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo("클린 코드");
    assertThat(result.get().thumbnailImage()).isNull();
  }

  @Test
  @DisplayName("Content-Length가 2MB를 넘는 썸네일은 받지 않고 썸네일 없이 응답한다")
  void findByIsbn_thumbnailOverLimitWithLength() throws Exception {
    assertThumbnailIsNull(new byte[2_000_001], false, 0);
  }

  @Test
  @DisplayName("Content-Length 없이 2MB를 넘게 보내도 상한까지만 읽고 썸네일 없이 응답한다")
  void findByIsbn_thumbnailOverLimitChunked() throws Exception {
    assertThumbnailIsNull(new byte[2_000_001], true, 0);
  }

  @Test
  @DisplayName("헤더는 바로 오고 본문이 3초 안에 다 오지 않으면 썸네일 없이 응답한다")
  void findByIsbn_thumbnailSlowBody() throws Exception {
    assertThumbnailIsNull(new byte[10], false, 4_000);
  }

  // chunked면 Content-Length 없이 보낸다. delayMillis만큼 헤더를 보낸 뒤 기다렸다가 본문을 쓴다
  private void assertThumbnailIsNull(byte[] body, boolean chunked, long delayMillis)
      throws Exception {
    HttpServer thumbServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    thumbServer.createContext(
        "/thumb.jpg",
        exchange -> {
          exchange.sendResponseHeaders(200, chunked ? 0 : body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            Thread.sleep(delayMillis);
            out.write(body);
          } catch (InterruptedException | IOException ignored) {
            // 클라이언트가 먼저 끊으면 쓰기가 실패한다
          }
        });
    thumbServer.start();
    try {
      String url = "http://127.0.0.1:" + thumbServer.getAddress().getPort() + "/thumb.jpg";
      server
          .expect(requestTo(SEARCH_URL))
          .andRespond(withSuccess(kakaoJson(url), MediaType.APPLICATION_JSON));

      Optional<BookInfoDto> result = adapter.findByIsbn(Isbn.of("9788966260959"));

      assertThat(result).isPresent();
      assertThat(result.get().thumbnailImage()).isNull();
    } finally {
      thumbServer.stop(0);
    }
  }
}
