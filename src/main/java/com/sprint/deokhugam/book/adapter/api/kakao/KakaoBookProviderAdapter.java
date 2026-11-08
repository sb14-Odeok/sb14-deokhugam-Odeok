package com.sprint.deokhugam.book.adapter.api.kakao;

import com.sprint.deokhugam.book.application.dto.BookInfoDto;
import com.sprint.deokhugam.book.application.required.BookProviderPort;
import com.sprint.deokhugam.book.domain.Isbn;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoBookProviderAdapter implements BookProviderPort {

  // 표지 하나가 2MB를 넘을 일이 없어서, 넘으면 이상한 응답으로 보고 버린다
  private static final int MAX_THUMBNAIL_BYTES = 2_000_000;

  private final RestClient kakaoClient;
  private final RestClient thumbnailClient;

  @Override
  public Optional<BookInfoDto> findByIsbn(Isbn isbn) {
    return fetchDocument(isbn.value()).map(doc -> toDto(doc, downloadThumbnail(doc.thumbnail())));
  }

  // 카카오 호출 실패(4xx·5xx·끊김)는 잡지 않는다. 책 정보가 없으면 돌려줄 게 없으니 500이 맞다
  private Optional<KakaoBookResponse.Document> fetchDocument(String isbn) {
    KakaoBookResponse response =
        kakaoClient
            .get()
            .uri("/v3/search/book?target=isbn&query={isbn}", isbn)
            .retrieve()
            .body(KakaoBookResponse.class);
    if (response == null || response.documents().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(response.documents().getFirst());
  }

  // 표지는 없어도 되는 정보라 받기에 실패하면 null로 두고 책 정보는 살린다
  private byte[] downloadThumbnail(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      // 카카오 URL은 fname 값이 이미 인코딩돼 있다. 문자열로 넘기면 한 번 더 인코딩돼 403이 난다
      return thumbnailClient
          .get()
          .uri(URI.create(url))
          .exchange((request, response) -> readThumbnail(url, response));
    } catch (RestClientException e) {
      log.warn("썸네일 받기 실패: {} ({})", url, e.getMessage());
      return null;
    }
  }

  // 크기를 모르는 채로 다 읽지 않는다. Content-Length가 없으면 상한보다 1바이트만 더 읽어 본다
  private byte[] readThumbnail(String url, ClientHttpResponse response) throws IOException {
    if (!response.getStatusCode().is2xxSuccessful()) {
      log.warn("썸네일 받기 실패: {} ({})", url, response.getStatusCode());
      return null;
    }
    long length = response.getHeaders().getContentLength();
    if (length > MAX_THUMBNAIL_BYTES) {
      log.warn("썸네일 크기 초과: {} ({}바이트)", url, length);
      return null;
    }
    byte[] bytes = response.getBody().readNBytes(MAX_THUMBNAIL_BYTES + 1);
    if (bytes.length > MAX_THUMBNAIL_BYTES) {
      log.warn("썸네일 크기 초과: {} (Content-Length 없음)", url);
      return null;
    }
    return bytes;
  }

  private BookInfoDto toDto(KakaoBookResponse.Document doc, byte[] thumbnail) {
    return new BookInfoDto(
        doc.title(),
        doc.authors() == null ? null : String.join(", ", doc.authors()),
        doc.contents(),
        doc.publisher(),
        toDate(doc.datetime()),
        pickIsbn(doc.isbn()),
        thumbnail);
  }

  private LocalDate toDate(String datetime) {
    if (datetime == null || datetime.isBlank()) {
      return null;
    }
    return OffsetDateTime.parse(datetime).toLocalDate();
  }

  // 카카오 isbn은 "10자리 13자리"처럼 공백으로 붙어 오고 한쪽만 올 수도 있다. 13자리를 먼저 고른다
  private String pickIsbn(String isbn) {
    if (isbn == null || isbn.isBlank()) {
      return null;
    }
    List<String> parts = Arrays.stream(isbn.trim().split("\\s+")).toList();
    return parts.stream()
        .filter(part -> part.length() == Isbn.LENGTH)
        .findFirst()
        .orElse(parts.getFirst());
  }
}
