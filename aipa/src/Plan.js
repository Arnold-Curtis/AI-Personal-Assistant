import React, { useState, useEffect, useRef } from 'react';
import { toast } from 'react-toastify';
import axios from 'axios';
import './Plan.css';
import { scrollAfterResponse } from './utils/scrollUtils';

export const Plan = ({ planData, onUpdatePlan, darkMode = false }) => {
  const [steps, setSteps] = useState([]);
  const [planTitle, setPlanTitle] = useState('');
  const [editMode, setEditMode] = useState({}); // Track which fields are being edited
  const [editValues, setEditValues] = useState({}); // Track edited values
  const planContainerRef = useRef(null);

  useEffect(() => {
    if (planData) {
      // Check if we have a plan with steps and title
      if (planData.steps && planData.steps.length > 0) {
        setSteps(planData.steps);
        setPlanTitle(planData.title || '');
        // Reset edit states when new plan data arrives
        setEditMode({});
        setEditValues({});
        
        // Use scroll after response completes
        scrollAfterResponse(planContainerRef.current);
      } else if (Array.isArray(planData) && planData.length > 0) {
        // Backward compatibility with older format without title
        setSteps(planData);
        setPlanTitle('');
        setEditMode({});
        setEditValues({});
        
        scrollAfterResponse(planContainerRef.current);
      }
    }
  }, [planData]);

  const toggleEdit = (stepIndex, field) => {
    const key = `${stepIndex}-${field}`;
    setEditMode(prev => ({
      ...prev,
      [key]: !prev[key]
    }));
    
    // Initialize edit value if entering edit mode
    if (!editMode[key]) {
      setEditValues(prev => ({
        ...prev,
        [key]: steps[stepIndex][field]
      }));
    }
  };

  const handleEditChange = (stepIndex, field, value) => {
    const key = `${stepIndex}-${field}`;
    setEditValues(prev => ({
      ...prev,
      [key]: value
    }));
  };

  const saveEdit = (stepIndex, field) => {
    const key = `${stepIndex}-${field}`;
    const updatedSteps = [...steps];
    updatedSteps[stepIndex] = {
      ...updatedSteps[stepIndex],
      [field]: editValues[key]
    };
    
    setSteps(updatedSteps);
    setEditMode(prev => ({
      ...prev,
      [key]: false
    }));
  };

  const handleKeyDown = (e, stepIndex, field) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      saveEdit(stepIndex, field);
    }
  };

  const handleUpdatePlan = () => {
    if (onUpdatePlan) {
      // Send both the title and steps back to parent
      onUpdatePlan({
        title: planTitle,
        steps: steps
      });
    }
    toast.success('Plan updated successfully!');
  };
  
  const handleCancel = () => {
    if (onUpdatePlan) {
      // Clear the plan by sending empty array to hide the component
      onUpdatePlan([]);
    }
    toast.success('Plan cancelled');
  };
  
  const handleAgree = async () => {
    try {
      let addedCount = 0;
      
      // Add each step as a calendar event
      for (const step of steps) {
        // Extract day number from the day string (e.g. "3 days from today")
        const dayOffset = extractDayNumber(step.day);
        
        // Calculate the event date
        const date = new Date();
        date.setDate(date.getDate() + dayOffset);
        const formattedDate = date.toISOString().split('T')[0];
        
        // Create the event object
        const eventData = {
          title: step.title,
          start: formattedDate,
          isAllDay: true,
          eventColor: "#3b82f6", // Default blue color
          description: step.description,
          planTitle: planTitle, // Store the plan title with the event
          isPlanEvent: true  // Mark as a plan event for identification later
        };
        
        console.log('Sending event to backend:', eventData);
        
        // Send to backend
        const response = await axios.post('/api/calendar/add-event', eventData);
        console.log('Backend response:', response.data);
        addedCount++;
      }
      
      toast.success(`Added ${addedCount} events to your calendar!`);
      
      // Scroll to calendar after plan events are added
      const calendarElement = document.querySelector('.calendar-container');
      if (calendarElement) {
        scrollAfterResponse(calendarElement);
      }
      
    } catch (error) {
      console.error('Error adding plan to calendar:', error);
      toast.error(error.response?.data?.error || 'Failed to add plan to calendar');
    }
  };

  // Calculate day name from days offset
  const getDayName = (daysOffset) => {
    const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    const today = new Date();
    const targetDate = new Date(today);
    targetDate.setDate(today.getDate() + parseInt(daysOffset));
    return days[targetDate.getDay()];
  };

  // Extract day number from format like "2 days from today"
  const extractDayNumber = (dayString) => {
    const match = dayString.match(/(\d+)/);
    return match ? parseInt(match[1]) : 0;
  };

  if (!planData || planData.length === 0) {
    return null;
  }

  return (
    <div className="plan-container" ref={planContainerRef}>
      <h3 className="plan-title">Your Plan</h3>
      <h4 className="plan-subtitle">{planTitle}</h4>
      
      <div className="plan-table">
        <div className="plan-header">
          <div className="plan-cell">Time</div>
          <div className="plan-cell">Title</div>
          <div className="plan-cell">Description</div>
          <div className="plan-cell">Completion</div>
          <div className="plan-cell">Day</div>
        </div>
        
        {steps.map((step, index) => (
          <div className="plan-row" key={index}>
            <div className="plan-cell">
              {editMode[`${index}-time`] ? (
                <input
                  type="text"
                  value={editValues[`${index}-time`] || ''}
                  onChange={(e) => handleEditChange(index, 'time', e.target.value)}
                  onBlur={() => saveEdit(index, 'time')}
                  onKeyDown={(e) => handleKeyDown(e, index, 'time')}
                  autoFocus
                />
              ) : (
                <div className="editable" onClick={() => toggleEdit(index, 'time')}>
                  {step.time}
                </div>
              )}
            </div>
            
            <div className="plan-cell">
              {editMode[`${index}-title`] ? (
                <input
                  type="text"
                  value={editValues[`${index}-title`] || ''}
                  onChange={(e) => handleEditChange(index, 'title', e.target.value)}
                  onBlur={() => saveEdit(index, 'title')}
                  onKeyDown={(e) => handleKeyDown(e, index, 'title')}
                  autoFocus
                />
              ) : (
                <div className="editable" onClick={() => toggleEdit(index, 'title')}>
                  {step.title}
                </div>
              )}
            </div>
            
            <div className="plan-cell">
              {editMode[`${index}-description`] ? (
                <textarea
                  value={editValues[`${index}-description`] || ''}
                  onChange={(e) => handleEditChange(index, 'description', e.target.value)}
                  onBlur={() => saveEdit(index, 'description')}
                  autoFocus
                />
              ) : (
                <div className="editable" onClick={() => toggleEdit(index, 'description')}>
                  {step.description}
                </div>
              )}
            </div>
            
            <div className="plan-cell">
              {editMode[`${index}-completion`] ? (
                <input
                  type="text"
                  value={editValues[`${index}-completion`] || ''}
                  onChange={(e) => handleEditChange(index, 'completion', e.target.value)}
                  onBlur={() => saveEdit(index, 'completion')}
                  onKeyDown={(e) => handleKeyDown(e, index, 'completion')}
                  autoFocus
                />
              ) : (
                <div className="editable" onClick={() => toggleEdit(index, 'completion')}>
                  {step.completion}
                </div>
              )}
            </div>
            
            <div className="plan-cell day-cell">
              {editMode[`${index}-day`] ? (
                <input
                  type="text"
                  value={editValues[`${index}-day`] || ''}
                  onChange={(e) => handleEditChange(index, 'day', e.target.value)}
                  onBlur={() => saveEdit(index, 'day')}
                  onKeyDown={(e) => handleKeyDown(e, index, 'day')}
                  autoFocus
                />
              ) : (
                <div className="editable" onClick={() => toggleEdit(index, 'day')}>
                  <span className="day-number">{step.day}</span>
                  <span className="day-name">({getDayName(extractDayNumber(step.day))})</span>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
      
      <div className="plan-actions">
        <button className="cancel-btn" onClick={handleCancel}>
          Cancel Plan
        </button>
        <button className="update-btn" onClick={handleUpdatePlan}>
          Update Plan
        </button>
        <button className="agree-btn" onClick={handleAgree}>
          Agree
        </button>
      </div>
    </div>
  );
};