import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

export interface AlphabetItem {
  letter: string;
  ipa: string;
  pron: string;
  word: string;
  wordMeaning: string;
  wordIcon: string;
}

export interface SentenceExercise {
  id: number;
  category: 'ALL' | 'BASIC' | 'ARTICLE_ADJ' | 'NEGATIVE' | 'CONVERSATION';
  categoryLabel: string;
  level: string;
  vietnamese: string;
  expectedTokens: string[];
  scrambledTokens: string[];
  userTokens: string[];
  status: 'none' | 'correct' | 'incorrect';
  grammarPattern: string;
  explanation: string;
  hint: string;
  showHint?: boolean;
}

export interface QuizQuestion {
  id: number;
  question: string;
  vietnamesePrompt: string;
  options: string[];
  correctIndex: number;
  explanation: string;
  selectedOption?: number;
  isAnswered?: boolean;
}

@Component({
  selector: 'app-french-learning',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './french-learning.component.html',
  styleUrls: ['./french-learning.component.css']
})
export class FrenchLearningComponent implements OnInit {
  activeTab: 'alphabet' | 'sentence-guide' | 'sentence-builder' | 'quiz' | 'history' = 'sentence-builder';
  
  // 1. BẢNG CHỮ CÁI TIẾNG PHÁP ĐẦY ĐỦ 26 CHỮ CÁI (A - Z)
  alphabet: AlphabetItem[] = [
    { letter: 'A', ipa: '[a]', pron: 'a', word: 'Avion', wordMeaning: 'Máy bay', wordIcon: '✈️' },
    { letter: 'B', ipa: '[be]', pron: 'bê', word: 'Bonjour', wordMeaning: 'Xin chào', wordIcon: '👋' },
    { letter: 'C', ipa: '[se]', pron: 'xê', word: 'Café', wordMeaning: 'Cà phê', wordIcon: '☕' },
    { letter: 'D', ipa: '[de]', pron: 'đê', word: 'Danse', wordMeaning: 'Khiêu vũ', wordIcon: '💃' },
    { letter: 'E', ipa: '[ə]', pron: 'ơ (khum môi)', word: 'École', wordMeaning: 'Trường học', wordIcon: '🏫' },
    { letter: 'F', ipa: '[ɛf]', pron: 'ép-phờ', word: 'Fleur', wordMeaning: 'Bông hoa', wordIcon: '🌸' },
    { letter: 'G', ipa: '[ʒe]', pron: 'giê (nhẹ)', word: 'Garçon', wordMeaning: 'Cậu bé', wordIcon: '👦' },
    { letter: 'H', ipa: '[aʃ]', pron: 'át-sơ (âm câm)', word: 'Hôtel', wordMeaning: 'Khách sạn', wordIcon: '🏨' },
    { letter: 'I', ipa: '[i]', pron: 'i', word: 'Idée', wordMeaning: 'Ý tưởng', wordIcon: '💡' },
    { letter: 'J', ipa: '[ʒi]', pron: 'gi', word: 'Jardin', wordMeaning: 'Khu vườn', wordIcon: '🌳' },
    { letter: 'K', ipa: '[ka]', pron: 'ca', word: 'Kilo', wordMeaning: 'Ki-lô-gam', wordIcon: '⚖️' },
    { letter: 'L', ipa: '[ɛl]', pron: 'e-lờ', word: 'Livre', wordMeaning: 'Quyển sách', wordIcon: '📖' },
    { letter: 'M', ipa: '[ɛm]', pron: 'em-mờ', word: 'Maison', wordMeaning: 'Ngôi nhà', wordIcon: '🏡' },
    { letter: 'N', ipa: '[ɛn]', pron: 'en-nờ', word: 'Nuit', wordMeaning: 'Ban đêm', wordIcon: '🌙' },
    { letter: 'O', ipa: '[o]', pron: 'ô', word: 'Orange', wordMeaning: 'Quả cam', wordIcon: '🍊' },
    { letter: 'P', ipa: '[pe]', pron: 'pê', word: 'Pomme', wordMeaning: 'Quả táo', wordIcon: '🍎' },
    { letter: 'Q', ipa: '[ky]', pron: 'kiu', word: 'Quatre', wordMeaning: 'Số bốn', wordIcon: '4️⃣' },
    { letter: 'R', ipa: '[ɛʁ]', pron: 'e-khừ (rung họng)', word: 'Rouge', wordMeaning: 'Màu đỏ', wordIcon: '🔴' },
    { letter: 'S', ipa: '[ɛs]', pron: 'ét-sơ', word: 'Soleil', wordMeaning: 'Mặt trời', wordIcon: '☀️' },
    { letter: 'T', ipa: '[te]', pron: 'tê', word: 'Table', wordMeaning: 'Cái bàn', wordIcon: '🪑' },
    { letter: 'U', ipa: '[y]', pron: 'u (chu tròn môi)', word: 'Un', wordMeaning: 'Số một', wordIcon: '1️⃣' },
    { letter: 'V', ipa: '[ve]', pron: 'vê', word: 'Voiture', wordMeaning: 'Xe hơi', wordIcon: '🚗' },
    { letter: 'W', ipa: '[dubləve]', pron: 'đúp-lơ-vê', word: 'Wagon', wordMeaning: 'Toa tàu', wordIcon: '🚃' },
    { letter: 'X', ipa: '[iks]', pron: 'ích-xừ', word: 'Xylophone', wordMeaning: 'Đàn phiến gỗ', wordIcon: '🎵' },
    { letter: 'Y', ipa: '[igʁɛk]', pron: 'i-gờ-rếch', word: 'Yeux', wordMeaning: 'Đôi mắt', wordIcon: '👀' },
    { letter: 'Z', ipa: '[zɛd]', pron: 'dét', word: 'Zèbre', wordMeaning: 'Ngựa vằn', wordIcon: '🦓' }
  ];

