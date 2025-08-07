import React, { useState, useEffect, useRef, useCallback } from 'react';
import axios from 'axios';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import './App.css';
import { Textbox } from './Textbox';
import { Calendar } from './Calendar';
import { Plan } from './Plan';
import { Login } from './Login';
import MyAccount from './MyAccount';
import Settings from './Settings';
import SettingsButton from './components/SettingsButton';
import { setupScrollDetection, scrollAfterResponse } from './utils/scrollUtils';

// Configure axios defaults
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
axios.defaults.baseURL = API_BASE_URL;

// Add request interceptor to include token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, error => {
  return Promise.reject(error);
});

// Add response interceptor to handle errors
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.code === 'ERR_NETWORK') {
      toast.error('Network error - backend may be down', { autoClose: false });
    } else if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.reload();
    } else if (error.response?.data?.error) {
      // Don't show global error toasts for calendar event operations
      // These are handled locally in the Calendar component
      const isCalendarEventError = error.config?.url?.includes('/api/calendar/events');
      const is404Error = error.response?.status === 404;
      
      if (!(isCalendarEventError && is404Error)) {
        toast.error(error.response.data.error);
      }
    }
    return Promise.reject(error);
  }
);

// Function to apply color schemes
const applyColorScheme = (colorScheme, isDarkMode) => {
  const root = document.documentElement;
  
  if (isDarkMode) {
    // Dark mode color schemes - darker, richer colors
    switch (colorScheme) {
      case 'default-dark':
        // Keep current dark mode colors as default
        root.style.setProperty('--bg-primary', '#111827');
        root.style.setProperty('--bg-secondary', '#1f2937');
        root.style.setProperty('--text-primary', '#f9fafb');
        root.style.setProperty('--text-secondary', '#d1d5db');
        root.style.setProperty('--accent-color', '#818cf8');
        root.style.setProperty('--accent-hover', '#6366f1');
        root.style.setProperty('--accent-light', '#3730a3');
        root.style.setProperty('--border-color', '#374151');
        root.style.setProperty('--card-bg', '#1f2937');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #6366f1, #4f46e5)');
        break;
      case 'dark-blue':
        root.style.setProperty('--bg-primary', '#0f172a');
        root.style.setProperty('--bg-secondary', '#1e293b');
        root.style.setProperty('--text-primary', '#f1f5f9');
        root.style.setProperty('--text-secondary', '#cbd5e1');
        root.style.setProperty('--accent-color', '#3b82f6');
        root.style.setProperty('--accent-hover', '#1d4ed8');
        root.style.setProperty('--accent-light', '#1e40af');
        root.style.setProperty('--border-color', '#334155');
        root.style.setProperty('--card-bg', '#1e293b');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #3b82f6, #1d4ed8)');
        break;
      case 'dark-purple':
        root.style.setProperty('--bg-primary', '#1e1b4b');
        root.style.setProperty('--bg-secondary', '#312e81');
        root.style.setProperty('--text-primary', '#f8fafc');
        root.style.setProperty('--text-secondary', '#e2e8f0');
        root.style.setProperty('--accent-color', '#a78bfa');
        root.style.setProperty('--accent-hover', '#8b5cf6');
        root.style.setProperty('--accent-light', '#7c3aed');
        root.style.setProperty('--border-color', '#4c1d95');
        root.style.setProperty('--card-bg', '#312e81');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #8b5cf6, #7c3aed)');
        break;
      case 'dark-green':
        root.style.setProperty('--bg-primary', '#064e3b');
        root.style.setProperty('--bg-secondary', '#065f46');
        root.style.setProperty('--text-primary', '#f0fdf4');
        root.style.setProperty('--text-secondary', '#dcfce7');
        root.style.setProperty('--accent-color', '#34d399');
        root.style.setProperty('--accent-hover', '#10b981');
        root.style.setProperty('--accent-light', '#059669');
        root.style.setProperty('--border-color', '#047857');
        root.style.setProperty('--card-bg', '#065f46');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #34d399, #10b981)');
        break;
      case 'pure-black':
        root.style.setProperty('--bg-primary', '#000000');
        root.style.setProperty('--bg-secondary', '#1a1a1a');
        root.style.setProperty('--text-primary', '#ffffff');
        root.style.setProperty('--text-secondary', '#e5e5e5');
        root.style.setProperty('--accent-color', '#ffffff');
        root.style.setProperty('--accent-hover', '#e5e5e5');
        root.style.setProperty('--accent-light', '#404040');
        root.style.setProperty('--border-color', '#333333');
        root.style.setProperty('--card-bg', '#1a1a1a');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #ffffff, #e5e5e5)');
        break;
      default:
        // Default dark
        root.style.setProperty('--bg-primary', '#111827');
        root.style.setProperty('--bg-secondary', '#1f2937');
        root.style.setProperty('--text-primary', '#f9fafb');
        root.style.setProperty('--text-secondary', '#d1d5db');
        root.style.setProperty('--accent-color', '#818cf8');
        root.style.setProperty('--accent-hover', '#6366f1');
        root.style.setProperty('--accent-light', '#3730a3');
        root.style.setProperty('--border-color', '#374151');
        root.style.setProperty('--card-bg', '#1f2937');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #6366f1, #4f46e5)');
        break;
    }
  } else {
    // Light mode color schemes - bright, clean colors
    switch (colorScheme) {
      case 'default-white':
        // Pure white background with blue accents
        root.style.setProperty('--bg-primary', '#ffffff');
        root.style.setProperty('--bg-secondary', '#f8fafc');
        root.style.setProperty('--text-primary', '#0f172a');
        root.style.setProperty('--text-secondary', '#475569');
        root.style.setProperty('--accent-color', '#3b82f6');
        root.style.setProperty('--accent-hover', '#2563eb');
        root.style.setProperty('--accent-light', '#dbeafe');
        root.style.setProperty('--border-color', '#e2e8f0');
        root.style.setProperty('--card-bg', '#ffffff');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #3b82f6, #2563eb)');
        break;
      case 'light-blue':
        root.style.setProperty('--bg-primary', '#f0f9ff');
        root.style.setProperty('--bg-secondary', '#e0f2fe');
        root.style.setProperty('--text-primary', '#0c4a6e');
        root.style.setProperty('--text-secondary', '#0369a1');
        root.style.setProperty('--accent-color', '#0ea5e9');
        root.style.setProperty('--accent-hover', '#0284c7');
        root.style.setProperty('--accent-light', '#bae6fd');
        root.style.setProperty('--border-color', '#7dd3fc');
        root.style.setProperty('--card-bg', '#e0f2fe');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #0ea5e9, #0284c7)');
        break;
      case 'light-purple':
        root.style.setProperty('--bg-primary', '#faf5ff');
        root.style.setProperty('--bg-secondary', '#f3e8ff');
        root.style.setProperty('--text-primary', '#581c87');
        root.style.setProperty('--text-secondary', '#7c3aed');
        root.style.setProperty('--accent-color', '#a855f7');
        root.style.setProperty('--accent-hover', '#9333ea');
        root.style.setProperty('--accent-light', '#e9d5ff');
        root.style.setProperty('--border-color', '#d8b4fe');
        root.style.setProperty('--card-bg', '#f3e8ff');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #a855f7, #9333ea)');
        break;
      case 'light-green':
        root.style.setProperty('--bg-primary', '#f0fdf4');
        root.style.setProperty('--bg-secondary', '#dcfce7');
        root.style.setProperty('--text-primary', '#14532d');
        root.style.setProperty('--text-secondary', '#166534');
        root.style.setProperty('--accent-color', '#22c55e');
        root.style.setProperty('--accent-hover', '#16a34a');
        root.style.setProperty('--accent-light', '#bbf7d0');
        root.style.setProperty('--border-color', '#86efac');
        root.style.setProperty('--card-bg', '#dcfce7');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #22c55e, #16a34a)');
        break;
      case 'light-orange':
        root.style.setProperty('--bg-primary', '#fffbeb');
        root.style.setProperty('--bg-secondary', '#fef3c7');
        root.style.setProperty('--text-primary', '#92400e');
        root.style.setProperty('--text-secondary', '#b45309');
        root.style.setProperty('--accent-color', '#f59e0b');
        root.style.setProperty('--accent-hover', '#d97706');
        root.style.setProperty('--accent-light', '#fed7aa');
        root.style.setProperty('--border-color', '#fbbf24');
        root.style.setProperty('--card-bg', '#fef3c7');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #f59e0b, #d97706)');
        break;
      default:
        // Default white
        root.style.setProperty('--bg-primary', '#ffffff');
        root.style.setProperty('--bg-secondary', '#f8fafc');
        root.style.setProperty('--text-primary', '#0f172a');
        root.style.setProperty('--text-secondary', '#475569');
        root.style.setProperty('--accent-color', '#3b82f6');
        root.style.setProperty('--accent-hover', '#2563eb');
        root.style.setProperty('--accent-light', '#dbeafe');
        root.style.setProperty('--border-color', '#e2e8f0');
        root.style.setProperty('--card-bg', '#ffffff');
        root.style.setProperty('--gradient-primary', 'linear-gradient(135deg, #3b82f6, #2563eb)');
        break;
    }
  }
};

