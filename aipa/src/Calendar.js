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
  const [lastUpdated, setLastUpdated] = useState(null);

  // Format events for FullCalendar
  const formatEvents = (rawEvents) => {
    return rawEvents.map(event => ({
      id: event.id,
      title: event.title,
      start: event.start,
      allDay: true,
      extendedProps: event,
      textColor: 'black',
      borderColor: '#3b82f6',
      backgroundColor: '#f0f7ff'
    }));
  };

  // Fetch events from backend
  const fetchEvents = async () => {
    if (!backendOnline) return;
    
    try {
      setIsLoading(true);
      const response = await axios.get('/api/calendar/events');
      const formattedEvents = formatEvents(response.data);
      setInternalEvents(formattedEvents);
      setLastUpdated(new Date());
    } catch (error) {
      console.error('Error fetching events:', error);
      toast.error('Failed to refresh calendar events', {
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
    const interval = setInterval(fetchEvents, 30000); // Refresh every 30 seconds
    return () => clearInterval(interval);
  }, [backendOnline]);

  // Update when parent events change
  useEffect(() => {
    if (events && events.length > 0) {
      const formattedEvents = formatEvents(events);
      setInternalEvents(prev => {
        // Filter out duplicates
        const existingIds = prev.map(e => e.id);
        const newEvents = formattedEvents.filter(e => !existingIds.includes(e.id));
        return [...prev, ...newEvents];
      });
    }
  }, [events]);

  // Calendar event handlers
  const handleEventClick = (info) => {
    toast.info(`Event: ${info.event.title}`, {
      position: 'bottom-right',
      autoClose: 2000
    });
  };

  const handleDateClick = (arg) => {
    toast.info(`Date selected: ${arg.dateStr}`, {
      position: 'bottom-right',
      autoClose: 2000
    });
  };

  return (
    <div className="calendar-container" ref={ref}>
      {isLoading && (
        <div className="calendar-loading">
          <div className="spinner"></div>
          <span>Loading calendar data...</span>
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
            info.el.style.borderLeft = '4px solid #3b82f6';
            info.el.style.backgroundColor = '#f0f7ff';
            info.el.style.color = 'black';
          }}
        />
      </div>

      {lastUpdated && (
        <div className="calendar-footer">
          Last updated: {lastUpdated.toLocaleTimeString()}
        </div>
      )}
    </div>
  );
});

Calendar.displayName = 'Calendar';

// Export both as named export and default export
export { Calendar };
export default Calendar;