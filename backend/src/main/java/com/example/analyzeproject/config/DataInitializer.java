package com.example.analyzeproject.config;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDatabase(LotteryNumberRepository repository,
                                          ResourceLoader resourceLoader,
                                          ObjectMapper objectMapper) {
        return args -> {
            try {
                long count = repository.count();
                if (count == 0) {
                    log.info("Cơ sở dữ liệu xổ số đang trống. Đang nạp dữ liệu lịch sử ban đầu cho MEGA & POWER...");
                    Resource resource = resourceLoader.getResource("classpath:data/lottery_numbers.json");
                    if (resource.exists()) {
                        try (InputStream is = resource.getInputStream()) {
                            List<LotteryNumber> list = objectMapper.readValue(is, new TypeReference<List<LotteryNumber>>() {});
                            for (LotteryNumber item : list) {
                                item.setId(null); // DB tự tăng ID
                                if (item.getCreatedAt() == null) {
                                    item.setCreatedAt(LocalDateTime.now());
                                }
                            }
                            repository.saveAll(list);
                            log.info("Đã nạp thành công {} kỳ quay lịch sử vào Database.", list.size());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Bỏ qua nạp dữ liệu khởi tạo tự động: {}", e.getMessage());
            }
        };
    }
}
