import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import './App.css';

// Components
import { Textbox } from './Textbox';
import { Calendar } from './Calendar';

// Set the base URL for all API requests
axios.defaults.baseURL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Configure axios to handle errors globally
axios.interceptors.response.use(
    response => response,
    error => {
        if (error.code === 'ERR_NETWORK') {
            toast.error('Network error - backend may be down', { autoClose: false });
        }
        return Promise.reject(error);
    }
);

function App() {
    const [events, setEvents] = useState([]);
    const [loading, setLoading] = useState(true);
    const [backendOnline, setBackendOnline] = useState(false);
    const calendarRef = useRef(null);

    // Check backend status and load initial events
    useEffect(() => {
        const initializeApp = async () => {
            try {
                // First check if backend is available
                await checkBackendStatus();
                
                // Then load events if backend is online
                if (backendOnline) {
                    await loadEvents();
                }
            } catch (error) {
                console.error('Initialization error:', error);
            } finally {
                setLoading(false);
            }
        };

        initializeApp();
    }, []);

    const checkBackendStatus = async () => {
        try {
          const response = await axios.get('/api/health', {
            timeout: 3000,
            validateStatus: (status) => status < 500 // Allow 404 to be handled
          });
          
          // Handle different status codes
          if (response.status === 200) {
            setBackendOnline(true);
          } else if (response.status === 404) {
            setBackendOnline(false);
            throw new Error('Health endpoint not found');
          }
        } catch (error) {
          setBackendOnline(false);
          console.error('Backend connection error:', error);
          throw error;
        }
    };

    const loadEvents = async () => {
        try {
            const response = await axios.get('/api/calendar/events');
            setEvents(response.data);
        } catch (error) {
            console.error('Failed to load events:', error);
            throw error;
        }
    };

    const handleAddEvent = async (eventData) => {
        if (!backendOnline) {
            toast.error('Backend is unavailable - cannot add event');
            return;
        }

        try {
            const response = await axios.post('/api/calendar/add-event', eventData, {
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            setEvents(prev => [...prev, response.data]);
            toast.success('Event added successfully!');
        } catch (error) {
            console.error('Failed to add event:', error);
            let errorMessage = 'Failed to add event';
            
            if (error.response) {
                if (error.response.status === 409) {
                    errorMessage = 'Event already exists on this date';
                } else if (error.response.data?.error) {
                    errorMessage = error.response.data.error;
                }
            }

            toast.error(errorMessage);
        }
    };

    const handleAddEventFromText = async (newEvents) => {
        if (!backendOnline) {
            toast.error('Backend is unavailable - cannot add events');
            return;
        }

        try {
            const addedEvents = [];
            for (const event of newEvents) {
                const response = await axios.post('/api/calendar/add-event', event, {
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                addedEvents.push(response.data);
            }

            setEvents(prev => [...prev, ...addedEvents]);
            toast.success(`${addedEvents.length} event(s) added from text!`);
            
            // Smooth scroll to calendar
            if (calendarRef.current) {
                setTimeout(() => {
                    calendarRef.current.scrollIntoView({ behavior: 'smooth' });
                }, 300);
            }
        } catch (error) {
            console.error('Failed to add events from text:', error);
            let errorMessage = 'Failed to add events from text';
            
            if (error.response) {
                if (error.response.status === 409) {
                    errorMessage = 'Some events already exist';
                } else if (error.response.data?.error) {
                    errorMessage = error.response.data.error;
                }
            }

            toast.error(errorMessage);
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

    return (
        <div className="App">
            <header className="app-header">
                <h1 className="app-title">Event Calendar</h1>
                <div className={`backend-status ${backendOnline ? 'online' : 'offline'}`}>
                    {backendOnline ? 'Backend Online' : 'Backend Offline'}
                </div>
            </header>

            <main className="app-main-content">
                <Textbox 
                    onSubmit={handleAddEvent} 
                    onCalendarEventDetected={handleAddEventFromText}
                    disabled={!backendOnline} 
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