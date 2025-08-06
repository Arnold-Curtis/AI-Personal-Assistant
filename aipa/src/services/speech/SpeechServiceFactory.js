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
    
    
    if (process.env.REACT_APP_ASSEMBLYAI_API_KEY) {
      providers.push({
        id: SpeechServiceProviders.ASSEMBLY_AI,
        name: 'AssemblyAI',
        description: 'High-accuracy cloud-based speech recognition',
        supported: true
      });
    }
    
    
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
    
    
    
    const webSpeech = providers.find(p => p.id === SpeechServiceProviders.WEB_SPEECH && p.supported);
    if (webSpeech) {
      return SpeechServiceProviders.WEB_SPEECH;
    }
    
    
    const assemblyAI = providers.find(p => p.id === SpeechServiceProviders.ASSEMBLY_AI && p.supported);
    if (assemblyAI) {
      return SpeechServiceProviders.ASSEMBLY_AI;
    }
    
    return null;
  }
}

