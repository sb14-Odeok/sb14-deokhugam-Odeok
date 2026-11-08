package com.sprint.deokhugam.book.application.provided.query;

import org.springframework.web.multipart.MultipartFile;

public interface IsbnRecognizer {

  // 도서 이미지에서 ISBN 하나를 읽는다. 못 읽으면 BOOK_OCR_FAILED(400)
  // provided 포트는 웹 어댑터만 부르므로 MultipartFile을 받는다 (required 포트에는 들이지 않는다)
  String recognizeIsbn(MultipartFile image);
}