  // Các ký tự dấu đặc biệt trong tiếng Pháp
  specialAccents = [
    { char: 'é', name: 'Accent aigu (Dấu sắc)', note: 'Đọc là âm "ê" khép miệng.', example: 'Café (Cà phê), Été (Mùa hè)' },
    { char: 'è / ê', name: 'Accent grave / circonflexe', note: 'Đọc là âm "e" mở rộng miệng.', example: 'Mère (Mẹ), Fête (Lễ hội)' },
    { char: 'ç', name: 'Cédille (Dấu móc dưới c)', note: 'Chữ C phát âm thành "s" trước a, o, u.', example: 'Français (Tiếng Pháp), Garçon (Cậu bé)' },
    { char: 'H câm', name: 'H muet (Âm H câm)', note: 'H không bao giờ phát âm, luôn nối âm với từ trước.', example: "L'homme (Người đàn ông), L'hôtel" },
    { char: 'ou', name: 'Nguyên âm đôi ou', note: 'Đọc như âm "u" thuần tiếng Việt.', example: 'Bonjour (Xin chào), Nous (Chúng tôi)' },
    { char: 'au / eau', name: 'Tổ hợp au / eau', note: 'Đọc như âm "ô" tiếng Việt.', example: "L'eau (Nước), Beaucoup (Nhiều)" }
  ];

  // 2. BÀI TẬP GHÉP TỪ THÀNH CÂU (SENTENCE BUILDER EXERCISES)
  selectedCategory: string = 'ALL';
  currentExerciseIndex: number = 0;
  totalScore: number = 0;
  attemptHistory: any[] = [];

