


const testSpeechIntegration = async () => {
  console.log('🎤 Testing AIPA Voice Integration...');
  
  try {
    
    const { SpeechServiceFactory, SpeechEvents } = await import('./src/services/speech');
    
    console.log('✅ Speech services imported successfully');
    
    
    const providers = SpeechServiceFactory.getAvailableProviders();
    console.log('📋 Available providers:', providers);
    
    
    const bestProvider = SpeechServiceFactory.getBestAvailableProvider();
    console.log('🏆 Best available provider:', bestProvider);
    
    if (!bestProvider) {
      console.warn('⚠️ No speech providers available');
      return;
    }
    
    
    const speechService = SpeechServiceFactory.createService(bestProvider);
    console.log('🔧 Created speech service:', bestProvider);
    
    
    console.log('🔍 Service supported:', speechService.isSupported());
    
    
    speechService.on(SpeechEvents.START, () => {
      console.log('🎙️ Recording started');
    });
    
    speechService.on(SpeechEvents.TRANSCRIPT, (data) => {
      console.log('📝 Interim transcript:', data.transcript);
    });
    
    speechService.on(SpeechEvents.FINAL_TRANSCRIPT, (data) => {
      console.log('✨ Final transcript:', data.transcript);
    });
    
    speechService.on(SpeechEvents.ERROR, (data) => {
      console.error('❌ Speech error:', data);
    });
    
    speechService.on(SpeechEvents.END, () => {
      console.log('🛑 Recording ended');
    });
    
    console.log('🎉 Voice integration test setup complete!');
    console.log('💡 Test manually by clicking the microphone button in the UI');
    
    return speechService;
    
  } catch (error) {
    console.error('❌ Voice integration test failed:', error);
  }
};


if (typeof window !== 'undefined') {
  window.testSpeechIntegration = testSpeechIntegration;
  console.log('🔧 Run window.testSpeechIntegration() to test voice features');
}

export default testSpeechIntegration;

