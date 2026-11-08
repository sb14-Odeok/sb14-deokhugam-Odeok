package com.sprint.deokhugam.book.application;

import java.net.URI;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

final class ThumbnailKeys {

  private ThumbnailKeys() {}

  static String newKey(MultipartFile image) {
    String extension = StringUtils.getFilenameExtension(image.getOriginalFilename()).toLowerCase();
    return "books/" + UUID.randomUUID() + "." + extension;
  }

  // DB의 썸네일 URL은 늘 우리 버킷 URL이라 경로가 곧 키다
  static String keyOf(String url) {
    return URI.create(url).getPath().substring(1);
  }
}
