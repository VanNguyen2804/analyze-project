package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.NumberEntryRequest;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LotteryNumberService {

    private final LotteryNumberRepository repository;

    @Autowired
    public LotteryNumberService(LotteryNumberRepository repository) {
        this.repository = repository;
    }

    public List<LotteryNumber> getAllNumbers() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public LotteryNumber saveNumbers(NumberEntryRequest request) {
        List<Integer> numbers = request.getNumbers();

        if (numbers == null || numbers.size() != 6) {
            throw new IllegalArgumentException("Yêu cầu nhập chính xác đúng 6 con số!");
        }

        // Validate duplicates
        Set<Integer> uniqueCheck = new HashSet<>(numbers);
        if (uniqueCheck.size() != 6) {
            throw new IllegalArgumentException("Các con số không được trùng nhau!");
        }

        // Validate range 1 to 45
        for (Integer num : numbers) {
            if (num == null || num < 1 || num > 45) {
                throw new IllegalArgumentException("Mỗi số phải nằm trong khoảng từ 1 đến 45! (Số không hợp lệ: " + num + ")");
            }
        }

        // Sort ascending
        Collections.sort(numbers);

        LotteryNumber entity = new LotteryNumber(numbers, request.getNote());
        return repository.save(entity);
    }

    public boolean deleteNumber(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}
