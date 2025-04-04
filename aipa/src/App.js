import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import './App.css';
import { Textbox } from './Textbox';
import { Calendar } from './Calendar';
import { Login } from './Login';

// Set the base URL for all API requests
axios.defaults.baseURL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Configure axios to include credentials and handle errors globally
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, error => {
  return Promise.reject(error);
});

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
  const [loading, setLoading] = useState(true);
  const calendarRef = useRef(null);

  // Check authentication status and load user data
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const token = localStorage.getItem('token');
        if (token) {
          const response = await axios.get('/api/auth/me');
          setUser({
            ...response.data,
            profileImageUrl: response.data.profileImageUrl || getDefaultAvatar(response.data.email)
          });
          await loadEvents();
        }
      } catch (error) {
        console.error('Authentication check failed:', error);
        localStorage.removeItem('token');
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
      const response = await axios.get('/api/calendar/events');
      setEvents(response.data);
    } catch (error) {
      console.error('Failed to load events:', error);
      toast.error('Failed to load calendar events');
    }
  };

  const handleLogin = (userData) => {
    localStorage.setItem('token', userData.token);
    setUser({
      ...userData.user,
      profileImageUrl: userData.user.profileImageUrl || getDefaultAvatar(userData.user.email)
    });
    loadEvents();
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    setUser(null);
    setEvents([]);
  };

  const handleAddEvent = async (eventData) => {
    try {
      const response = await axios.post('/api/calendar/add-event', eventData);
      setEvents(prev => [...prev, response.data]);
      toast.success('Event added successfully!');
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
      
      if (calendarRef.current) {
        setTimeout(() => {
          calendarRef.current.scrollIntoView({ behavior: 'smooth' });
        }, 300);
      }
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
            onError={(e) => {
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
        />
        <Calendar 
          ref={calendarRef}
          events={events} 
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