  allExercises: SentenceExercise[] = [
    {
      id: 1,
      category: 'BASIC',
      categoryLabel: 'Chủ ngữ + Động từ Être',
      level: 'Khởi động',
      vietnamese: 'Tôi là một sinh viên.',
      expectedTokens: ['Je', 'suis', 'un', 'étudiant.'],
      scrambledTokens: ['un', 'étudiant.', 'Je', 'suis'],
      userTokens: [],
      status: 'none',
      grammarPattern: 'Chủ ngữ (Je) + Động từ Être (suis) + Mạo từ (un) + Danh từ (étudiant)',
      explanation: '• "Je" (Tôi) là đại từ nhân xưng đóng vai trò Chủ ngữ.\n• "suis" là dạng chia thì hiện tại của động từ "Être" (thì, là, ở) đi với ngôi "Je".\n• "un" là mạo từ bất định giống đực số ít đi kèm danh từ "étudiant".',
      hint: 'Trật tự câu: [Chủ ngữ: Je] ➔ [Động từ: suis] ➔ [Mạo từ: un] ➔ [Danh từ: étudiant.]'
    },
    {
      id: 2,
      category: 'ARTICLE_ADJ',
      categoryLabel: 'Ghép Danh từ + Tính từ màu sắc',
      level: 'Cơ bản',
      vietnamese: 'Bạn có một con mèo màu đen.',
      expectedTokens: ['Tu', 'as', 'un', 'chat', 'noir.'],
      scrambledTokens: ['noir.', 'as', 'chat', 'Tu', 'un'],
      userTokens: [],
      status: 'none',
      grammarPattern: 'Sujet (Tu) + Verbe Avoir (as) + Article (un) + Nom (chat) + Adjectif (noir)',
      explanation: '• "Tu" (Bạn) đi với động từ "Avoir" (có) chia thành "as".\n• "un chat" = một con mèo (danh từ giống đực).\n• QUY TẮC GHÉP TỪ QUAN TRỌNG: Trong tiếng Pháp, tính từ chỉ màu sắc LUÔN ĐỨNG SAU danh từ ("un chat noir", khác với tiếng Anh "a black cat").',
      hint: 'Nhớ trật tự ghép từ: Danh từ "chat" đứng TRƯỚC tính từ màu sắc "noir".'
    },
    {
      id: 3,
      category: 'ARTICLE_ADJ',
      categoryLabel: 'Giống cái & Tính từ bổ nghĩa',
      level: 'Cơ bản',
      vietnamese: 'Cô ấy ăn một quả táo màu đỏ.',
      expectedTokens: ['Elle', 'mange', 'une', 'pomme', 'rouge.'],
      scrambledTokens: ['pomme', 'Elle', 'rouge.', 'une', 'mange'],
      userTokens: [],
      status: 'none',
      grammarPattern: 'Sujet (Elle) + Verbe (mange) + Article féminin (une) + Nom (pomme) + Adjectif (rouge)',
      explanation: '• "Elle" (Cô ấy) là chủ ngữ ngôi thứ 3 số ít.\n• "mange" là động từ "manger" (ăn) chia ở ngôi Elle.\n• "pomme" là danh từ giống cái nên dùng mạo từ "une".\n• Tính từ màu sắc "rouge" đứng sau danh từ.',
      hint: 'Chủ ngữ "Elle" ➔ Động từ "mange" ➔ Cụm danh từ "une pomme rouge."'
    },
    {
      id: 4,
      category: 'NEGATIVE',
      categoryLabel: 'Công thức Phủ định (Ne... Pas)',
      level: 'Cơ bản',
      vietnamese: 'Tôi không nói tiếng Anh.',
      expectedTokens: ['Je', 'ne', 'parle', 'pas', 'anglais.'],
      scrambledTokens: ['parle', 'anglais.', 'Je', 'pas', 'ne'],
      userTokens: [],
      status: 'none',
      grammarPattern: 'Sujet (Je) + ne + Verbe (parle) + pas + Complément (anglais)',
      explanation: '• CÔNG THỨC PHỦ ĐỊNH "KẸP BÁNH MÌ": Động từ được kẹp giữa "ne" và "pas".\n• Khẳng định: "Je parle anglais." ➔ Phủ định: "Je [ne] parle [pas] anglais."',
      hint: 'Đặt "ne" ngay trước động từ "parle", và "pas" ngay sau động từ.'
    },
    {
      id: 5,
      category: 'ARTICLE_ADJ',
      categoryLabel: 'Tính từ BANGS đứng trước danh từ',
      level: 'Nâng cao chút',
      vietnamese: 'Anh ấy là một người bạn tốt.',
      expectedTokens: ['Il', 'est', 'un', 'bon', 'ami.'],
      scrambledTokens: ['ami.', 'bon', 'Il', 'un', 'est'],
      userTokens: [],
      status: 'none',
      grammarPattern: 'Sujet (Il) + Verbe (est) + Article (un) + Adjectif BANGS (bon) + Nom (ami)',
      explanation: '• NGOẠI LỆ VỊ TRÍ TÍNH TỪ (BANGS): Các tính từ chỉ Phẩm chất (Goodness như "bon") đứng TRƯỚC danh từ!\n• Vì vậy ta ghép: "un bon ami" (người bạn tốt).',
      hint: 'Tính từ "bon" đứng TRƯỚC danh từ "ami".'
    },
    {
      id: 6,
      category: 'NEGATIVE',
      categoryLabel: 'Phủ định với nguyên âm (N\'... Pas)',
      level: 'Cơ bản',
      vietnamese: 'Tôi không thích cà phê.',
      expectedTokens: ['Je', "n'", 'aime', 'pas', 'le', 'café.'],
      scrambledTokens: ['le', 'aime', 'café.', 'Je', "n'", 'pas'],
      userTokens: [],
      status: 'none',
      grammarPattern: "Sujet (Je) + n' + Verbe voyelle (aime) + pas + Article (le) + Nom (café)",
      explanation: '• Động từ "aimer" (thích) bắt đầu bằng nguyên âm "a", do đó "ne" bị lược âm biến thành "n\'".\n• Ta có cấu trúc: "Je n\'aime pas...".\n• Nói về sở thích chung dùng mạo từ xác định "le café".',
      hint: 'Nguyên âm "a" làm "ne" rút gọn thành "n\'" đi liền trước "aime".'
    },
    {
      id: 7,
      category: 'BASIC',
      categoryLabel: 'Động từ Avoir & Liên từ',
      level: 'Cơ bản',
      vietnamese: 'Tôi có một con chó và hai con mèo.',
      expectedTokens: ["J'", 'ai', 'un', 'chien', 'et', 'deux', 'chats.'],
      scrambledTokens: ['chien', 'chats.', 'ai', "J'", 'et', 'deux', 'un'],
      userTokens: [],
      status: 'none',
      grammarPattern: "Sujet élidé (J') + Verbe Avoir (ai) + Nom 1 + Conjonction (et) + Nom 2 pluriel",
      explanation: '• Đại từ "Je" đi trước nguyên âm của động từ "ai" rút gọn thành "J\'ai" (Tôi có).\n• "et" là liên từ mang nghĩa "và".\n• "chats" thêm "-s" ở số nhiều sau số đếm "deux".',
      hint: 'Ghép "J\'ai" ➔ "un chien" ➔ liên từ "et" ➔ "deux chats."'
    },
    {
      id: 8,
      category: 'CONVERSATION',
      categoryLabel: 'Giới từ nơi chốn & Tính từ',
      level: 'Thực hành',
      vietnamese: 'Chúng tôi sống trong một ngôi nhà lớn.',
      expectedTokens: ['Nous', 'habitons', 'dans', 'une', 'grande', 'maison.'],
      scrambledTokens: ['grande', 'dans', 'Nous', 'maison.', 'habitons', 'une'],
      userTokens: [],
      status: 'none',
      grammarPattern: 'Sujet (Nous) + Verbe (habitons) + Préposition (dans) + Article (une) + Adjectif BANGS (grande) + Nom (maison)',
      explanation: '• "Nous habitons": Chúng tôi sống (động từ habiter chia theo ngôi Nous).\n• "dans": giới từ mang nghĩa "trong / bên trong".\n• "grande": tính từ chỉ kích cỡ (Size trong BANGS) đứng TRƯỚC danh từ. "Maison" là giống cái nên tính từ thêm "-e".',
      hint: '"grande" đứng trước "maison" (thuộc quy tắc BANGS).'
    },
    {
      id: 9,
      category: 'CONVERSATION',
      categoryLabel: 'Câu giao tiếp lịch sự (Politesse)',
      level: 'Thực hành',
      vietnamese: 'Làm ơn cho tôi một ly cà phê.',
      expectedTokens: ['Je', 'voudrais', 'un', 'café,', "s'il", 'vous', 'plaît.'],
      scrambledTokens: ["s'il", 'un', 'Je', 'vous', 'voudrais', 'café,', 'plaît.'],
      userTokens: [],
      status: 'none',
      grammarPattern: "Formule: Je voudrais (Tôi muốn lịch sự) + Nom + s'il vous plaît (Làm ơn)",
      explanation: '• "Je voudrais" là mẫu câu giao tiếp lịch sự chuẩn mực để gọi món, mua hàng thay vì dùng "Je veux".\n• "s\'il vous plaît" nghĩa là "xin làm ơn", đặt ở cuối câu để thể hiện sự tôn trọng.',
      hint: '"Je voudrais" ở đầu câu, cụm "s\'il vous plaît" ở cuối câu.'
    },
    {
      id: 10,
      category: 'ARTICLE_ADJ',
      categoryLabel: 'Mạo từ rút gọn trước nguyên âm (L\')',
      level: 'Cơ bản',
      vietnamese: 'Trường học rất đẹp.',
      expectedTokens: ["L'", 'école', 'est', 'très', 'belle.'],
      scrambledTokens: ['très', 'est', 'belle.', "L'", 'école'],
      userTokens: [],
      status: 'none',
      grammarPattern: "Article élidé (L') + Nom féminin (école) + Verbe Être (est) + Adverbe (très) + Adjectif (belle)",
      explanation: '• "école" là danh từ giống cái nhưng bắt đầu bằng nguyên âm "é", nên "la" rút gọn thành "L\'".\n• "très" (rất) là phó từ bổ nghĩa đứng trước tính từ "belle".\n• "belle" là tính từ giống cái của "beau" hợp giống với "école".',
      hint: '"L\'" đi liền trước "école", phó từ "très" đứng trước "belle."'
    }
  ];

