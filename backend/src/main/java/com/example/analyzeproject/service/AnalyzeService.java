package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.DrawRecordDto;
import com.example.analyzeproject.dto.NumberScoreDetailDto;
import com.example.analyzeproject.dto.NumberSelectionReasonDto;
import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyzeService {

    // Khuôn mẫu Wheeling System: Xáo 10 số thành 10 vé
    private static final int[][] WHEEL_TEMPLATE_10_TO_6 = {
        {0, 1, 2, 3, 4, 5}, {0, 1, 2, 6, 7, 8}, {0, 3, 4, 6, 7, 9}, {0, 3, 5, 6, 8, 9},
        {1, 2, 3, 4, 7, 9}, {1, 2, 4, 5, 8, 9}, {1, 3, 5, 6, 7, 8}, {2, 4, 5, 6, 7, 9},
        {0, 2, 4, 6, 8, 9}, {1, 3, 4, 5, 7, 8}
    };

    private final LotteryNumberRepository repository;

    @Autowired
    public AnalyzeService(LotteryNumberRepository repository) {
        this.repository = repository;
    }

    public PredictionResponseDto analyzeAndPredict(String categoryInput) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        int maxLimit = "POWER".equals(category) ? 55 : 45;

        List<LotteryNumber> records = repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(category);
        List<LotteryNumber> chronologicalRecords = new ArrayList<>(records);
        Collections.reverse(chronologicalRecords);
        int totalDraws = chronologicalRecords.size();

        int[] mainFrequency = new int[maxLimit + 1];
        int[] freqLast5 = new int[maxLimit + 1]; 
        int[] specialFrequency = new int[maxLimit + 1];
        int[] lastSeenMain = new int[maxLimit + 1];
        int[] lastSeenSpecial = new int[maxLimit + 1];
        Arrays.fill(lastSeenMain, -1);
        Arrays.fill(lastSeenSpecial, -1);

        double[] mainMomentum = new double[maxLimit + 1];
        double[] specialMomentum = new double[maxLimit + 1];

        int[][] pairMatrix = new int[maxLimit + 1][maxLimit + 1];
        int[][] specialPairMatrix = new int[maxLimit + 1][maxLimit + 1];

        for (int t = 0; t < totalDraws; t++) {
            LotteryNumber draw = chronologicalRecords.get(t);
            List<Integer> nums = draw.getNumbers();
            if (nums == null) continue;

            List<Integer> validNums = nums.stream()
                    .filter(n -> n != null && n >= 1 && n <= maxLimit)
                    .distinct()
                    .collect(Collectors.toList());

            double weight = Math.exp(-0.12 * (totalDraws - 1 - t));

            for (int n : validNums) {
                mainFrequency[n]++;
                lastSeenMain[n] = t;
                mainMomentum[n] += weight;
                
                if (t >= totalDraws - 5) {
                    freqLast5[n]++;
                }
            }

            for (int i = 0; i < validNums.size(); i++) {
                for (int j = i + 1; j < validNums.size(); j++) {
                    int n1 = validNums.get(i);
                    int n2 = validNums.get(j);
                    pairMatrix[n1][n2]++;
                    pairMatrix[n2][n1]++;
                }
            }

            if ("POWER".equals(category) && draw.getSpecialNumber() != null) {
                int sp = draw.getSpecialNumber();
                if (sp >= 1 && sp <= maxLimit) {
                    specialFrequency[sp]++;
                    lastSeenSpecial[sp] = t;
                    specialMomentum[sp] += weight * 1.2;

                    for (int mn : validNums) {
                        specialPairMatrix[sp][mn]++;
                    }
                }
            }
        }

        int[] drawGap = new int[maxLimit + 1];
        int[] specialDrawGap = new int[maxLimit + 1];
        for (int i = 1; i <= maxLimit; i++) {
            drawGap[i] = (lastSeenMain[i] == -1) ? (totalDraws + 1) : ((totalDraws - 1) - lastSeenMain[i]);
            specialDrawGap[i] = (lastSeenSpecial[i] == -1) ? (totalDraws + 1) : ((totalDraws - 1) - lastSeenSpecial[i]);
        }

        double maxMainMom = Arrays.stream(mainMomentum).max().orElse(1.0);
        double maxSpecMom = Arrays.stream(specialMomentum).max().orElse(1.0);
        if (maxMainMom == 0.0) maxMainMom = 1.0;
        if (maxSpecMom == 0.0) maxSpecMom = 1.0;

        Random random = new Random();
        List<ScoredNumber> candidateList = new ArrayList<>();
        double avgCycle = (double) maxLimit / 6.0;

        for (int i = 1; i <= maxLimit; i++) {
            double normFreq = totalDraws > 0 ? ((double) mainFrequency[i] / totalDraws) : 0.2;
            double normMom = mainMomentum[i] / maxMainMom;
            
            // 1. CHẤM ĐIỂM SỐ NÓNG (HOT SCORE)
            // Ưu tiên các số ra từ 2 lần trở lên trong 5 kỳ gần nhất (tạo quán tính mạnh)
            double hotScore = (freqLast5[i] >= 2) ? 1.5 : (freqLast5[i] == 1 ? 0.5 : 0.0);
            
            // 2. CHẤM ĐIỂM SỐ LẠNH / HỒI QUY (COLD SCORE)
            // Ưu tiên tuyệt đối các số vắng mặt trong 5 kỳ gần nhất
            double coldScore = (freqLast5[i] == 0) ? 1.2 : 0.0;

            // 3. ĐIỂM CHU KỲ LÔ GAN (GAP SCORE)
            double gapRatio = (double) drawGap[i] / avgCycle;
            double gapScore = (gapRatio >= 1.0 && gapRatio <= 2.5) ? 1.0 : (gapRatio > 2.5 ? 0.6 : 0.2);

            int topPairSum = 0;
            for (int j = 1; j <= maxLimit; j++) {
                if (i != j && pairMatrix[i][j] > 0) topPairSum += pairMatrix[i][j];
            }
            double pairScore = Math.min(1.0, topPairSum / 5.0);

            double z;
            if (totalDraws >= 5) {
                // THUẬT TOÁN CÂN BẰNG: Dùng Math.max để chọn ra đặc tính nổi trội nhất của con số
                // Con số sẽ đạt điểm cao nếu nó RẤT NÓNG (hotScore + normMom) HOẶC RẤT LẠNH (coldScore + gapScore)
                double dynamicScore = Math.max((hotScore * 1.5 + normMom * 1.0), (coldScore * 1.5 + gapScore * 1.2));
                
                z = dynamicScore + (pairScore * 0.7) + (normFreq * 0.4) - 1.8 + (random.nextDouble() * 0.2 - 0.1);
            } else {
                z = Math.sin(i * 0.55) * 0.6 + Math.cos(i * 0.35) * 0.4 + (random.nextDouble() * 0.8 - 0.4);
            }

            double probability = 1.0 / (1.0 + Math.exp(-z));
            candidateList.add(new ScoredNumber(i, probability, mainFrequency[i], drawGap[i]));
        }

        // Sort toàn bộ tập số theo xác suất giảm dần
        candidateList.sort((a, b) -> Double.compare(b.probability, a.probability));

        List<ScoredNumber> selected10 = new ArrayList<>();
        int oddCount = 0;
        int evenCount = 0;

        for (ScoredNumber candidate : candidateList) {
            if (selected10.size() >= 10) break;

            boolean isOdd = (candidate.number % 2 != 0);
            if (isOdd && oddCount >= 6 && selected10.size() < 9) continue;
            if (!isOdd && evenCount >= 6 && selected10.size() < 9) continue;

            selected10.add(candidate);
            if (isOdd) oddCount++;
            else evenCount++;
        }

        if (selected10.size() < 10) {
            for (ScoredNumber candidate : candidateList) {
                if (selected10.size() >= 10) break;
                if (!selected10.contains(candidate)) selected10.add(candidate);
            }
        }

        selected10.sort((a, b) -> Double.compare(b.probability, a.probability));

        List<Integer> selected10NumbersForWheeling = selected10.stream()
                .map(s -> s.number)
                .sorted()
                .collect(Collectors.toList());

        // ÁP DỤNG WHEELING SYSTEM
        List<List<Integer>> generatedTickets = new ArrayList<>();
        for (int[] ticketIndices : WHEEL_TEMPLATE_10_TO_6) {
            List<Integer> ticket = new ArrayList<>();
            for (int index : ticketIndices) {
                ticket.add(selected10NumbersForWheeling.get(index));
            }
            Collections.sort(ticket);
            generatedTickets.add(ticket);
        }

        // TÍNH ĐIỂM TỔNG CỦA TỪNG VÉ VÀ SẮP XẾP VÉ THEO TỶ LỆ TRÚNG GIẢM DẦN
        Map<Integer, Double> probabilityMap = selected10.stream()
                .collect(Collectors.toMap(sn -> sn.number, sn -> sn.probability));

        generatedTickets.sort((t1, t2) -> {
            double sum1 = t1.stream().mapToDouble(probabilityMap::get).sum();
            double sum2 = t2.stream().mapToDouble(probabilityMap::get).sum();
            return Double.compare(sum2, sum1); 
        });

        // GÁN NHÃN LÝ DO ĐỘNG DỰA TRÊN ĐẶC TÍNH CỦA SỐ ĐƯỢC CHỌN
        List<NumberScoreDetailDto> detailDtos = new ArrayList<>();
        for (ScoredNumber sn : selected10) {
            String tag;
            if (freqLast5[sn.number] >= 2) {
                tag = "SỐ NÓNG"; 
            } else if (freqLast5[sn.number] == 0 && sn.drawGap >= avgCycle) {
                tag = "LÔ GAN";
            } else if (freqLast5[sn.number] == 0) {
                tag = "CHỜ BÙ TRỪ";
            } else {
                boolean hasPair = selected10.stream()
                        .anyMatch(other -> other.number != sn.number && pairMatrix[sn.number][other.number] >= 2);
                tag = hasPair ? "CẶP ĐI KÈM" : "CÂN BẰNG";
            }

            double percent = Math.round(sn.probability * 1000.0) / 10.0;
            detailDtos.add(new NumberScoreDetailDto(sn.number, percent, sn.frequency, sn.drawGap, tag));
        }

        Integer recommendedSpecialNumber = null;
        List<Integer> specialHotNumbers = new ArrayList<>();
        List<String> jackpot2Pairs = new ArrayList<>();

        if ("POWER".equals(category)) {
            double bestSpecialScore = -1.0;
            for (int s = 1; s <= maxLimit; s++) {
                if (selected10NumbersForWheeling.contains(s)) continue; 

                double subsetSynergy = 0.0;
                for (int m : selected10NumbersForWheeling) {
                    subsetSynergy += (specialPairMatrix[s][m] * 1.8) + (pairMatrix[s][m] * 0.5);
                }

                double normSpecFreq = totalDraws > 0 ? ((double) specialFrequency[s] / totalDraws) : 0.15;
                double normSpecMom = specialMomentum[s] / maxSpecMom;
                int specGap = specialDrawGap[s];
                double specGapScore = (specGap >= 3 && specGap <= 12) ? 0.85 : 0.40;

                double zSpecial = (normSpecMom * 1.5) + (normSpecFreq * 1.3)
                        + (subsetSynergy / (selected10NumbersForWheeling.size() * 2.0) * 1.6)
                        + (specGapScore * 0.8) - 0.85;

                double probSpecial = 1.0 / (1.0 + Math.exp(-zSpecial));
                
                if (probSpecial > bestSpecialScore) {
                    bestSpecialScore = probSpecial;
                    recommendedSpecialNumber = s;
                }
            }

            List<Integer> allSpecialNums = new ArrayList<>();
            for (int i = 1; i <= maxLimit; i++) {
                if (specialFrequency[i] > 0) allSpecialNums.add(i);
            }
            allSpecialNums.sort((a, b) -> Integer.compare(specialFrequency[b], specialFrequency[a]));
            specialHotNumbers = allSpecialNums.stream().limit(3).collect(Collectors.toList());

            class SpMnPair {
                int sp, mn, count;
                SpMnPair(int sp, int mn, int count) { this.sp = sp; this.mn = mn; this.count = count; }
            }
            List<SpMnPair> jpList = new ArrayList<>();
            for (int s = 1; s <= maxLimit; s++) {
                for (int m = 1; m <= maxLimit; m++) {
                    if (specialPairMatrix[s][m] > 0) jpList.add(new SpMnPair(s, m, specialPairMatrix[s][m]));
                }
            }
            jpList.sort((a, b) -> Integer.compare(b.count, a.count));
            for (int p = 0; p < Math.min(3, jpList.size()); p++) {
                SpMnPair pair = jpList.get(p);
                jackpot2Pairs.add(String.format("Chính %02d • Phụ %02d (%d lần)", pair.mn, pair.sp, pair.count));
            }
        }

        List<Integer> hotNumbers = candidateList.stream()
                .sorted((a, b) -> Integer.compare(b.frequency, a.frequency)).limit(5).map(sn -> sn.number).collect(Collectors.toList());
        List<Integer> coldNumbers = candidateList.stream()
                .sorted((a, b) -> Integer.compare(b.drawGap, a.drawGap)).limit(5).map(sn -> sn.number).collect(Collectors.toList());

        List<String> frequentPairs = new ArrayList<>();
        List<PairOccur> pairList = new ArrayList<>();
        for (int i = 1; i <= maxLimit; i++) {
            for (int j = i + 1; j <= maxLimit; j++) {
                if (pairMatrix[i][j] > 0) pairList.add(new PairOccur(i, j, pairMatrix[i][j]));
            }
        }
        pairList.sort((a, b) -> Integer.compare(b.count, a.count));
        for (int p = 0; p < Math.min(3, pairList.size()); p++) {
            PairOccur po = pairList.get(p);
            frequentPairs.add(String.format("%02d - %02d (%d lần)", po.n1, po.n2, po.count));
        }

        List<DrawRecordDto> recentDraws = records.stream()
                .sorted((a, b) -> {
                    int c = b.getDrawDate().compareTo(a.getDrawDate());
                    if (c != 0) return c;
                    return Long.compare(b.getId() != null ? b.getId() : 0, a.getId() != null ? a.getId() : 0);
                })
                .limit(10)
                .map(r -> new DrawRecordDto(r.getId(), r.getDrawDate() != null ? r.getDrawDate().toString() : "", r.getNumbers(), r.getSpecialNumber(), r.getNote()))
                .collect(Collectors.toList());

        List<NumberSelectionReasonDto> selectionReasons = new ArrayList<>();
        for (NumberScoreDetailDto sn : detailDtos) {
            String title;
            String reason;
            
            if ("CHỜ BÙ TRỪ".equals(sn.getTag())) {
                title = "Điểm Rơi Bù Trừ Ngắn Hạn";
                reason = "Hoàn toàn vắng bóng trong 5 kỳ quay gần nhất. Đang rơi vào nhịp hồi quy (reversion to the mean).";
            } else if ("LÔ GAN".equals(sn.getTag())) {
                title = "Chu Kỳ Hoàn Vốn Dài Hạn";
                reason = String.format("Đã vắng bóng %d kỳ quay liên tiếp. Điểm trễ (gap) đạt ngưỡng tối ưu để xuất hiện trở lại.", sn.getDrawGap());
            } else if ("SỐ NÓNG".equals(sn.getTag())) {
                title = "Quán Tính Chuỗi (Trend)";
                reason = "Đang có đà xuất hiện cực mạnh trong ngắn hạn, thuật toán dự báo đà này chưa kết thúc.";
            } else if ("CẶP ĐI KÈM".equals(sn.getTag())) {
                title = "Cặp Số Tương Tác Đồng Hành";
                reason = "Bổ trợ gia tăng xác suất trúng thưởng nhờ có chỉ số đồng xuất hiện mạnh với các số khác trong bộ vé.";
            } else {
                title = "Cân Bằng Dải Số";
                reason = "Đóng vai trò điều tiết cấu trúc dàn trải dải số, duy trì tỷ lệ Chẵn/Lẻ hài hòa.";
            }
            
            selectionReasons.add(new NumberSelectionReasonDto(sn.getNumber(), "main", sn.getTag(), title, reason, sn.getProbabilityPercent(), sn.getFrequency(), sn.getDrawGap()));
        }

        PredictionResponseDto response = new PredictionResponseDto();
        response.setStatus("SUCCESS");
        response.setCategory(category);
        response.setLotteryType(category);
        response.setNumbers(selected10NumbersForWheeling);
        response.setTickets(generatedTickets);
        response.setSpecialNumber(recommendedSpecialNumber);
        response.setTotalDrawsAnalyzed(totalDraws);
        response.setHotNumbers(hotNumbers);
        response.setColdNumbers(coldNumbers);
        response.setSpecialHotNumbers(specialHotNumbers);
        response.setFrequentPairs(frequentPairs);
        response.setJackpot2Pairs(jackpot2Pairs);
        response.setOddEvenRatio(String.format("%d Chẵn / %d Lẻ", 10 - oddCount, oddCount));
        response.setDetails(detailDtos); 
        response.setSelectionReasons(selectionReasons);
        response.setRecentDraws(recentDraws);

        String wheelingMsg = "Hệ thống đã chọn lọc 10 số ưu tú nhất (Cân bằng giữa Số Đang Lên và Lô Gan) để nén thành 5 dãy vé tối ưu.";
        
        if ("POWER".equals(category)) {
            response.setAnalysisSummary(String.format(
                    "Phân tích %d kỳ quay Power 6/55. %s Đề xuất Banh Phụ #%02d bảo hiểm Jackpot 2.",
                    totalDraws, wheelingMsg, recommendedSpecialNumber != null ? recommendedSpecialNumber : 0));
        } else {
            response.setAnalysisSummary(String.format(
                    "Phân tích %d kỳ quay Mega 6/45. %s",
                    totalDraws, wheelingMsg));
        }
        response.setOverallReason(response.getAnalysisSummary());

        return response;
    }

    private static class ScoredNumber {
        int number;
        double probability;
        int frequency;
        int drawGap;

        ScoredNumber(int number, double probability, int frequency, int drawGap) {
            this.number = number;
            this.probability = probability;
            this.frequency = frequency;
            this.drawGap = drawGap;
        }
    }

    private static class PairOccur {
        int n1;
        int n2;
        int count;

        PairOccur(int n1, int n2, int count) {
            this.n1 = n1;
            this.n2 = n2;
            this.count = count;
        }
    }
}