package com.example.demeerror.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ErrorTestController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/error-test")
    public ResponseEntity<Void> errorTest() {
        // 의도적으로 예외를 발생시켜 로그와 알람 파이프라인을 검증
        throw new IllegalStateException("테스트 목적의 의도적 예외");
    }
}

