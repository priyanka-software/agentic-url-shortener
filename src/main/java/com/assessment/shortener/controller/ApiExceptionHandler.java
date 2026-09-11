package com.assessment.shortener.controller;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.time.Instant; import java.util.Map;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,Object>> bad(IllegalArgumentException e){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("timestamp",Instant.now(),"status",404,"error",e.getMessage()));}
}
