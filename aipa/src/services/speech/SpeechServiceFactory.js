import { SpeechServiceProviders } from './types';
import { AssemblyAIService } from './AssemblyAIService';
import { WebSpeechService } from './WebSpeechService';

export class SpeechServiceFactory {
  static createService(provider = SpeechServiceProviders.ASSEMBLY_AI) {
    switch (provider) {
      case SpeechServiceProviders.ASSEMBLY_AI:
        return new AssemblyAIService();
      case SpeechServiceProviders.WEB_SPEECH:
        return new WebSpeechService();
      default:
        throw new Error(`Unknown speech service provider: ${provider}`);
    }
  }

  static getAvailableProviders() {
    const providers = [];
    
    // Check AssemblyAI availability
    if (process.env.REACT_APP_ASSEMBLYAI_API_KEY) {
      providers.push({
        id: SpeechServiceProviders.ASSEMBLY_AI,
        name: 'AssemblyAI',
        description: 'High-accuracy cloud-based speech recognition',
        supported: true
      });
    }
    
    // Check Web Speech API availability
    const webSpeechSupported = 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window;
    providers.push({
      id: SpeechServiceProviders.WEB_SPEECH,
      name: 'Web Speech API',
      description: 'Built-in browser speech recognition',
      supported: webSpeechSupported
    });

    return providers;
  }
  static getBestAvailableProvider() {
    const providers = this.getAvailableProviders();
    
    // For now, prefer Web Speech API as it's more reliable for browser usage
    // AssemblyAI requires additional setup for CORS and API validation
    const webSpeech = providers.find(p => p.id === SpeechServiceProviders.WEB_SPEECH && p.supported);
    if (webSpeech) {
      return SpeechServiceProviders.WEB_SPEECH;
    }
    
    // Fallback to AssemblyAI if available
    const assemblyAI = providers.find(p => p.id === SpeechServiceProviders.ASSEMBLY_AI && p.supported);
    if (assemblyAI) {
      return SpeechServiceProviders.ASSEMBLY_AI;
    }
    
    return null;
  }
}
