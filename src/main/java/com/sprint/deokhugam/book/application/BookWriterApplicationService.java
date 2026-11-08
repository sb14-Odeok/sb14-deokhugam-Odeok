package com.sprint.deokhugam.book.application;

import com.sprint.deokhugam.book.application.dto.BookCreateRequest;
import com.sprint.deokhugam.book.application.dto.BookDto;
import com.sprint.deokhugam.book.application.dto.BookUpdateRequest;
import com.sprint.deokhugam.book.application.provided.command.BookWriterUseCase;
import com.sprint.deokhugam.book.application.required.ThumbnailStoragePort;
import com.sprint.deokhugam.book.domain.exception.BookErrorCode;
import com.sprint.deokhugam.common.exception.BusinessException;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// S3를 기다리는 동안 DB 커넥션을 쥐지 않도록 트랜잭션을 두지 않는다. DB 쓰기는 BookCreator, BookUpdater가 한다
@Service
@RequiredArgsConstructor
public class BookWriterApplicationService implements BookWriterUseCase {

  private static final long MAX_THUMBNAIL_BYTES = 5_000_000;
  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
  private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

  private final ThumbnailStoragePort thumbnailStoragePort;
  private final BookCreator bookCreator;
  private final BookUpdater bookUpdater;

  @Override
  public BookDto create(BookCreateRequest request, MultipartFile thumbnailImage) {
    String thumbnailUrl = uploadThumbnail(thumbnailImage);
    try {
      return bookCreator.create(request, thumbnailUrl);
    } catch (RuntimeException e) {
      // 확인 실패, 커밋 중 실패까지 여기로 온다. 낙관적 락과 DB 오류도 있어 RuntimeException으로 잡는다
      deleteUploaded(thumbnailUrl);
      throw e;
    }
  }

  @Override
  public BookDto update(UUID bookId, BookUpdateRequest request, MultipartFile thumbnailImage) {
    String thumbnailUrl = uploadThumbnail(thumbnailImage);
    try {
      return bookUpdater.update(bookId, request, thumbnailUrl);
    } catch (RuntimeException e) {
      deleteUploaded(thumbnailUrl);
      throw e;
    }
  }

  private String uploadThumbnail(MultipartFile image) {
    if (image == null) {
      return null;
    }
    validateThumbnail(image);
    byte[] bytes;
    try {
      bytes = image.getBytes();
    } catch (IOException e) {
      throw new BusinessException(BookErrorCode.INVALID_IMAGE, e);
    }
    return thumbnailStoragePort.upload(ThumbnailKeys.newKey(image), bytes, image.getContentType());
  }

  // S3 삭제 실패는 포트가 로그만 남겨서 원래 예외가 그대로 나간다
  private void deleteUploaded(String thumbnailUrl) {
    if (thumbnailUrl != null) {
      thumbnailStoragePort.delete(ThumbnailKeys.keyOf(thumbnailUrl));
    }
  }

  private void validateThumbnail(MultipartFile image) {
    if (image.isEmpty()) {
      throw new BusinessException(BookErrorCode.INVALID_IMAGE, "빈 파일");
    }
    if (image.getSize() > MAX_THUMBNAIL_BYTES) {
      throw new BusinessException(BookErrorCode.INVALID_IMAGE, "크기 초과: " + image.getSize());
    }
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
