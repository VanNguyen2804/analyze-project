package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.exception.ServiceBusyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes={StockController.class,AiLabController.class})
public class PageCapacityAdvice {
    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<?> busy(ServiceBusyException error){
        return ResponseEntity.status(503).header("Retry-After","2").body(Map.of("code","SERVICE_BUSY","message",error.getMessage()));
    }
}
