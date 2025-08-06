import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { toast } from './utils/toastUtils';
import './Settings.css';

const Settings = ({ onBack, darkMode, toggleTheme, user }) => {
  const [activeCategory, setActiveCategory] = useState('ui-appearance');
  const [settings, setSettings] = useState({
    
    theme: darkMode ? 'dark' : 'light',
    fontSize: localStorage.getItem('fontSize') || 'medium',
    animations: localStorage.getItem('animations') !== 'false',
    darkModeColorScheme: localStorage.getItem('darkModeColorScheme') || 'default-dark',
    lightModeColorScheme: localStorage.getItem('lightModeColorScheme') || 'default-white',
    
    
    speechProvider: localStorage.getItem('speechProvider') || 'auto',
    voiceLanguage: localStorage.getItem('voiceLanguage') || 'en-US',
    voiceAutoStop: localStorage.getItem('voiceAutoStop') !== 'false',
    
    
    toastPosition: localStorage.getItem('toastPosition') || 'bottom-right',
    toastDuration: parseInt(localStorage.getItem('toastDuration')) || 5000,
    soundEnabled: localStorage.getItem('soundEnabled') !== 'false',
    notificationsEnabled: localStorage.getItem('notificationsEnabled') !== 'false',
    
    
    defaultView: localStorage.getItem('calendarDefaultView') || 'dayGridMonth',
    eventColors: localStorage.getItem('eventColors') !== 'false',
    timeZone: localStorage.getItem('timeZone') || Intl.DateTimeFormat().resolvedOptions().timeZone,
    weekStartsOn: parseInt(localStorage.getItem('weekStartsOn')) || 0,
    
    
    autoSave: localStorage.getItem('autoSave') !== 'false',
    dataRetention: localStorage.getItem('dataRetention') || '1year',
    exportFormat: localStorage.getItem('exportFormat') || 'json',
  });

  const [loading, setLoading] = useState(false);

  
  useEffect(() => {
    
    const fontSize = settings.fontSize;
    document.documentElement.style.setProperty('--app-font-size', 
      fontSize === 'small' ? '14px' : fontSize === 'large' ? '18px' : '16px');

    
    document.documentElement.style.setProperty('--animations-enabled', 
      settings.animations ? '1' : '0');
    document.documentElement.classList.toggle('animations-disabled', !settings.animations);

    
    const currentColorScheme = darkMode ? settings.darkModeColorScheme : settings.lightModeColorScheme;
    applyColorScheme(currentColorScheme, darkMode);
  }, [settings.fontSize, settings.animations, settings.darkModeColorScheme, settings.lightModeColorScheme, darkMode]);

  
  useEffect(() => {
    
    const savedFontSize = localStorage.getItem('fontSize') || 'medium';
    document.documentElement.style.setProperty('--app-font-size', 
      savedFontSize === 'small' ? '14px' : savedFontSize === 'large' ? '18px' : '16px');

    
    const savedAnimations = localStorage.getItem('animations') !== 'false';
    document.documentElement.style.setProperty('--animations-enabled', 
      savedAnimations ? '1' : '0');
    document.documentElement.classList.toggle('animations-disabled', !savedAnimations);

    
    const savedDarkColorScheme = localStorage.getItem('darkModeColorScheme') || 'default-dark';
    const savedLightColorScheme = localStorage.getItem('lightModeColorScheme') || 'default-white';
    const currentColorScheme = darkMode ? savedDarkColorScheme : savedLightColorScheme;
    applyColorScheme(currentColorScheme, darkMode);
  }, [darkMode]); 

  const categories = [
    {
      id: 'ui-appearance',
      name: 'UI & Appearance',
      icon: '🎨',
      description: 'Customize the look and feel'
    },
    {
      id: 'voice-speech',
      name: 'Voice & Speech',
      icon: '🎤',
      description: 'Configure voice input settings'
    },
    {
      id: 'notifications',
      name: 'Notifications',
      icon: '🔔',
      description: 'Manage notification preferences'
    },
    {
      id: 'calendar',
      name: 'Calendar',
      icon: '📅',
      description: 'Calendar display and behavior'
    },
    {
      id: 'privacy-data',
      name: 'Privacy & Data',
      icon: '🔒',
      description: 'Data management and privacy'
    }
  ];

  const handleSettingChange = (key, value) => {
    setSettings(prev => ({
      ...prev,
      [key]: value
    }));

    
    switch (key) {
      case 'theme':
        if (value !== (darkMode ? 'dark' : 'light')) {
          toggleTheme();
        }
        break;
      case 'fontSize':
        document.documentElement.style.setProperty('--app-font-size', 
          value === 'small' ? '14px' : value === 'large' ? '18px' : '16px');
        localStorage.setItem('fontSize', value);
        break;
      case 'animations':
        document.documentElement.style.setProperty('--animations-enabled', 
          value ? '1' : '0');
        document.documentElement.classList.toggle('animations-disabled', !value);
        localStorage.setItem('animations', value);
        break;
      case 'darkModeColorScheme':
        localStorage.setItem('darkModeColorScheme', value);
        if (darkMode) {
          applyColorScheme(value, true);
        }
        break;
      case 'lightModeColorScheme':
        localStorage.setItem('lightModeColorScheme', value);
        if (!darkMode) {
          applyColorScheme(value, false);
        }
        break;
      case 'notificationsEnabled':
        localStorage.setItem('notificationsEnabled', value);
        if (!value) {
          
          window.toastify?.clear?.();
        }
        break;
      case 'defaultView':
        localStorage.setItem('calendarDefaultView', value);
        
        window.dispatchEvent(new CustomEvent('calendarViewChange', { detail: { view: value } }));
        break;
      case 'toastPosition':
        localStorage.setItem('toastPosition', value);
        
        window.dispatchEvent(new CustomEvent('toastPositionChange', { detail: { position: value } }));
        break;
      case 'toastDuration':
        localStorage.setItem('toastDuration', value);
        
        window.dispatchEvent(new CustomEvent('toastDurationChange', { detail: { duration: value } }));
        break;
      default:
        localStorage.setItem(key, value);
        break;
    }
  };

  const applyColorScheme = (colorScheme, isDarkMode) => {
    const root = document.documentElement;
    
    
    root.classList.remove('default-dark', 'dark-blue', 'dark-purple', 'dark-green', 'pure-black');
    root.classList.remove('default-white', 'light-blue', 'light-purple', 'light-green', 'light-orange');
    
    
    if (colorScheme !== 'default-dark' && colorScheme !== 'default-white') {
      root.classList.add(colorScheme);
    }
  };

  const saveSettings = async () => {
    setLoading(true);
    try {
      
      const settingsPayload = {
        userId: user.id,
        settings: settings
      };
      
      await axios.post('/api/user/settings', settingsPayload);
      
      
      Object.entries(settings).forEach(([key, value]) => {
        localStorage.setItem(key, value);
      });
      
      
      if (settings.notificationsEnabled) {
        toast.success('Settings saved successfully!');
      }
    } catch (error) {
      console.error('Error saving settings:', error);
      
      if (settings.notificationsEnabled) {
        toast.error('Failed to save settings: ' + (error.response?.data?.message || error.message));
      }
    } finally {
      setLoading(false);
    }
  };

  const resetToDefaults = () => {
    const defaultSettings = {
      theme: 'dark',
      fontSize: 'medium',
      animations: true,
      darkModeColorScheme: 'default-dark',
      lightModeColorScheme: 'default-white',
      speechProvider: 'auto',
      voiceLanguage: 'en-US',
      voiceAutoStop: true,
      toastPosition: 'bottom-right',
      toastDuration: 5000,
      soundEnabled: true,
      notificationsEnabled: true,
      defaultView: 'dayGridMonth',
      eventColors: true,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      weekStartsOn: 0,
      autoSave: true,
      dataRetention: '1year',
      exportFormat: 'json'
    };
    
    setSettings(defaultSettings);
    
    
    if (defaultSettings.theme !== (darkMode ? 'dark' : 'light')) {
      toggleTheme();
    }
    
    
    applyColorScheme('default-dark', true);  
    applyColorScheme('default-white', false); 
    
    
    document.documentElement.style.setProperty('--app-font-size', '16px');
    document.documentElement.style.setProperty('--animations-enabled', '1');
    document.documentElement.classList.remove('animations-disabled');
  };

  const renderUIAppearanceSettings = () => (
    <div className="settings-section">
      <h3>UI & Appearance Settings</h3>
      
      <div className="setting-item">
        <label>Theme</label>
        <div className="setting-control">
          <select 
            value={settings.theme} 
            onChange={(e) => handleSettingChange('theme', e.target.value)}
          >
            <option value="light">Light Mode</option>
            <option value="dark">Dark Mode</option>
          </select>
        </div>
        <p className="setting-description">Choose between light and dark themes</p>
      </div>

      <div className="setting-item">
        <label>Font Size</label>
        <div className="setting-control">
          <select 
            value={settings.fontSize} 
            onChange={(e) => handleSettingChange('fontSize', e.target.value)}
          >
            <option value="small">Small</option>
            <option value="medium">Medium</option>
            <option value="large">Large</option>
          </select>
        </div>
        <p className="setting-description">Adjust the default font size</p>
      </div>

      <div className="setting-item">
        <label>Animations</label>
        <div className="setting-control">
          <label className="toggle-switch">
            <input 
              type="checkbox" 
              checked={settings.animations} 
              onChange={(e) => handleSettingChange('animations', e.target.checked)}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
        <p className="setting-description">Enable or disable UI animations</p>
      </div>

      <div className="setting-item">
        <label>Dark Mode Color Scheme</label>
        <div className="setting-control">
          <select 
            value={settings.darkModeColorScheme} 
            onChange={(e) => handleSettingChange('darkModeColorScheme', e.target.value)}
          >
            <option value="default-dark">Default Dark</option>
            <option value="dark-blue">Dark Blue</option>
            <option value="dark-purple">Dark Purple</option>
            <option value="dark-green">Dark Green</option>
            <option value="pure-black">Pure Black</option>
          </select>
        </div>
        <p className="setting-description">Choose your preferred color scheme for dark mode</p>
      </div>

      <div className="setting-item">
        <label>Light Mode Color Scheme</label>
        <div className="setting-control">
          <select 
            value={settings.lightModeColorScheme} 
            onChange={(e) => handleSettingChange('lightModeColorScheme', e.target.value)}
          >
            <option value="default-white">Default White</option>
            <option value="light-blue">Light Blue</option>
            <option value="light-purple">Light Purple</option>
            <option value="light-green">Light Green</option>
            <option value="light-orange">Light Orange</option>
          </select>
        </div>
        <p className="setting-description">Choose your preferred color scheme for light mode</p>
      </div>
    </div>
  );

  const renderVoiceSpeechSettings = () => (
    <div className="settings-section">
      <h3>Voice & Speech Settings</h3>
      
      <div className="setting-item">
        <label>Speech Provider</label>
        <div className="setting-control">
          <select 
            value={settings.speechProvider} 
            onChange={(e) => handleSettingChange('speechProvider', e.target.value)}
          >
            <option value="auto">Auto (Best Available)</option>
            <option value="assemblyai">AssemblyAI (Premium)</option>
            <option value="web-speech">Browser Speech API</option>
          </select>
        </div>
        <p className="setting-description">Choose your preferred speech recognition service</p>
      </div>

      <div className="setting-item">
        <label>Voice Language</label>
        <div className="setting-control">
          <select 
            value={settings.voiceLanguage} 
            onChange={(e) => handleSettingChange('voiceLanguage', e.target.value)}
          >
            <option value="en-US">English (US)</option>
            <option value="en-GB">English (UK)</option>
            <option value="es-ES">Spanish</option>
            <option value="fr-FR">French</option>
            <option value="de-DE">German</option>
            <option value="it-IT">Italian</option>
            <option value="pt-BR">Portuguese (Brazil)</option>
          </select>
        </div>
        <p className="setting-description">Select your preferred language for voice input</p>
      </div>

      <div className="setting-item">
        <label>Auto-stop Recording</label>
        <div className="setting-control">
          <label className="toggle-switch">
            <input 
              type="checkbox" 
              checked={settings.voiceAutoStop} 
              onChange={(e) => handleSettingChange('voiceAutoStop', e.target.checked)}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
        <p className="setting-description">Automatically stop recording after silence</p>
      </div>
    </div>
  );

  const renderNotificationsSettings = () => (
    <div className="settings-section">
      <h3>Notification Settings</h3>
      
      <div className="setting-item">
        <label>Notifications Enabled</label>
        <div className="setting-control">
          <label className="toggle-switch">
            <input 
              type="checkbox" 
              checked={settings.notificationsEnabled} 
              onChange={(e) => handleSettingChange('notificationsEnabled', e.target.checked)}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
        <p className="setting-description">Enable or disable all notifications</p>
      </div>

      <div className="setting-item">
        <label>Toast Position</label>
        <div className="setting-control">
          <select 
            value={settings.toastPosition} 
            onChange={(e) => handleSettingChange('toastPosition', e.target.value)}
          >
            <option value="top-left">Top Left</option>
            <option value="top-center">Top Center</option>
            <option value="top-right">Top Right</option>
            <option value="bottom-left">Bottom Left</option>
            <option value="bottom-center">Bottom Center</option>
            <option value="bottom-right">Bottom Right</option>
          </select>
        </div>
        <p className="setting-description">Choose where notifications appear</p>
      </div>

      <div className="setting-item">
        <label>Toast Duration</label>
        <div className="setting-control">
          <select 
            value={settings.toastDuration} 
            onChange={(e) => handleSettingChange('toastDuration', parseInt(e.target.value))}
          >
            <option value={3000}>3 seconds</option>
            <option value={5000}>5 seconds</option>
            <option value={8000}>8 seconds</option>
            <option value={0}>Manual dismiss</option>
          </select>
        </div>
        <p className="setting-description">How long notifications are displayed</p>
      </div>

      <div className="setting-item">
        <label>Sound Notifications</label>
        <div className="setting-control">
          <label className="toggle-switch">
            <input 
              type="checkbox" 
              checked={settings.soundEnabled} 
              onChange={(e) => handleSettingChange('soundEnabled', e.target.checked)}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
        <p className="setting-description">Play sounds for notifications</p>
      </div>
    </div>
  );

  const renderCalendarSettings = () => (
    <div className="settings-section">
      <h3>Calendar Settings</h3>
      
      <div className="setting-item">
        <label>Default View</label>
        <div className="setting-control">
          <select 
            value={settings.defaultView} 
            onChange={(e) => handleSettingChange('defaultView', e.target.value)}
          >
            <option value="dayGridMonth">Month View</option>
            <option value="timeGridWeek">Week View</option>
            <option value="timeGridDay">Day View</option>
            <option value="listWeek">List View</option>
          </select>
        </div>
        <p className="setting-description">Default calendar view when opening the app</p>
      </div>

      <div className="setting-item">
        <label>Week Starts On</label>
        <div className="setting-control">
          <select 
            value={settings.weekStartsOn} 
            onChange={(e) => handleSettingChange('weekStartsOn', parseInt(e.target.value))}
          >
            <option value={0}>Sunday</option>
            <option value={1}>Monday</option>
          </select>
        </div>
        <p className="setting-description">Choose the first day of the week</p>
      </div>

      <div className="setting-item">
        <label>Event Colors</label>
        <div className="setting-control">
          <label className="toggle-switch">
            <input 
              type="checkbox" 
              checked={settings.eventColors} 
              onChange={(e) => handleSettingChange('eventColors', e.target.checked)}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
        <p className="setting-description">Use automatic colors for different event types</p>
      </div>

      <div className="setting-item">
        <label>Time Zone</label>
        <div className="setting-control">
          <select 
            value={settings.timeZone} 
            onChange={(e) => handleSettingChange('timeZone', e.target.value)}
          >
            <option value="America/New_York">Eastern Time</option>
            <option value="America/Chicago">Central Time</option>
            <option value="America/Denver">Mountain Time</option>
            <option value="America/Los_Angeles">Pacific Time</option>
            <option value="Europe/London">London</option>
            <option value="Europe/Paris">Paris</option>
            <option value="Asia/Tokyo">Tokyo</option>
            <option value="Australia/Sydney">Sydney</option>
          </select>
        </div>
        <p className="setting-description">Set your time zone for events</p>
      </div>
    </div>
  );

  const renderPrivacyDataSettings = () => (
    <div className="settings-section">
      <h3>Privacy & Data Settings</h3>
      
      <div className="setting-item">
        <label>Auto-save</label>
        <div className="setting-control">
          <label className="toggle-switch">
            <input 
              type="checkbox" 
              checked={settings.autoSave} 
              onChange={(e) => handleSettingChange('autoSave', e.target.checked)}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
        <p className="setting-description">Automatically save changes as you type</p>
      </div>

      <div className="setting-item">
        <label>Data Retention</label>
        <div className="setting-control">
          <select 
            value={settings.dataRetention} 
            onChange={(e) => handleSettingChange('dataRetention', e.target.value)}
          >
            <option value="3months">3 Months</option>
            <option value="6months">6 Months</option>
            <option value="1year">1 Year</option>
            <option value="2years">2 Years</option>
            <option value="forever">Forever</option>
          </select>
        </div>
        <p className="setting-description">How long to keep your data</p>
      </div>

      <div className="setting-item">
        <label>Export Format</label>
        <div className="setting-control">
          <select 
            value={settings.exportFormat} 
            onChange={(e) => handleSettingChange('exportFormat', e.target.value)}
          >
            <option value="json">JSON</option>
            <option value="csv">CSV</option>
            <option value="pdf">PDF</option>
          </select>
        </div>
        <p className="setting-description">Default format for data exports</p>
      </div>

      <div className="setting-item">
        <label>Clear All Data</label>
        <div className="setting-control">
          <button 
            className="danger-button"
            onClick={() => {
              if (window.confirm('Are you sure you want to clear all your data? This cannot be undone.')) {
                
                const keysToKeep = ['authToken', 'token', 'darkMode']; 
                Object.keys(localStorage).forEach(key => {
                  if (!keysToKeep.includes(key)) {
                    localStorage.removeItem(key);
                  }
                });
                
                
                resetToDefaults();
                
                if (settings.notificationsEnabled) {
                  toast.success('All user data has been cleared');
                }
              }
            }}
          >
            Clear Data
          </button>
        </div>
        <p className="setting-description">Permanently delete all your data</p>
      </div>
    </div>
  );

  const renderCurrentSettings = () => {
    switch (activeCategory) {
      case 'ui-appearance':
        return renderUIAppearanceSettings();
      case 'voice-speech':
        return renderVoiceSpeechSettings();
      case 'notifications':
        return renderNotificationsSettings();
      case 'calendar':
        return renderCalendarSettings();
      case 'privacy-data':
        return renderPrivacyDataSettings();
      default:
        return renderUIAppearanceSettings();
    }
  };

  return (
    <div className={`settings-container ${darkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="settings-header">
        <button className="back-button" onClick={onBack}>
          ← Back
        </button>
        <h1>Settings</h1>
        <div className="settings-header-actions">
          <button className="reset-button" onClick={resetToDefaults}>
            Reset to Defaults
          </button>
          <button 
            className="save-button" 
            onClick={saveSettings}
            disabled={loading}
          >
            {loading ? 'Saving...' : 'Save Settings'}
          </button>
        </div>
      </div>

      <div className="settings-content">
        <div className="settings-sidebar">
          {categories.map(category => (
            <div 
              key={category.id}
              className={`category-item ${activeCategory === category.id ? 'active' : ''}`}
              onClick={() => setActiveCategory(category.id)}
            >
              <div className="category-icon">{category.icon}</div>
              <div className="category-info">
                <h3>{category.name}</h3>
                <p>{category.description}</p>
              </div>
            </div>
          ))}
        </div>

        <div className="settings-main">
          {renderCurrentSettings()}
        </div>
      </div>
    </div>
  );
};

export default Settings;

