import React, { useState, useEffect, useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import axios from 'axios';
import { toast } from 'react-toastify';
import './Calendar.css';

// Helper function to load deleted events from localStorage
const loadDeletedEvents = () => {
  try {
    const saved = localStorage.getItem('calendar_deleted_events');
    return saved ? JSON.parse(saved) : [];
  } catch (e) {
    console.error('Error loading deleted events from localStorage:', e);
    return [];
  }
};

// Helper function to save deleted events to localStorage
const saveDeletedEvents = (ids) => {
  try {
    localStorage.setItem('calendar_deleted_events', JSON.stringify(ids));
  } catch (e) {
    console.error('Error saving deleted events to localStorage:', e);
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
  const [deletedEvent, setDeletedEvent] = useState(null);
  const [undoTimerId, setUndoTimerId] = useState(null);
  const [isUndo, setIsUndo] = useState(false);
  // Add a specific state for showing the undo button
  const [showUndoButton, setShowUndoButton] = useState(false);
  const modalRef = useRef(null);
  const pendingDeleteRef = useRef(null);
  
  // Persistent list of deleted events (loaded from localStorage)
  const [deletedEventIds, setDeletedEventIds] = useState(loadDeletedEvents);
  
  // Track if backend deletion has been completed
  const [backendDeletedIds, setBackendDeletedIds] = useState([]);

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

  // Save deleted events to localStorage whenever the list changes
  useEffect(() => {
    saveDeletedEvents(deletedEventIds);
    console.log('Updated deleted events list:', deletedEventIds);
  }, [deletedEventIds]);

  // Fetch events from backend with improved error handling
  const fetchEvents = async () => {
    if (!backendOnline) {
      setIsLoading(false);
      return;
    }
    
    try {
      setIsLoading(true);
      setHasError(false);
      
      console.log("Fetching calendar events...");
      const response = await axios.get('/api/calendar/events');
      console.log("Calendar API response:", response);
      
      if (!response || !response.data) {
        console.warn("Empty response from calendar API");
        setInternalEvents([]);
        setLastUpdated(new Date());
        setIsLoading(false);
        return;
      }
      
      const formattedEvents = formatEvents(response.data);
      
      // Filter out any events marked as deleted - CRITICAL STEP
      console.log('Filtering out deleted events:', deletedEventIds);
      const filteredEvents = formattedEvents.filter(event => {
        const isDeleted = deletedEventIds.includes(event.extendedProps.id);
        if (isDeleted) {
          console.log(`Filtering out deleted event: ${event.title} (ID: ${event.extendedProps.id})`);
        }
        return !isDeleted;
      });
        
      console.log("Filtered events for calendar:", filteredEvents);
      
      setInternalEvents(filteredEvents);
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
    console.log("Calendar component mounted or backendOnline changed:", backendOnline);
    fetchEvents();
    const interval = setInterval(fetchEvents, 60000); // Refresh every minute
    return () => clearInterval(interval);
  }, [backendOnline]);

  // Update when parent events change
  useEffect(() => {
    if (events && events.length > 0) {
      console.log("New events received from parent:", events);
      const formattedEvents = formatEvents(events);
      
      setInternalEvents(prev => {
        // Filter out duplicates and deleted events
        const existingIds = prev.map(e => e.id);
        const newEvents = formattedEvents.filter(e => 
          !existingIds.includes(e.id) && !deletedEventIds.includes(e.extendedProps.id)
        );
        console.log("Adding new events to calendar:", newEvents);
        return [...prev, ...newEvents];
      });

      // Force refresh data from backend after a short delay
      setTimeout(fetchEvents, 1000);
    }
  }, [events, deletedEventIds]);

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

  // Clean up timers when component unmounts
  useEffect(() => {
    return () => {
      if (undoTimerId) clearTimeout(undoTimerId);
      if (pendingDeleteRef.current) clearTimeout(pendingDeleteRef.current);
    };
  }, [undoTimerId]);

  // Manual refresh method that can be called from parent
  const refreshEvents = () => {
    console.log("Manual calendar refresh triggered");
    fetchEvents();
  };

  // Expose the refresh method via ref
  React.useImperativeHandle(ref, () => ({
    refreshEvents
  }));

  // Calendar event handlers
  const handleEventClick = (info) => {
    // If there's already an undo in progress, clear it
    if (undoTimerId) {
      clearTimeout(undoTimerId);
      setUndoTimerId(null);
    }
    
    // Reset undo state when selecting a new event
    setIsUndo(false);
    setShowUndoButton(false);
    setSelectedEvent(info.event);
    
    toast.info(`Event: ${info.event.title}`, {
      position: 'bottom-right',
      autoClose: 2000
    });
  };

  const handleDateClick = (arg) => {
    // Clear selection when clicking on a date
    setSelectedEvent(null);
    setShowUndoButton(false);

    toast.info(`Date selected: ${arg.dateStr}`, {
      position: 'bottom-right',
      autoClose: 2000
    });
  };

  // Function to reset all undo-related states
  const resetUndoStates = () => {
    setIsUndo(false);
    setShowUndoButton(false);
    setSelectedEvent(null);
    setDeletedEvent(null);
    if (undoTimerId) {
      clearTimeout(undoTimerId);
      setUndoTimerId(null);
    }
    if (pendingDeleteRef.current) {
      pendingDeleteRef.current = null;
    }
  };

  const handleDelete = async () => {
    if (!selectedEvent) return;
    
    if (isUndo) {
      // Handle undo action
      try {
        setIsLoading(true);
        
        // Cancel the pending deletion
        if (pendingDeleteRef.current) {
          clearTimeout(pendingDeleteRef.current);
          pendingDeleteRef.current = null;
        }
        
        // Remove from deleted events list
        setDeletedEventIds(prev => prev.filter(
          id => id !== deletedEvent.extendedProps.id
        ));
        
        console.log('Restoring event with original date:', deletedEvent.extendedProps.start);
        
        // Check if this event ID has been deleted from backend
        if (backendDeletedIds.includes(deletedEvent.extendedProps.id)) {
          // If it was deleted from backend, create a new one
          console.log('Event was deleted from backend, creating new one');
          
          // Create a modified copy of the event to bypass the "event already exists" check
          const eventToRestore = {
            ...deletedEvent.extendedProps,
            id: null,  // Remove ID so backend creates new event instead of updating
            title: deletedEvent.title, // Use original title directly
            start: deletedEvent.extendedProps.start
          };
          
          await axios.post('/api/calendar/add-event', eventToRestore);
          
          // Remove from backend deleted IDs list
          setBackendDeletedIds(prev => prev.filter(id => id !== deletedEvent.extendedProps.id));
          
          toast.success('Event restored successfully!');
        } else {
          // If it wasn't deleted from backend, just restore visibility
          console.log('Event not deleted from backend, just restoring visibility');
          toast.success('Event restored successfully!');
        }
        
        // Reset states
        resetUndoStates();
        
        // Refresh calendar to show the restored event
        await fetchEvents();
      } catch (error) {
        console.error('Error restoring event:', error);
        toast.error(`Failed to restore event: ${error.message || 'Unknown error'}`);
        
        // Remove from deleted events list to ensure it can still be displayed
        setDeletedEventIds(prevIds => 
          prevIds.filter(id => id !== deletedEvent?.extendedProps?.id)
        );
        
        fetchEvents(); // Still refresh to ensure UI is in sync
      } finally {
        setIsLoading(false);
      }
      return;
    }
    
    // Handle delete action
    try {
      // Store the event for potential undo before removing
      console.log('Deleting event with date:', selectedEvent.extendedProps.start);
      setDeletedEvent(selectedEvent);
      
      // Add to the deleted events list - IMPORTANT
      const eventId = selectedEvent.extendedProps.id;
      console.log(`Adding event ID ${eventId} to deleted list`);
      
      // Make sure we don't add duplicates
      setDeletedEventIds(prev => {
        if (!prev.includes(eventId)) {
          return [...prev, eventId];
        }
        return prev;
      });
      
      // Force immediate visual update by directly manipulating the FullCalendar API
      const calendarApi = calendarRef.current.getApi();
      const eventApi = calendarApi.getEventById(selectedEvent.id);
      if (eventApi) {
        eventApi.remove(); // This immediately removes the event from the calendar view
      }
      
      // Also update our internal state for React
      setInternalEvents(prev => 
        prev.filter(e => e.id !== selectedEvent.id)
      );
      
      // Force a re-render by updating lastUpdated state
      setLastUpdated(new Date());
      
      // Set undo state immediately
      setIsUndo(true);
      setShowUndoButton(true);
      
      // Show toast for visual feedback
      toast.success('Event deleted - click Undo to restore', { 
        autoClose: 10000 
      });
      
      // Start undo timer - only delete from backend after timeout
      const timerId = setTimeout(async () => {
        try {
          const eventIdToDelete = selectedEvent.extendedProps.id;
          console.log('Undo timer expired, deleting from backend:', eventIdToDelete);
          
          // Only delete from backend after timer expires
          await axios.delete(`/api/calendar/events/${eventIdToDelete}`);
          console.log('Event permanently deleted from backend');
          
          // Mark as backend deleted to prevent duplication during restore
          setBackendDeletedIds(prev => [...prev, eventIdToDelete]);
          
          // Show confirmation toast
          toast.info('Event permanently deleted', { autoClose: 3000 });
        } catch (err) {
          console.error('Error in delayed event deletion:', err);
          // Even if the API call fails, we want to keep the UI consistent
        } finally {
          // CRITICAL: Reset UI states ALWAYS do this even if the API fails
          resetUndoStates();
        }
      }, 10000); // 10 seconds
      
      setUndoTimerId(timerId);
      pendingDeleteRef.current = timerId;
      
    } catch (error) {
      console.error('Error handling delete event:', error);
      toast.error(`Error processing delete: ${error.message || 'Unknown error'}`);
      
      // If error occurs, remove from deleted events list to prevent it from being hidden
      if (selectedEvent?.extendedProps?.id) {
        setDeletedEventIds(prev => prev.filter(id => id !== selectedEvent.extendedProps.id));
      }
      
      // Restore the event in UI on error
      fetchEvents();
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
      
      // Refresh calendar
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

      {/* Separate condition for showing event actions */}
      {(selectedEvent && (!isUndo || showUndoButton)) && (
        <div className="calendar-event-actions">
          <button 
            onClick={handleDelete} 
            className={`event-action-button ${isUndo ? 'undo-button' : 'delete-button'}`}
          >
            {isUndo ? 'Undo Delete' : 'Delete Event'}
          </button>
          
          {!isUndo && (
            <button 
              onClick={openEditModal} 
              className="event-action-button edit-button"
            >
              Edit Event
            </button>
          )}
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
              <button className="save-button" onClick={handleSaveEvent}>
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