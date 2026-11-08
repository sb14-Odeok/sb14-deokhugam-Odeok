package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.provided.query.IsbnRecognizer;
import com.sprint.deokhugam.book.application.required.OcrTextPort;
import com.sprint.deokhugam.book.domain.Isbn;
import com.sprint.deokhugam.book.domain.IsbnExtractor;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// OCR Space만 부르고 DB는 쓰지 않아서 트랜잭션을 두지 않는다
@Service
@RequiredArgsConstructor
public class IsbnRecognitionService implements IsbnRecognizer {

  private final OcrTextPort ocrTextPort;
  private final IsbnExtractor isbnExtractor;

  private static final long MAX_IMAGE_BYTES = 1_000_000; // OCR Space 무료 한도 1MB
  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
  private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

  // 사진에서 글자를 읽고 그 안에서 ISBN을 찾는다. 글자나 ISBN이 없으면 400
  @Override
  public String recognizeIsbn(MultipartFile image) {
    validateImage(image);
    String filename = image.getOriginalFilename();

    String text;
    try {
      text = ocrTextPort.readText(image.getBytes(), filename, image.getContentType());
    } catch (IOException e) {
      // 업로드된 파일을 읽지 못하면 파일 쪽 문제라 400
      throw new BusinessException(BookErrorCode.INVALID_IMAGE, e);
    }

    if (text.isBlank()) {
      throw new BusinessException(BookErrorCode.OCR_FAILED, "글자를 읽지 못함, file=" + filename);
    }
    return isbnExtractor
        .extract(text)
        .map(Isbn::value)
        .orElseThrow(
            () -> new BusinessException(BookErrorCode.OCR_FAILED, "ISBN 없음, file=" + filename));
  }

  // OCR Space에 보내기 전에 파일을 먼저 확인한다. 무료 한도가 하루 500회라 헛된 호출을 줄인다
  private void validateImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw new BusinessException(BookErrorCode.INVALID_IMAGE, "빈 파일");
    }
    if (image.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(BookErrorCode.INVALID_IMAGE, "크기 초과: " + image.getSize());
    }
    // jpg, png만 받는다. Content-Type과 확장자를 둘 다 본다
    String contentType = image.getContentType();
    String extension = StringUtils.getFilenameExtension(image.getOriginalFilename());
    boolean typeOk = contentType != null && IMAGE_TYPES.contains(contentType);
    boolean extensionOk = extension != null && IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    if (!typeOk || !extensionOk) {
      throw new BusinessException(
          BookErrorCode.INVALID_IMAGE, "형식: " + contentType + " / " + extension);
    }
  }
}