  filteredExercises: SentenceExercise[] = [];

  // 3. BÀI TẬP TRẮC NGHIỆM GHÉP TỪ (QUIZ)
  quizQuestions: QuizQuestion[] = [
    {
      id: 1,
      question: '___ maison est très belle.',
      vietnamesePrompt: 'Chọn mạo từ đúng cho danh từ giống cái "maison" (ngôi nhà):',
      options: ['Le', 'La', 'Un', 'Les'],
      correctIndex: 1,
      explanation: 'Chính xác! "Maison" là danh từ giống cái (féminin) số ít, nên mạo từ xác định phù hợp là "La".'
    },
    {
      id: 2,
      question: 'Chọn cách ghép từ ĐÚNG cho: "Một chiếc xe hơi màu xanh dương"',
      vietnamesePrompt: 'Quy tắc ghép tính từ màu sắc trong tiếng Pháp:',
      options: ['Une bleue voiture', 'Une voiture bleue', 'Un voiture bleu', 'Un bleu voiture'],
      correctIndex: 1,
      explanation: 'Chính xác! Trong tiếng Pháp, tính từ chỉ màu sắc LUÔN ĐỨNG SAU danh từ. Ngoài ra "voiture" là giống cái nên dùng "une" và tính từ phải có "-e" ➔ "Une voiture bleue".'
    },
    {
      id: 3,
      question: 'Tu ___ très gentil.',
      vietnamesePrompt: 'Điền dạng đúng của động từ "Être" (thì, là) đi với chủ ngữ "Tu":',
      options: ['suis', 'es', 'est', 'êtes'],
      correctIndex: 1,
      explanation: 'Chính xác! Động từ Être chia theo các ngôi: Je suis, Tu es, Il/Elle est, Nous sommes, Vous êtes, Ils/Elles sont.'
    },
    {
      id: 4,
      question: 'Je ___ mange ___ de viande.',
      vietnamesePrompt: 'Chọn cặp từ phủ định "kẹp" lấy động từ trong câu phủ định:',
      options: ['ne ... pas', 'pas ... ne', 'ne ... non', 'rien ... ne'],
      correctIndex: 0,
      explanation: 'Chính xác! Cấu trúc câu phủ định cơ bản của tiếng Pháp là: [Chủ ngữ] + ne + [Động từ] + pas.'
    },
    {
      id: 5,
      question: 'C\'est un ___ garçon.',
      vietnamesePrompt: 'Chọn tính từ đứng TRƯỚC danh từ theo quy tắc BANGS (Phẩm chất tốt):',
      options: ['bon', 'noir', 'français', 'froid'],
      correctIndex: 0,
      explanation: 'Chính xác! Tính từ "bon" (tốt, ngoan) thuộc nhóm BANGS (Goodness) nên đứng TRƯỚC danh từ "garçon". Các tính từ màu sắc hay quốc tịch thì phải đứng sau.'
    },
    {
      id: 6,
      question: 'Nous ___ un grand jardin.',
      vietnamesePrompt: 'Điền động từ "Avoir" (có) đi với chủ ngữ "Nous":',
      options: ['sommes', 'avons', 'avez', 'ont'],
      correctIndex: 1,
      explanation: 'Chính xác! Động từ Avoir chia theo ngôi: J\'ai, Tu as, Il/Elle a, Nous avons, Vous avez, Ils/Elles ont.'
    },
    {
      id: 7,
      question: '___ école est moderne.',
      vietnamesePrompt: 'Chọn mạo từ đứng trước danh từ bắt đầu bằng nguyên âm "é":',
      options: ['La', 'L\'', 'Le', 'Une'],
      correctIndex: 1,
      explanation: 'Chính xác! Khi danh từ bắt đầu bằng nguyên âm (a, e, i, o, u, y) hoặc âm h câm, mạo từ "le" hoặc "la" rút gọn thành "L\'" để tránh đụng âm.'
    },
    {
      id: 8,
      question: 'Ces robes sont ___.',
      vietnamesePrompt: 'Hòa hợp tính từ với danh từ số nhiều giống cái "robes" (những chiếc váy):',
      options: ['blanche', 'blanc', 'blanches', 'blancs'],
      correctIndex: 2,
      explanation: 'Chính xác! "Robes" là danh từ giống cái số nhiều, do đó tính từ "blanc" đổi thành dạng giống cái ("blanche") và thêm "-s" số nhiều ➔ "blanches".'
    }
  ];

