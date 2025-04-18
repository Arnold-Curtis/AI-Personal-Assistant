import React, { useState, useEffect, useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import axios from 'axios';
import { toast } from 'react-toastify';
import './Calendar.css';

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
        finalizeEventDeletion();
      }, 10000);
      
      setUndoTimerId(timer);
      return () => clearTimeout(timer);
    }
  }, [showUndoButton]);

  // Simple deletion function that only tries once and suppresses expected errors
  const deleteEvent = async (eventId) => {
    if (!eventId) return;
    
    try {
      await axios.delete(`/api/calendar/events/${eventId}`);
      console.log(`Successfully deleted event ${eventId}`);
      return true;
    } catch (error) {
      if (error.response && error.response.status === 404) {
        // Event already deleted or doesn't exist - this is an expected case
        console.log(`Event ${eventId} was already deleted or doesn't exist`);
        return true; // Consider it a success
      }
      // Only log actual errors, don't show them to the user
      console.error(`Error deleting event ${eventId}:`, error);
      return false;
    }
  };
  
  // Finalize event deletion - called when undo timer expires
  const finalizeEventDeletion = async () => {
    if (!lastDeletedEvent || !lastDeletedEvent.extendedProps?.id) return;
    
    try {
      setIsProcessing(true);
      
      // Simple one-time deletion attempt
      await deleteEvent(lastDeletedEvent.extendedProps.id);
      
      // Reset state
      setLastDeletedEvent(null);
      setShowUndoButton(false);
      if (undoTimerId) {
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
    } catch (error) {
      console.error('Error finalizing event deletion:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  // Manual refresh method that can be called from parent
  const refreshEvents = () => {
    fetchEvents();
  };

  // Expose the refresh method via ref
  React.useImperativeHandle(ref, () => ({
    refreshEvents
  }));

  // Helper to reliably remove event instances from calendar
  const removeEventFromUI = (event) => {
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
    // If there's a pending deletion, finalize it first
    if (showUndoButton && lastDeletedEvent) {
      // Clear the undo timer
      if (undoTimerId) {
        clearTimeout(undoTimerId);
        setUndoTimerId(null);
      }
      
      // Finalize the deletion immediately
      finalizeEventDeletion();
    }
    
    // Now select the new event
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
      // Handle UNDO: Restore the deleted event
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
        
        // Remove from UI
        removeEventFromUI(selectedEvent);
        
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
    
    setEventTitle(selectedEvent.title);
    setEventDescription(selectedEvent.extendedProps.description || '');
    setIsEditModalOpen(true);
  };

  const closeEditModal = () => {
    setIsEditModalOpen(false);
    setEventTitle('');
    setEventDescription('');
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
        description: eventDescription.trim()
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

  // Handle component unmount cleanup
  useEffect(() => {
    return () => {
      // On unmount, silently cleanup any pending deletion without notifications
      if (lastDeletedEvent && lastDeletedEvent.extendedProps?.id) {
        // Use a synchronous XMLHttpRequest to ensure deletion happens before unmount
        try {
          const xhr = new XMLHttpRequest();
          xhr.open('DELETE', `/api/calendar/events/${lastDeletedEvent.extendedProps.id}`, false);
          xhr.setRequestHeader('Authorization', `Bearer ${localStorage.getItem('jwtToken') || ''}`);
          xhr.setRequestHeader('Content-Type', 'application/json');
          xhr.send();
          console.log(`Unmount cleanup: attempted to delete event ${lastDeletedEvent.extendedProps.id}`);
        } catch (e) {
          // Silence any errors during unmount
          console.log('Unmount cleanup: event may already be deleted');
        }
      }
    };
  }, [lastDeletedEvent]);

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
          noEventsText="No events to display"
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