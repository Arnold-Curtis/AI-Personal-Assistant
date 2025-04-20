import React, { useState, useEffect, useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import axios from 'axios';
import { toast } from 'react-toastify';
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

// Create singleton for event deletion queue
const eventDeletionQueue = {
  queue: [],
  processing: false,
  processedIds: new Set(), // Track already processed IDs to avoid duplicates
  
  isEventInQueue: (eventId) => eventDeletionQueue.queue.some(e => e.id === eventId),
  
  addEvent: (event) => {
    const eventId = event.extendedProps?.id;
    // Don't add if it's already in the queue or was recently processed
    if (!eventId || eventDeletionQueue.isEventInQueue(eventId) || 
        eventDeletionQueue.processedIds.has(eventId)) {
      return;
    }
    eventDeletionQueue.queue.push({
      id: eventId,
      title: event.title,
      event: event
    });
  },
  
  processNext: async () => {
    if (eventDeletionQueue.processing || eventDeletionQueue.queue.length === 0) return;
    
    eventDeletionQueue.processing = true;
    const next = eventDeletionQueue.queue.shift();
    
    // Add to processed set to prevent duplicates
    eventDeletionQueue.processedIds.add(next.id);
    
    try {
      await axios.delete(`/api/calendar/events/${next.id}`);
      console.log(`Event ${next.id} deleted successfully`);
      return { status: 'success', id: next.id };
    } catch (error) {
      const status = error.response?.status;
      if (status === 404) {
        console.log(`Event ${next.id} already deleted or not found`);
        return { status: 'not_found', id: next.id };
      } else if (status === 401 || status === 403) {
        console.log(`Authentication error for event ${next.id} - refreshing token`);
        // Refresh token or handle auth errors - session might have expired
        window.dispatchEvent(new CustomEvent('auth-refresh-needed'));
        return { status: 'auth_error', id: next.id };
      } else {
        console.error(`Error deleting event ${next.id}:`, error.message);
        return { status: 'error', id: next.id, message: error.message };
      }
    } finally {
      eventDeletionQueue.processing = false;
      
      // Process next item if any
      if (eventDeletionQueue.queue.length > 0) {
        setTimeout(() => eventDeletionQueue.processNext(), 300);
      }
      
      // Clear processed IDs after some time to prevent memory leaks
      // But only clear IDs that aren't in the current queue
      if (eventDeletionQueue.processedIds.size > 100) {
        const currentQueueIds = new Set(eventDeletionQueue.queue.map(e => e.id));
        eventDeletionQueue.processedIds.forEach(id => {
          if (!currentQueueIds.has(id)) {
            eventDeletionQueue.processedIds.delete(id);
          }
        });
      }
    }
  },
  
  clear: () => {
    eventDeletionQueue.queue = [];
    // Don't clear processedIds to maintain deleted event history
  }
};

const Calendar = React.forwardRef(({ events, backendOnline }, ref) => {
  const calendarRef = useRef(null);
  const [internalEvents, setInternalEvents] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(null);
  
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
  const modalRef = useRef(null);
  
  // Format events for FullCalendar
  const formatEvents = (rawEvents) => {
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
  };

  // Fetch events from backend with improved error handling
  const fetchEvents = async () => {
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
      toast.error(`Failed to refresh calendar events: ${error.message || 'Unknown error'}`, {
        toastId: 'calendar-fetch-error',
        autoClose: 5000
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Initial load and periodic refresh
  useEffect(() => {
    fetchEvents();
    const interval = setInterval(fetchEvents, 60000); // Refresh every minute
    return () => clearInterval(interval);
  }, [backendOnline]);

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
  }, [events]);

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
        setTimeout(() => {
          eventDeletionQueue.processNext();
        }, 1000);
      }
    };
    
    window.addEventListener('auth-refresh-needed', handleAuthRefresh);
    return () => window.removeEventListener('auth-refresh-needed', handleAuthRefresh);
  }, []);

  // Clean up timer when component unmounts
  useEffect(() => {
    return () => {
      if (undoTimerId) clearTimeout(undoTimerId);
    };
  }, [undoTimerId]);

  // Auto-hide undo button after timeout
  useEffect(() => {
    if (showUndoButton) {
      const timer = setTimeout(() => {
        // Actually delete from backend when undo timeout expires
        permanentlyDeleteEvent();
      }, 10000);
      
      setUndoTimerId(timer);
      return () => clearTimeout(timer);
    }
  }, [showUndoButton]);

  // Handle event deletion on unmount
  useEffect(() => {
    return () => {
      if (lastDeletedEvent && lastDeletedEvent.extendedProps?.id) {
        // Handle pending deletion when component unmounts
        eventDeletionQueue.addEvent(lastDeletedEvent);
        eventDeletionQueue.processNext();
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
  
  // Function to permanently delete the event in the backend
  const permanentlyDeleteEvent = async () => {
    if (!lastDeletedEvent) return;
    
    try {
      setIsProcessing(true);
      
      // Check if we have a valid event ID to delete
      if (!lastDeletedEvent.extendedProps?.id) {
        console.warn("Attempted to delete event without a valid ID", lastDeletedEvent);
        // Still clear the UI state since we can't delete anything
        setLastDeletedEvent(null);
        setShowUndoButton(false);
        if (undoTimerId) {
          clearTimeout(undoTimerId);
          setUndoTimerId(null);
        }
        return;
      }
      
      // Add to event deletion queue
      eventDeletionQueue.addEvent(lastDeletedEvent);
      
      // Start processing the queue
      eventDeletionQueue.processNext()
        .then(result => {
          if (result && (result.status === 'success' || result.status === 'not_found')) {
            toast.info(`Event removed permanently`);
          } else if (result && result.status === 'auth_error') {
            toast.error('Authentication error. Try refreshing the page.');
          } else {
            toast.error('Failed to remove event. Try again later.');
          }
        })
        .catch(() => {
          toast.error('Network error. Check your connection.');
        });
      
      // Clear undo state immediately (queue will handle actual deletion)
      setLastDeletedEvent(null);
      setShowUndoButton(false);
      if (undoTimerId) {
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
    } catch (error) {
      console.error('Error queueing event deletion:', error);
      toast.error(`Failed to delete event: ${error.message || 'Unknown error'}`);
    } finally {
      setIsProcessing(false);
    }
  };

  // Calendar event handlers
  const handleEventClick = (info) => {
    // If there's a pending deletion, finalize it instead of canceling
    if (showUndoButton && lastDeletedEvent) {
      // Clear the undo timer
      if (undoTimerId) {
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
      
      // Permanently delete the event
      eventDeletionQueue.addEvent(lastDeletedEvent);
      eventDeletionQueue.processNext();
      
      // Reset the undo UI state
      setShowUndoButton(false);
      setLastDeletedEvent(null);
    }
    
    // Now select the new event
    console.log('Event clicked:', info.event);
    console.log('Event extendedProps:', info.event.extendedProps);
    console.log('Plan title value:', info.event.extendedProps?.planTitle);
    setSelectedEvent(info.event);
  };

  const handleDateClick = (arg) => {
    setSelectedEvent(null);
    setShowUndoButton(false);
  };

  const handleDelete = async () => {
    // Prevent duplicate actions while processing
    if (isProcessing) return;
    
    setIsProcessing(true);
    
    try {
      // Handle UNDO: Restore the last deleted event
      if (showUndoButton && lastDeletedEvent) {
        // Cancel the undo timer
        if (undoTimerId) {
          clearTimeout(undoTimerId);
          setUndoTimerId(null);
        }
        
        try {
          // First restore the event in the backend
          const eventToRestore = {
            ...lastDeletedEvent.extendedProps,
            title: lastDeletedEvent.title,
            start: lastDeletedEvent.start,
            allDay: lastDeletedEvent.allDay || true
          };
          
          // Make sure to remove the ID property so the backend doesn't try to update
          delete eventToRestore.id;
          
          // Add a small timestamp to the title to avoid duplicate detection
          // This will be invisible to the user but prevents backend duplicate errors
          const timestamp = new Date().getTime();
          const uniqueTitle = `${eventToRestore.title} [${timestamp}]`;
          const originalTitle = lastDeletedEvent.title;
          
          // Create a new event with a slightly modified title to avoid duplicate detection
          const modifiedEvent = {
            ...eventToRestore,
            title: uniqueTitle,
            originalTitle: originalTitle // Keep track of the original title
          };
          
          // Create a new event in the backend
          const response = await axios.post('/api/calendar/add-event', modifiedEvent);
          
          // Get the newly created event with its new ID
          const newEventData = response.data;
          
          // Create a formatted event object with the NEW backend ID to add to the calendar
          const restoredEventObject = {
            id: `restored-${timestamp}`, // Use a completely new frontend ID to avoid conflicts
            title: originalTitle, // Use the clean original title for display (not the unique one)
            start: lastDeletedEvent.start,
            allDay: lastDeletedEvent.allDay,
            extendedProps: {
              ...lastDeletedEvent.extendedProps,
              id: newEventData.id, // Update with the NEW backend ID
              title: originalTitle, // Store the clean original title
              _backendTitle: uniqueTitle // Store the unique backend title separately
            },
            textColor: 'black',
            borderColor: lastDeletedEvent.extendedProps.eventColor || '#3b82f6',
            backgroundColor: lastDeletedEvent.extendedProps.eventColor ? 
              `${lastDeletedEvent.extendedProps.eventColor}22` : '#f0f7ff'
          };
          
          // Add the event back to the calendar UI
          const calendarApi = calendarRef.current.getApi();
          calendarApi.addEvent(restoredEventObject);
          
          // Add back to our state
          setInternalEvents(prev => {
            if (prev.some(e => e.id === restoredEventObject.id)) {
              return prev;
            }
            return [...prev, restoredEventObject];
          });
          
          // Reset undo-related state
          setLastDeletedEvent(null);
          setShowUndoButton(false);
          
          toast.success('Event restored successfully!');
        } catch (error) {
          console.error('Error restoring event:', error);
          toast.error(`Failed to restore event: ${error.message || 'Unknown error'}`);
          
          // Clean up the undo state to avoid further errors
          setLastDeletedEvent(null);
          setShowUndoButton(false);
        }
      } 
      // Handle DELETE: Remove the selected event
      else if (selectedEvent) {
        // Store reference before removing
        setLastDeletedEvent(selectedEvent);
        
        // Enhanced event removal that works more reliably with restored events
        removeAllEventInstances(selectedEvent);
        
        // Show undo UI
        setShowUndoButton(true);
        setSelectedEvent(null);
        
        toast.success('Event deleted - click Undo to restore', { 
          autoClose: 10000 
        });
      }
    } catch (error) {
      console.error('Error handling event action:', error);
      toast.error(`Error: ${error.message || 'Unknown error'}`);
      fetchEvents(); // Try to recover state
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
          plugins={[dayGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          headerToolbar={{
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,dayGridWeek,dayGridDay'
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
            info.el.style.borderLeft = `4px solid ${info.event.borderColor || '#3b82f6'}`;
            info.el.style.backgroundColor = info.event.backgroundColor || '#f0f7ff';
            info.el.style.color = 'black';
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
            onClick={handleDelete} 
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