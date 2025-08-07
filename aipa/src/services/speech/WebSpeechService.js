import { BaseSpeechService, SpeechEvents } from './types';

export class WebSpeechService extends BaseSpeechService {
  constructor() {
    super();
    this.recognition = null;
    this.recording = false;
    this.lastFinalTranscript = '';
  }

  isSupported() {
    return 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window;
  }
  async startRecording() {
    if (!this.isSupported()) {
      throw new Error('Web Speech API not supported in this browser');
    }

    if (this.recording) {
      console.log('⚠️ WebSpeech: Already recording, skipping start');
      return;
    }    try {
      console.log('🎤 WebSpeech: Starting recognition...');
      
      // Reset duplicate tracking
      this.lastFinalTranscript = '';
      
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      this.recognition = new SpeechRecognition();
      
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.lang = 'en-US';

      this.recognition.onstart = () => {
        console.log('🎙️ WebSpeech: Recognition started');
        this.recording = true;
        this.emit(SpeechEvents.START, { message: 'Speech recognition started' });
      };

      this.recognition.onresult = (event) => {
        console.log('📨 WebSpeech: Recognition result event:', event);
        let interimTranscript = '';
        let finalTranscript = '';

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const transcript = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            finalTranscript += transcript;
            console.log('✨ WebSpeech: Final transcript:', transcript);
          } else {
            interimTranscript += transcript;
            console.log('📝 WebSpeech: Interim transcript:', transcript);
          }
        }

        if (interimTranscript) {
          this.emit(SpeechEvents.TRANSCRIPT, { 
            transcript: interimTranscript, 
            isFinal: false 
          });
        }        if (finalTranscript && finalTranscript !== this.lastFinalTranscript) {
          this.lastFinalTranscript = finalTranscript;
          console.log('✨ WebSpeech: New final transcript:', finalTranscript);
          this.emit(SpeechEvents.FINAL_TRANSCRIPT, { 
            transcript: finalTranscript, 
            isFinal: true 
          });
        } else if (finalTranscript) {
          console.log('🔄 WebSpeech: Duplicate final transcript ignored');
        }
      };

      this.recognition.onerror = (event) => {
        console.error('❌ WebSpeech: Recognition error:', event.error);
        this.recording = false;
        this.emit(SpeechEvents.ERROR, { 
          error: event.error, 
          message: `Speech recognition error: ${event.error}` 
        });
      };

      this.recognition.onend = () => {
        console.log('🛑 WebSpeech: Recognition ended');
        this.recording = false;
        this.emit(SpeechEvents.END, { message: 'Speech recognition ended' });
      };

      this.recognition.start();
      console.log('🚀 WebSpeech: Recognition start command sent');
    } catch (error) {
      console.error('❌ WebSpeech: Failed to start:', error);
      this.recording = false;
      throw error;
    }
  }

  async stopRecording() {
    if (this.recognition && this.recording) {
      this.recognition.stop();
      this.recording = false;
    }
  }

  isRecording() {
    return this.recording;
  }
}
