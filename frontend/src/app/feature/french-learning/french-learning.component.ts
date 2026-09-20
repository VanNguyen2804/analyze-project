import { Component } from '@angular/core';

interface Exercise {
  id: number;
  level: string;
  sentence: string;
  translation: string;
  userInput: string;
  status: 'none' | 'correct' | 'incorrect';
}

@Component({
  selector: 'app-french-learning',
  templateUrl: './french-learning.component.html',
  styleUrls: ['./french-learning.component.css']
})
export class FrenchLearningComponent {
  // Quản lý Tab hiển thị
  activeTab: 'alphabet' | 'dictation' = 'alphabet';

  // Dữ liệu Bảng chữ cái kèm phiên âm tiếng Việt gần đúng
  alphabet = [
    { letter: 'A', pron: 'a' }, { letter: 'B', pron: 'bê' }, { letter: 'C', pron: 'xê' },
    { letter: 'D', pron: 'đê' }, { letter: 'E', pron: 'ơ' }, { letter: 'F', pron: 'ép' },
    { letter: 'G', pron: 'giê' }, { letter: 'H', pron: 'hát' }, { letter: 'I', pron: 'i' },
    { letter: 'J', pron: 'ji' }, { letter: 'K', pron: 'ca' }, { letter: 'L', pron: 'eo' },
    { letter: 'M', pron: 'em' }, { letter: 'N', pron: 'en' }, { letter: 'O', pron: 'ô' },
    { letter: 'P', pron: 'pê' }, { letter: 'Q', pron: 'cuy' }, { letter: 'R', pron: 'e-rờ' },
    { letter: 'S', pron: 'ét' }, { letter: 'T', pron: 'tê' }, { letter: 'U', pron: 'uy' },
    { letter: 'V', pron: 'vê' }, { letter: 'W', pron: 'đúp-lơ-vê' }, { letter: 'X', pron: 'ích' },
    { letter: 'Y', pron: 'i-gờ-réc' }, { letter: 'Z', pron: 'zét' }
  ];

  // Dữ liệu Bài tập nghe - viết
  exercises: Exercise[] = [
    { id: 1, level: 'Cơ bản', sentence: 'Bonjour', translation: 'Xin chào', userInput: '', status: 'none' },
    { id: 2, level: 'Cơ bản', sentence: 'Merci beaucoup', translation: 'Cảm ơn rất nhiều', userInput: '', status: 'none' },
    { id: 3, level: 'Trung bình', sentence: 'Comment allez-vous?', translation: 'Bạn có khỏe không?', userInput: '', status: 'none' },
    { id: 4, level: 'Trung bình', sentence: 'Je voudrais un café', translation: 'Tôi muốn một tách cà phê', userInput: '', status: 'none' },
    { id: 5, level: 'Nâng cao', sentence: 'Il pleut aujourd\'hui.', translation: 'Hôm nay trời mưa.', userInput: '', status: 'none' },
    { id: 6, level: 'Nâng cao', sentence: 'C\'est la vie!', translation: 'Đó là cuộc sống!', userInput: '', status: 'none' }
  ];

  totalScore: number = 0;

  // Sử dụng Web Speech API để đọc văn bản tiếng Pháp
  playAudio(text: string) {
    if ('speechSynthesis' in window) {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'fr-FR'; // Thiết lập giọng đọc tiếng Pháp
      utterance.rate = 0.85;    // Tốc độ đọc chậm lại một chút để dễ nghe
      window.speechSynthesis.speak(utterance);
    } else {
      alert('Trình duyệt của bạn không hỗ trợ tính năng phát âm.');
    }
  }

  // Thuật toán làm sạch chuỗi (bỏ dấu câu, chuyển chữ thường) để chấm điểm linh hoạt
  private normalizeString(str: string): string {
    return str.toLowerCase()
              .replace(/[.,!?]/g, '') // Bỏ dấu chấm, phẩy, hỏi, chấm than
              .replace(/'/g, ' ')     // Đổi dấu nháy đơn thành khoảng trắng (VD: C'est -> C est)
              .replace(/\s+/g, ' ')   // Bỏ khoảng trắng thừa
              .trim();
  }

  // Chấm điểm từng câu
  checkAnswer(ex: Exercise) {
    if (!ex.userInput) return;

    const normalizedExpected = this.normalizeString(ex.sentence);
    const normalizedUser = this.normalizeString(ex.userInput);

    if (normalizedExpected === normalizedUser) {
      if (ex.status !== 'correct') {
        this.totalScore += 10; // Cộng 10 điểm cho mỗi câu đúng
      }
      ex.status = 'correct';
    } else {
      ex.status = 'incorrect';
    }
  }
}