// Initialize app settings
const initializeAppSettings = (isDarkMode) => {
  // Apply font size from localStorage
  const savedFontSize = localStorage.getItem('fontSize') || 'medium';
  document.documentElement.style.setProperty('--app-font-size', 
    savedFontSize === 'small' ? '14px' : savedFontSize === 'large' ? '18px' : '16px');

  // Apply animations from localStorage
  const savedAnimations = localStorage.getItem('animations') !== 'false';
  document.documentElement.style.setProperty('--animations-enabled', 
    savedAnimations ? '1' : '0');
  document.documentElement.classList.toggle('animations-disabled', !savedAnimations);

  // Apply color schemes from localStorage
  const savedDarkColorScheme = localStorage.getItem('darkModeColorScheme') || 'default-dark';
  const savedLightColorScheme = localStorage.getItem('lightModeColorScheme') || 'default-white';
  const currentColorScheme = isDarkMode ? savedDarkColorScheme : savedLightColorScheme;
  applyColorScheme(currentColorScheme, isDarkMode);
};

function App() {
  const [user, setUser] = useState(null);
  const [events, setEvents] = useState([]);
  const [planSteps, setPlanSteps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [backendOnline, setBackendOnline] = useState(true);
  const [showMyAccount, setShowMyAccount] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [toastPosition, setToastPosition] = useState(
    localStorage.getItem('toastPosition') || 'bottom-right'
  );
  const [toastDuration, setToastDuration] = useState(
    parseInt(localStorage.getItem('toastDuration')) || 5000
  );
  const [darkMode, setDarkMode] = useState(() => {
    const savedMode = localStorage.getItem('darkMode');
    return savedMode !== null ? JSON.parse(savedMode) : true; // Set default to true for dark mode
  });
  const calendarRef = useRef(null);
  
  // Setup global scroll detection
  useEffect(() => {
    const cleanup = setupScrollDetection();
    return cleanup;
  }, []);

  // Initialize app settings when dark mode changes
  useEffect(() => {
    initializeAppSettings(darkMode);
  }, [darkMode]);

  // Listen for toast setting changes
  useEffect(() => {
    const handleToastPositionChange = (event) => {
      setToastPosition(event.detail.position);
    };

    const handleToastDurationChange = (event) => {
      setToastDuration(event.detail.duration);
    };

    window.addEventListener('toastPositionChange', handleToastPositionChange);
    window.addEventListener('toastDurationChange', handleToastDurationChange);
    
    return () => {
      window.removeEventListener('toastPositionChange', handleToastPositionChange);
      window.removeEventListener('toastDurationChange', handleToastDurationChange);
    };
  }, []);
  
  // Check backend health periodically
  useEffect(() => {
    const checkHealth = async () => {
      try {
        await axios.get('/api/health');
        if (!backendOnline) {
          setBackendOnline(true);
          toast.success("Backend connection restored!");
        }
      } catch (error) {
        if (backendOnline) {
          setBackendOnline(false);
          toast.error("Backend connection lost. Some features may not work.", { 
            autoClose: false,
            toastId: 'backend-offline'
          });
        }
      }
    };
    
    checkHealth();
    const interval = setInterval(checkHealth, 30000); // every 30 seconds
    return () => clearInterval(interval);
  }, [backendOnline]);

  // Apply dark mode to body and html
  useEffect(() => {
    if (darkMode) {
      document.body.classList.add('dark-mode');
      document.documentElement.classList.add('dark-mode');
      document.body.classList.remove('light-mode');
      document.documentElement.classList.remove('light-mode');
    } else {
      document.body.classList.add('light-mode');
      document.documentElement.classList.add('light-mode');
      document.body.classList.remove('dark-mode');
      document.documentElement.classList.remove('dark-mode');
    }
    localStorage.setItem('darkMode', JSON.stringify(darkMode));
  }, [darkMode]);

  const toggleTheme = () => {
    setDarkMode(prevMode => !prevMode);
  };

  const getDefaultAvatar = (email) => {
    return `https://ui-avatars.com/api/?name=${email.charAt(0).toUpperCase()}&background=3b82f6&color=fff`;
  };

  const loadEvents = useCallback(async () => {
    try {
      console.log("Loading calendar events...");
      const response = await axios.get('/api/calendar/events');
      console.log("Calendar events received:", response.data);
      
      if (Array.isArray(response.data)) {
        setEvents(response.data);
        
        // Refresh calendar if ref exists
        if (calendarRef.current && calendarRef.current.refreshEvents) {
          calendarRef.current.refreshEvents();
        }
      }
    } catch (error) {
      console.error("Error loading events:", error);
      toast.error('Failed to load calendar events: ' + (error.response?.data?.message || error.message));
    }
  }, [calendarRef]);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('token');
    setUser(null);
    setEvents([]);
    setPlanSteps([]);  // Clear plan data on logout
    setShowMyAccount(false); // Close My Account if open
    toast.info('You have been logged out');
  }, []);

  // Check authentication status on mount
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const token = localStorage.getItem('token');
        if (token) {
          const response = await axios.get('/api/auth/me');
          
          // Fix profile image URL by ensuring it has the full server path
          let profileImageUrl = response.data.profileImageUrl;
          
          // If it's a relative URL (starts with /), prepend the API base URL
          if (profileImageUrl && profileImageUrl.startsWith('/uploads/')) {
            profileImageUrl = `${API_BASE_URL}${profileImageUrl}`;
          }
          
          console.log("Profile image URL:", profileImageUrl);
          
          setUser({
            ...response.data,
            profileImageUrl: profileImageUrl || getDefaultAvatar(response.data.email)
          });
          
          await loadEvents();
        }
      } catch (error) {
        console.error('Authentication check failed:', error);
        handleLogout();
      } finally {
        setLoading(false);
      }
    };

    checkAuth();
  }, [loadEvents, handleLogout]);

  const handleLogin = (userData) => {
    localStorage.setItem('token', userData.token);
    
    // Fix profile image URL for newly logged in user
    let profileImageUrl = userData.user.profileImageUrl;
    if (profileImageUrl && profileImageUrl.startsWith('/uploads/')) {
      profileImageUrl = `${API_BASE_URL}${profileImageUrl}`;
    }
    
    setUser({
      ...userData.user,
      profileImageUrl: profileImageUrl || getDefaultAvatar(userData.user.email)
    });
    
    loadEvents();
    toast.success('Login successful! Loading your data...');
  };

  const handleAddEvent = async (eventData) => {
    try {
      const response = await axios.post('/api/calendar/add-event', eventData);
      setEvents(prev => [...prev, response.data]);
      toast.success('Event added successfully!');
      
      // Refresh calendar data after adding event
      setTimeout(loadEvents, 500);
    } catch (error) {
      console.error('Failed to add event:', error);
      toast.error(error.response?.data?.error || 'Failed to add event');
    }
  };

  const handleAddEventFromText = async (newEvents) => {
    try {
      const addedEvents = [];
      for (const event of newEvents) {
        const response = await axios.post('/api/calendar/add-event', event);
        addedEvents.push(response.data);
      }
      setEvents(prev => [...prev, ...addedEvents]);
      toast.success(`${addedEvents.length} event(s) added from text!`);
      
      // Scroll to calendar after adding events
      const calendarElement = document.querySelector('.calendar-container');
      if (calendarElement) {
        scrollAfterResponse(calendarElement);
      }
      
      // Refresh calendar data after adding events
      setTimeout(loadEvents, 1000);
    } catch (error) {
      console.error('Failed to add events from text:', error);
      toast.error(error.response?.data?.error || 'Failed to add events from text');
    }
  };

  const handleUserUpdate = (updatedUser) => {
    // Fix profile image URL for updated user data
    let profileImageUrl = updatedUser.profileImageUrl;
    if (profileImageUrl && profileImageUrl.startsWith('/uploads/')) {
      profileImageUrl = `${API_BASE_URL}${profileImageUrl}`;
    }
    
    setUser({
      ...updatedUser,
      profileImageUrl: profileImageUrl || getDefaultAvatar(updatedUser.email)
    });
  };

  const handleProfileClick = () => {
    setShowMyAccount(true);
  };

  if (loading) {
    return (
      <div className="app-loading-screen">
        <div className="loading-spinner"></div>
        <p>Initializing application...</p>
      </div>
    );
  }

  if (!user) {
    return <Login onLogin={handleLogin} darkMode={darkMode} />;
  }

  if (showMyAccount) {
    return (
      <MyAccount 
        user={user}
        onBack={() => setShowMyAccount(false)}
        onUserUpdate={handleUserUpdate}
        onLogout={handleLogout}
        darkMode={darkMode}
      />
    );
  }

  if (showSettings) {
    return (
      <Settings 
        onBack={() => setShowSettings(false)}
        darkMode={darkMode}
        toggleTheme={toggleTheme}
        user={user}
      />
    );
  }

  return (
    <div className={`App ${darkMode ? 'dark-mode' : 'light-mode'}`}>
      <header className="app-header">
        <h1 className="app-title">Welcome, {user.fullName || user.email}</h1>
        <div className="header-controls">
          <div className="user-profile">
            <img 
              src={user.profileImageUrl} 
              alt="Profile" 
              className="profile-photo"
              onClick={handleProfileClick}
              title="Click to open My Account"
              onError={(e) => {
                console.error("Error loading profile image:", e.target.src);
                e.target.onerror = null;
                e.target.src = getDefaultAvatar(user.email);
              }}
            />
          </div>
          <SettingsButton onOpenSettings={() => setShowSettings(true)} />
        </div>
      </header>

      <main className="app-main-content">
        <div className="content-wrapper">
          <div className="main-content-top content-container">
            <Textbox 
              onSubmit={handleAddEvent} 
              onCalendarEventDetected={handleAddEventFromText}
              onPlanDetected={setPlanSteps}
              darkMode={darkMode}
            />
          </div>
          
          {/* Plan component will only display if planSteps has items */}
          <div className="plan-wrapper content-container">
            <Plan 
              planData={planSteps} 
              onUpdatePlan={(updatedPlan) => setPlanSteps(updatedPlan)} 
              darkMode={darkMode}
            />
          </div>
          
          <div className="calendar-wrapper content-container">
            <Calendar 
              ref={calendarRef}
              events={events}
              backendOnline={backendOnline}
              darkMode={darkMode}
            />
          </div>
        </div>
      </main>

      <ToastContainer
        position={toastPosition}
        autoClose={toastDuration}
        hideProgressBar={false}
        newestOnTop
        closeOnClick
        pauseOnFocusLoss
        draggable
        pauseOnHover
        theme={darkMode ? 'dark' : 'light'}
        enableMultiContainer={false}
        containerId="main-toast-container"
      />
    </div>
  );
}

export default App;