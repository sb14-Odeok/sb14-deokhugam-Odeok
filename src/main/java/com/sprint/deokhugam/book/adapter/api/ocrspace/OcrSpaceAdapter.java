package com.sprint.deokhugam.book.adapter.api.ocrspace;

import com.sprint.deokhugam.book.application.required.OcrTextPort;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class OcrSpaceAdapter implements OcrTextPort {

  private final RestClient ocrSpaceClient;

  @Override
  public String readText(byte[] image, String filename, String contentType) {
    OcrSpaceResponse response = requestOcr(image, filename, contentType);

    if (response == null || response.failed()) {
      // 사진이 아니라 OCR Space 쪽 문제라서 500
      Object reason = response == null ? "응답 없음" : response.errorMessage();
      throw new BusinessException(BookErrorCode.OCR_UNAVAILABLE, "OCR Space 처리 오류: " + reason);
    }

    // 이미지 한 장을 보냈으니 결과도 하나. 글자가 없으면 빈 문자열
    List<OcrSpaceResponse.ParsedResult> results = response.parsedResults();
    if (results == null || results.isEmpty() || results.getFirst().parsedText() == null) {
      return "";
    }
    return results.getFirst().parsedText();
  }

  // 이미지를 multipart 요청의 file 파트에 담아 보낸다
  private OcrSpaceResponse requestOcr(byte[] image, String filename, String contentType) {
    // 파일명과 Content-Type이 있어야 OCR Space가 파일로 인식한다
    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.parseMediaType(contentType));
    ByteArrayResource file =
        new ByteArrayResource(image) {
          @Override
          public String getFilename() {
            return filename;
          }
        };

    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("language", "auto");
    form.add("file", new HttpEntity<>(file, partHeaders));

    try {
      return ocrSpaceClient
          .post()
          .uri("/parse/image")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(form)
          .retrieve()
          .body(OcrSpaceResponse.class);
    } catch (RestClientException e) {
      // 오류 응답이든 타임아웃이든 OCR Space를 쓸 수 없는 상태라서 500
      throw new BusinessException(BookErrorCode.OCR_UNAVAILABLE, e);
    }
  }
}