  constructor(private service: AnalyzeService) {}

  ngOnInit() {
    this.filterCategory('ALL');
    this.loadHistory();
    this.loadScore();
  }

  loadScore() {
    const saved = localStorage.getItem('french_user_score');
    if (saved) {
      this.totalScore = parseInt(saved, 10) || 0;
    }
  }

  saveScore() {
    localStorage.setItem('french_user_score', this.totalScore.toString());
  }

  loadHistory() {
    this.service.getFrenchHistory().subscribe({
      next: (res) => {
        if (Array.isArray(res) && res.length > 0) {
          this.attemptHistory = res;
        } else {
          this.loadLocalHistory();
        }
      },
      error: () => {
        this.loadLocalHistory();
      }
    });
  }

  loadLocalHistory() {
    try {
      const local = localStorage.getItem('french_attempt_history');
      if (local) {
        this.attemptHistory = JSON.parse(local);
      }
    } catch (e) {
      this.attemptHistory = [];
    }
  }

  saveLocalHistory(record: any) {
    this.attemptHistory.unshift(record);
    if (this.attemptHistory.length > 50) {
      this.attemptHistory = this.attemptHistory.slice(0, 50);
    }
    localStorage.setItem('french_attempt_history', JSON.stringify(this.attemptHistory));
  }

