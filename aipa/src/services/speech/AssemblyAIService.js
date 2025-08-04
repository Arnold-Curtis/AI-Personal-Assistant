import RecordRTC from 'recordrtc';
import { BaseSpeechService, SpeechEvents } from './types';

export class AssemblyAIService extends BaseSpeechService {
  constructor() {
    super();
    this.apiKey = process.env.REACT_APP_ASSEMBLYAI_API_KEY;
    this.recorder = null;
    this.recording = false;
    this.websocket = null;
    this.audioBlobs = [];
    
    if (!this.apiKey) {
      console.warn('AssemblyAI API key not found in environment variables');
    }
  }

  isSupported() {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && this.apiKey);
  }
  async startRecording() {
    if (!this.isSupported()) {
      throw new Error('AssemblyAI service not supported - missing browser APIs or API key');
    }

    if (this.recording) {
      console.log('⚠️ Already recording, skipping start');
      return;
    }

    try {
      console.log('🎤 AssemblyAI: Requesting user media...');
      // Get user media
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true
        } 
      });
      console.log('✅ AssemblyAI: User media granted');

      // Setup WebSocket connection to AssemblyAI
      console.log('🔗 AssemblyAI: Setting up WebSocket...');
      await this.setupWebSocket();
      console.log('✅ AssemblyAI: WebSocket connected');

      // Setup RecordRTC
      console.log('🎙️ AssemblyAI: Setting up RecordRTC...');
      this.recorder = new RecordRTC(stream, {
        type: 'audio',
        mimeType: 'audio/wav',
        recorderType: RecordRTC.StereoAudioRecorder,
        numberOfAudioChannels: 1,
        desiredSampRate: 16000,
        timeSlice: 250, // Send data every 250ms
        ondataavailable: (blob) => {
          console.log('🎵 AssemblyAI: Audio data available, size:', blob.size);
          if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.sendAudioData(blob);
          } else {
            console.warn('⚠️ AssemblyAI: WebSocket not ready, skipping audio data');
          }
        }
      });

      this.recorder.startRecording();
      this.recording = true;
      
      console.log('🎉 AssemblyAI: Recording started successfully');
      this.emit(SpeechEvents.START, { message: 'AssemblyAI recording started' });
    } catch (error) {
      console.error('❌ AssemblyAI: Failed to start recording:', error);
      this.recording = false;
      this.emit(SpeechEvents.ERROR, { 
        error: error.message, 
        message: `Failed to start AssemblyAI recording: ${error.message}` 
      });
      throw error;
    }
  }
  async setupWebSocket() {
    return new Promise((resolve, reject) => {
      // Create WebSocket connection to AssemblyAI real-time API
      // Note: AssemblyAI real-time API uses token parameter in URL
      const wsUrl = `wss://api.assemblyai.com/v2/realtime/ws?sample_rate=16000&token=${this.apiKey}`;
      console.log('🔗 AssemblyAI: Connecting to WebSocket URL (token hidden for security)');
      
      this.websocket = new WebSocket(wsUrl);

      this.websocket.onopen = () => {
        console.log('🟢 AssemblyAI WebSocket connected');
        resolve();
      };

      this.websocket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.handleWebSocketMessage(data);
        } catch (error) {
          console.error('❌ AssemblyAI: Failed to parse WebSocket message:', error);
        }
      };

      this.websocket.onerror = (error) => {
        console.error('❌ AssemblyAI: WebSocket error:', error);
        this.emit(SpeechEvents.ERROR, { 
          error: 'WebSocket connection failed', 
          message: 'Failed to connect to AssemblyAI real-time service' 
        });
        reject(error);
      };

      this.websocket.onclose = (event) => {
        console.log('🔴 AssemblyAI: WebSocket closed, code:', event.code, 'reason:', event.reason);
        this.emit(SpeechEvents.END, { message: 'AssemblyAI session ended' });
      };
    });
  }handleWebSocketMessage(data) {
    console.log('📨 AssemblyAI: WebSocket message received:', data);
    
    if (data.error) {
      console.error('❌ AssemblyAI: API Error:', data.error);
      this.emit(SpeechEvents.ERROR, { 
        error: data.error, 
        message: `AssemblyAI Error: ${data.error}` 
      });
      return;
    }
    
    if (data.message_type === 'PartialTranscript') {
      console.log('📝 AssemblyAI: Partial transcript:', data.text);
      this.emit(SpeechEvents.TRANSCRIPT, { 
        transcript: data.text, 
        isFinal: false,
        confidence: data.confidence 
      });
    } else if (data.message_type === 'FinalTranscript') {
      console.log('✨ AssemblyAI: Final transcript:', data.text);
      this.emit(SpeechEvents.FINAL_TRANSCRIPT, { 
        transcript: data.text, 
        isFinal: true,
        confidence: data.confidence 
      });
    } else if (data.message_type === 'SessionBegins') {
      console.log('🟢 AssemblyAI: Session began:', data.session_id);
    } else if (data.message_type === 'SessionTerminated') {
      console.log('🔴 AssemblyAI: Session terminated');
    } else {
      console.log('ℹ️ AssemblyAI: Unknown message type:', data.message_type);
    }
  }

  async sendAudioData(blob) {
    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
      const arrayBuffer = await blob.arrayBuffer();
      const base64Data = this.arrayBufferToBase64(arrayBuffer);
      
      const message = {
        audio_data: base64Data
      };
      
      this.websocket.send(JSON.stringify(message));
    }
  }

  arrayBufferToBase64(buffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  async stopRecording() {
    if (this.recorder && this.recording) {
      this.recorder.stopRecording();
      this.recording = false;
      
      // Close WebSocket connection
      if (this.websocket) {
        this.websocket.close();
        this.websocket = null;
      }
      
      // Stop media stream
      if (this.recorder.stream) {
        this.recorder.stream.getTracks().forEach(track => track.stop());
      }
    }
  }

  isRecording() {
    return this.recording;
  }
}
