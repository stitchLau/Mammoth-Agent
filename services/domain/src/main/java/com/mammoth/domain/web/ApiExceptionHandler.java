package com.mammoth.domain.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain/API errors to RFC 9457 problem responses without leaking internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException error) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
    detail.setTitle(error.getStatus().getReasonPhrase());
    return detail;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException error) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(
        org.springframework.http.HttpStatus.BAD_REQUEST, error.getMessage());
    detail.setTitle("Bad Request");
    return detail;
  }
}