  // --- Chức năng Ghép từ thành câu ---
  filterCategory(cat: string) {
    this.selectedCategory = cat;
    if (cat === 'ALL') {
      this.filteredExercises = [...this.allExercises];
    } else {
      this.filteredExercises = this.allExercises.filter(e => e.category === cat);
    }
    this.currentExerciseIndex = 0;
    this.resetCurrentExercise();
  }

  get currentExercise(): SentenceExercise | undefined {
    return this.filteredExercises[this.currentExerciseIndex];
  }

  selectExercise(index: number) {
    this.currentExerciseIndex = index;
    this.resetCurrentExercise();
  }

  nextExercise() {
    if (this.currentExerciseIndex < this.filteredExercises.length - 1) {
      this.currentExerciseIndex++;
      this.resetCurrentExercise();
    }
  }

  prevExercise() {
    if (this.currentExerciseIndex > 0) {
      this.currentExerciseIndex--;
      this.resetCurrentExercise();
    }
  }

  addTokenToAnswer(token: string, tokenIndex: number) {
    const ex = this.currentExercise;
    if (!ex || ex.status === 'correct') return;

    ex.userTokens.push(token);
    ex.scrambledTokens.splice(tokenIndex, 1);
    ex.status = 'none';
  }

  removeTokenFromAnswer(token: string, userIndex: number) {
    const ex = this.currentExercise;
    if (!ex || ex.status === 'correct') return;

    ex.userTokens.splice(userIndex, 1);
    ex.scrambledTokens.push(token);
    ex.status = 'none';
  }

