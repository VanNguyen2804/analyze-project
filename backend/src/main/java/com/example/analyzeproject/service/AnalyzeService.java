package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.*;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.model.UserTicket;
import com.example.analyzeproject.model.AlgorithmHyperparameter;
import com.example.analyzeproject.model.LotteryDeviationVariable;
import com.example.analyzeproject.repository.LotteryNumberRepository;
import com.example.analyzeproject.repository.UserTicketRepository;
import com.example.analyzeproject.repository.AlgorithmHyperparameterRepository;
import com.example.analyzeproject.repository.LotteryDeviationVariableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyzeService {

    private static final int[][] WHEEL_TEMPLATE_10_TO_6 = {
        {0, 1, 2, 3, 4, 5}, {0, 1, 2, 6, 7, 8}, {0, 3, 4, 6, 7, 9}, {0, 3, 5, 6, 8, 9},
        {1, 2, 3, 4, 7, 9}, {1, 2, 4, 5, 8, 9}, {1, 3, 5, 6, 7, 8}, {2, 4, 5, 6, 7, 9},
        {0, 2, 4, 6, 8, 9}, {1, 3, 4, 5, 7, 8}
    };

    private final LotteryNumberRepository repository;
    private final UserTicketRepository userTicketRepo;
    private final AlgorithmHyperparameterRepository hyperparameterRepo;
    private final LotteryDeviationVariableRepository devVarRepo;

    @Autowired
    public AnalyzeService(LotteryNumberRepository repository, UserTicketRepository userTicketRepo,
                          AlgorithmHyperparameterRepository hyperparameterRepo,
                          @Autowired(required = false) LotteryDeviationVariableRepository devVarRepo) {
        this.repository = repository;
        this.userTicketRepo = userTicketRepo;
        this.hyperparameterRepo = hyperparameterRepo;
        this.devVarRepo = devVarRepo;
    }

    public Map<String, Object> analyzeOfficialDraw(String categoryInput, String date, String algorithm) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        int maxLimit = "POWER".equals(category) ? 55 : 45;

        List<LotteryNumber> records = eligibleHistory(category);
        if (records.isEmpty()) {
            throw new RuntimeException("Chưa có dữ liệu xổ số trong Database.");
        }

        LotteryNumber targetDraw = records.get(0);
        if (date != null && !date.trim().isEmpty()) {
            targetDraw = records.stream()
                .filter(r -> r.getDrawDate() != null && r.getDrawDate().toString().equals(date.trim()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kết quả cho ngày " + date));
        }

        List<Integer> winningNumbers = targetDraw.getNumbers();
        Integer specialNumber = targetDraw.getSpecialNumber();
        
        int targetIndex = records.indexOf(targetDraw);
        List<LotteryNumber> pastRecords = new ArrayList<>(records.subList(targetIndex + 1, records.size()));
        Collections.reverse(pastRecords);
        int totalDraws = pastRecords.size();

        int[] frequency = new int[maxLimit + 1];
        int[] drawGap = new int[maxLimit + 1];
        double[] momentum = new double[maxLimit + 1];
        int[][] pairMatrix = new int[maxLimit + 1][maxLimit + 1];
        
        Arrays.fill(drawGap, totalDraws);

        for (int t = 0; t < totalDraws; t++) {
            List<Integer> nums = pastRecords.get(t).getNumbers();
            if (nums == null) continue;
            // UPDATE: Hệ số suy giảm quán tính chuỗi (Momentum Decay Rate λ) tăng lên 0.16
            double weight = Math.exp(-0.16 * (totalDraws - 1 - t));

            for (int i = 0; i < nums.size(); i++) {
                int n = nums.get(i);
                if (n < 1 || n > maxLimit) continue;
                frequency[n]++;
                momentum[n] += weight;
                drawGap[n] = (totalDraws - 1) - t; 

                for (int j = i + 1; j < nums.size(); j++) {
                    int n2 = nums.get(j);
                    if (n2 >= 1 && n2 <= maxLimit) {
                        pairMatrix[n][n2]++;
                        pairMatrix[n2][n]++;
                    }
                }
            }
        }

        double maxMom = Arrays.stream(momentum).max().orElse(1.0);
        if (maxMom == 0) maxMom = 1.0;

        List<ScoredNumber> allScored = new ArrayList<>();
        for (int i = 1; i <= maxLimit; i++) {
            double normFreq = totalDraws > 0 ? ((double) frequency[i] / totalDraws) : 0.2;
            double normMom = momentum[i] / maxMom;
            double z = (normMom * 1.5) + (normFreq * 1.2) - 1.0;
            double prob = 1.0 / (1.0 + Math.exp(-z));
            allScored.add(new ScoredNumber(i, prob, frequency[i], drawGap[i]));
        }
        allScored.sort((a, b) -> Double.compare(b.probability, a.probability));

        List<NumberSelectionReasonDto> selectionReasons = new ArrayList<>();
        
        for (int wNum : winningNumbers) {
            if (wNum < 1 || wNum > maxLimit) continue;
            
            int rank = 1;
            double prob = 0.0;
            for (int i = 0; i < allScored.size(); i++) {
                if (allScored.get(i).number == wNum) {
                    rank = i + 1;
                    prob = allScored.get(i).probability;
                    break;
                }
            }
            
            List<PairOccur> pairs = new ArrayList<>();
            for (int j = 1; j <= maxLimit; j++) {
                if (pairMatrix[wNum][j] > 0) pairs.add(new PairOccur(wNum, j, pairMatrix[wNum][j]));
            }
            pairs.sort((a, b) -> Integer.compare(b.count, a.count));
            String pairedStr = pairs.stream().limit(3).map(p -> String.valueOf(p.n2)).collect(Collectors.joining(", "));

            NumberSelectionReasonDto dto = new NumberSelectionReasonDto();
            dto.setNumber(wNum);
            dto.setRole("main");
            dto.setProbabilityPercent(Math.round(prob * 1000.0) / 10.0);
            dto.setRank(rank);
            dto.setFrequency(frequency[wNum]);
            dto.setDrawGap(drawGap[wNum]);
            dto.setMomentum(Math.round((momentum[wNum] / maxMom) * 100.0) / 100.0);
            int transitions = 0, matches = 0;
            List<Integer> previousNumbers = totalDraws > 0 ? pastRecords.get(totalDraws - 1).getNumbers() : Collections.emptyList();
            for (int t = 1; t < totalDraws; t++) {
                List<Integer> previous = pastRecords.get(t - 1).getNumbers();
                List<Integer> next = pastRecords.get(t).getNumbers();
                if (previous == null || next == null) continue;
                for (int seed : previousNumbers) {
                    if (previous.contains(seed)) {
                        transitions++;
                        if (next.contains(wNum)) matches++;
                    }
                }
            }
            dto.setMarkov(transitions == 0 ? 0 : (int) Math.round(100.0 * matches / transitions));
            dto.setPoisson((int) Math.round(100 * (1 - Math.pow(1 - 6.0 / maxLimit, drawGap[wNum]))));
            dto.setCompanion(frequency[wNum] == 0 || pairs.isEmpty() ? 0
                    : (int) Math.round(100.0 * pairs.get(0).count / frequency[wNum]));
            dto.setPairedNumbers(pairedStr.isEmpty() ? "N/A" : pairedStr);

            if (drawGap[wNum] > 10) {
                dto.setTag("CẦU NỐI PHÂN VÙNG");
                dto.setTitle("Điểm Rơi Chu Kỳ & Nhịp Dao Động Điều Hòa");
                dto.setReason("Số " + wNum + " giữ vai trò bù lấp khoảng trống phân vùng, với nhịp dao động điều hòa sau chu kỳ gan dài.");
            } else {
                dto.setTag("SỐ NÓNG TRỰC TÂM");
                dto.setTitle("Hạt Nhân Chu Kỳ Ngắn & Tần Suất Ổn Định");
                dto.setReason("Số " + wNum + " là hạt nhân tần suất với lực quán tính mạnh, duy trì điểm rơi cực tốt trong khoảng gap = " + drawGap[wNum] + " kỳ.");
            }
            selectionReasons.add(dto);
        }

        int sum = winningNumbers.stream().mapToInt(Integer::intValue).sum();
        int oddCount = (int) winningNumbers.stream().filter(n -> n % 2 != 0).count();
        int evenCount = winningNumbers.size() - oddCount;

        Map<String, Object> response = new HashMap<>();
        response.put("drawDate", targetDraw.getDrawDate().toString());
        response.put("numbers", winningNumbers);
        response.put("specialNumber", specialNumber);
        response.put("sum", sum);
        response.put("oddEvenRatio", evenCount + " Chẵn / " + oddCount + " Lẻ");
        response.put("algorithmName", "AI " + algorithm + " Analysis");
        response.put("selectionReasons", selectionReasons);
        
        return response;
    }

    // =========================================================================================
    // 2. CHỨC NĂNG DỰ ĐOÁN (XGBOOST + WHEELING)
    // =========================================================================================
    public PredictionResponseDto analyzeAndPredict(String categoryInput, String algorithm) {
        return analyzeAndPredict(categoryInput, algorithm, (String) null);
    }

    public PredictionResponseDto analyzeAndPredict(String categoryInput, String algorithm, String algorithm2) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        List<LotteryNumber> history = eligibleHistory(category);
        PredictionResponseDto result = analyzeAndPredict(category, algorithm, algorithm2, history);
        if (result.getRelationshipLearning() != null) result.getRelationshipLearning().put("verifiedWinningTickets", verifiedWinningTickets(history, category));
        return result;
    }

    public PredictionResponseDto analyzeAndPredict(String categoryInput, String algorithm, List<LotteryNumber> customRecords) {
        return analyzeAndPredict(categoryInput, algorithm, null, customRecords);
    }

    public PredictionResponseDto analyzeAndPredict(String categoryInput, String algorithm, String algorithm2, List<LotteryNumber> customRecords) {
        String category = "POWER".equalsIgnoreCase(categoryInput == null ? "" : categoryInput.trim()) ? "POWER" : "MEGA";
        return analyzeAndPredictWithConfiguration(categoryInput, algorithm, algorithm2, customRecords, getLatestHyperparameter(category));
    }

    public PredictionResponseDto analyzeAndPredictWithConfiguration(String categoryInput, String algorithm, String algorithm2,
            List<LotteryNumber> customRecords, AlgorithmHyperparameter capturedConfiguration) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        int maxLimit = "POWER".equals(category) ? 55 : 45;

        AlgorithmHyperparameter latestHyp = capturedConfiguration;
        double decayRate = 0.16;
        double gapMinRatio = 0.8;
        double gapMaxRatio = 2.2;
        double coOccurrenceWeight = 0.85;
        double repeatExhaustionPenalty = -0.45;
        double extremeGanBonus = 0.85;
        double specialMigrationWeight = 0.75;
        double adaptiveRepeatWeight = 0.65;
        int sumMin = "POWER".equals(category) ? 75 : 84;
        int sumMax = "POWER".equals(category) ? 195 : 144;

        if (latestHyp != null && latestHyp.getHyperparametersJson() != null) {
            String json = latestHyp.getHyperparametersJson();
            decayRate = parseDoubleFromJson(json, "momentumDecayRate", decayRate);
            gapMinRatio = parseDoubleFromJson(json, "poissonGapMinRatio", gapMinRatio);
            gapMaxRatio = parseDoubleFromJson(json, "poissonGapMaxRatio", gapMaxRatio);
            coOccurrenceWeight = parseDoubleFromJson(json, "coOccurrenceWeight", coOccurrenceWeight);
            repeatExhaustionPenalty = parseDoubleFromJson(json, "repeatExhaustionPenalty", repeatExhaustionPenalty);
            extremeGanBonus = parseDoubleFromJson(json, "extremeGanReboundBonus", extremeGanBonus);
            specialMigrationWeight = parseDoubleFromJson(json, "specialToMainMigrationWeight", specialMigrationWeight);
            adaptiveRepeatWeight = parseDoubleFromJson(json, "adaptiveRepeatWeight", adaptiveRepeatWeight);
        }

        List<LotteryNumber> source = customRecords != null ? customRecords : eligibleHistory(category);
        List<LotteryNumber> records = DrawQuality.history(source).stream()
                .filter(r -> r != null && category.equalsIgnoreCase(r.getCategory()) && r.getDrawDate() != null)
                .filter(r -> r.getNumbers() != null && r.getNumbers().size() == 6
                        && new HashSet<>(r.getNumbers()).size() == 6
                        && r.getNumbers().stream().allMatch(n -> n != null && n >= 1 && n <= maxLimit))
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate).reversed()
                        .thenComparing(LotteryNumber::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        Set<java.time.LocalDate> seenDates = new HashSet<>();
        records.removeIf(r -> !seenDates.add(r.getDrawDate()));
        if (records.isEmpty()) {
            PredictionResponseDto empty = new PredictionResponseDto();
            empty.setStatus("ERROR");
            empty.setMessage("No valid draw history available for " + category);
            empty.setCategory(category);
            empty.setLotteryType(category);
            empty.setNumbers(Collections.emptyList());
            empty.setTickets(Collections.emptyList());
            empty.setTotalDrawsAnalyzed(0);
            return empty;
        }
        List<LotteryNumber> chronologicalRecords = new ArrayList<>(records);
        Collections.reverse(chronologicalRecords);
        int totalDraws = chronologicalRecords.size();
        String learningJson = latestHyp == null ? null : latestHyp.getHyperparametersJson();
        TemporalRelationshipModel.Result learned = TemporalRelationshipModel.fit(records, category,
                parseDoubleFromJson(learningJson, "temporalPriorStrength", maxLimit / 6.0),
                parseDoubleFromJson(learningJson, "temporalLearningRate", 0.5),
                (int) parseDoubleFromJson(learningJson, "temporalMinHistory", 5));

        int[] mainFrequency = new int[maxLimit + 1];
        int[] freqLast10 = new int[maxLimit + 1];
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
        int[][] transitionMatrix = new int[maxLimit + 1][maxLimit + 1];

        List<List<Integer>> empiricalGaps = new ArrayList<>();
        for (int i = 0; i <= maxLimit; i++) empiricalGaps.add(new ArrayList<>());
        int[] ballLastSeen = new int[maxLimit + 1];
        Arrays.fill(ballLastSeen, -1);

        for (int t = 0; t < totalDraws; t++) {
            LotteryNumber draw = chronologicalRecords.get(t);
            List<Integer> nums = draw.getNumbers();
            if (nums == null) continue;

            List<Integer> validNums = nums.stream()
                    .filter(n -> n != null && n >= 1 && n <= maxLimit)
                    .distinct()
                    .collect(Collectors.toList());

            // Tự động sử dụng hệ số suy giảm quán tính chuỗi từ siêu tham số đang kích hoạt
            double weight = Math.exp(-decayRate * (totalDraws - 1 - t));

            for (int n : validNums) {
                mainFrequency[n]++;
                lastSeenMain[n] = t;
                mainMomentum[n] += weight;
                
                if (t >= Math.max(0, totalDraws - 10)) {
                    freqLast10[n]++;
                }
                if (t >= Math.max(0, totalDraws - 5)) {
                    freqLast5[n]++;
                }
                if (ballLastSeen[n] != -1) {
                    empiricalGaps.get(n).add(t - ballLastSeen[n]);
                }
                ballLastSeen[n] = t;
            }

            for (int i = 0; i < validNums.size(); i++) {
                for (int j = i + 1; j < validNums.size(); j++) {
                    int n1 = validNums.get(i);
                    int n2 = validNums.get(j);
                    pairMatrix[n1][n2]++;
                    pairMatrix[n2][n1]++;
                }
            }

            // Chuyển dịch Markov giữa các kỳ liên tiếp t và t + 1
            if (t < totalDraws - 1) {
                LotteryNumber nextDraw = chronologicalRecords.get(t + 1);
                if (nextDraw != null && nextDraw.getNumbers() != null) {
                    List<Integer> nextValid = nextDraw.getNumbers().stream()
                            .filter(n -> n != null && n >= 1 && n <= maxLimit)
                            .distinct()
                            .collect(Collectors.toList());
                    for (int currN : validNums) {
                        for (int nextN : nextValid) {
                            transitionMatrix[currN][nextN]++;
                        }
                    }
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

        double avgCycle = (double) maxLimit / 6.0;

        // Chuẩn bị các cấu trúc cho DSE-Copula
        double[] empiricalMeanGap = new double[maxLimit + 1];
        double[] empiricalStdGap = new double[maxLimit + 1];
        Arrays.fill(empiricalMeanGap, avgCycle);
        Arrays.fill(empiricalStdGap, 2.5);

        for (int i = 1; i <= maxLimit; i++) {
            List<Integer> gaps = empiricalGaps.get(i);
            if (!gaps.isEmpty()) {
                double mean = gaps.stream().mapToInt(Integer::intValue).average().orElse(avgCycle);
                empiricalMeanGap[i] = mean;
                if (gaps.size() > 1) {
                    double varSum = 0;
                    for (int g : gaps) varSum += Math.pow(g - mean, 2);
                    empiricalStdGap[i] = Math.max(1.0, Math.sqrt(varSum / (gaps.size() - 1)));
                } else {
                    empiricalStdGap[i] = Math.max(1.0, mean * 0.45);
                }
            }
        }

        double[][] jaccardMatrix = new double[maxLimit + 1][maxLimit + 1];
        double[] copulaCentrality = new double[maxLimit + 1];
        for (int i = 1; i <= maxLimit; i++) {
            for (int j = 1; j <= maxLimit; j++) {
                if (i != j) {
                    int coCount = pairMatrix[i][j];
                    int unionCount = mainFrequency[i] + mainFrequency[j] - coCount;
                    if (unionCount > 0 && coCount > 0) {
                        jaccardMatrix[i][j] = (double) coCount / unionCount;
                    }
                }
            }
        }
        for (int i = 1; i <= maxLimit; i++) {
            double cScore = 0;
            for (int j = 1; j <= maxLimit; j++) {
                if (i != j && jaccardMatrix[i][j] > 0) {
                    double partnerWeight = (mainMomentum[j] / maxMainMom) * 0.6 + ((double) mainFrequency[j] / Math.max(1, totalDraws)) * 0.4;
                    cScore += jaccardMatrix[i][j] * partnerWeight;
                }
            }
            copulaCentrality[i] = cScore;
        }
        double maxCopula = 0.01;
        for (int i = 1; i <= maxLimit; i++) {
            if (copulaCentrality[i] > maxCopula) maxCopula = copulaCentrality[i];
        }

        List<Integer> drawT1 = (totalDraws >= 1 && chronologicalRecords.get(totalDraws - 1).getNumbers() != null)
                ? chronologicalRecords.get(totalDraws - 1).getNumbers() : Collections.emptyList();
        List<Integer> drawT2 = (totalDraws >= 2 && chronologicalRecords.get(totalDraws - 2).getNumbers() != null)
                ? chronologicalRecords.get(totalDraws - 2).getNumbers() : Collections.emptyList();
        double[] markov2Score = new double[maxLimit + 1];
        for (int i = 1; i <= maxLimit; i++) {
            double t1Sum = 0;
            for (int prev : drawT1) {
                if (prev >= 1 && prev <= maxLimit) {
                    t1Sum += (double) transitionMatrix[prev][i] / Math.max(1, mainFrequency[prev]);
                }
            }
            double t2Sum = 0;
            for (int prev : drawT2) {
                if (prev >= 1 && prev <= maxLimit) {
                    t2Sum += (double) transitionMatrix[prev][i] / Math.max(1, mainFrequency[prev]);
                }
            }
            markov2Score[i] = t1Sum * 0.70 + t2Sum * 0.30;
        }

        double[] specialMigrationScore = new double[maxLimit + 1];
        if ("POWER".equalsIgnoreCase(category)) {
            Integer lastSpec = totalDraws > 0 ? chronologicalRecords.get(totalDraws - 1).getSpecialNumber() : null;
            Integer last2Spec = totalDraws > 1 ? chronologicalRecords.get(totalDraws - 2).getSpecialNumber() : null;
            if (lastSpec != null && lastSpec >= 1 && lastSpec <= maxLimit) specialMigrationScore[lastSpec] += 0.88;
            if (last2Spec != null && last2Spec >= 1 && last2Spec <= maxLimit) specialMigrationScore[last2Spec] += 0.42;
        }

        boolean isBayesianGraph = (algorithm != null && (algorithm.toLowerCase().contains("bayes") || algorithm.toLowerCase().contains("graph") || algorithm.toLowerCase().contains("begn")));
        boolean isDeepStacking = (algorithm == null || algorithm.toLowerCase().contains("deep") || algorithm.toLowerCase().contains("stack") || algorithm.equalsIgnoreCase("dse_copula"));

        List<ScoredNumber> candidateList = scoreCandidatesForAlgorithm(
                algorithm, category, maxLimit, totalDraws, chronologicalRecords,
                mainFrequency, freqLast5, freqLast10, mainMomentum, drawGap, pairMatrix,
                lastSeenSpecial, avgCycle, maxMainMom, extremeGanBonus, specialMigrationWeight,
                adaptiveRepeatWeight, coOccurrenceWeight, markov2Score, specialMigrationScore,
                empiricalMeanGap, empiricalStdGap, copulaCentrality, maxCopula, drawT1, learned
        );

        // 1. STRATIFIED MULTI-PILLAR CANDIDATE SELECTION
        int zone1End = (int) Math.round((double) maxLimit / 3.0);
        int zone2End = (int) Math.round(((double) maxLimit * 2.0) / 3.0);

        Set<Integer> pickedNumberSet = new HashSet<>();
        List<ScoredNumber> selected10 = new ArrayList<>();
        int[] oddCountArr = new int[1];
        int[] evenCountArr = new int[1];

        java.util.function.BiPredicate<ScoredNumber, Integer[]> tryAdd = (c, limits) -> {
            if (pickedNumberSet.contains(c.number)) return false;
            boolean isOdd = (c.number % 2 != 0);
            if (isOdd && oddCountArr[0] >= limits[0]) return false;
            if (!isOdd && evenCountArr[0] >= limits[1]) return false;
            selected10.add(c);
            pickedNumberSet.add(c.number);
            if (isOdd) oddCountArr[0]++; else evenCountArr[0]++;
            return true;
        };

        // A. Special Migration: Nếu có bóng phụ kỳ trước chuyển vị mạnh
        if ("POWER".equalsIgnoreCase(category)) {
            List<ScoredNumber> specialCands = candidateList.stream()
                .filter(c -> specialDrawGap[c.number] <= 1)
                .sorted((a, b) -> Double.compare(b.probability, a.probability))
                .collect(Collectors.toList());
            for (ScoredNumber c : specialCands) {
                if (tryAdd.test(c, new Integer[]{5, 5})) break;
            }
        }

        // B. Hot / Repeat numbers (gap <= 2): Lấy 2-3 số nóng nhất có xung lực cao
        List<ScoredNumber> hotCands = candidateList.stream()
            .filter(c -> c.drawGap <= 2)
            .sorted((a, b) -> Double.compare(b.probability, a.probability))
            .collect(Collectors.toList());
        for (ScoredNumber c : hotCands) {
            if (selected10.size() >= 4) break;
            tryAdd.test(c, new Integer[]{5, 5});
        }

        // C. Điểm rơi Poisson (gap 3..9): Phân bổ đều cho các phân vùng Zone 1, Zone 2, Zone 3
        for (int z : new int[]{1, 2, 3}) {
            List<ScoredNumber> zonePoisson = candidateList.stream()
                .filter(c -> {
                    int cz = (c.number <= zone1End ? 1 : (c.number <= zone2End ? 2 : 3));
                    return cz == z && c.drawGap >= 3 && c.drawGap <= 9;
                })
                .sorted((a, b) -> Double.compare(b.probability, a.probability))
                .collect(Collectors.toList());
            for (ScoredNumber c : zonePoisson) {
                if (selected10.size() >= 7) break;
                if (tryAdd.test(c, new Integer[]{5, 5})) break;
            }
        }

        // Bổ sung thêm các số Poisson có điểm rơi cao nhất chưa được chọn
        List<ScoredNumber> remainingPoisson = candidateList.stream()
            .filter(c -> c.drawGap >= 3 && c.drawGap <= 9)
            .sorted((a, b) -> Double.compare(b.probability, a.probability))
            .collect(Collectors.toList());
        for (ScoredNumber c : remainingPoisson) {
            if (selected10.size() >= 8) break;
            tryAdd.test(c, new Integer[]{5, 5});
        }

        // D. Lô Gan Cực Hạn (gap >= 10): Đón đầu hồi quy trung bình đa phân vùng (Zone 2, Zone 1, Zone 3)
        for (int z : new int[]{2, 1, 3}) {
            List<ScoredNumber> zoneGan = candidateList.stream()
                .filter(c -> {
                    int cz = (c.number <= zone1End ? 1 : (c.number <= zone2End ? 2 : 3));
                    return cz == z && c.drawGap >= 10;
                })
                .sorted((a, b) -> {
                    int gapComp = Integer.compare(b.drawGap, a.drawGap);
                    return gapComp != 0 ? gapComp : Double.compare(b.probability, a.probability);
                })
                .collect(Collectors.toList());
            for (ScoredNumber c : zoneGan) {
                if (selected10.size() >= 10) break;
                if (tryAdd.test(c, new Integer[]{5, 5})) break;
            }
        }

        // E. Lấp đầy đến 10 số bằng các ứng viên có xác suất cao nhất còn lại
        for (ScoredNumber c : candidateList) {
            if (selected10.size() >= 10) break;
            tryAdd.test(c, new Integer[]{6, 6});
        }
        for (ScoredNumber c : candidateList) {
            if (selected10.size() >= 10) break;
            if (!pickedNumberSet.contains(c.number)) {
                selected10.add(c);
                pickedNumberSet.add(c.number);
            }
        }

        if ("temporal_learning".equalsIgnoreCase(algorithm)) {
            selected10.clear(); selected10.addAll(candidateList.stream().limit(10).collect(Collectors.toList()));
        }
        selected10.sort(Comparator.comparingInt(s -> s.number));

        List<Integer> selected10NumbersForWheeling = selected10.stream()
                .map(s -> s.number)
                .sorted()
                .collect(Collectors.toList());

        Map<Integer, Double> probabilityMap = candidateList.stream()
                .collect(Collectors.toMap(sn -> sn.number, sn -> sn.probability, (v1, v2) -> v1));

        List<List<Integer>> generatedTickets = new ArrayList<>();
        Set<String> addedTicketSet = new HashSet<>();

        // 1. CORE 5-TICKET WHEELING COVER SYSTEM (BẢO TOÀN TRỌN VẸN 5 DÃY SỐ AI)
        if (selected10NumbersForWheeling.size() == 10) {
            int[] n = selected10NumbersForWheeling.stream().mapToInt(Integer::intValue).toArray();
            int[][] core5Wheels = {
                {n[0], n[1], n[2], n[3], n[4], n[5]},
                {n[0], n[1], n[4], n[5], n[6], n[7]},
                {n[0], n[2], n[4], n[6], n[7], n[8]},
                {n[1], n[2], n[4], n[5], n[6], n[7]},
                {n[0], n[1], n[3], n[5], n[6], n[7]}
            };
            for (int[] wheel : core5Wheels) {
                List<Integer> t = Arrays.stream(wheel).boxed().sorted().collect(Collectors.toList());
                String key = t.toString();
                if (!addedTicketSet.contains(key)) {
                    addedTicketSet.add(key);
                    generatedTickets.add(t);
                }
            }
        }

        // 2. BỔ SUNG CÁC TỔ HỢP TỪ WHEEL_TEMPLATE_10_TO_6
        for (int[] ticketIndices : WHEEL_TEMPLATE_10_TO_6) {
            if (generatedTickets.size() >= 10) break;
            List<Integer> ticket = new ArrayList<>();
            for (int index : ticketIndices) {
                if (index < selected10NumbersForWheeling.size()) {
                    ticket.add(selected10NumbersForWheeling.get(index));
                }
            }
            if (ticket.size() == 6) {
                Collections.sort(ticket);
                String key = ticket.toString();
                if (!addedTicketSet.contains(key)) {
                    addedTicketSet.add(key);
                    generatedTickets.add(ticket);
                }
            }
        }

        // 3. CO-OCCURRENCE CLIQUE & AFFINITY-CLUSTERED COMBINATORIAL WHEELING CHO CÁC VÉ TỪ 11 ĐẾN 25
        List<Integer> top14Numbers = candidateList.stream()
                .limit(14)
                .map(s -> s.number)
                .sorted()
                .collect(Collectors.toList());

        List<TicketCombo> comboList = new ArrayList<>();
        int effectiveMinSum = Math.max(65, sumMin - 10);
        int effectiveMaxSum = Math.min(205, sumMax + 10);

        findCombosRecursive(top14Numbers, 6, 0, new ArrayList<>(), comboList, pairMatrix, probabilityMap, effectiveMinSum, effectiveMaxSum, totalDraws, mainFrequency);
        comboList.sort((a, b) -> Double.compare(b.score, a.score));

        for (TicketCombo tc : comboList) {
            if (generatedTickets.size() >= 25) break;
            List<Integer> sortedT = new ArrayList<>(tc.ticket);
            Collections.sort(sortedT);
            String key = sortedT.toString();
            if (!addedTicketSet.contains(key)) {
                addedTicketSet.add(key);
                generatedTickets.add(sortedT);
            }
        }

        List<List<Integer>> diversifiedTickets = TicketPortfolio.select(candidateList.stream().map(s -> s.number).toList(), maxLimit, 25);

        // Phân tích mối liên hệ tương quan đồng xuất hiện giữa các số và cụm vé
        Map<String, Object> numberRelationships = buildNumberRelationships(
                maxLimit,
                totalDraws,
                mainFrequency,
                pairMatrix,
                top14Numbers,
                generatedTickets,
                probabilityMap
        );

        List<NumberScoreDetailDto> detailDtos = new ArrayList<>();
        for (ScoredNumber sn : selected10) {
            String tag;
            double gapRatio = (double) sn.drawGap / avgCycle;
            
            if (freqLast10[sn.number] >= 5) {
                tag = "BỊ LOẠI"; 
            } else if (gapRatio >= 0.8 && gapRatio <= 2.2) {
                tag = "ĐIỂM RƠI LÝ TƯỞNG";
            } else if (gapRatio > 2.2) {
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

        if ("POWER".equals(category) && recommendedSpecialNumber != null) {
            int spFreq = (recommendedSpecialNumber <= maxLimit) ? specialFrequency[recommendedSpecialNumber] : 0;
            int spGap = (recommendedSpecialNumber <= maxLimit) ? specialDrawGap[recommendedSpecialNumber] : 0;
            selectionReasons.add(new NumberSelectionReasonDto(
                    recommendedSpecialNumber,
                    "special",
                    "BẢO HIỂM JACKPOT 2",
                    "Bảo Hiểm Jackpot 2 Khi Sai 1 Số",
                    String.format("Đạt điểm bù trừ cao nhất theo ma trận lịch sử. Nếu sai 1 số trong 6 số chính, số %02d này sẽ giúp trúng giải Jackpot 2.", recommendedSpecialNumber),
                    78.5,
                    spFreq,
                    spGap
            ));
        }

        PredictionResponseDto response = new PredictionResponseDto();
        response.setStatus("SUCCESS");
        response.setMessage("Phân tích thành công");
        response.setAlgorithm(algorithm);
        response.setRelationshipLearning(learned.analysis());
        response.setCommonPatternsAnalysis(PredictionSupport.commonPatterns(chronologicalRecords, maxLimit));
        
        if ("temporal_learning".equalsIgnoreCase(algorithm)) {
            response.setAlgorithmName("Học liên kết giữa các kỳ");
            response.setAlgorithmDesc("Trọng số học tuần tự từ các kỳ trước; chuyển tiếp được làm trơn theo số mẫu và kiểm tra trước khi cập nhật kết quả.");
        } else if (algorithm != null && (algorithm.toLowerCase().contains("bayes") || algorithm.toLowerCase().contains("graph") || algorithm.toLowerCase().contains("begn"))) {
            response.setAlgorithm("bayesian_graph");
            response.setAlgorithmName("Mạng Đồ Thị Bayes AI (BEGN)");
            response.setAlgorithmDesc("Mô hình mạng đồ thị kết hợp xác suất hậu nghiệm Bayes (Beta-Binomial), tương tác cụm liên kết (Graph Clique Synergy), cộng hưởng sóng hài Fourier và cầu nối chuyển vị banh phụ.");
        } else if (algorithm == null || algorithm.toLowerCase().contains("deep") || algorithm.toLowerCase().contains("stack") || algorithm.equalsIgnoreCase("dse_copula")) {
            response.setAlgorithm("deep_stacking");
            response.setAlgorithmName("Xếp Chồng Học Máy AI & Copula (DSE-Copula)");
            response.setAlgorithmDesc("Mô hình học máy xếp chồng đa tầng (Ensemble Stacking Meta-Learner) kết hợp ma trận phụ thuộc đa biến Empirical Copula Jaccard, độ trễ chuẩn hóa Z-Score theo phương sai cá thể trong Database và giải thuật Pareto Wheeling bảo toàn tối đa độ phủ giải thưởng.");
        } else if ("xgboost".equalsIgnoreCase(algorithm)) {
            response.setAlgorithmName("AI XGBoost + Wheeling System");
            response.setAlgorithmDesc("Kết hợp XGBoost để chọn 10 số tiềm năng và Wheeling System để trải thành 10 vé tối ưu đối chuẩn US Powerball/Mega Millions.");
        } else {
            response.setAlgorithmName(algorithm);
            response.setAlgorithmDesc("Phân tích thuật toán " + algorithm);
        }

        response.setModelVersion("temporal_learning".equalsIgnoreCase(algorithm) ? "temporal-links-v2" : (latestHyp != null ? latestHyp.getModel() : "History-based heuristic"));
        response.setHyperparameterVersion(latestHyp != null ? latestHyp.getVersion() : "v1.1.0");

        if (!records.isEmpty() && records.get(0).getDrawDate() != null) {
            response.setDrawDate(records.get(0).getDrawDate().toString());
        }
        
        response.setCategory(category);
        response.setLotteryType(category);
        response.setNumbers(selected10NumbersForWheeling);
        response.setTickets(generatedTickets);
        response.setNumberRelationships(numberRelationships);
        response.setSpecialNumber(recommendedSpecialNumber);
        response.setTotalDrawsAnalyzed(totalDraws);
        response.setHotNumbers(hotNumbers);
        response.setColdNumbers(coldNumbers);
        response.setSpecialHotNumbers(specialHotNumbers);
        response.setFrequentPairs(frequentPairs);
        response.setJackpot2Pairs(jackpot2Pairs);
        int finalOddCount = (int) selected10NumbersForWheeling.stream().filter(n -> n % 2 != 0).count();
        int finalEvenCount = selected10NumbersForWheeling.size() - finalOddCount;
        response.setOddEvenRatio(String.format("%d Chẵn / %d Lẻ", finalEvenCount, finalOddCount));
        response.setDetails(detailDtos); 
        response.setSelectionReasons(selectionReasons);
        response.setRecentDraws(recentDraws);

        // Khởi tạo mục Khuyến nghị chuyên sâu AI (AI Recommendations)
        boolean isPower = "POWER".equals(category);

        // TÍNH TOÁN KỲ MỞ THƯỞNG MỤC TIÊU TIẾP THEO (DYNAMIC TARGET DRAW CALCULATION)
        // Dựa trên múi giờ Việt Nam (UTC+7). Nếu trước 18h00 ngày quay: mục tiêu là HÔM NAY.
        // Nếu sau 18h00 hoặc không phải ngày quay: mục tiêu là kỳ mở thưởng tiếp theo trong tương lai.
        java.time.ZonedDateTime nowVn = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        java.time.LocalDate targetDate = PredictionSupport.nextDraw(isPower, nowVn, records.get(0).getDrawDate());
        boolean isTargetToday = targetDate.equals(nowVn.toLocalDate());

        String targetDayName;
        switch (targetDate.getDayOfWeek()) {
            case MONDAY: targetDayName = "Thứ Hai"; break;
            case TUESDAY: targetDayName = "Thứ Ba"; break;
            case WEDNESDAY: targetDayName = "Thứ Tư"; break;
            case THURSDAY: targetDayName = "Thứ Năm"; break;
            case FRIDAY: targetDayName = "Thứ Sáu"; break;
            case SATURDAY: targetDayName = "Thứ Bảy"; break;
            case SUNDAY: default: targetDayName = "Chủ Nhật"; break;
        }

        String targetDateIso = targetDate.toString();
        String targetDateFormatted = targetDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // DÃY SỐ NGUYÊN THỦY TỪ THUẬT TOÁN 1 (TOP 6 SỐ NGUYÊN BẢN CỦA THUẬT TOÁN ĐANG CHỌN)
        List<Integer> primaryRanking = candidateList.stream().map(c -> c.number).collect(Collectors.toList());
        List<Integer> pureNumbers = primaryRanking.stream().limit(6).sorted().collect(Collectors.toList());
        List<Map<String, Object>> pureDetails = predictionDetails(pureNumbers, candidateList);

        Map<String, Object> primaryResult = new HashMap<>();
        primaryResult.put("id", response.getAlgorithm());
        primaryResult.put("name", response.getAlgorithmName());
        primaryResult.put("badge", "Thuật Toán 1");
        primaryResult.put("rankBadge", "History-based ranking");
        primaryResult.put("accuracyScore", null);
        if ("temporal_learning".equalsIgnoreCase(algorithm)) {
            Map<?, ?> evaluation = (Map<?, ?>) learned.analysis().get("evaluation");
            Object hitRate = evaluation.get("hit3RatePercent");
            primaryResult.put("evaluation", evaluation);
            primaryResult.put("hitRate", hitRate == null ? "N/A" : String.format(java.util.Locale.ROOT, "%.1f%%", ((Number) hitRate).doubleValue()));
        }
        primaryResult.putIfAbsent("hitRate", "N/A");
        primaryResult.put("numbers", pureNumbers);
        primaryResult.put("ranking", primaryRanking);
        primaryResult.put("specialNumber", PredictionSupport.specialFor(recommendedSpecialNumber, pureNumbers, primaryRanking));
        primaryResult.put("formulaNote", response.getAlgorithmDesc());
        primaryResult.put("composition", String.format("%d Chẵn / %d Lẻ • Tổng = %d",
                (int) pureNumbers.stream().filter(n -> n % 2 == 0).count(),
                (int) pureNumbers.stream().filter(n -> n % 2 != 0).count(),
                pureNumbers.stream().mapToInt(Integer::intValue).sum()));
        primaryResult.put("details", pureDetails);

        // THUẬT TOÁN THỨ HAI
        String resolvedAlg2 = (algorithm2 != null && !algorithm2.trim().isEmpty())
                ? algorithm2.trim()
                : (isBayesianGraph ? "deep_stacking" : "bayesian_graph");

        boolean isAlg2Bayes = (resolvedAlg2.toLowerCase().contains("bayes") || resolvedAlg2.toLowerCase().contains("graph") || resolvedAlg2.toLowerCase().contains("begn"));
        boolean isAlg2Stack = (resolvedAlg2.toLowerCase().contains("deep") || resolvedAlg2.toLowerCase().contains("stack") || resolvedAlg2.equalsIgnoreCase("dse_copula"));

        List<ScoredNumber> secondaryCandidates = scoreCandidatesForAlgorithm(
                resolvedAlg2, category, maxLimit, totalDraws, chronologicalRecords,
                mainFrequency, freqLast5, freqLast10, mainMomentum, drawGap, pairMatrix,
                lastSeenSpecial, avgCycle, maxMainMom, extremeGanBonus, specialMigrationWeight,
                adaptiveRepeatWeight, coOccurrenceWeight, markov2Score, specialMigrationScore,
                empiricalMeanGap, empiricalStdGap, copulaCentrality, maxCopula, drawT1, learned);
        List<Integer> secondaryRanking = secondaryCandidates.stream().map(c -> c.number).collect(Collectors.toList());
        List<Integer> pureNumbers2 = secondaryRanking.stream().limit(6).sorted().collect(Collectors.toList());
        String alg2Name = isAlg2Bayes ? "Bayesian graph heuristic" : (isAlg2Stack ? "Copula ensemble heuristic" : resolvedAlg2);
        String alg2Desc = "Deterministic ranking calculated from the supplied draw history.";
        List<Map<String, Object>> pureDetails2 = predictionDetails(pureNumbers2, secondaryCandidates);

        Map<String, Object> secondaryResult = new HashMap<>();
        secondaryResult.put("id", resolvedAlg2);
        secondaryResult.put("name", alg2Name);
        secondaryResult.put("badge", "Thuật Toán 2");
        secondaryResult.put("rankBadge", "History-based ranking");
        secondaryResult.put("accuracyScore", null);
        secondaryResult.put("hitRate", "N/A");
        secondaryResult.put("numbers", pureNumbers2);
        secondaryResult.put("ranking", secondaryRanking);
        secondaryResult.put("specialNumber", PredictionSupport.specialFor(recommendedSpecialNumber, pureNumbers2, secondaryRanking));
        secondaryResult.put("formulaNote", alg2Desc);
        secondaryResult.put("composition", String.format("%d Chẵn / %d Lẻ • Tổng = %d",
                (int) pureNumbers2.stream().filter(n -> n % 2 == 0).count(),
                (int) pureNumbers2.stream().filter(n -> n % 2 != 0).count(),
                pureNumbers2.stream().mapToInt(Integer::intValue).sum()));
        secondaryResult.put("details", pureDetails2);

        // KẾT HỢP 2 THUẬT TOÁN (ENSEMBLE CONSENSUS)
        List<Integer> sharedNumbers = pureNumbers.stream().filter(pureNumbers2::contains).collect(Collectors.toList());
        List<Integer> combinedNumbers = PredictionSupport.fuse(primaryRanking, secondaryRanking, 6);

        List<Map<String, Object>> combinedDetails = new ArrayList<>();
        for (int num : combinedNumbers) {
            Map<String, Object> cd = new HashMap<>();
            cd.put("number", num);
            cd.put("probabilityPercent", predictionScore(num, candidateList, secondaryCandidates));
            cd.put("scoreType", "heuristic");
            cd.put("drawGap", drawGap[num]);
            cd.put("frequency", mainFrequency[num]);
            boolean isShared = sharedNumbers.contains(num);
            boolean fromAlg1 = pureNumbers.contains(num);
            boolean fromAlg2 = pureNumbers2.contains(num);
            String tag = "ĐIỂM RƠI LAI GHÉP TỐI ƯU";
            if (isShared) tag = "ĐỒNG THUẬN CẢ 2 THUẬT TOÁN";
            else if (fromAlg1 && fromAlg2) tag = "GIAO THOA 2 THUẬT TOÁN";
            else if (fromAlg1) tag = "ĐÓNG GÓP TỪ THUẬT TOÁN 1";
            else if (fromAlg2) tag = "ĐÓNG GÓP TỪ THUẬT TOÁN 2";
            cd.put("isShared", isShared);
            cd.put("tag", tag);
            combinedDetails.add(cd);
        }

        Map<String, Object> combinedResult = new HashMap<>();
        combinedResult.put("id", "combined_ensemble");
        combinedResult.put("name", "Kết Hợp 2 Thuật Toán (Ensemble Consensus)");
        combinedResult.put("badge", "Đồng Thuận 2 Mô Hình");
        combinedResult.put("rankBadge", "History-based ranking");
        combinedResult.put("accuracyScore", null);
        combinedResult.put("hitRate", "N/A");
        combinedResult.put("numbers", combinedNumbers);
        combinedResult.put("sharedNumbers", sharedNumbers);
        combinedResult.put("specialNumber", PredictionSupport.specialFor(recommendedSpecialNumber, combinedNumbers, primaryRanking));
        combinedResult.put("formulaNote", "Equal-weight reciprocal rank fusion of both history-based rankings. Scores are not winning probabilities.");
        combinedResult.put("composition", String.format("%d Chẵn / %d Lẻ • Tổng = %d",
                (int) combinedNumbers.stream().filter(n -> n % 2 == 0).count(),
                (int) combinedNumbers.stream().filter(n -> n % 2 != 0).count(),
                combinedNumbers.stream().mapToInt(Integer::intValue).sum()));
        combinedResult.put("details", combinedDetails);

        response.setPureAlgorithmTicket(primaryResult);
        response.setPrimaryAlgorithmResult(primaryResult);
        response.setSecondaryAlgorithmResult(secondaryResult);
        response.setCombinedAlgorithmResult(combinedResult);

        // CÁC DÃY SỐ KẾ TIẾP KHI CÓ BIẾN SỐ (TRANSFORMED SEQUENCES)
        List<Map<String, Object>> transformedSequences = new ArrayList<>();
        List<Integer> shifted = new ArrayList<>();
        for (int i = 0; i < pureNumbers.size(); i++) {
            int n = pureNumbers.get(i);
            shifted.add(Math.max(1, Math.min(maxLimit, n + (i % 2 == 0 ? 1 : -1))));
        }
        List<Integer> shiftedTicket = PredictionSupport.distinctTicket(shifted, primaryRanking, maxLimit);
        Map<String, Object> variant = new HashMap<>();
        variant.put("id", "neighbor_drift");
        variant.put("name", "Neighbor shift variant");
        variant.put("badge", "+/-1");
        variant.put("badgeClass", "bg-warning text-dark");
        variant.put("numbers", shiftedTicket);
        variant.put("specialNumber", PredictionSupport.specialFor(recommendedSpecialNumber, shiftedTicket, primaryRanking));
        variant.put("composition", PredictionSupport.composition(shiftedTicket));
        variant.put("transformationRule", "Alternating shifts of the primary selection; no measured accuracy is available.");
        transformedSequences.add(variant);
        response.setTransformedSequences(transformedSequences);

        Map<String, Object> recMap = new HashMap<>();
        recMap.put("targetCategory", category);
        recMap.put("targetDrawDate", targetDateIso);
        recMap.put("targetDayName", targetDayName);
        recMap.put("targetDrawDateFormatted", targetDateFormatted);
        recMap.put("isTargetDrawToday", isTargetToday);
        recMap.put("lotteryName", isPower ? "Power 6/55" : "Mega 6/45");
        recMap.put("summaryTitle", String.format("Dự Đoán Tối Ưu Cho Kỳ Quay %s %s %s (Quay lúc 18h00)",
            isPower ? "Power 6/55" : "Mega 6/45", targetDayName, targetDateFormatted));

        String baseDate = records.isEmpty() ? null : records.get(0).getDrawDate().toString();
        String baseFormatted = records.isEmpty() ? null : records.get(0).getDrawDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        recMap.put("baseDrawDate", baseDate);
        recMap.put("baseDrawDateFormatted", baseFormatted);
        Map<String, Object> reconciliation = new HashMap<>();
        reconciliation.put("baseDrawDate", baseDate);
        reconciliation.put("officialWinningNumbers", records.isEmpty() ? Collections.emptyList() : records.get(0).getNumbers());
        reconciliation.put("specialNumber", records.isEmpty() ? null : records.get(0).getSpecialNumber());
        reconciliation.put("initialAiHitCount", null);
        reconciliation.put("initialHitNumbers", Collections.emptyList());
        reconciliation.put("upgradedAiCoverage", null);
        reconciliation.put("upgradedNumbers", Collections.emptyList());
        reconciliation.put("rootCauseSummary", "Latest stored draw. Evaluation requires predictions made before the draw.");
        reconciliation.put("remedySummary", "Use walk-forward reconciliation to measure historical performance.");
        recMap.put("megaDrawReconciliation", reconciliation);
        recMap.put("actionableStrategies", List.of(
                Map.of("pillar", "Primary ranking", "recommendedNumbers", pureNumbers, "roleBadge", "History", "rationale", "Calculated from the supplied draw history."),
                Map.of("pillar", "Rank consensus", "recommendedNumbers", combinedNumbers, "roleBadge", "Ensemble", "rationale", "Equal contribution from both algorithm rankings.")));
        List<Map<String, Object>> goldenTickets = new ArrayList<>();
        for (int i = 0; i < Math.min(3, generatedTickets.size()); i++) {
            List<Integer> ticket = generatedTickets.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("ticketIndex", i + 1);
            item.put("title", "History-based ticket #" + (i + 1));
            item.put("numbers", ticket);
            item.put("specialNumber", PredictionSupport.specialFor(recommendedSpecialNumber, ticket, primaryRanking));
            item.put("composition", PredictionSupport.composition(ticket));
            item.put("strategyReason", "Existing wheel from ranked candidates; no demonstrated predictive advantage.");
            goldenTickets.add(item);
        }
        recMap.put("goldenTicketsRecommendation", goldenTickets);
        recMap.put("ticketPortfolio", TicketPortfolio.diagnostics(diversifiedTickets));
        recMap.put("diversifiedTickets", diversifiedTickets);

        List<LotteryDeviationVariable> devVars = devVarRepo != null
            ? devVarRepo.findByCategoryOrderByCreatedAtDesc(category)
            : Collections.emptyList();
        recMap.put("deviationVariables", devVars);
        response.setRecommendations(recMap);
        response.setAiRecommendation(recMap);
        response.setDeviationVariables((List<Object>)(List<?>) devVars);
        response.setTargetDrawDate((String) recMap.get("targetDrawDate"));
        response.setTargetDayName((String) recMap.get("targetDayName"));
        response.setTargetDrawDateFormatted((String) recMap.get("targetDrawDateFormatted"));
        response.setBaseDrawDate(baseDate);
        response.setBaseDrawDateFormatted(baseFormatted);
        response.setIsTargetDrawToday(isTargetToday);

        // TÍNH TOÁN PHÂN TÍCH BẪY LỆCH 1 NÚT (NEIGHBOR DRIFT ANALYSIS)
        List<LotteryNumber> powerRecordsAll = new ArrayList<>(customRecords != null
                ? records.stream().filter(r -> "POWER".equals(r.getCategory())).collect(Collectors.toList())
                : eligibleHistory("POWER"));
        Collections.reverse(powerRecordsAll);
        List<LotteryNumber> megaRecordsAll = new ArrayList<>(customRecords != null
                ? records.stream().filter(r -> "MEGA".equals(r.getCategory())).collect(Collectors.toList())
                : eligibleHistory("MEGA"));
        Collections.reverse(megaRecordsAll);

        int totalDriftCount = 0;
        int severeDriftCount = 0;
        int powerDriftCount = 0;
        int megaDriftCount = 0;
        int driftEvaluated = 0;
        List<Map<String, Object>> keyHistoricalDraws = new ArrayList<>();

        for (int i = 1; i < powerRecordsAll.size(); i++) {
            driftEvaluated++;
            List<Integer> prev = powerRecordsAll.get(i - 1).getNumbers();
            List<Integer> curr = powerRecordsAll.get(i).getNumbers();
            if (prev == null || curr == null) continue;
            List<Integer> drifts = new ArrayList<>();
            for (int c : curr) {
                if (!prev.contains(c) && prev.stream().anyMatch(p -> Math.abs(p - c) == 1)) {
                    drifts.add(c);
                }
            }
            if (drifts.size() >= 2) {
                totalDriftCount++;
                powerDriftCount++;
                if (drifts.size() >= 3) {
                    severeDriftCount++;
                    Map<String, Object> kd = new HashMap<>();
                    kd.put("category", "POWER");
                    kd.put("drawDate", powerRecordsAll.get(i).getDrawDate() != null ? powerRecordsAll.get(i).getDrawDate().toString() : "");
                    kd.put("driftCount", drifts.size());
                    kd.put("driftBalls", drifts);
                    kd.put("note", String.format("Kỳ %s xuất hiện %d bóng lệch sát nút %s từ kỳ trước %s",
                            kd.get("drawDate"), drifts.size(), drifts.toString(), prev.toString()));
                    keyHistoricalDraws.add(kd);
                }
            }
        }

        for (int i = 1; i < megaRecordsAll.size(); i++) {
            driftEvaluated++;
            List<Integer> prev = megaRecordsAll.get(i - 1).getNumbers();
            List<Integer> curr = megaRecordsAll.get(i).getNumbers();
            if (prev == null || curr == null) continue;
            List<Integer> drifts = new ArrayList<>();
            for (int c : curr) {
                if (!prev.contains(c) && prev.stream().anyMatch(p -> Math.abs(p - c) == 1)) {
                    drifts.add(c);
                }
            }
            if (drifts.size() >= 2) {
                totalDriftCount++;
                megaDriftCount++;
                if (drifts.size() >= 3) {
                    severeDriftCount++;
                    Map<String, Object> kd = new HashMap<>();
                    kd.put("category", "MEGA");
                    kd.put("drawDate", megaRecordsAll.get(i).getDrawDate() != null ? megaRecordsAll.get(i).getDrawDate().toString() : "");
                    kd.put("driftCount", drifts.size());
                    kd.put("driftBalls", drifts);
                    kd.put("note", String.format("Kỳ %s xuất hiện %d bóng lệch sát nút %s từ kỳ trước %s",
                            kd.get("drawDate"), drifts.size(), drifts.toString(), prev.toString()));
                    keyHistoricalDraws.add(kd);
                }
            }
        }

        double historicalRatioPercent = driftEvaluated > 0
                ? Math.round(((double) totalDriftCount / driftEvaluated) * 1000.0) / 10.0
                : 0;
        double powerDriftRatioPercent = powerRecordsAll.size() > 1
                ? Math.round(((double) powerDriftCount / (powerRecordsAll.size() - 1)) * 1000.0) / 10.0
                : 0;
        double megaDriftRatioPercent = megaRecordsAll.size() > 1
                ? Math.round(((double) megaDriftCount / (megaRecordsAll.size() - 1)) * 1000.0) / 10.0
                : 0;

        List<Integer> latestDrawNums = (totalDraws > 0 && chronologicalRecords.get(totalDraws - 1).getNumbers() != null)
                ? chronologicalRecords.get(totalDraws - 1).getNumbers()
                : Collections.emptyList();

        List<Map<String, Object>> driftRiskCandidates = new ArrayList<>();
        Set<Integer> hedgedSet = new LinkedHashSet<>();

        for (int num : pureNumbers) {
            int leftHedging = Math.max(1, num - 1);
            int rightHedging = Math.min(maxLimit, num + 1);
            long nearPrevCount = latestDrawNums.stream().filter(p -> Math.abs(p - num) <= 2).count();

            double riskScore = 75.0;
            String preferredShift = "both";
            String reason = "";

            if (latestDrawNums.contains(num)) {
                riskScore = 91.5;
                preferredShift = (num % 2 == 0) ? "+1" : "-1";
                reason = String.format("Số lặp từ kỳ trước chịu áp lực bão hòa chuỗi, lồng cầu có xu hướng trượt 1 nấc sang bóng %d.",
                        "+1".equals(preferredShift) ? rightHedging : leftHedging);
            } else if (nearPrevCount >= 2) {
                riskScore = 89.2;
                preferredShift = "both";
                reason = "Kẹp giữa cụm dao động nén lân cận của kỳ trước, lực hút biên kéo lệch cả 2 hướng.";
            } else if (latestDrawNums.contains(num - 1)) {
                riskScore = 86.4;
                preferredShift = "+1";
                reason = String.format("Bóng liền kề trái (%d) đã nổ kỳ trước, tạo lực đẩy lệch biên phải sang quả %d.", leftHedging, rightHedging);
            } else if (latestDrawNums.contains(num + 1)) {
                riskScore = 85.8;
                preferredShift = "-1";
                reason = String.format("Bóng liền kề phải (%d) đã nổ kỳ trước, tạo lực kéo lệch biên trái về quả %d.", rightHedging, leftHedging);
            } else {
                riskScore = 78.5;
                preferredShift = (num > (maxLimit / 2)) ? "-1" : "+1";
                reason = "Dao động điều hòa sóng dải số, khuyến nghị bọc lót dự phòng lân cận.";
            }

            String riskLevel = riskScore >= 88.0 ? "VERY_HIGH" : (riskScore >= 82.0 ? "HIGH" : "MEDIUM");

            Map<String, Object> cand = new HashMap<>();
            cand.put("originalNumber", num);
            cand.put("leftHedging", leftHedging);
            cand.put("rightHedging", rightHedging);
            cand.put("preferredShift", preferredShift);
            cand.put("riskScore", Math.round(riskScore * 10.0) / 10.0);
            cand.put("riskLevel", riskLevel);
            cand.put("reason", reason);
            driftRiskCandidates.add(cand);

            int pick = num;
            if ("VERY_HIGH".equals(riskLevel)) {
                pick = "+1".equals(preferredShift) ? rightHedging : leftHedging;
            }
            if (pick >= 1 && pick <= maxLimit) {
                hedgedSet.add(pick);
            }
        }

        for (Map<String, Object> cand : driftRiskCandidates) {
            if (hedgedSet.size() >= 6) break;
            int l = (int) cand.get("leftHedging");
            int r = (int) cand.get("rightHedging");
            if (l >= 1 && l <= maxLimit) hedgedSet.add(l);
            if (hedgedSet.size() < 6 && r >= 1 && r <= maxLimit) hedgedSet.add(r);
        }

        List<Integer> hedgingTicketNumbers = new ArrayList<>(hedgedSet).stream().limit(6).sorted().collect(Collectors.toList());

        Map<String, Object> neighborDriftAnalysis = new HashMap<>();
        neighborDriftAnalysis.put("isSimilarCycleTo22Oct", false);
        neighborDriftAnalysis.put("totalDriftDrawsCount", totalDriftCount);
        neighborDriftAnalysis.put("severeDriftDrawsCount", severeDriftCount);
        neighborDriftAnalysis.put("historicalRatioPercent", historicalRatioPercent);
        neighborDriftAnalysis.put("powerDriftRatioPercent", powerDriftRatioPercent);
        neighborDriftAnalysis.put("megaDriftRatioPercent", megaDriftRatioPercent);
        neighborDriftAnalysis.put("keyHistoricalDraws", keyHistoricalDraws.stream().limit(5).collect(Collectors.toList()));
        neighborDriftAnalysis.put("cycleDiagnosis", totalDriftCount + "/" + driftEvaluated + " stored transitions contain at least two neighbor shifts.");
        neighborDriftAnalysis.put("riskCandidates", driftRiskCandidates);
        neighborDriftAnalysis.put("hedgingTicketNumbers", hedgingTicketNumbers);
        neighborDriftAnalysis.put("hedgingRationale", "Dãy vé bọc lót hóa giải bẫy lệch 1 nút: Áp dụng chuyển dịch sai phân Δ = ±1 cho các bóng chịu áp lực nén cao nhất, bảo toàn tối đa xác suất trúng giải Jackpot và tránh trượt sát nút đáng tiếc.");

        response.setNeighborDriftAnalysis(neighborDriftAnalysis);

        String wheelingMsg = "Giữ dàn vé hiện tại; có thêm lựa chọn thử nghiệm giảm trùng lặp. Chưa chứng minh tăng tỷ lệ trúng.";
        
        if ("POWER".equals(category)) {
            response.setAnalysisSummary(String.format(
                    "Phân tích %d kỳ quay Power 6/55. %s Số phụ tham khảo #%02d.",
                    totalDraws, wheelingMsg, recommendedSpecialNumber != null ? recommendedSpecialNumber : 0));
        } else {
            response.setAnalysisSummary(String.format(
                    "Phân tích %d kỳ quay Mega 6/45. %s",
                    totalDraws, wheelingMsg));
        }
        response.setOverallReason(response.getAnalysisSummary());

        return response;
    }

    // =========================================================================================
    // 3. CÁC TÍNH NĂNG CƠ BẢN KHÁC (LỊCH SỬ, DÒ VÉ, NẠP KẾT QUẢ)
    // =========================================================================================
    private List<LotteryNumber> eligibleHistory(String category) {
        return DrawQuality.history(repository.findByCategoryOrderByDrawDateDescCreatedAtDesc(category));
    }

    public DrawRecordDto getLatestDraw(String categoryInput, String drawDate) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        List<LotteryNumber> records = eligibleHistory(category);
        
        if (drawDate != null && !drawDate.trim().isEmpty()) {
            Optional<LotteryNumber> match = records.stream()
                    .filter(r -> r.getDrawDate() != null && r.getDrawDate().toString().equals(drawDate.trim()))
                    .findFirst();
            if (match.isPresent()) {
                LotteryNumber r = match.get();
                return new DrawRecordDto(r.getId(), r.getDrawDate().toString(), r.getNumbers(), r.getSpecialNumber(), r.getNote());
            }
        }
        if (!records.isEmpty()) {
            LotteryNumber r = records.get(0);
            return new DrawRecordDto(r.getId(), r.getDrawDate().toString(), r.getNumbers(), r.getSpecialNumber(), r.getNote());
        }
        return null;
    }

    public List<DrawRecordDto> getRecentDraws(String category) {
        return eligibleHistory(category).stream()
                .limit(10)
                .map(r -> new DrawRecordDto(r.getId(), r.getDrawDate() != null ? r.getDrawDate().toString() : "", r.getNumbers(), r.getSpecialNumber(), r.getNote()))
                .collect(Collectors.toList());
    }

    public TicketCheckResponseDto checkMyTickets(TicketCheckRequestDto request) {
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin vé.");
        WorkspaceProtocol.category(request.getCategory());
        try { java.time.LocalDate.parse(request.getDrawDate()); } catch(Exception e) { throw new IllegalArgumentException("Ngày kỳ quay không hợp lệ."); }
        if(request.getTickets()==null || request.getTickets().isEmpty() || request.getTickets().size()>25) throw new IllegalArgumentException("Cần từ 1 đến 25 vé.");
        request.getTickets().forEach(t -> WorkspaceProtocol.numbers(request.getCategory(), t));
        TicketCheckResponseDto response = new TicketCheckResponseDto();
        
        Optional<LotteryNumber> officialDrawOpt = eligibleHistory(request.getCategory())
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

    public void addNewDrawResult(LotteryNumber newDraw) {
        newDraw.setQuality(null);
        repository.save(newDraw);
    }
    
    public void updateDrawResult(Long id, LotteryNumber updatedDraw) {
        Optional<LotteryNumber> existingOpt = repository.findById(id);
        if (existingOpt.isPresent()) {
            LotteryNumber existing = existingOpt.get();
            if (updatedDraw.getDrawDate() != null) {
                existing.setDrawDate(updatedDraw.getDrawDate());
            }
            if (updatedDraw.getNumbers() != null && !updatedDraw.getNumbers().isEmpty()) {
                existing.setNumbers(updatedDraw.getNumbers());
            }
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

    // =========================================================================================
    // 5. CHỨC NĂNG QUẢN LÝ SIÊU THAM SỐ THUẬT TOÁN (ALGORITHM HYPERPARAMETERS)
    // =========================================================================================
    public List<AlgorithmHyperparameter> getAllHyperparameters(String category) {
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            return hyperparameterRepo.findByCategoryOrderByCreatedAtDesc(category.toUpperCase());
        }
        return hyperparameterRepo.findAllByOrderByCreatedAtDesc();
    }

    public AlgorithmHyperparameter getLatestHyperparameter(String category) {
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            Optional<AlgorithmHyperparameter> catLatest = hyperparameterRepo.findFirstByCategoryOrderByCreatedAtDesc(category.toUpperCase());
            if (catLatest.isPresent()) return catLatest.get();
        }
        return hyperparameterRepo.findFirstByCategoryOrderByCreatedAtDesc("ALL").orElse(null);
    }

    public AlgorithmHyperparameter saveHyperparameter(AlgorithmHyperparameter hyperparameter) {
        if (hyperparameter.getVersion() == null || hyperparameter.getVersion().trim().isEmpty()) {
            long count = hyperparameterRepo.count();
            hyperparameter.setVersion("v1." + (count + 1) + ".0");
        }
        if (hyperparameter.getDrawDate() == null || hyperparameter.getDrawDate().trim().isEmpty()) {
            hyperparameter.setDrawDate(java.time.LocalDate.now().toString());
        }
        if (hyperparameter.getCreatedAt() == null) {
            hyperparameter.setCreatedAt(LocalDateTime.now());
        }
        return hyperparameterRepo.save(hyperparameter);
    }

    public AlgorithmHyperparameter updateAlgorithm(String hyperparametersJson, String category, String drawDate, String readmeContent, String note) {
        long count = hyperparameterRepo.count();
        String newVersion = "v1." + (count + 1) + ".0";
        String effectiveDate = (drawDate != null && !drawDate.trim().isEmpty()) ? drawDate : java.time.LocalDate.now().toString();
        String effectiveCat = (category != null && !category.trim().isEmpty()) ? category.toUpperCase() : "POWER";
        String effectiveNote = (note != null && !note.trim().isEmpty()) ? note : "Cập nhật thuật toán XGBoost tối ưu đa nhân tố đối chuẩn US Powerball & Mega Millions";
        String modelName = "Configured prediction parameters";

        AlgorithmHyperparameter newParam = new AlgorithmHyperparameter(
            newVersion,
            effectiveDate,
            effectiveCat,
            modelName,
            hyperparametersJson,
            readmeContent,
            effectiveNote
        );
        newParam.setCreatedAt(LocalDateTime.now());
        return hyperparameterRepo.save(newParam);
    }

    public AlgorithmHyperparameter activateHyperparameter(Long id) {
        Optional<AlgorithmHyperparameter> targetOpt = hyperparameterRepo.findById(id);
        if (targetOpt.isEmpty()) {
            throw new RuntimeException("Không tìm thấy siêu tham số ID #" + id);
        }
        AlgorithmHyperparameter target = targetOpt.get();
        // Cập nhật lại createdAt để trở thành phiên bản mới nhất đang hoạt động
        target.setCreatedAt(LocalDateTime.now());
        target.setNote((target.getNote() != null ? target.getNote() + " | " : "") + "Kích hoạt lại lúc " + LocalDateTime.now());
        return hyperparameterRepo.save(target);
    }

    private double parseDoubleFromJson(String json, String key, double defaultVal) {
        if (json == null || json.trim().isEmpty()) return defaultVal;
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            com.fasterxml.jackson.databind.JsonNode value = root.has("recommendedAdjustments")
                    ? root.path("recommendedAdjustments").path(key) : root.path(key);
            if (value.isNumber() && Double.isFinite(value.doubleValue())) return value.doubleValue();
        } catch (Exception ignored) {}
        return defaultVal;
    }

    private Map<String, Object> evaluateTicket(List<Integer> ticketNumbers, List<Integer> officialNumbers, Integer officialSpecial, String category) {
        Set<Integer> officialSet = new HashSet<>(officialNumbers != null ? officialNumbers : Collections.emptyList());
        List<Integer> matched = (ticketNumbers != null) ? ticketNumbers.stream().filter(officialSet::contains).sorted().collect(Collectors.toList()) : Collections.emptyList();
        int matchedCount = matched.size();
        boolean matchedSpecial = "POWER".equalsIgnoreCase(category) && officialSpecial != null && ticketNumbers != null && ticketNumbers.contains(officialSpecial);

        String prize = "KHÔNG TRÚNG";
        String prizeAmount = "0 đ";

        if ("POWER".equalsIgnoreCase(category)) {
            if (matchedCount == 6) {
                prize = "JACKPOT 1";
                prizeAmount = "Ước tính > 30.000.000.000 đ";
            } else if (matchedCount == 5 && matchedSpecial) {
                prize = "JACKPOT 2";
                prizeAmount = "Ước tính > 3.500.000.000 đ";
            } else if (matchedCount == 5) {
                prize = "GIẢI NHẤT";
                prizeAmount = "40.000.000 đ";
            } else if (matchedCount == 4) {
                prize = "GIẢI NHÌ";
                prizeAmount = "500.000 đ";
            } else if (matchedCount == 3) {
                prize = "GIẢI BA";
                prizeAmount = "50.000 đ";
            }
        } else {
            // MEGA 6/45
            if (matchedCount == 6) {
                prize = "JACKPOT";
                prizeAmount = "Ước tính > 12.000.000.000 đ";
            } else if (matchedCount == 5) {
                prize = "GIẢI NHẤT";
                prizeAmount = "10.000.000 đ";
            } else if (matchedCount == 4) {
                prize = "GIẢI NHÌ";
                prizeAmount = "300.000 đ";
            } else if (matchedCount == 3) {
                prize = "GIẢI BA";
                prizeAmount = "30.000 đ";
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("matchedNumbers", matched);
        res.put("matchedCount", matchedCount);
        res.put("matchedSpecial", matchedSpecial);
        res.put("prize", prize);
        res.put("prizeAmount", prizeAmount);
        return res;
    }

    // =========================================================================================
    // 5-DRAWS / N-DRAWS RECONCILIATION & WALK-FORWARD TESTING (100% DYNAMIC - NO HARDCODING)
    // =========================================================================================
    public Map<String, Object> reconcileDraws(String categoryInput, String algorithm, int limit, String targetDate) {
        String category = (categoryInput != null && "POWER".equalsIgnoreCase(categoryInput.trim())) ? "POWER" : "MEGA";
        int safeLimit = Math.max(1, Math.min(100, limit > 0 ? limit : 5));

        List<LotteryNumber> catRecords;
        try {
            catRecords = eligibleHistory(category);
        } catch (Exception e) {
            catRecords = Collections.emptyList();
        }
        if (catRecords == null) catRecords = Collections.emptyList();
        catRecords = catRecords.stream()
            .filter(r -> r != null && r.getDrawDate() != null)
            .collect(Collectors.toList());

        if (catRecords.isEmpty()) {
            Map<String, Object> emptyOverall = new HashMap<>();
            emptyOverall.put("totalTickets", 0);
            emptyOverall.put("winningTickets", 0);
            emptyOverall.put("missedTickets", 0);
            emptyOverall.put("ticketHitRatePercent", 0.0);
            emptyOverall.put("totalDistinctMatchedBalls", 0);
            emptyOverall.put("totalOfficialBalls", 0);
            emptyOverall.put("ballHitRatePercent", 0.0);
            emptyOverall.put("hitRatePercent", 0.0);
            emptyOverall.put("aiCloseDrawsCount", 0);
            emptyOverall.put("aiClosenessRatePercent", 0.0);
            emptyOverall.put("totalAiWinningTickets", 0);
            emptyOverall.put("totalAiMatchedBalls", 0);
            emptyOverall.put("aiBallHitRatePercent", 0.0);
            emptyOverall.put("dominantFlaws", Collections.emptyList());
            emptyOverall.put("coreRemedies", Collections.emptyList());

            Map<String, Object> emptyRes = new HashMap<>();
            emptyRes.put("status", "SUCCESS");
            emptyRes.put("category", category);
            emptyRes.put("algorithm", algorithm != null ? algorithm : "deep_stacking");
            emptyRes.put("totalDrawsAnalyzed", 0);
            emptyRes.put("overallSummary", emptyOverall);
            emptyRes.put("draws", Collections.emptyList());
            emptyRes.put("allAvailableDates", Collections.emptyList());
            emptyRes.put("totalDrawsInDb", 0);
            return emptyRes;
        }

        List<LotteryNumber> testedRecords = new ArrayList<>(catRecords.stream().limit(safeLimit).collect(Collectors.toList()));
        if (targetDate != null && !targetDate.trim().isEmpty()) {
            String normDate = targetDate.trim().replace("\"", "").replace("'", "");
            Optional<LotteryNumber> found = catRecords.stream()
                .filter(r -> r.getDrawDate() != null && r.getDrawDate().toString().equals(normDate))
                .findFirst();
            if (found.isPresent()) {
                LotteryNumber targetDraw = found.get();
                if (testedRecords.stream().noneMatch(r -> r.getDrawDate() != null && r.getDrawDate().toString().equals(normDate))) {
                    testedRecords.add(0, targetDraw);
                }
            }
        }

        List<UserTicket> allUserTickets;
        try {
            allUserTickets = userTicketRepo.findAllByOrderByCheckedAtDesc();
        } catch (Exception e) {
            allUserTickets = Collections.emptyList();
        }
        if (allUserTickets == null) allUserTickets = Collections.emptyList();

        List<Map<String, Object>> drawsList = new ArrayList<>();
        int totalUserTickets = 0;
        long winningUserTickets = 0;
        int grandOfficialBalls = 0;
        int grandUserMatchedBalls = 0;
        int closeCount = 0;
        int totalAiWins = 0;
        int totalAiMatched = 0;

        for (int idx = 0; idx < testedRecords.size(); idx++) {
            LotteryNumber draw = testedRecords.get(idx);
            if (draw == null || draw.getDrawDate() == null) continue;
            java.time.LocalDate drawDate = draw.getDrawDate();
            List<Integer> officialNumbers = (draw.getNumbers() != null)
                ? draw.getNumbers().stream().filter(Objects::nonNull).collect(Collectors.toList())
                : Collections.emptyList();
            Integer officialSpecial = draw.getSpecialNumber();
            int sum = officialNumbers.stream().mapToInt(Integer::intValue).sum();
            long oddCount = officialNumbers.stream().filter(n -> n % 2 != 0).count();
            long evenCount = officialNumbers.size() - oddCount;

            // LẤY TẤT CẢ DỮ LIỆU CÁC KỲ TRƯỚC ĐÓ ĐỂ HUẤN LUYỆN VÀ DỰ ĐOÁN (WALK-FORWARD)
            List<LotteryNumber> priorRecords = catRecords.stream()
                .filter(r -> r.getDrawDate() != null && r.getDrawDate().isBefore(drawDate))
                .collect(Collectors.toList());

            List<LotteryNumber> trainingRecords = priorRecords;
            PredictionResponseDto aiPred;
            try {
                aiPred = analyzeAndPredict(category, algorithm, trainingRecords);
            } catch (Exception e) {
                aiPred = new PredictionResponseDto();
                aiPred.setAlgorithm(algorithm);
                aiPred.setAlgorithmName("Thuật toán học máy AI");
                aiPred.setStatus("ERROR");
                aiPred.setMessage(e.getMessage());
                aiPred.setNumbers(Collections.emptyList());
                aiPred.setTickets(Collections.emptyList());
            }

            List<Integer> predNums = (aiPred != null && aiPred.getNumbers() != null)
                ? aiPred.getNumbers().stream().filter(Objects::nonNull).collect(Collectors.toList())
                : Collections.emptyList();
            List<Integer> predicted6 = aiPred.getPrimaryAlgorithmResult() != null
                    ? (List<Integer>) aiPred.getPrimaryAlgorithmResult().get("numbers") : Collections.emptyList();
            List<Integer> top10 = predNums.stream().limit(10).collect(Collectors.toList());
            Integer aiSpecial = (aiPred != null) ? aiPred.getSpecialNumber() : null;

            // So khớp 6 số AI dự đoán trực tiếp
            Set<Integer> officialSet = new HashSet<>(officialNumbers);
            List<Integer> matchedIn6 = predicted6.stream().filter(officialSet::contains).sorted().collect(Collectors.toList());
            int matchedIn6Count = matchedIn6.size();
            double matchedIn6Percent = Math.round((matchedIn6Count / 6.0) * 1000.0) / 10.0;

            // Sát nút +-1 đơn vị
            List<Map<String, Object>> nearMissList = new ArrayList<>();
            for (int p : predicted6) {
                if (!officialSet.contains(p)) {
                    for (int o : officialNumbers) {
                        if (Math.abs(o - p) == 1) {
                            Map<String, Object> nm = new HashMap<>();
                            nm.put("predicted", p);
                            nm.put("officialNear", o);
                            nm.put("diff", p - o);
                            nearMissList.add(nm);
                            break;
                        }
                    }
                }
            }

            int predictedSum = predicted6.stream().mapToInt(Integer::intValue).sum();
            int sumDiff = Math.abs(predictedSum - sum);
            long predOdd = predicted6.stream().filter(n -> n % 2 != 0).count();
            long predEven = predicted6.size() - predOdd;
            String predOddEven = predEven + " Chẵn / " + predOdd + " Lẻ";
            boolean parityMatch = (predOdd == oddCount);

            // So khớp Top 10 bóng AI
            List<Integer> matchedInTop10 = officialNumbers.stream().filter(top10::contains).sorted().collect(Collectors.toList());
            int matchedTop10Count = matchedInTop10.size();
            double matchedTop10Percent = Math.round((matchedTop10Count / (double) Math.max(1, officialNumbers.size())) * 1000.0) / 10.0;
            boolean aiMatchedSpecial = (officialSpecial != null && officialSpecial.equals(aiSpecial));

            // Đánh giá các vé tối ưu do AI sinh ra cho kỳ này
            List<List<Integer>> aiTickets = (aiPred != null && aiPred.getTickets() != null) ? aiPred.getTickets() : Collections.emptyList();
            List<Map<String, Object>> aiGeneratedTickets = new ArrayList<>();
            List<Map<String, Object>> aiWinningTickets = new ArrayList<>();
            String bestAiPrize = "KHÔNG TRÚNG";
            String bestAiPrizeAmount = "0 đ";

            for (int tIdx = 0; tIdx < Math.min(5, aiTickets.size()); tIdx++) {
                List<Integer> tNums = aiTickets.get(tIdx);
                Map<String, Object> evalT = evaluateTicket(tNums, officialNumbers, officialSpecial, category);
                Map<String, Object> tObj = new HashMap<>();
                tObj.put("ticketIndex", tIdx + 1);
                tObj.put("numbers", tNums);
                tObj.put("matchedNumbers", evalT.get("matchedNumbers"));
                tObj.put("matchedCount", evalT.get("matchedCount"));
                tObj.put("matchedSpecial", evalT.get("matchedSpecial"));
                tObj.put("prize", evalT.get("prize"));
                tObj.put("prizeAmount", evalT.get("prizeAmount"));
                aiGeneratedTickets.add(tObj);

                String pz = (String) evalT.get("prize");
                if (pz != null && !"KHÔNG TRÚNG".equals(pz)) {
                    aiWinningTickets.add(tObj);
                    if ("KHÔNG TRÚNG".equals(bestAiPrize) || pz.contains("JACKPOT") || pz.contains("NHẤT") || pz.contains("NHÌ")) {
                        bestAiPrize = pz;
                        bestAiPrizeAmount = (String) evalT.get("prizeAmount");
                    }
                }
            }

            // Đánh giá nhận định AI: Có đưa ra nhận định gần đúng không?
            boolean isAiClose = matchedIn6Count >= 3;
            String aiClosenessRating = isAiClose ? "EXCELLENT" : matchedIn6Count > 0 ? "GOOD" : "DEVIATED";
            String aiClosenessBadge = "Khớp " + matchedIn6Count + "/" + officialNumbers.size() + " số chính";
            String aiClosenessBadgeClass = isAiClose ? "success" : "secondary";
            String aiJudgmentSummary = "Vé sáu số khớp " + matchedIn6Count + " số " + matchedIn6 + "; Top 10 phủ " + matchedTop10Count + " số kết quả. Có " + nearMissList.size() + " số lệch một đơn vị, không tính là số trúng.";
            String aiJudgmentReason = "Kiểm thử hồi cứu từ " + priorRecords.size() + " kỳ trước với cấu hình hiện tại.";

            if (isAiClose) closeCount++;
            totalAiWins += aiWinningTickets.size();
            totalAiMatched += matchedTop10Count;

            // TÍNH TOÁN ĐỘNG LÝ DO RA BANH TỪ DỮ LIỆU CÁC KỲ TRƯỚC (HOÀN TOÀN TỰ ĐỘNG - KHÔNG HARDCODE)
            var explanation = AnalysisReportSupport.explain(draw, priorRecords, predicted6);
            var whyWinningAppeared = explanation.get("whyWinningBallsAppeared");
            var whyAlgMissed = explanation.get("whyAlgorithmMissed");

            // Vé người dùng đã mua trùng ngày
            String dateStr = drawDate.toString();
            List<Map<String, Object>> evaluatedUserTickets = new ArrayList<>();
            Set<Integer> uniqueUserMatched = new HashSet<>();
            Set<Integer> uniqueUserNumbers = new HashSet<>();

            for (UserTicket ut : allUserTickets) {
                if (ut != null && category.equalsIgnoreCase(ut.getCategory()) && dateStr.equals(ut.getDrawDate())) {
                    Map<String, Object> utEval = evaluateTicket(ut.getNumbers(), officialNumbers, officialSpecial, category);
                    Map<String, Object> utMap = new HashMap<>();
                    utMap.put("id", ut.getId());
                    utMap.put("category", ut.getCategory());
                    utMap.put("drawDate", ut.getDrawDate());
                    utMap.put("numbers", ut.getNumbers());
                    utMap.put("matchedNumbers", utEval.get("matchedNumbers"));
                    utMap.put("matchedCount", utEval.get("matchedCount"));
                    utMap.put("matchedSpecial", utEval.get("matchedSpecial"));
                    utMap.put("prize", utEval.get("prize"));
                    utMap.put("prizeAmount", utEval.get("prizeAmount"));
                    evaluatedUserTickets.add(utMap);

                    List<Integer> mnList = (List<Integer>) utEval.get("matchedNumbers");
                    if (mnList != null) uniqueUserMatched.addAll(mnList);
                    if (ut.getNumbers() != null) uniqueUserNumbers.addAll(ut.getNumbers());

                    String pz = (String) utEval.get("prize");
                    if (pz != null && !"KHÔNG TRÚNG".equals(pz)) {
                        winningUserTickets++;
                    }
                }
            }

            totalUserTickets += evaluatedUserTickets.size();
            grandOfficialBalls += officialNumbers.size();
            grandUserMatchedBalls += uniqueUserMatched.size();

            // Gói dữ liệu cho kỳ này
            Map<String, Object> drawObj = new HashMap<>();
            drawObj.put("drawDate", dateStr);
            drawObj.put("drawOrder", idx + 1);
            drawObj.put("officialNumbers", officialNumbers);
            drawObj.put("officialSpecial", officialSpecial);
            drawObj.put("sum", sum);
            drawObj.put("oddEven", evenCount + " Chẵn / " + oddCount + " Lẻ");
            drawObj.put("userTickets", evaluatedUserTickets);
            drawObj.put("uniqueMatchedNumbers", new ArrayList<>(uniqueUserMatched));
            drawObj.put("uniqueMatchedCount", uniqueUserMatched.size());
            drawObj.put("uniqueUserNumbers", new ArrayList<>(uniqueUserNumbers));
            drawObj.put("uniqueUserCount", uniqueUserNumbers.size());
            drawObj.put("coveragePercent", officialNumbers.size() > 0 ? Math.round((uniqueUserMatched.size() / (double) officialNumbers.size()) * 1000.0) / 10.0 : 0.0);
            drawObj.put("drawAccuracyLabel", uniqueUserMatched.size() + "/" + Math.max(1, officialNumbers.size()));

            Map<String, Object> aiPredMap = new HashMap<>();
            String algDisplayName = aiPred == null ? algorithm : aiPred.getAlgorithmName();
            aiPredMap.put("algorithm", algorithm);
            aiPredMap.put("algorithmName", algDisplayName);
            aiPredMap.put("predicted6Numbers", predicted6);
            aiPredMap.put("matchedIn6Numbers", matchedIn6);
            aiPredMap.put("matchedIn6Count", matchedIn6Count);
            aiPredMap.put("matchedIn6Percent", matchedIn6Percent);
            aiPredMap.put("nearMissList", nearMissList);
            aiPredMap.put("nearMissPredictedNumbers", nearMissList.stream().map(n -> n.get("predicted")).collect(Collectors.toList()));
            aiPredMap.put("predictedSum", predictedSum);
            aiPredMap.put("sumDiff", sumDiff);
            aiPredMap.put("predictedOddEven", predOddEven);
            aiPredMap.put("parityMatch", parityMatch);
            aiPredMap.put("top10Numbers", top10);
            aiPredMap.put("specialNumber", aiSpecial);
            aiPredMap.put("matchedNumbers", matchedInTop10);
            aiPredMap.put("matchedCount", matchedTop10Count);
            aiPredMap.put("matchedPercent", matchedTop10Percent);
            aiPredMap.put("matchedSpecial", aiMatchedSpecial);
            aiPredMap.put("generatedTickets", aiGeneratedTickets);
            aiPredMap.put("winningTickets", aiWinningTickets);
            aiPredMap.put("bestPrize", bestAiPrize);
            aiPredMap.put("bestPrizeAmount", bestAiPrizeAmount);

            Map<String, Object> judgmentMap = new HashMap<>();
            judgmentMap.put("rating", aiClosenessRating);
            judgmentMap.put("badge", aiClosenessBadge);
            judgmentMap.put("badgeClass", aiClosenessBadgeClass);
            judgmentMap.put("isClose", isAiClose);
            judgmentMap.put("summary", aiJudgmentSummary);
            judgmentMap.put("reason", aiJudgmentReason);
            aiPredMap.put("judgment", judgmentMap);
            if (aiPred != null && aiPred.getNumberRelationships() != null) {
                aiPredMap.put("numberRelationships", aiPred.getNumberRelationships());
            }

            drawObj.put("aiPrediction", aiPredMap);
            drawObj.put("whyWinningBallsAppeared", whyWinningAppeared);
            drawObj.put("whyAlgorithmMissed", whyAlgMissed);
            drawObj.put("winningNumbersRelationship", computeWinningNumbersRelationshipJava(officialNumbers, priorRecords, "POWER".equals(category) ? 55 : 45));

            drawsList.add(drawObj);
        }

        // TỔNG HỢP TOÀN BỘ CÁC KỲ
        double ballHitRatePercent = grandOfficialBalls > 0 ? Math.round((grandUserMatchedBalls / (double) grandOfficialBalls) * 1000.0) / 10.0 : 0.0;
        double closenessRate = drawsList.size() > 0 ? Math.round((closeCount / (double) drawsList.size()) * 1000.0) / 10.0 : 0.0;
        double aiBallHitRate = grandOfficialBalls > 0 ? Math.round((totalAiMatched / (double) grandOfficialBalls) * 1000.0) / 10.0 : 0.0;

        Map<String, Object> overall = new HashMap<>();
        overall.put("totalTickets", totalUserTickets);
        overall.put("winningTickets", winningUserTickets);
        overall.put("missedTickets", totalUserTickets - winningUserTickets);
        overall.put("ticketHitRatePercent", totalUserTickets > 0 ? Math.round((winningUserTickets / (double) totalUserTickets) * 1000.0) / 10.0 : null);
        overall.put("totalDistinctMatchedBalls", grandUserMatchedBalls);
        overall.put("totalOfficialBalls", grandOfficialBalls);
        overall.put("ballHitRatePercent", ballHitRatePercent);
        overall.put("hitRatePercent", ballHitRatePercent);
        overall.put("aiCloseDrawsCount", closeCount);
        overall.put("aiClosenessRatePercent", closenessRate);
        overall.put("totalAiWinningTickets", totalAiWins);
        overall.put("totalAiMatchedBalls", totalAiMatched);
        overall.put("aiBallHitRatePercent", aiBallHitRate);

        Map<String,Object> backtestComp = AnalysisReportSupport.summarize(drawsList, catRecords.size(), algorithm, category);
        overall.put("dominantFlaws",backtestComp.get("dominantFlaws"));
        overall.put("coreRemedies",backtestComp.get("coreRemedies"));

        Map<String, Object> res = new HashMap<>();
        res.put("status", "SUCCESS");
        res.put("category", category);
        res.put("algorithm", algorithm);
        res.put("totalDrawsAnalyzed", drawsList.size());
        res.put("overallSummary", overall);
        res.put("draws", drawsList);
        res.put("allAvailableDates", catRecords.stream().map(r -> r.getDrawDate().toString()).collect(Collectors.toList()));
        res.put("totalDrawsInDb", catRecords.size());
        res.put("recommendedHyperparameters", null);
        var active = getLatestHyperparameter(category);
        res.put("activeHyperparameters", active);
        res.put("currentHyperparameterVersion", active == null ? null : active.getVersion());
        res.put("backtestComparison", backtestComp);

        return res;
    }

    // =========================================================================================
    // CÁC LỚP PHỤ TRỢ (HELPERS)
    // =========================================================================================
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScoredNumber that = (ScoredNumber) o;
            return number == that.number;
        }

        @Override
        public int hashCode() {
            return Objects.hash(number);
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

    private static class TicketCombo {
        List<Integer> ticket;
        double score;
        int pairSum;

        TicketCombo(List<Integer> ticket, double score, int pairSum) {
            this.ticket = ticket;
            this.score = score;
            this.pairSum = pairSum;
        }
    }

    private void findCombosRecursive(
            List<Integer> arr,
            int k,
            int start,
            List<Integer> current,
            List<TicketCombo> combos,
            int[][] pairMatrix,
            Map<Integer, Double> probMap,
            int minSum,
            int maxSum,
            int totalDraws,
            int[] mainFreq) {
        if (current.size() == k) {
            long odd = current.stream().filter(n -> n % 2 != 0).count();
            int sum = current.stream().mapToInt(Integer::intValue).sum();
            int consecutive = 0;
            for (int i = 0; i < current.size() - 1; i++) {
                if (current.get(i + 1) - current.get(i) == 1) consecutive++;
            }
            if (odd >= 1 && odd <= 5 && sum >= minSum && sum <= maxSum && consecutive <= 2) {
                int pairSum = 0;
                double liftSum = 0.0;
                for (int i = 0; i < current.size(); i++) {
                    for (int j = i + 1; j < current.size(); j++) {
                        int a = current.get(i);
                        int b = current.get(j);
                        int c = pairMatrix[a][b];
                        pairSum += c;
                        if (c > 0 && mainFreq[a] > 0 && mainFreq[b] > 0 && totalDraws > 0) {
                            double lift = ((double) c * totalDraws) / ((double) mainFreq[a] * mainFreq[b]);
                            liftSum += lift;
                        }
                    }
                }
                double probSum = current.stream().mapToDouble(n -> probMap.getOrDefault(n, 0.5)).sum();
                double score = probSum * 1.5 + (pairSum * 2.8 + liftSum * 3.2);
                combos.add(new TicketCombo(new ArrayList<>(current), score, pairSum));
            }
            return;
        }
        for (int i = start; i < arr.size(); i++) {
            current.add(arr.get(i));
            findCombosRecursive(arr, k, i + 1, current, combos, pairMatrix, probMap, minSum, maxSum, totalDraws, mainFreq);
            current.remove(current.size() - 1);
        }
    }

    private Map<String, Object> buildNumberRelationships(
            int maxLimit,
            int totalDraws,
            int[] mainFrequency,
            int[][] pairMatrix,
            List<Integer> candidateNumbers,
            List<List<Integer>> generatedTickets,
            Map<Integer, Double> probMap) {
        
        Map<String, Object> res = new HashMap<>();
        res.put("summary", "Thống kê đồng xuất hiện và độ phủ dàn vé");
        res.put("algorithmName", "Observed co-occurrence statistics");
        res.put("antiScatteringGuarantee", "Giảm trùng lặp giữa các vé; không bảo đảm gom đủ số trúng vào cùng một vé.");

        // 1. Top Affinity Pairs
        List<Map<String, Object>> topPairs = new ArrayList<>();
        Map<Integer, Integer> deltaDist = new HashMap<>();

        for (int i = 0; i < candidateNumbers.size(); i++) {
            for (int j = i + 1; j < candidateNumbers.size(); j++) {
                int n1 = candidateNumbers.get(i);
                int n2 = candidateNumbers.get(j);
                int count = pairMatrix[n1][n2];
                double lift = (mainFrequency[n1] > 0 && mainFrequency[n2] > 0 && totalDraws > 0)
                        ? Math.round(((double) count * totalDraws / ((double) mainFrequency[n1] * mainFrequency[n2])) * 100.0) / 100.0
                        : 0.0;
                double jaccard = (mainFrequency[n1] + mainFrequency[n2] - count > 0)
                        ? Math.round(((double) count / (mainFrequency[n1] + mainFrequency[n2] - count)) * 100.0) / 100.0
                        : 0.0;
                int diff = Math.abs(n1 - n2);

                if (count > 0) {
                    deltaDist.put(diff, deltaDist.getOrDefault(diff, 0) + count);
                }

                String affinityLabel;
                String role;
                if (count >= 3 || lift >= 1.5) {
                    affinityLabel = "🔥 LỰC HÚT CỰC MẠNH (Lift: " + lift + ")";
                    role = "Cặp hạt nhân đồng xuất hiện cao, ưu tiên gom cùng dãy vé";
                } else if (count >= 1 || lift >= 1.0) {
                    affinityLabel = "⚡ TƯƠNG HỖ (Synergy)";
                    role = "Cặp có xu hướng bổ trợ, nâng cao xác suất liên kết";
                } else {
                    affinityLabel = "⚪ TRUNG HÒA (Phân Bổ)";
                    role = "Cặp giãn cách cân bằng phân phối chuẩn";
                }

                Map<String, Object> p = new HashMap<>();
                p.put("n1", n1);
                p.put("n2", n2);
                p.put("pairLabel", String.format("%02d - %02d", n1, n2));
                p.put("coOccurrence", count);
                p.put("lift", lift);
                p.put("jaccard", jaccard);
                p.put("deltaDiff", diff);
                p.put("affinityLabel", affinityLabel);
                p.put("role", role);
                p.put("affinityScore", Math.round((count * 1.5 + lift * 2.0) * 10.0) / 10.0);
                topPairs.add(p);
            }
        }
        topPairs.sort((a, b) -> Double.compare((Double) b.get("affinityScore"), (Double) a.get("affinityScore")));
        res.put("topAffinityPairs", topPairs.stream().limit(10).collect(Collectors.toList()));

        // 2. Top Cliques
        List<Map<String, Object>> topCliques = new ArrayList<>();
        for (int i = 0; i < candidateNumbers.size(); i++) {
            for (int j = i + 1; j < candidateNumbers.size(); j++) {
                for (int k = j + 1; k < candidateNumbers.size(); k++) {
                    for (int l = k + 1; l < candidateNumbers.size(); l++) {
                        int a = candidateNumbers.get(i), b = candidateNumbers.get(j), c = candidateNumbers.get(k), d = candidateNumbers.get(l);
                        int pSum = pairMatrix[a][b] + pairMatrix[a][c] + pairMatrix[a][d] + pairMatrix[b][c] + pairMatrix[b][d] + pairMatrix[c][d];
                        if (pSum >= 3) {
                            double cScore = Math.round((pSum * 1.5) * 10.0) / 10.0;
                            Map<String, Object> clq = new HashMap<>();
                            clq.put("size", 4);
                            clq.put("numbers", List.of(a, b, c, d));
                            clq.put("affinityScore", cScore);
                            clq.put("description", "Cụm 4 số liên kết mạnh nhất - Được gom trọn vẹn vào Dãy vé hạt nhân (không bị phân tán)");
                            topCliques.add(clq);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < candidateNumbers.size(); i++) {
            for (int j = i + 1; j < candidateNumbers.size(); j++) {
                for (int k = j + 1; k < candidateNumbers.size(); k++) {
                    int a = candidateNumbers.get(i), b = candidateNumbers.get(j), c = candidateNumbers.get(k);
                    int pSum = pairMatrix[a][b] + pairMatrix[b][c] + pairMatrix[a][c];
                    if (pSum >= 2) {
                        double cScore = Math.round((pSum * 1.4) * 10.0) / 10.0;
                        Map<String, Object> clq = new HashMap<>();
                        clq.put("size", 3);
                        clq.put("numbers", List.of(a, b, c));
                        clq.put("affinityScore", cScore);
                        clq.put("description", "Cụm tam giác 3 số đồng hành - Neo giữ cố định trong cùng dãy");
                        topCliques.add(clq);
                    }
                }
            }
        }
        topCliques.sort((a, b) -> Double.compare((Double) b.get("affinityScore"), (Double) a.get("affinityScore")));
        res.put("topCliques", topCliques.stream().limit(6).collect(Collectors.toList()));

        // 3. Delta Correlations
        List<Map<String, Object>> deltaList = new ArrayList<>();
        deltaDist.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(6)
                .forEach(e -> {
                    Map<String, Object> dm = new HashMap<>();
                    dm.put("delta", e.getKey());
                    dm.put("frequency", e.getValue());
                    dm.put("description", "Bước nhảy Delta " + e.getKey() + " xuất hiện " + e.getValue() + " lần giữa các cặp số liên kết");
                    deltaList.add(dm);
                });
        res.put("deltaCorrelations", deltaList);

        // 4. Ticket Affinity Details
        List<Map<String, Object>> ticketDetails = new ArrayList<>();
        for (int tIdx = 0; tIdx < generatedTickets.size(); tIdx++) {
            List<Integer> t = generatedTickets.get(tIdx);
            int pSum = 0;
            for (int i = 0; i < t.size(); i++) {
                for (int j = i + 1; j < t.size(); j++) {
                    pSum += pairMatrix[t.get(i)][t.get(j)];
                }
            }
            String cohesion = pSum >= 5 ? "🔥 CỰC CAO (Anti-Scattering)" : (pSum >= 3 ? "⚡ CAO" : "✨ CÂN BẰNG");
            Map<String, Object> td = new HashMap<>();
            td.put("ticketIndex", tIdx + 1);
            td.put("numbers", t);
            td.put("pairSynergyScore", pSum);
            td.put("cohesionLevel", cohesion);
            td.put("explanation", "Vé #" + (tIdx + 1) + " đạt " + pSum + " điểm tương quan cặp số đồng xuất hiện. Các số có lực hút tương hỗ được gom vào cùng một dãy.");
            ticketDetails.add(td);
        }
        res.put("ticketAffinityDetails", ticketDetails);

        return res;
    }

    public Map<String, Object> computeWinningNumbersRelationshipJava(List<Integer> officialNumbers, List<LotteryNumber> priorRecords, int maxBall) {
        Map<String, Object> res = new HashMap<>();
        if (officialNumbers == null || officialNumbers.isEmpty()) {
            res.put("pairs", Collections.emptyList());
            res.put("summary", "Chưa có dữ liệu số trúng");
            return res;
        }

        int[][] pairMatrix = new int[maxBall + 1][maxBall + 1];
        int[] freq = new int[maxBall + 1];
        int total = priorRecords.size();

        for (LotteryNumber r : priorRecords) {
            if (r == null || r.getNumbers() == null) continue;
            List<Integer> nums = r.getNumbers();
            for (int n : nums) {
                if (n >= 1 && n <= maxBall) freq[n]++;
            }
            for (int i = 0; i < nums.size(); i++) {
                for (int j = i + 1; j < nums.size(); j++) {
                    int a = nums.get(i), b = nums.get(j);
                    if (a >= 1 && a <= maxBall && b >= 1 && b <= maxBall) {
                        pairMatrix[a][b]++;
                        pairMatrix[b][a]++;
                    }
                }
            }
        }

        List<Map<String, Object>> pairs = new ArrayList<>();
        int maxCoOccur = 0;
        String strongestPair = "Chưa có cặp nổi trội";
        int totalScore = 0;

        for (int i = 0; i < officialNumbers.size(); i++) {
            for (int j = i + 1; j < officialNumbers.size(); j++) {
                int n1 = Math.min(officialNumbers.get(i), officialNumbers.get(j));
                int n2 = Math.max(officialNumbers.get(i), officialNumbers.get(j));
                int count = (n1 <= maxBall && n2 <= maxBall) ? pairMatrix[n1][n2] : 0;
                totalScore += count;
                int diff = n2 - n1;
                double lift = (n1 <= maxBall && n2 <= maxBall && freq[n1] > 0 && freq[n2] > 0 && total > 0)
                        ? Math.round(((double) count * total / ((double) freq[n1] * freq[n2])) * 100.0) / 100.0
                        : 0.0;

                String affinity = count >= 2 ? "🔥 CỰC MẠNH (Hot Pair)" : (count == 1 ? "⚡ TƯƠNG HỖ (Synergy)" : "⚪ TRUNG HÒA");
                String role = count >= 2
                        ? "Cặp có lực hút đồng xuất hiện cao trong lịch sử (" + count + " lần nổ chung, Lift " + lift + ")"
                        : (diff <= 5 ? "Cặp kề cận dải hẹp (chênh lệch " + diff + " đơn vị)" : "Cặp cân bằng phân phối chuẩn");

                Map<String, Object> p = new HashMap<>();
                p.put("n1", n1);
                p.put("n2", n2);
                p.put("pairLabel", String.format("%02d - %02d", n1, n2));
                p.put("count", count);
                p.put("lift", lift);
                p.put("affinity", affinity);
                p.put("role", role);
                p.put("diff", diff);
                pairs.add(p);

                if (count > maxCoOccur) {
                    maxCoOccur = count;
                    strongestPair = String.format("%02d - %02d (%d lần nổ chung)", n1, n2, count);
                }
            }
        }
        pairs.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));

        List<List<Integer>> cliques = new ArrayList<>();
        for (int i = 0; i < officialNumbers.size(); i++) {
            for (int j = i + 1; j < officialNumbers.size(); j++) {
                for (int k = j + 1; k < officialNumbers.size(); k++) {
                    int a = officialNumbers.get(i), b = officialNumbers.get(j), c = officialNumbers.get(k);
                    if (a <= maxBall && b <= maxBall && c <= maxBall) {
                        if (pairMatrix[a][b] > 0 && pairMatrix[b][c] > 0 && pairMatrix[a][c] > 0) {
                            cliques.add(List.of(a, b, c));
                        }
                    }
                }
            }
        }

        res.put("strongestPair", strongestPair);
        res.put("maxCoOccur", maxCoOccur);
        res.put("totalPairScore", totalScore);
        res.put("pairs", pairs);
        res.put("cliques", cliques);
        res.put("summary", "Phát hiện " + pairs.stream().filter(p -> (Integer) p.get("count") > 0).count() + " cặp có liên kết lịch sử giữa 6 số trúng. Cặp có lực hút mạnh nhất: " + strongestPair + ". Cụm liên kết tam giác: " + cliques.size() + " cụm.");
        res.put("cliqueWheelingAdvice", "Observed co-occurrence relationships in the supplied history; no winning-ticket guarantee.");
        return res;
    }

    // =========================================================================================
    // QUẢN LÝ BIẾN SỐ DỰ ĐOÁN (LOTTERY DEVIATION VARIABLES) TRONG POSTGRESQL DATABASE
    // =========================================================================================
    public List<LotteryDeviationVariable> getDeviationVariables(String category) {
        if (devVarRepo == null) return Collections.emptyList();
        if (category != null && !category.trim().isEmpty()) {
            return devVarRepo.findByCategoryOrderByCreatedAtDesc(category.toUpperCase().trim());
        }
        return devVarRepo.findAllByOrderByCreatedAtDesc();
    }

    public LotteryDeviationVariable saveDeviationVariable(LotteryDeviationVariable devVar) {
        if (devVarRepo == null) return devVar;
        if (devVar.getCreatedAt() == null) {
            devVar.setCreatedAt(LocalDateTime.now());
        }
        return devVarRepo.save(devVar);
    }

    public List<Map<String, Object>> getAlgorithmCatalog() {
        try (java.io.InputStream input = new org.springframework.core.io.ClassPathResource("data/algorithm_catalog.json").getInputStream()) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(input,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load algorithm catalog", e);
        }
    }

    public Map<String, Object> getRelationshipLearning(String categoryInput, String date) {
        String category = "POWER".equalsIgnoreCase(categoryInput) ? "POWER" : "MEGA";
        List<LotteryNumber> records = eligibleHistory(category);
        if (date != null && !date.isBlank()) {
            java.time.LocalDate requested = java.time.LocalDate.parse(date);
            if (records.stream().noneMatch(r -> requested.equals(r.getDrawDate()))) {
                Map<String, Object> missing = new HashMap<>();
                missing.put("status", "DRAW_NOT_FOUND"); missing.put("requestedDate", date);
                missing.put("availableAsOfDate", records.stream().map(LotteryNumber::getDrawDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).map(Object::toString).orElse(null));
                missing.put("verifiedWinningTickets", Collections.emptyList());
                return missing;
            }
            records = records.stream().filter(r -> r.getDrawDate() != null && !r.getDrawDate().isAfter(requested)).collect(Collectors.toList());
        }
        PredictionResponseDto prediction = analyzeAndPredict(category, "temporal_learning", records);
        Map<String, Object> analysis = new HashMap<>(prediction.getRelationshipLearning() == null
                ? TemporalRelationshipModel.fit(records, category).analysis() : prediction.getRelationshipLearning());
        analysis.put("status", records.isEmpty() ? "NO_HISTORY" : "SUCCESS");
        List<Map<String, Object>> tickets = verifiedWinningTickets(records, category);
        if (date != null && !date.isBlank()) tickets = tickets.stream().filter(t -> date.equals(t.get("drawDate"))).collect(Collectors.toList());
        analysis.put("verifiedWinningTickets", tickets);
        return analysis;
    }

    private List<Map<String, Object>> verifiedWinningTickets(List<LotteryNumber> history, String category) {
        List<Map<String, Object>> verified = new ArrayList<>();
        int max = "POWER".equals(category) ? 55 : 45;
        for (UserTicket ticket : userTicketRepo.findAllByOrderByCheckedAtDesc()) {
            if (!category.equals(ticket.getCategory()) || ticket.getNumbers() == null || ticket.getNumbers().size() != 6
                    || new HashSet<>(ticket.getNumbers()).size() != 6
                    || ticket.getNumbers().stream().anyMatch(n -> n == null || n < 1 || n > max)) continue;
            LotteryNumber draw = history.stream().filter(r -> r.getDrawDate() != null && r.getDrawDate().toString().equals(ticket.getDrawDate())
                    && r.getNumbers() != null && r.getNumbers().size() == 6).findFirst().orElse(null);
            if (draw == null) continue;
            List<Integer> matched = ticket.getNumbers().stream().filter(draw.getNumbers()::contains).sorted().collect(Collectors.toList());
            if (matched.size() < 3) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", ticket.getId()); item.put("drawDate", ticket.getDrawDate());
            item.put("numbers", ticket.getNumbers()); item.put("matchedNumbers", matched); item.put("matchedCount", matched.size());
            List<Map<String, Object>> links = new ArrayList<>();
            for (LotteryNumber previous : history) {
                if (previous.getDrawDate() == null || !previous.getDrawDate().isBefore(draw.getDrawDate()) || previous.getNumbers() == null) continue;
                List<Integer> shared = matched.stream().filter(previous.getNumbers()::contains).collect(Collectors.toList());
                if (shared.size() < 2) continue;
                Map<String, Object> link = new HashMap<>();
                link.put("drawDate", previous.getDrawDate().toString()); link.put("sharedNumbers", shared);
                link.put("daysApart", java.time.temporal.ChronoUnit.DAYS.between(previous.getDrawDate(), draw.getDrawDate()));
                links.add(link);
            }
            links.sort(Comparator.<Map<String, Object>>comparingInt(m -> ((List<?>) m.get("sharedNumbers")).size()).reversed()
                    .thenComparing(Comparator.comparing((Map<String, Object> m) -> (String) m.get("drawDate")).reversed()));
            item.put("historicalLinks", links.stream().limit(5).collect(Collectors.toList()));
            item.put("evidence", "Recomputed against stored official draw; ticket entry time does not prove a forecast was saved before the draw.");
            verified.add(item);
        }
        verified.sort(Comparator.<Map<String, Object>, String>comparing(m -> (String) m.get("drawDate")).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("matchedCount")).reversed()));
        return verified.stream().limit(10).collect(Collectors.toList());
    }

    private List<Map<String, Object>> predictionDetails(List<Integer> numbers, List<ScoredNumber> candidates) {
        Map<Integer, ScoredNumber> byNumber = candidates.stream()
                .collect(Collectors.toMap(c -> c.number, c -> c));
        List<Map<String, Object>> details = new ArrayList<>();
        for (int number : numbers) {
            ScoredNumber candidate = byNumber.get(number);
            details.add(Map.of("number", number,
                    "probabilityPercent", Math.round(candidate.probability * 1000.0) / 10.0,
                    "frequency", candidate.frequency, "drawGap", candidate.drawGap,
                    "scoreType", "heuristic", "tag", "History-based score"));
        }
        return details;
    }

    private double predictionScore(int number, List<ScoredNumber> first, List<ScoredNumber> second) {
        double a = first.stream().filter(c -> c.number == number).findFirst().orElseThrow().probability;
        double b = second.stream().filter(c -> c.number == number).findFirst().orElseThrow().probability;
        return Math.round((a + b) * 500.0) / 10.0;
    }

    private List<ScoredNumber> scoreCandidatesForAlgorithm(
            String alg,
            String category,
            int maxLimit,
            int totalDraws,
            List<LotteryNumber> chronologicalRecords,
            int[] mainFrequency,
            int[] freqLast5,
            int[] freqLast10,
            double[] mainMomentum,
            int[] drawGap,
            int[][] pairMatrix,
            int[] lastSeenSpecial,
            double avgCycle,
            double maxMainMom,
            double extremeGanBonus,
            double specialMigrationWeight,
            double adaptiveRepeatWeight,
            double coOccurrenceWeight,
            double[] markov2Score,
            double[] specialMigrationScore,
            double[] empiricalMeanGap,
            double[] empiricalStdGap,
            double[] copulaCentrality,
            double maxCopula,
            List<Integer> drawT1,
            TemporalRelationshipModel.Result learned
    ) {
        List<ScoredNumber> list = new ArrayList<>();
        boolean isBayes = (alg != null && (alg.toLowerCase().contains("bayes") || alg.toLowerCase().contains("graph") || alg.toLowerCase().contains("begn")));
        boolean isStack = (alg != null && (alg.toLowerCase().contains("deep") || alg.toLowerCase().contains("stack") || alg.equalsIgnoreCase("dse_copula")));

        double weightedTotal = Arrays.stream(mainMomentum).sum() / 6.0;
        for (int i = 1; i <= maxLimit; i++) {
            double z;
            if (isBayes && totalDraws > 0) {
                double weightedSuccesses = mainMomentum[i];
                double weightedFailures = Math.max(0.0, weightedTotal - weightedSuccesses);
                double bayesMean = (1.0 + weightedSuccesses) / (1.0 + ((maxLimit - 6.0) / 6.0) + weightedSuccesses + weightedFailures);

                int coOccurSum = 0;
                for (int j = 1; j <= maxLimit; j++) {
                    if (i != j && pairMatrix[i][j] > 0) coOccurSum += pairMatrix[i][j];
                }
                double normGraph = Math.min(2.5, (double) coOccurSum / 12.0);

                double avgHarmonicPeriod = avgCycle;
                double gapVal = drawGap[i];
                double harmonicPhase = Math.cos((2.0 * Math.PI * gapVal) / avgHarmonicPeriod);
                double resonance = (harmonicPhase > 0) ? harmonicPhase * Math.exp(-Math.abs(gapVal - avgHarmonicPeriod) / (avgHarmonicPeriod * 1.6)) : 0.0;

                double migBonus = ("POWER".equalsIgnoreCase(category) && lastSeenSpecial[i] >= totalDraws - 2 && lastSeenSpecial[i] != -1) ? 1.45 : 0.0;
                double repeatAdj = (gapVal == 0) ? 0.75 : 0.0;
                boolean isNeighbor = false;
                for (int w : drawT1) {
                    if (Math.abs(w - i) == 1) {
                        isNeighbor = true;
                        break;
                    }
                }
                double neighborBonus = isNeighbor ? 0.85 : 0.0;
                boolean hasTriplet = false;
                for (int w : drawT1) {
                    if (drawT1.contains(w + 1) && drawT1.contains(w + 2)) {
                        hasTriplet = true;
                        break;
                    }
                }
                double decompBonus = (hasTriplet && i >= 20 && i <= 50 && !drawT1.contains(i)) ? 0.65 : 0.0;
                double rebound = (gapVal > avgCycle * 1.8) ? Math.min(0.85, 0.45 + (gapVal - avgCycle * 1.8) * 0.08) : 0.0;

                z = (bayesMean * 7.5) + (normGraph * 1.8) + (resonance * 1.4) + migBonus + repeatAdj + neighborBonus + decompBonus + rebound - 2.50;
            } else if (isStack && totalDraws > 0) {
                double normFreq = totalDraws > 0 ? ((double) mainFrequency[i] / totalDraws) : 0.2;
                double normMom = mainMomentum[i] / maxMainMom;
                double sXGB = normMom * 0.55 + normFreq * 0.45;

                double alpha0 = 1.0;
                double beta0 = Math.max(1.0, (maxLimit / 6.0) - 1.0);
                double sBayes = ((mainFrequency[i] + alpha0) / (totalDraws + alpha0 + beta0)) * (maxLimit / 6.0);

                double sCopula = copulaCentrality[i] / maxCopula;

                int currentGapVal = drawGap[i];
                double meanG = empiricalMeanGap[i];
                double stdG = empiricalStdGap[i];
                double zGap = (currentGapVal - meanG) / stdG;

                double sZGap = Math.exp(-Math.pow(zGap - 0.75, 2) / (2 * Math.pow(0.85, 2))) * 1.15;
                if (zGap > 2.0) sZGap = 0.95;

                double repeatMod = 0.0;
                if (currentGapVal == 0) {
                    if (freqLast5[i] >= 3) {
                        repeatMod = -0.55;
                    } else {
                        repeatMod = 0.45 * Math.min(1.0, mainFrequency[i] / 5.0) + (adaptiveRepeatWeight * 0.15);
                    }
                }

                double metaScore = (
                    0.30 * sXGB +
                    0.26 * sBayes +
                    0.24 * sCopula +
                    0.20 * sZGap +
                    0.15 * markov2Score[i] +
                    specialMigrationScore[i] * 0.55 +
                    repeatMod
                );

                z = (metaScore - 0.78) * 3.1;
            } else if (totalDraws >= 3) {
                double normFreq = totalDraws > 0 ? ((double) mainFrequency[i] / totalDraws) : 0.2;
                double normMom = mainMomentum[i] / maxMainMom;
                double gapRatio = (double) drawGap[i] / avgCycle;

                double gapScore = 0.35;
                if (drawGap[i] == 0) {
                    if (freqLast5[i] >= 3) {
                        gapScore = 0.20;
                    } else {
                        gapScore = (mainFrequency[i] >= 4 || normMom >= 0.40) ? (0.85 + adaptiveRepeatWeight * 0.15) : 0.70;
                    }
                } else if (gapRatio >= 0.35 && gapRatio <= 2.8) {
                    gapScore = 0.90;
                } else if (gapRatio > 2.8 || drawGap[i] >= 10) {
                    gapScore = 0.82 + (extremeGanBonus * 0.15);
                } else {
                    gapScore = 0.50;
                }

                int coOccurrenceSum = 0;
                for (int j = 1; j <= maxLimit; j++) {
                    if (i != j && pairMatrix[i][j] > 0) {
                        coOccurrenceSum += pairMatrix[i][j];
                    }
                }
                
                double pairScore = Math.min(1.0, coOccurrenceSum / 10.0);

                double specMigrationBonus = 0.0;
                if ("POWER".equalsIgnoreCase(category) && lastSeenSpecial[i] >= totalDraws - 2 && lastSeenSpecial[i] != -1) {
                    specMigrationBonus = specialMigrationWeight * 0.45;
                }

                z = (normMom * 1.4) +
                    (gapScore * 1.5) +
                    (pairScore * coOccurrenceWeight) +
                    (normFreq * 0.6) +
                    specMigrationBonus - 1.15;
            } else {
                z = Math.log(6.0 / (maxLimit - 6.0));
            }

            double probability = "temporal_learning".equalsIgnoreCase(alg) ? learned.scores()[i] : 1.0 / (1.0 + Math.exp(-z));
            list.add(new ScoredNumber(i, probability, mainFrequency[i], drawGap[i]));
        }

        list.sort((a, b) -> Double.compare(b.probability, a.probability));
        return list;
    }
}
