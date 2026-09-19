package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.NumberEntryRequest;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

    /**
     * Xác định category dựa trên ngày quay:
     * - Thứ 4, 6, Chủ nhật: MEGA (1-45)
     * - Thứ 3, 5, 7: POWER (1-55)
     */
    public String determineCategoryFromDate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.TUESDAY || dow == DayOfWeek.THURSDAY || dow == DayOfWeek.SATURDAY) {
            return "POWER";
        }
        return "MEGA";
    }

    public List<LotteryNumber> getAllNumbers(LocalDate drawDate, String category) {
        if (drawDate != null && category != null && !category.trim().isEmpty()) {
            return repository.findByDrawDateAndCategoryOrderByCreatedAtDesc(drawDate, category.trim().toUpperCase());
        } else if (drawDate != null) {
            return repository.findByDrawDateOrderByCreatedAtDesc(drawDate);
        } else if (category != null && !category.trim().isEmpty()) {
            return repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(category.trim().toUpperCase());
        }
        return repository.findAllByOrderByDrawDateDescCreatedAtDesc();
    }

    public LotteryNumber saveNumbers(NumberEntryRequest request) {
        List<Integer> numbers = request.getNumbers();

        if (numbers == null || numbers.size() != 6) {
            throw new IllegalArgumentException("Yêu cầu nhập chính xác đúng 6 con số chính!");
        }

        // Validate duplicates
        Set<Integer> uniqueCheck = new HashSet<>(numbers);
        if (uniqueCheck.size() != 6) {
            throw new IllegalArgumentException("Các con số chính không được trùng nhau!");
        }

        LocalDate date = request.getDrawDate() != null ? request.getDrawDate() : LocalDate.now();

        // Xác định hoặc chuẩn hóa category
        String category = request.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = determineCategoryFromDate(date);
        } else {
            category = category.trim().toUpperCase();
        }

        if (!"MEGA".equals(category) && !"POWER".equals(category)) {
            throw new IllegalArgumentException("Category không hợp lệ! Chỉ chấp nhận MEGA hoặc POWER.");
        }

        // Giới hạn số: MEGA: 1..45, POWER: 1..55
        int maxLimit = "POWER".equals(category) ? 55 : 45;
        for (Integer num : numbers) {
            if (num == null || num < 1 || num > maxLimit) {
                throw new IllegalArgumentException(
                        String.format("Với danh mục %s, mỗi số phải từ 1 đến %d! (Số không hợp lệ: %d)",
                                category, maxLimit, num));
            }
        }

        Integer specialNumber = null;
        if ("POWER".equals(category)) {
            if (request.getSpecialNumber() != null) {
                int sp = request.getSpecialNumber();
                if (sp < 1 || sp > 55) {
                    throw new IllegalArgumentException("Số phụ của Power 6/55 phải là số nguyên từ 1 đến 55!");
                }
                if (numbers.contains(sp)) {
                    throw new IllegalArgumentException(String.format("Số phụ (%d) không được trùng với 6 số chính!", sp));
                }
                specialNumber = sp;
            }
        }

        // Sắp xếp tăng dần
        Collections.sort(numbers);

        LotteryNumber entity = new LotteryNumber(date, category, numbers, specialNumber, request.getNote());
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
