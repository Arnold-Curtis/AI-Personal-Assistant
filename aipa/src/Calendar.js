import React, { useState, useEffect, useRef, useCallback } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import listPlugin from '@fullcalendar/list';
import interactionPlugin from '@fullcalendar/interaction';
import axios from 'axios';
import { toast as reactToastify } from 'react-toastify';
import { toast } from './utils/toastUtils';
import './Calendar.css';

// Add auth token to all axios requests
axios.interceptors.request.use(
  config => {
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

const Calendar = React.forwardRef(({ events, backendOnline, darkMode = false }, ref) => {
  const calendarRef = useRef(null);
  const [internalEvents, setInternalEvents] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [currentView, setCurrentView] = useState(
    localStorage.getItem('calendarDefaultView') || 'dayGridMonth'
  );
  
  // Event management state
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [eventTitle, setEventTitle] = useState('');
  const [eventDescription, setEventDescription] = useState('');
  const [eventPlanTitle, setEventPlanTitle] = useState('');
  const [lastDeletedEvent, setLastDeletedEvent] = useState(null);
  const [undoTimerId, setUndoTimerId] = useState(null);
  const [showUndoButton, setShowUndoButton] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [deletingEventIds, setDeletingEventIds] = useState(new Set()); // Track events being deleted
  const modalRef = useRef(null);
  
  // Format events for FullCalendar
  const formatEvents = useCallback((rawEvents) => {
    if (!rawEvents || !Array.isArray(rawEvents)) {
      console.warn('Invalid events data received:', rawEvents);
      return [];
    }
    
    return rawEvents.map(event => ({
      id: event.id || Math.random().toString(36).substring(2, 11),
      title: event.title || 'Untitled Event',
      start: event.start || new Date().toISOString().split('T')[0],
      allDay: event.isAllDay !== undefined ? event.isAllDay : true,
      extendedProps: event,
      textColor: 'black',
      borderColor: event.eventColor || '#3b82f6',
      backgroundColor: event.eventColor ? `${event.eventColor}22` : '#f0f7ff'
    }));
  }, []);

  // Listen for calendar view changes from settings
  useEffect(() => {
    const handleViewChange = (event) => {
      const newView = event.detail.view;
      setCurrentView(newView);
      if (calendarRef.current) {
        const calendarApi = calendarRef.current.getApi();
        calendarApi.changeView(newView);
      }
    };

    window.addEventListener('calendarViewChange', handleViewChange);
    return () => {
      window.removeEventListener('calendarViewChange', handleViewChange);
    };
  }, []);

  // Update view when currentView changes
  useEffect(() => {
    if (calendarRef.current) {
      const calendarApi = calendarRef.current.getApi();
      calendarApi.changeView(currentView);
    }
  }, [currentView]);

  // Fetch events from backend with improved error handling
  const fetchEvents = useCallback(async (silent = false) => {
    if (!backendOnline) {
      setIsLoading(false);
      return;
    }
    
    try {
      setIsLoading(true);
      setHasError(false);
      
      const response = await axios.get('/api/calendar/events');
      
      if (!response || !response.data) {
        setInternalEvents([]);
        setLastUpdated(new Date());
        setIsLoading(false);
        return;
      }
      
      const formattedEvents = formatEvents(response.data);
      setInternalEvents(formattedEvents);
      setLastUpdated(new Date());
    } catch (error) {
      console.error('Error fetching events:', error);
      setHasError(true);
      
      // Only show error toast if not in silent mode (used after successful deletion)
      if (!silent) {
        toast.error(`Failed to refresh calendar events: ${error.message || 'Unknown error'}`, {
          toastId: 'calendar-fetch-error',
          autoClose: 5000
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [backendOnline, formatEvents]);
  
  // Function was moved above before useEffect

  // Initial load and periodic refresh
  useEffect(() => {
    fetchEvents();
    const interval = setInterval(fetchEvents, 60000); // Refresh every minute
    return () => clearInterval(interval);
  }, [fetchEvents, backendOnline]);

  // Update when parent events change
  useEffect(() => {
    if (events && events.length > 0) {
      const formattedEvents = formatEvents(events);
      setInternalEvents(prev => {
        const existingIds = prev.map(e => e.id);
        const newEvents = formattedEvents.filter(e => !existingIds.includes(e.id));
        return [...prev, ...newEvents];
      });
    }
  }, [events, formatEvents]);

  // Handle click outside modal to close it
  useEffect(() => {
    function handleClickOutside(event) {
      if (modalRef.current && !modalRef.current.contains(event.target)) {
        closeEditModal();
      }
    }

    if (isEditModalOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isEditModalOpen]);

  // Process event queue when we detect auth events
  useEffect(() => {
    const handleAuthRefresh = () => {
      const token = localStorage.getItem('authToken');
      if (token) {
        // Refresh events when auth is refreshed
        setTimeout(() => {
          fetchEvents();
        }, 1000);
      }
    };
    
    window.addEventListener('auth-refresh-needed', handleAuthRefresh);
    return () => window.removeEventListener('auth-refresh-needed', handleAuthRefresh);
  }, [fetchEvents]);

  // Initialize timer on mount
  useEffect(() => {
    // When the component mounts, ensure no lingering timers
    setUndoTimerId(null);
  }, []);
  
  // Clean up timer when component unmounts or when undoTimerId changes
  useEffect(() => {
    return () => {
      if (undoTimerId) {
        console.log('Cleanup: clearing timer', undoTimerId);
        clearTimeout(undoTimerId);
      }
    };
  }, [undoTimerId]);

  // Monitor undo state to ensure we clear timers properly
  useEffect(() => {
    // This useEffect no longer creates timers - it only monitors for cleanup
    if (!showUndoButton && undoTimerId) {
      // Clear timer if undo button is no longer shown
      console.log('No undo button shown - clearing existing timer:', undoTimerId);
      clearTimeout(undoTimerId);
      setUndoTimerId(null);
    }
    
    // Cleanup function
    return () => {
      if (undoTimerId) {
        console.log('Cleanup: Clearing auto-delete timer:', undoTimerId);
        clearTimeout(undoTimerId);
      }
    };
  }, [showUndoButton, undoTimerId]);

  // Handle event deletion on unmount
  useEffect(() => {
    return () => {
      if (lastDeletedEvent && lastDeletedEvent.extendedProps?.id) {
        // Handle pending deletion when component unmounts
        axios.delete(`/api/calendar/events/${lastDeletedEvent.extendedProps.id}`)
          .catch(error => console.error('Error deleting event on unmount:', error));
      }
    };
  }, [lastDeletedEvent]);

  // Manual refresh method that can be called from parent
  const refreshEvents = () => {
    fetchEvents();
  };

  // Expose the refresh method via ref
  React.useImperativeHandle(ref, () => ({
    refreshEvents
  }));

  // Helper to reliably remove all instances of an event from calendar
  const removeAllEventInstances = (event) => {
    try {
      const eventId = event.id;
      const extendedId = event.extendedProps?.id;
      
      // Get all current events from calendar API
      const calendarApi = calendarRef.current?.getApi();
      if (!calendarApi) return;
      
      // Get all events and filter out any that match either ID
      const allEvents = calendarApi.getEvents();
      allEvents.forEach(ev => {
        if (
          ev.id === eventId || 
          ev.extendedProps?.id === extendedId ||
          (extendedId && ev.extendedProps?.id === extendedId)
        ) {
          ev.remove();
        }
      });
      
      // Also update our internal state
      setInternalEvents(prev => prev.filter(e => 
        e.id !== eventId && 
        e.extendedProps?.id !== extendedId
      ));
    } catch (err) {
      console.error('Error removing event from UI:', err);
    }
  };
  
  // Calendar event handlers
  const handleEventClick = (info) => {
    // If there's a pending deletion, auto-delete it immediately when clicking on another event
    if (showUndoButton && lastDeletedEvent) {
      // Clear the undo timer completely
      if (undoTimerId) {
        console.log('Clearing undo timer in handleEventClick:', undoTimerId);
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
      
      // Auto-delete the pending event from backend
      if (lastDeletedEvent.extendedProps?.id) {
        const eventId = lastDeletedEvent.extendedProps.id;
        
        // Check if already being deleted
        if (!deletingEventIds.has(eventId)) {
          setDeletingEventIds(prev => new Set(prev).add(eventId));
          
          // Clear undo state immediately
          setShowUndoButton(false);
          setLastDeletedEvent(null);
          
          // Add small delay to prevent rapid requests
          setTimeout(() => {
            axios.delete(`/api/calendar/events/${eventId}`)
              .then(() => {
                console.log('Previous pending event deleted from backend');
                toast.success('Event permanently deleted', { 
                  autoClose: 3000
                });
                // Refresh calendar after a delay with silent mode
                setTimeout(() => fetchEvents(true), 1000);
              })
              .catch((error) => {
                const status = error.response?.status;
                if (status === 404) {
                  // Event already deleted
                  console.log('Previous event was already deleted');
                } else if (status === 409) {
                  // Database busy
                  console.warn('Database busy during auto-delete');
                  setTimeout(() => fetchEvents(true), 1000); // Silent refresh
                } else {
                  console.error('Error auto-deleting previous event:', error);
                }
              })
              .finally(() => {
                // Remove from deleting set after delay
                setTimeout(() => {
                  setDeletingEventIds(prev => {
                    const newSet = new Set(prev);
                    newSet.delete(eventId);
                    return newSet;
                  });
                }, 2000);
              });
          }, 200);
        }
      } else {
        // No valid ID, just clear the state
        setShowUndoButton(false);
        setLastDeletedEvent(null);
      }
    }
    
    // Now select the new event
    console.log('Event clicked:', info.event);
    console.log('Event extendedProps:', info.event.extendedProps);
    console.log('Plan title value:', info.event.extendedProps?.planTitle);
    setSelectedEvent(info.event);
  };

  const handleDateClick = (arg) => {
    setSelectedEvent(null);
    
    // Clear any pending undo state
    if (showUndoButton && lastDeletedEvent) {
      if (undoTimerId) {
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
      setShowUndoButton(false);
      setLastDeletedEvent(null);
    }
  };

  const handleDelete = async () => {
    // Prevent duplicate actions while processing
    if (isProcessing) return;
    
    setIsProcessing(true);
    
    try {
      // Handle DELETE: Remove the selected event with undo option
      if (selectedEvent) {
        // Clear any existing undo state first
        if (undoTimerId) {
          clearTimeout(undoTimerId);
          setUndoTimerId(null);
        }
        
        // Store reference to the event with ALL its properties preserved
        const eventToStore = {
          id: selectedEvent.id,
          title: selectedEvent.title || 'Untitled Event', // Ensure title is preserved
          start: selectedEvent.start,
          startStr: selectedEvent.startStr, // Preserve string format
          allDay: selectedEvent.allDay,
          borderColor: selectedEvent.borderColor,
          backgroundColor: selectedEvent.backgroundColor,
          textColor: selectedEvent.textColor,
          extendedProps: {
            id: selectedEvent.extendedProps?.id || selectedEvent.id,
            description: selectedEvent.extendedProps?.description || '',
            planTitle: selectedEvent.extendedProps?.planTitle || '',
            eventColor: selectedEvent.extendedProps?.eventColor || selectedEvent.borderColor || '#3b82f6'
          }
        };
        
        console.log('Storing deleted event with preserved properties:', eventToStore);
        setLastDeletedEvent(eventToStore);
        
        // Remove from UI immediately (visual feedback)
        removeAllEventInstances(selectedEvent);
        
        // Show undo UI
        setShowUndoButton(true);
        setSelectedEvent(null);
        
        // First dismiss any existing toasts to avoid conflicts
        reactToastify.dismiss();
        
        // Use our custom toast utility which respects notification settings
        toast.success('Event deleted - click Undo to restore (10 seconds)', { 
          autoClose: 10000
        });
        
        // Start the auto-delete countdown (independent of notifications)
        console.log('Starting auto-delete countdown');
        
        // Create and store the auto-delete timer
        const timer = setTimeout(async () => {
          try {
            console.log('Auto-delete timer fired for event:', eventToStore.extendedProps.id);
            setIsProcessing(true);
            
            const eventId = eventToStore.extendedProps.id;
            console.log('Auto-deleting event from backend:', eventId);
            
            // Check if this event is already being deleted
            if (deletingEventIds.has(eventId)) {
              console.log("Event is already being deleted:", eventId);
              return;
            }
            
            // Mark as being deleted
            setDeletingEventIds(prev => new Set(prev).add(eventId));
            
            // Actually delete the event from backend now
            console.log('Sending DELETE request to backend for event ID:', eventId);
            const response = await axios.delete(`/api/calendar/events/${eventId}`);
            console.log('Backend delete response:', response.data);
            
            // Clear undo state completely
            setLastDeletedEvent(null);
            setShowUndoButton(false);
            setUndoTimerId(null);
            
            // Show confirmation
            toast.success('Event permanently deleted', {
              toastId: 'auto-delete-success', // Prevent duplicate toasts
              autoClose: 3000
            });
            
            // Refresh the calendar silently
            setTimeout(() => fetchEvents(true), 500);
            
          } catch (error) {
            console.error('Error during handleDelete auto-delete:', error);
            toast.error('Failed to permanently delete event');
            
            // Clear undo state
            setLastDeletedEvent(null);
            setShowUndoButton(false);
            setUndoTimerId(null);
            
            // Still refresh to check current state
            setTimeout(() => fetchEvents(true), 1000);
          } finally {
            setIsProcessing(false);
            
            // Remove from deleting set after a delay
            if (eventToStore?.extendedProps?.id) {
              setTimeout(() => {
                setDeletingEventIds(prev => {
                  const newSet = new Set(prev);
                  newSet.delete(eventToStore.extendedProps.id);
                  return newSet;
                });
              }, 2000);
            }
          }
        }, 10000); // 10 seconds
        
        console.log('Storing timer ID in handleDelete:', timer);
        setUndoTimerId(timer);
      }
    } catch (error) {
      console.error('Error handling event deletion:', error);
      toast.error(`Error: ${error.message || 'Unknown error'}`);
      
      // Clear any stuck state
      setLastDeletedEvent(null);
      setShowUndoButton(false);
      setUndoTimerId(null);
      
      fetchEvents(); // Try to recover state
    } finally {
      setIsProcessing(false);
    }
  };

  // Separate function for undo functionality
  const handleUndo = async () => {
    if (!lastDeletedEvent || isProcessing) return;
    
    setIsProcessing(true);
    
    try {
      // Cancel the auto-delete timer immediately
      if (undoTimerId) {
        console.log('Canceling auto-delete timer in handleUndo:', undoTimerId);
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
      
      // Dismiss any existing toasts immediately
      toast.dismiss();
      
      // Restore the event to the calendar UI with ALL preserved properties
      const calendarApi = calendarRef.current.getApi();
      const restoredEvent = {
        id: lastDeletedEvent.id,
        title: lastDeletedEvent.title, // Use preserved title
        start: lastDeletedEvent.startStr || lastDeletedEvent.start, // Use preserved date
        allDay: lastDeletedEvent.allDay,
        extendedProps: lastDeletedEvent.extendedProps, // Use preserved extendedProps
        textColor: lastDeletedEvent.textColor || 'black',
        borderColor: lastDeletedEvent.borderColor || lastDeletedEvent.extendedProps?.eventColor || '#3b82f6',
        backgroundColor: lastDeletedEvent.backgroundColor || (lastDeletedEvent.extendedProps?.eventColor ? `${lastDeletedEvent.extendedProps.eventColor}22` : '#f0f7ff')
      };
      
      console.log('Restoring event with preserved title:', restoredEvent.title);
      console.log('Restoring event with preserved date:', restoredEvent.start);
      console.log('Restoring event with preserved extendedProps:', restoredEvent.extendedProps);
      
      // Add back to calendar UI
      calendarApi.addEvent(restoredEvent);
      
      // Add back to our state
      setInternalEvents(prev => [...prev, restoredEvent]);
      
      // Clear undo state completely
      setLastDeletedEvent(null);
      setShowUndoButton(false);
      
      toast.success('Event restored!');
      
    } catch (error) {
      console.error('Error during undo operation:', error);
      toast.error('Failed to restore event');
      
      // Still clear the undo state to prevent stuck UI
      setLastDeletedEvent(null);
      setShowUndoButton(false);
      if (undoTimerId) {
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
    } finally {
      setIsProcessing(false);
    }
  };

  const openEditModal = () => {
    if (!selectedEvent) return;
    
    console.log('Selected event data:', selectedEvent);
    console.log('Plan title from event:', selectedEvent.extendedProps.planTitle);
    
    setEventTitle(selectedEvent.title);
    setEventDescription(selectedEvent.extendedProps.description || '');
    setEventPlanTitle(selectedEvent.extendedProps.planTitle || '');
    setIsEditModalOpen(true);
  };

  const closeEditModal = () => {
    setIsEditModalOpen(false);
    setEventTitle('');
    setEventDescription('');
    setEventPlanTitle('');
  };

  const handleSaveEvent = async () => {
    if (!selectedEvent || !eventTitle.trim()) {
      toast.error('Event title cannot be empty');
      return;
    }
    
    try {
      setIsLoading(true);
      
      const updatedEvent = {
        ...selectedEvent.extendedProps,
        title: eventTitle.trim(),
        description: eventDescription.trim(),
        planTitle: eventPlanTitle // Preserve plan title when updating
      };
      
      await axios.post('/api/calendar/add-event', updatedEvent);
      toast.success('Event updated successfully!');
      closeEditModal();
      await fetchEvents();
      setSelectedEvent(null);
    } catch (error) {
      console.error('Error updating event:', error);
      toast.error(`Failed to update event: ${error.message || 'Unknown error'}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="calendar-container">
      {isLoading && (
        <div className="calendar-loading">
          <div className="spinner"></div>
          <span>Loading calendar data...</span>
        </div>
      )}

      {hasError && !isLoading && (
        <div className="calendar-error">
          <p>Error loading calendar data. <button onClick={fetchEvents}>Try again</button></p>
        </div>
      )}

      <div className={`calendar-content ${isLoading ? 'loading' : ''}`}>
        <FullCalendar
          ref={calendarRef}
          plugins={[dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
          initialView={currentView}
          headerToolbar={{
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek,timeGridDay,listWeek'
          }}
          height="auto"
          events={internalEvents}
          eventClick={handleEventClick}
          dateClick={handleDateClick}
          nowIndicator={true}
          eventDisplay="block"
          eventTimeFormat={{
            hour: '2-digit',
            minute: '2-digit',
            meridiem: false
          }}
          eventDidMount={(info) => {
            const borderColor = info.event.borderColor || '#3b82f6';
            const backgroundColor = info.event.backgroundColor || (darkMode ? '#4c566a' : '#f0f7ff');
            const textColor = darkMode ? '#eceff4' : '#1e293b';
            
            info.el.style.borderLeft = `4px solid ${borderColor}`;
            info.el.style.backgroundColor = backgroundColor;
            info.el.style.color = textColor;
            info.el.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.1)';
            info.el.style.borderRadius = '4px';
            info.el.style.transition = 'transform 0.2s ease, box-shadow 0.2s ease';
            
            // Add hover effect
            info.el.addEventListener('mouseenter', () => {
              info.el.style.transform = 'translateY(-2px)';
              info.el.style.boxShadow = '0 4px 8px rgba(0, 0, 0, 0.15)';
            });
            
            info.el.addEventListener('mouseleave', () => {
              info.el.style.transform = 'translateY(0)';
              info.el.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.1)';
            });
          }}
          noEventsContent={() => (
            <div style={{
              padding: "20px",
              textAlign: "center",
              color: "#666",
              fontStyle: "italic"
            }}>
              No events to display
            </div>
          )}
        />
      </div>

      {/* Undo Button */}
      {showUndoButton && (
        <div className="calendar-event-actions">
          <button 
            onClick={handleUndo} 
            className="event-action-button undo-button"
            disabled={isProcessing}
          >
            {isProcessing ? 'Processing...' : 'Undo Delete'}
          </button>
        </div>
      )}

      {/* Normal Event Actions */}
      {selectedEvent && !showUndoButton && (
        <div className="calendar-event-actions">
          <button 
            onClick={handleDelete} 
            className="event-action-button delete-button"
            disabled={isProcessing}
          >
            {isProcessing ? 'Processing...' : 'Delete Event'}
          </button>
          <button 
            onClick={openEditModal} 
            className="event-action-button edit-button"
            disabled={isProcessing}
          >
            Edit Event
          </button>
        </div>
      )}

      <div className="calendar-footer">
        {lastUpdated ? (
          <>
            Last updated: {lastUpdated.toLocaleTimeString()}
            <button 
              onClick={fetchEvents} 
              style={{
                marginLeft: '10px',
                padding: '2px 8px',
                fontSize: '0.8rem',
                background: '#f0f7ff',
                border: '1px solid #3b82f6',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
              disabled={isProcessing || isLoading}
            >
              Refresh
            </button>
          </>
        ) : (
          <button 
            onClick={fetchEvents}
            style={{
              padding: '4px 12px',
              fontSize: '0.9rem',
              background: '#3b82f6',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
            disabled={isProcessing || isLoading}
          >
            Load Calendar
          </button>
        )}
      </div>

      {/* Edit Event Modal */}
      {isEditModalOpen && (
        <div className="modal-overlay">
          <div className="edit-event-modal" ref={modalRef}>
            <button className="close-modal" onClick={closeEditModal}>×</button>
            <h3>Edit Event</h3>
            <div className="form-group">
              <label htmlFor="eventTitle">Title</label>
              <input
                id="eventTitle"
                type="text"
                value={eventTitle}
                onChange={(e) => setEventTitle(e.target.value)}
                placeholder="Event title"
              />
            </div>
            <div className="form-group">
              <label htmlFor="eventDescription">Description</label>
              <textarea
                id="eventDescription"
                value={eventDescription}
                onChange={(e) => setEventDescription(e.target.value)}
                placeholder="Event description (optional)"
                rows={5}
              />
            </div>
            {console.log('Edit modal rendering, eventPlanTitle value:', eventPlanTitle, typeof eventPlanTitle)}
            {eventPlanTitle && eventPlanTitle.trim() !== '' && (
              <div className="form-group plan-title-box">
                <label>Part of Plan</label>
                <button className="plan-title-button" type="button">
                  {eventPlanTitle}
                </button>
              </div>
            )}
            <div className="modal-footer">
              <button 
                className="save-button" 
                onClick={handleSaveEvent}
                disabled={isProcessing}
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
});

Calendar.displayName = 'Calendar';

export { Calendar };
export default Calendar;