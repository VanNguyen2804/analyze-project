import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalyzeService } from 'src/app/core/services/analyze.service';

@Component({
  selector: 'app-french-learning',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './french-learning.component.html',
  styleUrls: ['./french-learning.component.css']
})
export class FrenchLearningComponent implements OnInit {
  activeTab: 'alphabet' | 'dictation' | 'history' = 'alphabet';
  currentCategory: string = 'ALL';
  
  exercises: any[] = [];
  attemptHistory: any[] = [];
  totalScore: number = 0;

  // Dữ liệu Bảng chữ cái giữ nguyên
  alphabet = [
    { letter: 'A', pron: 'a' }, { letter: 'B', pron: 'bê' }, { letter: 'C', pron: 'xê' },
    // ... (Thêm lại đầy đủ bảng chữ cái của bạn)
  ];

  constructor(private service: AnalyzeService) {}

  ngOnInit() {
    this.loadExercises();
    this.loadHistory();
  }

  loadExercises() {
    this.service.getFrenchExercises(this.currentCategory).subscribe(res => {
      // Map thêm các thuộc tính dùng cho UI
      this.exercises = res.map(ex => ({ ...ex, userInput: '', status: 'none' }));
    });
  }

  loadHistory() {
    this.service.getFrenchHistory().subscribe(res => {
      this.attemptHistory = res;
      this.totalScore = res.filter(h => h.correct).length * 10; // Cập nhật tổng điểm
    });
  }

  changeCategory(cat: string) {
    this.currentCategory = cat;
    this.loadExercises();
  }

  playAudio(text: string) {
    if ('speechSynthesis' in window) {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'fr-FR';
      utterance.rate = 0.85;
      window.speechSynthesis.speak(utterance);
    }
  }

  checkAnswer(ex: any) {
    if (!ex.userInput) return;

    const payload = {
      exerciseId: ex.id,
      userInput: ex.userInput
    };

    this.service.submitFrenchAttempt(payload).subscribe(result => {
      ex.status = result.correct ? 'correct' : 'incorrect';
      this.loadHistory(); // Refresh bảng điểm sau khi gửi
    });
  }
}