  resetCurrentExercise() {
    const ex = this.currentExercise;
    if (!ex) return;

    ex.userTokens = [];
    // Khôi phục lại danh sách từ xáo trộn ban đầu
    const original = this.allExercises.find(e => e.id === ex.id);
    if (original) {
      ex.scrambledTokens = [...original.scrambledTokens];
    }
    ex.status = 'none';
    ex.showHint = false;
  }

  toggleHint() {
    const ex = this.currentExercise;
    if (ex) {
      ex.showHint = !ex.showHint;
    }
  }

  checkSentenceBuilder() {
    const ex = this.currentExercise;
    if (!ex || ex.userTokens.length === 0) return;

    const userSentence = ex.userTokens.join(' ').replace(/\s+([.,!?'"])/g, '$1').replace(/\s+([’'])/g, '$1');
    const expectedSentence = ex.expectedTokens.join(' ').replace(/\s+([.,!?'"])/g, '$1').replace(/\s+([’'])/g, '$1');

    // So sánh chuẩn hóa không phân biệt ký tự thừa
    const cleanUser = userSentence.toLowerCase().replace(/[.,!?'"]/g, ' ').replace(/\s+/g, ' ').trim();
    const cleanExpected = expectedSentence.toLowerCase().replace(/[.,!?'"]/g, ' ').replace(/\s+/g, ' ').trim();

    const isCorrect = cleanUser === cleanExpected;
    ex.status = isCorrect ? 'correct' : 'incorrect';

    if (isCorrect) {
      this.totalScore += 10;
      this.saveScore();
      this.playAudio(expectedSentence);
    }

    // Lưu vào lịch sử
    const historyItem = {
      exerciseId: ex.id,
      userInput: userSentence,
      expectedSentence: expectedSentence,
      correct: isCorrect,
      attemptedAt: new Date().toISOString()
    };
    this.saveLocalHistory(historyItem);

    // Gửi lên backend nếu có
    this.service.submitFrenchAttempt({
      exerciseId: ex.id,
      userInput: userSentence
    }).subscribe({
      next: () => {},
      error: () => {}
    });
  }

  // --- Chức năng Trắc nghiệm (Quiz) ---
  selectQuizOption(q: QuizQuestion, optIndex: number) {
    if (q.isAnswered) return;
    q.selectedOption = optIndex;
    q.isAnswered = true;
    if (optIndex === q.correctIndex) {
      this.totalScore += 10;
      this.saveScore();
      this.playAudio(q.question.replace('___', q.options[optIndex]));
    }
  }

  resetQuiz() {
    this.quizQuestions.forEach(q => {
      q.selectedOption = undefined;
      q.isAnswered = false;
    });
  }

  // --- Phát âm SpeechSynthesis (fr-FR) ---
  playAudio(text: string) {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'fr-FR';
      utterance.rate = 0.85;
      utterance.pitch = 1.0;
      window.speechSynthesis.speak(utterance);
    }
  }

  playAlphabetItem(item: AlphabetItem) {
    // Phát âm chữ cái kèm ví dụ
    const textToSay = `${item.letter}. ${item.word}.`;
    this.playAudio(textToSay);
  }
}
