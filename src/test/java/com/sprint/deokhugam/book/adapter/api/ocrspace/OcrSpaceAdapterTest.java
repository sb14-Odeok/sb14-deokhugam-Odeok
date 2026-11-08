package com.sprint.deokhugam.book.adapter.api.ocrspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 실제 OCR Space 대신 MockRestServiceServer가 응답한다
class OcrSpaceAdapterTest {

  private static final String PARSE_URL = "https://api.ocr.space/parse/image";
  private static final byte[] IMAGE = {1, 2, 3};

  private final RestClient.Builder builder =
      RestClient.builder().baseUrl("https://api.ocr.space").defaultHeader("apikey", "test-key");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final OcrSpaceAdapter adapter = new OcrSpaceAdapter(builder.build());

  // OCR Space가 실제로 보내는 응답 모양. 안 쓰는 필드도 넣어서 무시되는지 본다
  private static String ocrJson(String parsedText, String exitCode) {
    return """
        {
          "ParsedResults": [{
            "TextOverlay": {"Lines": [], "HasOverlay": false, "Message": "Text overlay is not provided"},
            "FileParseExitCode": 1,
            "ParsedText": "%s",
            "ErrorMessage": "",
            "ErrorDetails": ""
          }],
          "OCRExitCode": %s,
          "IsErroredOnProcessing": false,
          "ProcessingTimeInMilliseconds": "495",
          "SearchablePDFURL": "Searchable PDF not generated as it was not requested."
        }
        """
        .formatted(parsedText, exitCode);
  }

  @Test
  @DisplayName("apikey 헤더와 multipart로 보내고 첫 ParsedText를 돌려준다")
  void readText_returnsFirstParsedText() {
    server
        .expect(requestTo(PARSE_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("apikey", "test-key"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
        .andRespond(
            withSuccess(ocrJson("ISBN 978-89-6626-095-9", "1"), MediaType.APPLICATION_JSON));

    String text = adapter.readText(IMAGE, "cover.jpg", "image/jpeg");

    assertThat(text).isEqualTo("ISBN 978-89-6626-095-9");
    server.verify();
  }

  @Test
  @DisplayName("OCRExitCode가 숫자로 와도 받는다")
  void readText_numericExitCode() {
    server
        .expect(requestTo(PARSE_URL))
        .andRespond(withSuccess(ocrJson("글", "1"), MediaType.APPLICATION_JSON));

    assertThat(adapter.readText(IMAGE, "cover.jpg", "image/jpeg")).isEqualTo("글");
  }

  @Test
  @DisplayName("ParsedResults가 비어 있으면 빈 글을 돌려준다")
  void readText_emptyResults() {
    server
        .expect(requestTo(PARSE_URL))
        .andRespond(
            withSuccess(
                "{\"ParsedResults\": [], \"OCRExitCode\": 1, \"IsErroredOnProcessing\": false}",
                MediaType.APPLICATION_JSON));

    assertThat(adapter.readText(IMAGE, "cover.jpg", "image/jpeg")).isEmpty();
  }

  @Test
  @DisplayName("IsErroredOnProcessing이면 OCR_UNAVAILABLE 예외")
  void readText_erroredOnProcessing() {
    server
        .expect(requestTo(PARSE_URL))
        .andRespond(
            withSuccess(
                """
                {"ParsedResults": null, "OCRExitCode": 4, "IsErroredOnProcessing": true,
                 "ErrorMessage": ["Unable to recognize the file type"]}
                """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> adapter.readText(IMAGE, "cover.jpg", "image/jpeg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.OCR_UNAVAILABLE);
  }

  @Test
  @DisplayName("OCR Space가 5xx를 주면 OCR_UNAVAILABLE 예외로 바꾼다")
  void readText_serverError() {
    server.expect(requestTo(PARSE_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> adapter.readText(IMAGE, "cover.jpg", "image/jpeg"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(BookErrorCode.OCR_UNAVAILABLE);
  }
}
