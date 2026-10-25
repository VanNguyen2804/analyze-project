import type { QualityDraw } from './draw-quality';

export function explainDraw(draw: QualityDraw, prior: QualityDraw[], predicted: number[]) {
  const history = [...prior].sort((a,b) => b.drawDate.localeCompare(a.drawDate));
  const balls = [...draw.numbers.map(number => ({number,isSpecial:false})), ...(draw.specialNumber == null ? [] : [{number:draw.specialNumber,isSpecial:true}])];
  const matched = predicted.filter(n => draw.numbers.includes(n));
  return {
    whyWinningBallsAppeared: balls.map(({number,isSpecial}) => {
      const occurs = (r:QualityDraw) => isSpecial ? r.specialNumber === number : r.numbers.includes(number);
      const gap = history.findIndex(occurs), frequency = history.filter(occurs).length;
      return { number,isSpecial,role:'Thống kê lịch sử trước kỳ quay',drawGap:gap < 0 ? null : gap,frequency,
        explanation:`Trong ${history.length} kỳ trước, số ${number} xuất hiện ${frequency} lần${gap < 0 ? '; chưa ghi nhận lần xuất hiện trước' : `; cách lần gần nhất ${gap} kỳ đã lưu`}.` };
    }),
    whyAlgorithmMissed: {
      summary:predicted.length === 6 ? `Vé sáu số khớp ${matched.length}/${draw.numbers.length}: [${matched.join(', ')}].` : 'Không đủ lịch sử để tạo vé kiểm thử.',
      primaryReason:'Đối chiếu dự đoán hồi cứu với kết quả đã lưu.',
      missedFactors:draw.numbers.filter(n=>!predicted.includes(n)).map(n=>`Số ${n} không nằm trong vé sáu số.`),
      correctiveAdjustment:'So sánh trên nhiều kỳ trước khi thay đổi tham số; số liệu này chưa xác định nguyên nhân vật lý của kết quả.'
    }
  };
}

export function summarizeBacktest(draws: any[], totalHistoricalDraws: number, algorithm: string, category: string) {
  const evaluated = draws.filter(d => d.aiPrediction?.predicted6Numbers?.length === 6);
  const hits = evaluated.reduce((sum,d) => sum + d.aiPrediction.matchedIn6Count,0);
  const hit3 = evaluated.filter(d=>d.aiPrediction.matchedIn6Count >= 3).length;
  const dates = draws.map(d=>d.drawDate).sort();
  return { algorithm,category,totalHistoricalDraws,requestedDrawsCount:draws.length,evaluatedDrawsCount:evaluated.length,
    unevaluatedDrawsCount:draws.length-evaluated.length,fromDate:dates[0]??null,toDate:dates.at(-1)??null,
    mainNumberHits:hits,averageMatched:evaluated.length ? hits/evaluated.length : null,
    hit3Draws:hit3,hit3RatePercent:evaluated.length ? hit3/evaluated.length*100 : null,
    scope:`${evaluated.length}/${draws.length} kỳ được chọn đủ lịch sử; ${totalHistoricalDraws} kỳ hợp lệ trong kho dữ liệu.`,
    conclusion:evaluated.length ? `Kiểm thử vé sáu số trên ${evaluated.length} kỳ: khớp tổng ${hits} số, ${hit3} kỳ đạt ít nhất ba số. Dùng cấu hình hiện tại; chưa có so sánh phiên bản dự đoán lưu trước giờ quay.` : 'Chưa có kỳ đủ lịch sử để đo hiệu quả.',
    dominantFlaws:evaluated.length ? [`Có ${evaluated.length-hit3}/${evaluated.length} vé sáu số khớp dưới ba số.`] : [],
    coreRemedies:evaluated.length ? ['Đánh giá cùng số vé và cùng cửa sổ thời gian khi thử cấu hình khác.'] : [] };
}
