import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import './App.css';
import { Textbox } from './Textbox';
import { Calendar } from './Calendar';
import { Plan } from './Plan';
import { Login } from './Login';

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
      toast.error(error.response.data.error);
    }
    return Promise.reject(error);
  }
);

function App() {
  const [user, setUser] = useState(null);
  const [events, setEvents] = useState([]);
  const [planSteps, setPlanSteps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [backendOnline, setBackendOnline] = useState(true);
  const calendarRef = useRef(null);
  
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
  }, []);

  const getDefaultAvatar = (email) => {
    return `https://ui-avatars.com/api/?name=${email.charAt(0).toUpperCase()}&background=3b82f6&color=fff`;
  };

  const loadEvents = async () => {
    try {
      console.log("Loading calendar events...");
      const response = await axios.get('/api/calendar/events');
      console.log("Calendar events received:", response.data);
      
      if (Array.isArray(response.data)) {
        setEvents(response.data);
        
        // Refresh calendar if ref exists
        if (calendarRef.current && calendarRef.current.refreshEvents) {
          setTimeout(() => calendarRef.current.refreshEvents(), 500);
        }
      } else {
        console.error("Invalid events format received:", response.data);
        toast.error("Invalid calendar data format received");
      }
    } catch (error) {
      console.error('Failed to load events:', error);
      if (error.response?.status === 401) {
        toast.error('Authentication error. Please log in again.');
        handleLogout();
      } else {
        toast.error('Failed to load calendar events');
      }
    }
  };

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

  const handleLogout = () => {
    localStorage.removeItem('token');
    setUser(null);
    setEvents([]);
    setPlanSteps([]);  // Clear plan data on logout
    toast.info('You have been logged out');
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
      
      // Use document.querySelector instead of ref for scrolling
      setTimeout(() => {
        const calendarElement = document.querySelector('.calendar-container');
        if (calendarElement) {
          calendarElement.scrollIntoView({ behavior: 'smooth' });
        }
      }, 500);
      
      // Refresh calendar data after adding events
      setTimeout(loadEvents, 1000);
    } catch (error) {
      console.error('Failed to add events from text:', error);
      toast.error(error.response?.data?.error || 'Failed to add events from text');
    }
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
    return <Login onLogin={handleLogin} />;
  }

  return (
    <div className="App">
      <header className="app-header">
        <h1 className="app-title">Welcome, {user.fullName || user.email}</h1>
        <div className="user-profile">
          <img 
            src={user.profileImageUrl} 
            alt="Profile" 
            className="profile-photo"
            onClick={handleLogout}
            title="Click to log out"
            onError={(e) => {
              console.error("Error loading profile image:", e.target.src);
              e.target.onerror = null;
              e.target.src = getDefaultAvatar(user.email);
            }}
          />
        </div>
      </header>

      <main className="app-main-content">
        <Textbox 
          onSubmit={handleAddEvent} 
          onCalendarEventDetected={handleAddEventFromText}
          onPlanDetected={setPlanSteps}
        />
        
        {/* Plan component will only display if planSteps has items */}
        <Plan 
          planData={planSteps} 
          onUpdatePlan={(updatedPlan) => setPlanSteps(updatedPlan)} 
        />
        
        <Calendar 
          ref={calendarRef}
          events={events}
          backendOnline={backendOnline}
        />
      </main>

      <ToastContainer
        position="bottom-right"
        autoClose={5000}
        hideProgressBar={false}
        newestOnTop
        closeOnClick
        pauseOnFocusLoss
        draggable
        pauseOnHover
      />
    </div>
  );
}

export default App;