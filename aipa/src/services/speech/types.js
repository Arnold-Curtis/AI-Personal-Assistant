

export const SpeechServiceProviders = {
  ASSEMBLY_AI: 'assemblyai',
  WEB_SPEECH: 'web-speech'
};

export const SpeechEvents = {
  START: 'start',
  TRANSCRIPT: 'transcript',
  FINAL_TRANSCRIPT: 'final_transcript',
  ERROR: 'error',
  END: 'end'
};


export class BaseSpeechService {
  constructor() {
    this.eventHandlers = {};
  }

  
  on(event, handler) {
    if (!this.eventHandlers[event]) {
      this.eventHandlers[event] = [];
    }
    this.eventHandlers[event].push(handler);
  }

  off(event, handler) {
    if (this.eventHandlers[event]) {
      this.eventHandlers[event] = this.eventHandlers[event].filter(h => h !== handler);
    }
  }

  emit(event, data) {
    if (this.eventHandlers[event]) {
      this.eventHandlers[event].forEach(handler => handler(data));
    }
  }

  
  async startRecording() {
    throw new Error('startRecording must be implemented by provider');
  }

  async stopRecording() {
    throw new Error('stopRecording must be implemented by provider');
  }

  isRecording() {
    throw new Error('isRecording must be implemented by provider');
  }

  isSupported() {
    throw new Error('isSupported must be implemented by provider');
  }
}

