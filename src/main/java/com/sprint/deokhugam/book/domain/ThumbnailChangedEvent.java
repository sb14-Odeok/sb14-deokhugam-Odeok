package com.sprint.deokhugam.book.domain;

// 수정으로 썸네일이 바뀌었다는 사실. 커밋 뒤 이전 파일을 지우는 데만 쓴다
public record ThumbnailChangedEvent(String previousKey) {}
