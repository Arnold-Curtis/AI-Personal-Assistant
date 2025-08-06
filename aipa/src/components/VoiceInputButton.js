import React, { useState, useEffect, useRef } from 'react';
import { SpeechServiceFactory, SpeechEvents } from '../services/speech';
import { toast } from 'react-toastify';

export const VoiceInputButton = ({ onTranscript, onFinalTranscript, onStop, disabled = false }) => {
  const [isRecording, setIsRecording] = useState(false);
  const [isSupported, setIsSupported] = useState(false);
  const [currentProvider, setCurrentProvider] = useState(null);
  const [fallbackAttempted, setFallbackAttempted] = useState(false);
  const [lastFinalTranscript, setLastFinalTranscript] = useState('');
  const speechServiceRef = useRef(null);
  useEffect(() => {
    
    console.log('🔍 Checking speech service availability...');
    console.log('Environment AssemblyAI key:', process.env.REACT_APP_ASSEMBLYAI_API_KEY ? 'Present' : 'Missing');
    
    const bestProvider = SpeechServiceFactory.getBestAvailableProvider();
    console.log('🏆 Best available provider:', bestProvider);
    
    if (bestProvider) {
      setIsSupported(true);
      setCurrentProvider(bestProvider);
      console.log(`✅ Speech service available: ${bestProvider}`);
    } else {
      setIsSupported(false);
      console.warn('⚠️ No speech recognition services available');
    }
  }, []);  const handleStartRecording = async () => {
    if (!isSupported || isRecording || disabled) {
      console.log('Cannot start recording:', { isSupported, isRecording, disabled });
      return;
    }

    try {
      console.log('🎤 Starting voice recording with provider:', currentProvider);      
      if (!isRecording) {
        setFallbackAttempted(false);
        setLastFinalTranscript(''); 
      }
      
      
      speechServiceRef.current = SpeechServiceFactory.createService(currentProvider);
      console.log('✅ Speech service created:', speechServiceRef.current);
      
      
      speechServiceRef.current.on(SpeechEvents.START, () => {
        console.log('🎙️ Recording started event received');
        setIsRecording(true);
        
        setLastFinalTranscript('');
        toast.info('🎤 Listening...', { autoClose: 2000 });
      });

      speechServiceRef.current.on(SpeechEvents.TRANSCRIPT, (data) => {
        console.log('📝 Interim transcript received:', data);
        if (onTranscript) {
          onTranscript(data.transcript, data.isFinal);
        }
      });      speechServiceRef.current.on(SpeechEvents.FINAL_TRANSCRIPT, (data) => {
        console.log('✨ Final transcript received:', data);
        
        
        if (data.transcript && data.transcript !== lastFinalTranscript) {
          setLastFinalTranscript(data.transcript);
          if (onFinalTranscript) {
            onFinalTranscript(data.transcript);
          }
        } else {
          console.log('🔄 Duplicate final transcript ignored:', data.transcript);
        }
      });speechServiceRef.current.on(SpeechEvents.ERROR, (data) => {
        console.error('❌ Speech recognition error:', data);
        setIsRecording(false);
        
        
        if (currentProvider === 'assemblyai' && !fallbackAttempted && (data.error === 'Not authorized' || data.message.includes('Not authorized'))) {
          console.log('🔄 AssemblyAI authorization failed, falling back to Web Speech API...');
          toast.warning('AssemblyAI unavailable, switching to browser speech recognition...');
          
          
          setCurrentProvider('web-speech');
          setFallbackAttempted(true);
          setTimeout(() => {
            handleStartRecording();
          }, 500);
          return;
        }
        
        toast.error(`Voice input error: ${data.message}`);
      });

      speechServiceRef.current.on(SpeechEvents.END, () => {
        console.log('🛑 Recording ended event received');
        setIsRecording(false);
      });

      
      console.log('🚀 Starting speech service recording...');
      await speechServiceRef.current.startRecording();
      console.log('✅ Speech service recording started successfully');
    } catch (error) {
      console.error('❌ Failed to start voice recording:', error);
      setIsRecording(false);
      toast.error(`Failed to start voice input: ${error.message}`);
    }
  };
  const handleStopRecording = async () => {
    if (speechServiceRef.current && isRecording) {
      try {
        await speechServiceRef.current.stopRecording();
        setIsRecording(false);
        
        
        if (onStop) {
          onStop();
        }
        
        toast.success('🎤 Voice input stopped');
      } catch (error) {
        console.error('Failed to stop voice recording:', error);
        toast.error('Failed to stop voice input');
      }
    }
  };

  const handleToggleRecording = () => {
    if (isRecording) {
      handleStopRecording();
    } else {
      handleStartRecording();
    }
  };

  if (!isSupported) {
    return (
      <button
        type="button"
        disabled
        title="Voice input not available in this browser"
        style={{
          padding: '12px',
          background: '#6c757d',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'not-allowed',
          fontSize: '16px',
          opacity: 0.6
        }}
      >
        🎤
      </button>
    );
  }
  return (
    <>
      <style>{`
        @keyframes pulse {
          0% {
            box-shadow: 0 0 0 0 rgba(220, 53, 69, 0.7);
          }
          70% {
            box-shadow: 0 0 0 10px rgba(220, 53, 69, 0);
          }
          100% {
            box-shadow: 0 0 0 0 rgba(220, 53, 69, 0);
          }
        }
        
        @keyframes wave {
          0% { transform: scale(1); }
          30% { transform: scale(1.1); }
          60% { transform: scale(1); }
          100% { transform: scale(1); }
        }
        
        .voice-button {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 48px;
          height: 48px;
          border-radius: 50%;
          border: none;
          background: var(--button-bg, #f0f4f8);
          color: var(--button-color, #333);
          font-size: 20px;
          cursor: pointer;
          transition: all 0.3s cubic-bezier(0.68, -0.55, 0.27, 1.55);
          box-shadow: var(--button-shadow, 0 2px 8px rgba(0, 0, 0, 0.1));
          position: relative;
          overflow: hidden;
        }
        
        .voice-button:hover {
          transform: translateY(-2px);
          box-shadow: var(--button-shadow-hover, 0 6px 12px rgba(0, 0, 0, 0.15));
        }
        
        .voice-button:active {
          transform: translateY(1px);
        }
        
        .voice-button.recording {
          background: var(--recording-bg, #ff4d6d);
          color: white;
          animation: pulse 1.5s infinite;
        }
        
        .voice-button.recording .voice-icon {
          animation: wave 1.5s infinite;
        }
        
        .voice-button:disabled {
          opacity: 0.6;
          cursor: not-allowed;
          transform: none;
          box-shadow: none;
        }
        
        .voice-icon {
          font-size: 20px;
          line-height: 1;
        }
        
        body.dark-mode .voice-button {
          --button-bg: #2d3748;
          --button-color: #e2e8f0;
          --button-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
          --button-shadow-hover: 0 6px 12px rgba(0, 0, 0, 0.4);
          --recording-bg: #e53e3e;
        }
      `}</style>
      <button
        type="button"
        onClick={handleToggleRecording}
        disabled={disabled}
        className={`voice-button ${isRecording ? 'recording' : ''}`}
        title={isRecording ? 'Stop voice input' : 'Start voice input'}
        aria-label={isRecording ? 'Stop voice input' : 'Start voice input'}
      >
        <span className="voice-icon">{isRecording ? '⏺️' : '🎤'}</span>
      </button>
    </>
  );
};

