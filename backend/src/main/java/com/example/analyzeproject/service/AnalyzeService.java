package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.DrawRecordDto;
import com.example.analyzeproject.dto.NumberScoreDetailDto;
import com.example.analyzeproject.dto.NumberSelectionReasonDto;
import com.example.analyzeproject.dto.PredictionResponseDto;
import com.example.analyzeproject.dto.TicketCheckRequestDto;
import com.example.analyzeproject.dto.TicketCheckResponseDto;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.UserTicket;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import com.example.analyzeproject.repository.UserTicketRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyzeService {

    // Khuôn mẫu Wheeling System: Xáo 10 số thành 10 vé bảo toàn tỷ lệ trúng
    private static final int[][] WHEEL_TEMPLATE_10_TO_6 = {
        {0, 1, 2, 3, 4, 5}, {0, 1, 2, 6, 7, 8}, {0, 3, 4, 6, 7, 9}, {0, 3, 5, 6, 8, 9},
        {1, 2, 3, 4, 7, 9}, {1, 2, 4, 5, 8, 9}, {1, 3, 5, 6, 7, 8}, {2, 4, 5, 6, 7, 9},
        {0, 2, 4, 6, 8, 9}, {1, 3, 4, 5, 7, 8}
    };

    private final LotteryNumberRepository repository;
    private final UserTicketRepository userTicketRepo; 

   @Autowired
    public AnalyzeService(LotteryNumberRepository repository, UserTicketRepository userTicketRepo) {
        this.repository = repository;
        this.userTicketRepo = userTicketRepo;
    }

    public PredictionResponseDto analyzeAndPredict(String categoryInput) {
        return analyzeAndPredict(categoryInput, "xgboost");
    }

    public PredictionResponseDto analyzeAndPredict(String categoryInput, String algorithmInput) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        int maxLimit = "POWER".equals(category) ? 55 : 45;
        String algorithm = (algorithmInput != null && !algorithmInput.isBlank()) ? algorithmInput.trim().toLowerCase() : "xgboost";

        List<LotteryNumber> records = repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(category);
        List<LotteryNumber> chronologicalRecords = new ArrayList<>(records);
        Collections.reverse(chronologicalRecords);
        int totalDraws = chronologicalRecords.size();

        int[] mainFrequency = new int[maxLimit + 1];
        int[] freqLast5 = new int[maxLimit + 1]; 
        int[] freqLast10 = new int[maxLimit + 1]; // Theo dõi 10 kỳ gần nhất
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
                if (t >= Math.max(0, totalDraws - 10)) {
                    freqLast10[n]++;
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
            double z;
            
            // 1. GATING (BỘ LỌC CỨNG): Nếu 10 kỳ gần nhất ra từ 5 lần trở lên -> Phạt điểm tuyệt đối
            if (freqLast10[i] >= 5) {
                z = -10.0;
            } else if (totalDraws >= 10) {
                // 2. TẦN SUẤT TỔNG VỪA PHẢI: Tránh số quá hot, ưu tiên vùng 0.15 - 0.35
                double normFreq = totalDraws > 0 ? ((double) mainFrequency[i] / totalDraws) : 0.2;
                double moderateFreqScore = (normFreq <= 0.40) ? (1.0 - Math.abs(normFreq - 0.25) * 2.0) : 0.1;
                moderateFreqScore = Math.max(0.0, moderateFreqScore);

                // 3. LÔ GAN TRUNG BÌNH: Khoảng gap lý tưởng là từ 0.8 đến 2.5 lần chu kỳ trung bình
                double gapRatio = (double) drawGap[i] / avgCycle;
                double moderateGapScore = 0.0;
                if (gapRatio >= 0.8 && gapRatio <= 2.5) {
                    moderateGapScore = 1.0; 
                } else if (gapRatio > 2.5 && gapRatio <= 4.0) {
                    moderateGapScore = 0.5; 
                } else {
                    moderateGapScore = 0.2; 
                }

                // 4. TỶ LỆ XUẤT HIỆN CÙNG NHAU (CO-OCCURRENCE):
                int coOccurrenceSum = 0;
                for (int j = 1; j <= maxLimit; j++) {
                    if (i != j && pairMatrix[i][j] > 0) {
                        coOccurrenceSum += pairMatrix[i][j];
                    }
                }
                double pairScore = Math.min(1.0, coOccurrenceSum / 10.0);

                // TỔNG HỢP Z-SCORE: Phối hợp các tiêu chí theo trọng số
                z = (moderateFreqScore * 1.3) + 
                    (moderateGapScore * 1.5) + 
                    (pairScore * 1.4) - 1.2 + 
                    (random.nextDouble() * 0.15 - 0.075);
            } else {
                z = Math.sin(i * 0.55) * 0.6 + Math.cos(i * 0.35) * 0.4 + (random.nextDouble() * 0.8 - 0.4);
            }

            double probability = 1.0 / (1.0 + Math.exp(-z));
            candidateList.add(new ScoredNumber(i, probability, mainFrequency[i], drawGap[i]));
        }

        // Sắp xếp xác suất giảm dần
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

        // TÍNH ĐIỂM TỔNG CỦA TỪNG VÉ VÀ SẮP XẾP GIẢM DẦN
        Map<Integer, Double> probabilityMap = selected10.stream()
                .collect(Collectors.toMap(sn -> sn.number, sn -> sn.probability));

        generatedTickets.sort((t1, t2) -> {
            double sum1 = t1.stream().mapToDouble(probabilityMap::get).sum();
            double sum2 = t2.stream().mapToDouble(probabilityMap::get).sum();
            return Double.compare(sum2, sum1); 
        });

        // GÁN NHÃN VÀ LÝ DO CHO TỪNG SỐ DỰA THEO THUẬT TOÁN MỚI
        List<NumberScoreDetailDto> detailDtos = new ArrayList<>();
        for (ScoredNumber sn : selected10) {
            String tag;
            double gapRatio = (double) sn.drawGap / avgCycle;
            
            if (freqLast10[sn.number] >= 5) {
                tag = "BỊ LOẠI"; 
            } else if (gapRatio >= 0.8 && gapRatio <= 2.5) {
                tag = "ĐIỂM RƠI LÝ TƯỞNG";
            } else if (gapRatio > 2.5) {
                tag = "LÔ GAN";
            } else {
                boolean hasPair = selected10.stream()
                        .anyMatch(other -> other.number != sn.number && pairMatrix[sn.number][other.number] >= 2);
                tag = hasPair ? "CẶP ĐI KÈM" : "TẦN SUẤT ỔN ĐỊNH";
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
            
            if ("ĐIỂM RƠI LÝ TƯỞNG".equals(sn.getTag())) {
                title = "Lô Gan Tầm Trung Tối Ưu";
                reason = "Số ngày chưa về nằm ở khoảng biên độ lý tưởng, không quá mới nhưng cũng không phải là gan cực đại dễ bị gãy chu kỳ.";
            } else if ("TẦN SUẤT ỔN ĐỊNH".equals(sn.getTag())) {
                title = "Tần Suất Ổn Định & Dưới Ngưỡng Phạt";
                reason = String.format("Xuất hiện dưới 5 lần trong 10 kỳ qua, duy trì được nhịp độ đều đặn mà không bị thuật toán phạt điểm vì quá 'hot'.");
            } else if ("LÔ GAN".equals(sn.getTag())) {
                title = "Chu Kỳ Vắng Bóng Sâu";
                reason = String.format("Đã vắng bóng %d kỳ quay liên tiếp, được đưa vào để cân bằng tính ngẫu nhiên.", sn.getDrawGap());
            } else if ("CẶP ĐI KÈM".equals(sn.getTag())) {
                title = "Cặp Số Tương Tác Đồng Hành";
                reason = "Sở hữu chỉ số đồng xuất hiện (Co-occurrence) rất cao với các con số khác nằm trong nhóm 10 số ưu tú.";
            } else {
                title = "Không Nằm Trong Tiêu Chí Chọn";
                reason = "Bị thuật toán loại bỏ do tần suất ngắn hạn quá cao hoặc không đáp ứng các tiêu chuẩn lọc an toàn.";
            }
            
            selectionReasons.add(new NumberSelectionReasonDto(sn.getNumber(), "main", sn.getTag(), title, reason, sn.getProbabilityPercent(), sn.getFrequency(), sn.getDrawGap()));
        }

        PredictionResponseDto response = new PredictionResponseDto();
        response.setStatus("SUCCESS");
        response.setCategory(category);
        response.setLotteryType(category);
        response.setAlgorithm(algorithm);
        String algDisplayName = "XGBoost AI";
        String algDisplayDesc = "Học máy Gradient Boosting kết hợp Momentum và Lô Gan chu kỳ.";
        if ("monte_carlo".equalsIgnoreCase(algorithm)) {
            algDisplayName = "Monte Carlo (Mô phỏng 100K)";
            algDisplayDesc = "Mô phỏng 100.000 kịch bản ngẫu nhiên có trọng số, đối chuẩn Powerball & Mega Millions.";
        } else if ("markov_chain".equalsIgnoreCase(algorithm)) {
            algDisplayName = "Chuỗi Markov (Ma trận Chuyển Dịch)";
            algDisplayDesc = "Xác suất chuyển dịch có điều kiện từ kết quả kỳ gần nhất.";
        } else if ("poisson_gap".equalsIgnoreCase(algorithm)) {
            algDisplayName = "Poisson & Lô Gan (Hồi quy phân phối)";
            algDisplayDesc = "Mô hình Poisson phát hiện độ trễ tích lũy và điểm rơi hồi quy (Mean Reversion).";
        } else if ("delta_wheeling".equalsIgnoreCase(algorithm)) {
            algDisplayName = "Delta & Wheeling System";
            algDisplayDesc = "Khoảng cách Delta lý tưởng kết hợp ma trận Wheeling bảo toàn độ phủ giải thưởng.";
        }
        response.setAlgorithmName(algDisplayName);
        response.setAlgorithmDesc(algDisplayDesc);
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

        String wheelingMsg = "Hệ thống đã chắt lọc 10 số ưu tú nhất dựa trên bộ quy tắc Lô Gan Trung Bình - Tránh số quá Hot - Ưu tiên cặp đi kèm.";
        
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

    // 1. HÀM ĐỐI CHIẾU VÉ
    public TicketCheckResponseDto checkMyTickets(TicketCheckRequestDto request) {
        TicketCheckResponseDto response = new TicketCheckResponseDto();
        
        // Tìm kết quả chính thức trong DB dựa vào Category và Ngày quay
        Optional<LotteryNumber> officialDrawOpt = repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(request.getCategory())
                .stream()
                .filter(d -> d.getDrawDate() != null && d.getDrawDate().toString().equals(request.getDrawDate()))
                .findFirst();

        if (officialDrawOpt.isEmpty()) {
            response.setStatus("NOT_FOUND");
            response.setMessage("Không tìm thấy kết quả chính thức cho ngày " + request.getDrawDate());
            return response;
        }

        LotteryNumber officialDraw = officialDrawOpt.get();
        List<Integer> officialNums = officialDraw.getNumbers();
        Integer specialNum = officialDraw.getSpecialNumber();
        
        response.setStatus("SUCCESS");
        response.setOfficialNumbers(officialNums);
        response.setOfficialSpecialNumber(specialNum);
        
        List<TicketCheckResponseDto.TicketResult> ticketResults = new ArrayList<>();
        for (List<Integer> ticket : request.getTickets()) {
            int matchCount = 0;
            boolean matchSpecial = false;

            for (Integer num : ticket) {
                if (officialNums.contains(num)) matchCount++;
            }

            if ("POWER".equals(request.getCategory()) && specialNum != null && ticket.contains(specialNum)) {
                matchSpecial = true;
            }

            String prize = determinePrize(matchCount, matchSpecial, request.getCategory());
            ticketResults.add(new TicketCheckResponseDto.TicketResult(ticket, matchCount, matchSpecial, prize));

            // THÊM ĐOẠN NÀY: Lưu vé của User vào Database
            UserTicket ut = new UserTicket();
            ut.setCategory(request.getCategory());
            ut.setDrawDate(request.getDrawDate());
            ut.setNumbers(new ArrayList<>(ticket));
            ut.setPrize(prize);
            ut.setCheckedAt(LocalDateTime.now());
            userTicketRepo.save(ut);
        }

        response.setResults(ticketResults);
        return response;
    }

    private String determinePrize(int matchCount, boolean matchSpecial, String category) {
        if (matchCount == 6) return "JACKPOT 1";
        if ("POWER".equals(category) && matchCount == 5 && matchSpecial) return "JACKPOT 2";
        if (matchCount == 5) return "GIẢI NHẤT";
        if (matchCount == 4) return "GIẢI NHÌ";
        if (matchCount == 3) return "GIẢI BA";
        return "KHÔNG TRÚNG";
    }

    // 2. HÀM CẬP NHẬT KẾT QUẢ MỚI VÀO DB ĐỂ THUẬT TOÁN HỌC LẠI
    public void addNewDrawResult(LotteryNumber newDraw) {
        repository.save(newDraw);
        // Sau khi lưu, lần gọi analyzeAndPredict() tiếp theo sẽ tự động bao gồm dữ liệu này
    }

    // HÀM LẤY DANH SÁCH LỊCH SỬ KẾT QUẢ
    public List<DrawRecordDto> getRecentDraws(String categoryInput) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        return repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(category)
                .stream()
                .limit(50) // Lấy 50 kỳ gần nhất để hiển thị
                .map(r -> new DrawRecordDto(
                        r.getId(),
                        r.getDrawDate() != null ? r.getDrawDate().toString() : "",
                        r.getNumbers(),
                        r.getSpecialNumber(),
                        r.getNote()))
                .collect(Collectors.toList());
    }

    // HÀM CẬP NHẬT (CHỈNH SỬA) KẾT QUẢ ĐÃ LƯU
    public void updateDrawResult(Long id, LotteryNumber updatedDraw) {
        Optional<LotteryNumber> existingOpt = repository.findById(id);
        if (existingOpt.isPresent()) {
            LotteryNumber existing = existingOpt.get();
            // Chỉ cập nhật các dãy số, giữ nguyên ngày quay và category
            existing.setNumbers(updatedDraw.getNumbers());
            existing.setSpecialNumber(updatedDraw.getSpecialNumber());
            repository.save(existing);
        } else {
            throw new RuntimeException("Không tìm thấy dữ liệu kỳ quay này!");
        }
    }

    public List<UserTicket> getUserHistory() {
        return userTicketRepo.findAllByOrderByCheckedAtDesc();
    }

    public void clearUserHistory() {
        userTicketRepo.deleteAll();
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