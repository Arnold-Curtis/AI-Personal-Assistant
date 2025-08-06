import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { toast } from 'react-toastify';
import { VoiceInputButton } from './components/VoiceInputButton';
import { isUserCurrentlyScrolling } from './utils/scrollUtils';

export const Textbox = ({ onCalendarEventDetected, onPlanDetected, darkMode = false }) => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const [lastVoiceTranscript, setLastVoiceTranscript] = useState('');
    const [baseTextBeforeVoice, setBaseTextBeforeVoice] = useState('');
    const isVoiceSessionActiveRef = useRef(false);
    const baseTextBeforeVoiceRef = useRef(''); 
    
    const [chatHistory, setChatHistory] = useState([]);
    const abortControllerRef = useRef(null);
    const intervalRef = useRef(null);
    const responseBuilder = useRef('');
    const responseEndRef = useRef(null);
    const responseContainerRef = useRef(null);

    
    useEffect(() => {
        baseTextBeforeVoiceRef.current = baseTextBeforeVoice;
    }, [baseTextBeforeVoice]);

    
    useEffect(() => {
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, []);

    
    useEffect(() => {
        if (responseEndRef.current && responseContainerRef.current) {
            const container = responseContainerRef.current;
            const isNearBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 100;
            
            
            if ((isNearBottom || loading) && !isUserCurrentlyScrolling()) {
                responseEndRef.current.scrollIntoView({ 
                    behavior: 'auto',
                    block: 'nearest'
                });
            }
        }
    }, [response, loading]);

    
    const isProblematicTitle = (title) => {
        if (!title) return true;
        
        const lowerTitle = title.toLowerCase().trim();
        
        
        const problematicTitles = [
            'with', 'from', 'to', 'in', 'on', 'at', 'by', 'for',
            'the', 'a', 'an', 'and', 'or', 'but', 'is', 'are', 'was', 'were',
            'when', 'what', 'where', 'who', 'how', 'why',
            'upcoming', 'auto detect', 'with auto', 'auto',
            'none', 'null', 'undefined', 'calendar'
        ];
        
        
        
        for (const problematic of problematicTitles) {
            if (lowerTitle === problematic) {
                console.log('❌ Rejecting problematic title (exact match):', title);
                return true;
            }
        }
        
        
        if (lowerTitle.match(/\d+\s*days?/) && lowerTitle.match(/\s+\w{3,}\s+/)) {
            console.log('❌ Rejecting malformed title with days and extra text:', title);
            return true;
        }
        
        
        if (/^[^\w]*$/.test(title) || /^\d+$/.test(title)) {
            console.log('❌ Rejecting title with only punctuation/numbers:', title);
            return true;
        }
        
        
        if (title.trim().length < 2) {
            console.log('❌ Rejecting title too short:', title);
            return true;
        }
        
        return false;
    };

    
    const isQuestionAboutExistingInfo = (userInput) => {
        if (!userInput) return false;
        
        const lowerInput = userInput.toLowerCase().trim();
        
        
        const questionPatterns = [
            'when is my',
            'what is my', 
            'what\'s my',
            'when\'s my',
            'what time is',
            'what day is',
            'tell me about',
            'what are the details',
            'remind me about',
            'do you know when',
            'do you remember when',
            'can you tell me',
            'what is the name of',
            'what\'s the name of',
            'who is my',
            'who\'s my',
            'where is my',
            'where\'s my',
            'how old is',
            'what date is'
        ];
        
        
        for (const pattern of questionPatterns) {
            if (lowerInput.startsWith(pattern) || lowerInput.includes(pattern)) {
                console.log('🤔 Detected question pattern:', pattern, 'in input:', userInput);
                return true;
            }
        }
        
        
        const questionWords = ['when', 'what', 'where', 'who', 'how', 'which', 'why'];
        for (const qWord of questionWords) {
            if (lowerInput.startsWith(qWord + ' ')) {
                console.log('🤔 Detected question word:', qWord, 'at start of input:', userInput);
                return true;
            }
        }
        
        
        if (lowerInput.includes('?')) {
            console.log('🤔 Detected question mark in input:', userInput);
            return true;
        }
        
        return false;
    };

    const extractCalendarEvents = (text, originalUserInput = '') => {
        const events = [];
        console.log('🔍 Starting calendar event extraction from text:', text.substring(0, 200));
        
        
        
        if (originalUserInput && isQuestionAboutExistingInfo(originalUserInput)) {
            console.log('❌ Skipping calendar extraction - user input was an information question:', originalUserInput);
            return events;
        }
        
        
        const cleanText = text.replace(/\)\*!/g, '').replace(/\.!\.\./g, '');
        
        
        const patterns = [
            
            /Calendar:\s*(\d+)\s*days?\s*from\s*today\s+([^.!\n\r]+?)\.!\.(?:\.!\.)?/gi,
            
            
            /Calendar:\s*(\d+)\s*days?\s*from\s*today\s+([^\n\r.!]+?)(?=\s*(?:Plan|Suggestions|$))/gi
        ];
        
        
        const categoriesRegex = /\*\*Part 3: Categories\*\*([\s\S]*?)(?:\*\*|$)/i;
        const categoriesMatch = categoriesRegex.exec(cleanText);
        
        let searchText = categoriesMatch ? categoriesMatch[1] : cleanText;
        console.log('🔍 Searching in categories section:', searchText.substring(0, 300));
        
        
        const seenEvents = new Set(); 
        
        
        for (let patternIndex = 0; patternIndex < patterns.length; patternIndex++) {
            const pattern = patterns[patternIndex];
            pattern.lastIndex = 0; 
            let match;
            
            while ((match = pattern.exec(searchText)) !== null) {
                const days = parseInt(match[1]);
                let title = match[2].trim();
                
                console.log(`📅 Pattern ${patternIndex + 1} found potential event:`, { days, rawTitle: title, fullMatch: match[0] });
                
                
                title = title
                    .replace(/\.!\.\./g, '')
                    .replace(/\)\*!/g, '')
                    .replace(/Plan X!@:.*$/i, '')
                    .replace(/Step \d+:.*$/i, '')
                    .replace(/Suggestions X!@:.*$/i, '')
                    .replace(/\[None\]/g, '')
                    .replace(/^[.!]+|[.!]+$/g, '') 
                    .replace(/\s+/g, ' ') 
                    .replace(/\s*from\s*today\s*/gi, '') 
                    .replace(/\s*calendar\s*/gi, '') 
                    .replace(/\b\d+\s*days?\b/gi, '') 
                    .replace(/\s+\d+\s+/g, ' ') 
                    .trim();
                
                
                const eventSignature = `${title.toLowerCase()}_${days}`;
                
                
                const isValidEvent = title && 
                    title.length > 2 && 
                    title.length < 100 && 
                    !title.toLowerCase().includes('none') &&
                    !title.includes('Part ') && 
                    !title.includes('X!@') &&
                    !title.match(/^(plan|suggestions?|step|calendar)\s*\d*/i) &&
                    !title.match(/^\d+\s*(days?|weeks?|months?)/i) && 
                    !isProblematicTitle(title) && 
                    !isNaN(days) && 
                    days >= 0 && 
                    days <= 365 && 
                    !seenEvents.has(eventSignature); 
                
                if (isValidEvent) {
                    
                    seenEvents.add(eventSignature);
                    
                    const startDate = new Date();
                    startDate.setDate(startDate.getDate() + days);
                    
                    
                    let description = generateEventDescription(title, days);
                    
                    
                    let eventColor = determineEventColor(title, days);
                    
                    const eventData = {
                        title: formatEventTitle(title),
                        start: startDate.toISOString().split('T')[0],
                        isAllDay: true,
                        eventColor: eventColor,
                        description: description,
                        daysFromToday: days, 
                        signature: eventSignature 
                    };
                    
                    console.log('✅ Added unique event:', eventData);
                    events.push(eventData);
                } else {
                    console.log('❌ Invalid or duplicate event filtered out:', { 
                        title, 
                        days, 
                        signature: eventSignature,
                        alreadySeen: seenEvents.has(eventSignature),
                        reasons: getValidationErrors(title, days) 
                    });
                }
            }
        }
        
        
        const finalEvents = [];
        const finalSignatures = new Set();
        
        for (const event of events) {
            
            let cleanedTitle = event.title
                .replace(/\s*calendar\s*/gi, '')  
                .replace(/\b\d+\s*days?\b/gi, '') 
                .trim();
            
            
            if (cleanedTitle.length < 3) {
                console.log('🗑️ Skipping event with too short title after cleaning:', event.title);
                continue;
            }
            
            
            event.title = cleanedTitle;
            
            
            const flexibleSignature = cleanedTitle.toLowerCase().replace(/[^a-z0-9]/g, '') + '_' + event.daysFromToday;
            
            if (!finalSignatures.has(flexibleSignature)) {
                finalSignatures.add(flexibleSignature);
                finalEvents.push(event);
            } else {
                console.log('🗑️ Removed duplicate in final pass:', event.title);
            }
        }
        
        
        if (finalEvents.length === 0) {
            console.log('🔍 No structured events found, trying fallback extraction...');
            
            
            const fallbackEvents = extractEventsFromNaturalLanguage(text);
            finalEvents.push(...fallbackEvents);
            
            
            if (finalEvents.length === 0 && originalUserInput) {
                console.log('🔍 No events in response, trying original user input...');
                const userInputEvents = extractEventsFromNaturalLanguage(originalUserInput);
                finalEvents.push(...userInputEvents);
            }
        }
        
        console.log(`🎯 Final extracted events (${finalEvents.length}):`, finalEvents);
        return finalEvents;
    };

    
    const generateEventDescription = (title, days) => {
        const lowerTitle = title.toLowerCase();
        const urgency = days <= 3 ? 'urgent' : days <= 7 ? 'upcoming' : 'scheduled';
        
        if (lowerTitle.includes('wedding')) {
            return days <= 7 ? 'Wedding celebration (soon!)' : 'Wedding celebration';
        } else if (lowerTitle.includes('meeting') || lowerTitle.includes('meet')) {
            return `${urgency.charAt(0).toUpperCase() + urgency.slice(1)} meeting`;
        } else if (lowerTitle.includes('appointment')) {
            return `${urgency.charAt(0).toUpperCase() + urgency.slice(1)} appointment`;
        } else if (lowerTitle.includes('birthday')) {
            return 'Birthday celebration 🎂';
        } else if (lowerTitle.includes('party')) {
            return 'Party event 🎉';
        } else if (lowerTitle.includes('conference') || lowerTitle.includes('call')) {
            return 'Conference/call';
        } else if (lowerTitle.includes('vacation') || lowerTitle.includes('trip')) {
            return 'Travel/vacation ✈️';
        } else if (lowerTitle.includes('exam') || lowerTitle.includes('test')) {
            return days <= 7 ? 'Important exam (prepare!)' : 'Scheduled exam';
        } else if (lowerTitle.includes('interview')) {
            return 'Job interview';
        } else if (lowerTitle.includes('date')) {
            return 'Personal date';
        } else if (days <= 3) {
            return `Upcoming ${lowerTitle}`;
        } else {
            return `Scheduled event: ${title}`;
        }
    };

    
    const determineEventColor = (title, days) => {
        const lowerTitle = title.toLowerCase();
        
        
        if (days <= 1) return "#ef4444";
        
        
        if (lowerTitle.includes('wedding') || lowerTitle.includes('birthday')) {
            return "#8b5cf6";
        }
        
        
        if (lowerTitle.includes('meeting') || lowerTitle.includes('conference') || 
            lowerTitle.includes('interview') || lowerTitle.includes('call')) {
            return "#3b82f6";
        }
        
        
        if (lowerTitle.includes('appointment') || lowerTitle.includes('doctor')) {
            return "#10b981";
        }
        
        
        if (lowerTitle.includes('exam') || lowerTitle.includes('test') || lowerTitle.includes('school')) {
            return "#f59e0b";
        }
        
        
        if (lowerTitle.includes('party') || lowerTitle.includes('date') || lowerTitle.includes('meet')) {
            return "#ec4899";
        }
        
        
        return "#3b82f6";
    };

    
    const formatEventTitle = (title) => {
        
        return title.replace(/\b\w+/g, (word) => {
            
            const lowercaseWords = ['a', 'an', 'the', 'at', 'by', 'for', 'in', 'of', 'on', 'to', 'up', 'and', 'or'];
            return lowercaseWords.includes(word.toLowerCase()) ? 
                word.toLowerCase() : 
                word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
        });
    };

    
    const getValidationErrors = (title, days) => {
        const errors = [];
        if (!title || title.length === 0) errors.push('empty title');
        if (title && title.length < 2) errors.push('title too short');
        if (title && title.length >= 100) errors.push('title too long');
        if (title && title.toLowerCase().includes('none')) errors.push('contains "none"');
        if (title && title.includes('X!@')) errors.push('contains formatting artifacts');
        if (title && title.match(/^(plan|suggestions?|step|calendar)\s*\d*/i)) errors.push('starts with reserved word');
        if (title && title.match(/^\d+\s*(days?|weeks?|months?)/i)) errors.push('just a time reference');
        if (title && isProblematicTitle(title)) errors.push('problematic title');
        if (isNaN(days)) errors.push('invalid days');
        if (days < 0) errors.push('negative days');
        if (days > 365) errors.push('days too far in future');
        return errors;
    };

    
    const extractEventsFromNaturalLanguage = (text) => {
        const events = [];
        console.log('🔍 Starting natural language extraction from text:', text.substring(0, 300));
        
        const naturalPatterns = [
            
            /(?:i have|there's|there is)\s+(?:a|an)?\s*(\w+)\s+in\s+(\d+)\s+(days?|weeks?|months?)/gi,
            
            /(?:i have|there's|there is)\s+(?:a|an)?\s*(\w+)\s+in\s+(one|two|three|four|five|six|seven|eight|nine|ten)\s+(days?|weeks?|months?)/gi,
            
            /(\w+)\s+(tomorrow|today|next week|next month)/gi,
            
            /(\w+)\s+on\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)/gi,
            
            /(\w+)\s+in\s+a\s+(day|week|month)/gi
        ];
        
        
        const numberMap = {
            'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5,
            'six': 6, 'seven': 7, 'eight': 8, 'nine': 9, 'ten': 10,
            'a': 1, 'an': 1
        };
        
        for (let patternIndex = 0; patternIndex < naturalPatterns.length; patternIndex++) {
            const pattern = naturalPatterns[patternIndex];
            pattern.lastIndex = 0;
            let match;
            
            while ((match = pattern.exec(text)) !== null) {
                const eventType = match[1].trim();
                let days = 0;
                
                console.log(`🔍 Pattern ${patternIndex + 1} matched:`, match);
                
                
                if (patternIndex === 0) {
                    
                    const number = parseInt(match[2]);
                    const unit = match[3].toLowerCase();
                    
                    if (unit.includes('day')) days = number;
                    else if (unit.includes('week')) days = number * 7;
                    else if (unit.includes('month')) days = number * 30;
                    
                    console.log('📅 Pattern 1 - Number format:', { number, unit, calculatedDays: days });
                    
                } else if (patternIndex === 1) {
                    
                    const writtenNumber = match[2].toLowerCase();
                    const number = numberMap[writtenNumber] || 1;
                    const unit = match[3].toLowerCase();
                    
                    if (unit.includes('day')) days = number;
                    else if (unit.includes('week')) days = number * 7;
                    else if (unit.includes('month')) days = number * 30;
                    
                    console.log('📅 Pattern 2 - Written number format:', { writtenNumber, number, unit, calculatedDays: days });
                    
                } else if (patternIndex === 2) {
                    
                    const timeRef = match[2].toLowerCase();
                    
                    if (timeRef === 'tomorrow') days = 1;
                    else if (timeRef === 'today') days = 0;
                    else if (timeRef === 'next week') days = 7;
                    else if (timeRef === 'next month') days = 30;
                    
                    console.log('📅 Pattern 3 - Time reference format:', { timeRef, calculatedDays: days });
                    
                } else if (patternIndex === 3) {
                    
                    const dayRef = match[2].toLowerCase();
                    const today = new Date();
                    const currentDay = today.getDay(); 
                    
                    const dayMap = {
                        'sunday': 0, 'monday': 1, 'tuesday': 2, 'wednesday': 3,
                        'thursday': 4, 'friday': 5, 'saturday': 6
                    };
                    
                    const targetDay = dayMap[dayRef];
                    if (targetDay !== undefined) {
                        days = (targetDay - currentDay + 7) % 7;
                        if (days === 0) days = 7; 
                    }
                    
                    console.log('📅 Pattern 4 - Day reference format:', { dayRef, targetDay, currentDay, calculatedDays: days });
                    
                } else if (patternIndex === 4) {
                    
                    const unit = match[2].toLowerCase();
                    
                    if (unit === 'day') days = 1;
                    else if (unit === 'week') days = 7;
                    else if (unit === 'month') days = 30;
                    
                    console.log('📅 Pattern 5 - Article format:', { unit, calculatedDays: days });
                }
                
                
                if (days >= 0 && days <= 365 && eventType.length > 1 && !isProblematicTitle(eventType)) {
                    const startDate = new Date();
                    startDate.setDate(startDate.getDate() + days);
                    
                    const eventData = {
                        title: formatEventTitle(eventType),
                        start: startDate.toISOString().split('T')[0],
                        isAllDay: true,
                        eventColor: determineEventColor(eventType, days),
                        description: generateEventDescription(eventType, days) + ' (auto-detected)',
                        daysFromToday: days
                    };
                    
                    events.push(eventData);
                    console.log('✅ Auto-detected event from natural language:', eventData);
                    console.log('📅 Event will be scheduled for:', startDate.toDateString());
                } else {
                    console.log('❌ Invalid natural language event filtered out:', { 
                        eventType, 
                        days, 
                        eventTypeLength: eventType.length,
                        isProblematic: isProblematicTitle(eventType),
                        validDaysRange: days >= 0 && days <= 365,
                        reasons: getValidationErrors(eventType, days) 
                    });
                }
            }
        }
        
        console.log(`🎯 Natural language extraction found ${events.length} events:`, events);
        return events;
    };
    
    const extractPlanSteps = (text) => {
        
        if (text.includes("Plan X!@:") || !text.includes("Plan:")) {
            return [];
        }
        
        try {
            
            const planRegex = /Plan: \[(.*?)\]([\s\S]*?)\.\.!\./;
            const planMatch = planRegex.exec(text);
            
            if (!planMatch || !planMatch[1]) return [];
            
            const planTitle = planMatch[1].trim();
            const planContent = planMatch[2].trim();
            const steps = [];
            
            
            const stepRegex = /Step \d+: \[Time: ([^\]]+)\] \| \[Title: ([^\]]+)\] \| \[Description: ([^\]]+)\] \| \[Completion: ([^\]]+)\] \| \[Day: ([^\]]+)\]/g;
            let stepMatch;
            
            while ((stepMatch = stepRegex.exec(planContent)) !== null) {
                steps.push({
                    time: stepMatch[1].trim(),
                    title: stepMatch[2].trim(),
                    description: stepMatch[3].trim(),
                    completion: stepMatch[4].trim(),
                    day: stepMatch[5].trim()
                });
            }
            
            
            return {
                title: planTitle,
                steps: steps
            };
        } catch (error) {
            console.error('Error parsing plan steps:', error);
            return { title: '', steps: [] };
        }
    };

    const extractResponse = (responseData, originalUserInput = '') => {
        try {
            const responseText = typeof responseData === 'string' 
                ? responseData 
                : JSON.stringify(responseData, null, 2);

            console.log('🔍 Processing response for calendar/plan extraction:', responseText.substring(0, 500));

            
            const calendarEvents = extractCalendarEvents(responseText, originalUserInput);
            if (calendarEvents.length > 0) {
                console.log(`📅 Successfully extracted ${calendarEvents.length} calendar events:`, calendarEvents);
                if (onCalendarEventDetected) {
                    try {
                        onCalendarEventDetected(calendarEvents);
                        toast.success(`Added ${calendarEvents.length} event(s) to your calendar!`, {
                            position: "bottom-right",
                            autoClose: 3000
                        });
                    } catch (error) {
                        console.error('❌ Error in calendar event callback:', error);
                        toast.error('Failed to add events to calendar');
                    }
                }
            } else {
                console.log('📅 No calendar events detected in response');
                
                
                const eventKeywords = ['wedding', 'meeting', 'appointment', 'birthday', 'event', 'party', 'conference', 'interview'];
                const foundKeywords = eventKeywords.filter(keyword => 
                    responseText.toLowerCase().includes(keyword) || 
                    (originalUserInput && originalUserInput.toLowerCase().includes(keyword))
                );
                
                if (foundKeywords.length > 0) {
                    console.warn('⚠️ Event keywords found but no events parsed - possible parsing issue');
                    console.log('📋 Found keywords:', foundKeywords);
                    console.log('📋 Original user input:', originalUserInput);
                    console.log('📋 Response text sample:', responseText.substring(0, 500));
                    
                    
                    if (originalUserInput && !isQuestionAboutExistingInfo(originalUserInput)) {
                        console.log('🔄 Attempting direct extraction from user input as last resort...');
                        const directEvents = extractEventsFromNaturalLanguage(originalUserInput);
                        if (directEvents.length > 0) {
                            console.log('✅ Direct extraction successful:', directEvents);
                            if (onCalendarEventDetected) {
                                onCalendarEventDetected(directEvents);
                                toast.success(`Added ${directEvents.length} event(s) to your calendar!`, {
                                    position: "bottom-right",
                                    autoClose: 3000
                                });
                            }
                        }
                    }
                }
            }
            
            
            const planData = extractPlanSteps(responseText);
            if (planData.steps && planData.steps.length > 0) {
                console.log(`📋 Successfully extracted plan with ${planData.steps.length} steps:`, planData);
                if (onPlanDetected) {
                    try {
                        onPlanDetected(planData);
                        toast.success(`Created plan: ${planData.title}`, {
                            position: "bottom-right",
                            autoClose: 3000
                        });
                    } catch (error) {
                        console.error('❌ Error in plan callback:', error);
                        toast.error('Failed to create plan');
                    }
                }
            }

            
            const part2Match = responseText.match(/\*\*Part 2: Response\*\*([\s\S]*?)(?:\*\*Part 3:|$)/i);
            const extractedResponse = part2Match && part2Match[1] 
                ? cleanResponseText(part2Match[1]) 
                : cleanResponseText(responseText);

            console.log('✅ Response extraction complete');
            return extractedResponse;

        } catch (e) {
            console.error("❌ Response parsing error:", e);
            toast.error("Error processing response");
            return "An error occurred while processing the response";
        }
    };

    const cleanResponseText = (text) => {
        
        const codeBlocks = [];
        let processedText = text.replace(/```[\s\S]*?```/g, (match) => {
            codeBlocks.push(match);
            return `__CODE_BLOCK_${codeBlocks.length - 1}__`;
        });
        
        
        processedText = processedText
            .replace(/\*\*Part \d+:.*?\*\*/g, '')
            .replace(/\)\*!/g, '')
            .replace(/\.!\.\./g, '')
            .replace(/Plan X!@:.*?(?=\n|$)/g, '')
            .replace(/Step \d+:.*?(?=\n|$)/g, '')
            .replace(/Suggestions X!@:.*?(?=\n|$)/g, '')
            .replace(/\[None\]/g, '')
            .replace(/[*■#!→-]/g, '')
            .replace(/\\n/g, '\n')
            .replace(/\\"/g, '"')
            .replace(/\\u[\dA-F]{4}/gi, m => 
                String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16)))
            .split('\n')
            .map(line => line.trim())
            .filter(line => line && !line.match(/^[0-9.]+$/) && !line.startsWith('-') && !line.includes('X!@'))
            .join('\n')
            .trim();
            
        
        codeBlocks.forEach((block, i) => {
            processedText = processedText.replace(`__CODE_BLOCK_${i}__`, block);
        });
        
        return processedText;
    };

    const simulateStreaming = (text) => {
        responseBuilder.current = '';
        setResponse('');
        
        if (intervalRef.current) clearInterval(intervalRef.current);
        
        let index = 0;
        intervalRef.current = setInterval(() => {
            if (index < text.length) {
                responseBuilder.current = text.substring(0, index + 1);
                setResponse(responseBuilder.current);
                index++;
            } else {
                clearInterval(intervalRef.current);
                
                
                
                setChatHistory(prev => [...prev, { text, isUser: false }]);
            }
        }, 20);
    };    
    const handleVoiceTranscript = (transcript, isFinal) => {
        console.log('📝 Textbox received transcript:', { 
            transcript, 
            isFinal, 
            lastVoiceTranscript, 
            baseTextBeforeVoice: baseTextBeforeVoiceRef.current,
            isVoiceSessionActive: isVoiceSessionActiveRef.current,
            currentInput: input
        });
        
        if (!isFinal) {
            
            const cleanTranscript = transcript.trim();
            
            if (cleanTranscript) {
                
                if (!isVoiceSessionActiveRef.current) {
                    console.log('📝 Starting new voice session, saving base text:', input.trim());
                    const currentInput = input.trim();
                    setBaseTextBeforeVoice(currentInput);
                    baseTextBeforeVoiceRef.current = currentInput;
                    isVoiceSessionActiveRef.current = true;
                    
                    
                    const newText = currentInput + (currentInput ? ' ' : '') + cleanTranscript;
                    console.log('📝 Updating input with interim (new session):', newText);
                    setInput(newText);
                } else {
                    
                    
                    const currentBase = baseTextBeforeVoiceRef.current;
                    const newText = currentBase + (currentBase ? ' ' : '') + cleanTranscript;
                    console.log('📝 Updating input with interim (existing session):', newText);
                    setInput(newText);
                }
            }
        }
    };    const handleVoiceFinalTranscript = (transcript) => {
        console.log('✨ Textbox received final transcript:', transcript);
        const cleanTranscript = transcript.trim();
        
        
        if (cleanTranscript && cleanTranscript !== lastVoiceTranscript) {
            setLastVoiceTranscript(cleanTranscript);
            
            
            const currentBase = baseTextBeforeVoiceRef.current;
            const newText = currentBase + (currentBase ? ' ' : '') + cleanTranscript;
            console.log('✨ Setting final input text:', newText);
            
            
            setBaseTextBeforeVoice(newText);
            baseTextBeforeVoiceRef.current = newText;
            setInput(newText);
            
            
            
            
            toast.success('Voice input completed!');
        } else {
            console.log('✨ Transcript duplicate or empty, skipping');
        }
    };    const handleVoiceStop = () => {
        console.log('🛑 Voice input manually stopped, clearing voice tracking');
        
        setBaseTextBeforeVoice('');
        baseTextBeforeVoiceRef.current = '';
        setLastVoiceTranscript('');
        isVoiceSessionActiveRef.current = false;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (abortControllerRef.current) abortControllerRef.current.abort();
        if (!input.trim()) return;
        
        const controller = new AbortController();
        abortControllerRef.current = controller;
        setLoading(true);
        setResponse('');
        
        
        const userMessage = input.trim();
        setChatHistory(prev => [...prev, { text: userMessage, isUser: true }]);

        try {
            
            const response = await axios.post('/api/generate', 
                { 
                    prompt: userMessage,
                    history: chatHistory
                }, 
                { signal: controller.signal }
            );

            
            const responseText = typeof response.data === 'string'
                ? response.data
                : JSON.stringify(response.data, null, 2);

            const processed = extractResponse(responseText, userMessage);
            simulateStreaming(processed);

        } catch (error) {
            if (error.name === 'CanceledError' || error.name === 'AbortError') {
                setResponse("Request cancelled by user");
            } else {
                console.error('API Error:', error);
                setResponse(`ERROR: ${error.message || 'Unknown error'}`);
                toast.error(`Error: ${error.message || 'Unknown error'}`, {
                    position: 'bottom-right',
                    autoClose: 5000
                });
                
                
                setChatHistory(prev => [...prev, { 
                    text: `ERROR: ${error.message || 'Unknown error'}`, 
                    isUser: false 
                }]);
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
            setInput(''); 
            
        }
    };    const handleReset = () => {
        if (abortControllerRef.current) abortControllerRef.current.abort();
        if (intervalRef.current) clearInterval(intervalRef.current);
        setInput('');
        setResponse('');
        setLastVoiceTranscript(''); 
        setBaseTextBeforeVoice(''); 
        baseTextBeforeVoiceRef.current = ''; 
        isVoiceSessionActiveRef.current = false; 
        responseBuilder.current = '';
        
        setChatHistory([]);
        
        if (onPlanDetected) {
            onPlanDetected([]);
        }
    };

    return (
        <div className="textbox-container" style={{ 
            padding: '28px', 
            margin: '0 auto 30px auto',
            fontFamily: 'Inter, Arial, sans-serif',
            backgroundColor: darkMode ? 'var(--card-bg)' : 'var(--card-bg)',
            borderRadius: '16px',
            boxShadow: 'var(--shadow-lg)',
            border: 'var(--card-border)',
            position: 'relative',
            overflow: 'hidden',
            backgroundImage: darkMode ? 
              'linear-gradient(45deg, rgba(31, 41, 55, 0.8) 0%, rgba(31, 41, 55, 1) 100%)' : 
              'linear-gradient(45deg, rgba(255, 255, 255, 0.8) 0%, rgba(248, 250, 252, 1) 100%)',
            width: '100%',
            maxWidth: '100%',
            boxSizing: 'border-box',
            overflowX: 'hidden', 
            display: 'flex',
            flexDirection: 'column'
        }}>
            <form onSubmit={handleSubmit} style={{ marginBottom: '20px' }}>
                <textarea 
                    value={input} 
                    onChange={(e) => {
                        setInput(e.target.value);
                        
                        if (e.target.value !== input) {
                            setLastVoiceTranscript('');
                            setBaseTextBeforeVoice('');
                            baseTextBeforeVoiceRef.current = '';
                            isVoiceSessionActiveRef.current = false;
                        }
                    }}
                    onKeyDown={(e) => {
                        
                        if (e.key === 'Enter' && !e.shiftKey) {
                            e.preventDefault(); 
                            if (input.trim() && !loading) {
                                handleSubmit(e);
                            }
                        }
                    }}
                    placeholder="What's on your mind? I'm here to help..."
                    style={{ 
                        width: '100%',
                        padding: '20px',
                        fontSize: '16px',
                        marginBottom: '20px',
                        border: `1px solid ${darkMode ? 'var(--border-color)' : 'rgba(226, 232, 240, 0.8)'}`,
                        borderRadius: '12px',
                        minHeight: '140px',
                        resize: 'vertical',
                        fontFamily: 'inherit',
                        backgroundColor: darkMode ? 'rgba(17, 24, 39, 0.6)' : 'rgba(255, 255, 255, 0.8)',
                        color: darkMode ? 'var(--text-primary)' : '#1e293b',
                        transition: 'all 0.3s ease',
                        boxShadow: 'inset 0 2px 4px rgba(0, 0, 0, 0.05)',
                        backdropFilter: 'blur(8px)',
                        lineHeight: '1.6'
                    }}
                    disabled={loading}
                /><div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                    <button 
                        type="submit" 
                        disabled={loading || !input.trim()}
                        style={{
                            padding: '12px 24px',
                            background: loading || !input.trim() 
                                ? darkMode ? '#475569' : '#94a3b8'
                                : darkMode ? 'var(--accent-color)' : '#4f46e5',
                            color: 'white',
                            border: 'none',
                            borderRadius: '8px',
                            cursor: loading || !input.trim() ? 'not-allowed' : 'pointer',
                            fontSize: '16px',
                            fontWeight: '600',
                            transition: 'all 0.3s ease',
                            flex: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: 'var(--shadow-sm)',
                            transform: loading || !input.trim() ? 'none' : 'translateY(0)'
                        }}
                        onMouseOver={(e) => {
                            if (!loading && input.trim()) {
                                e.currentTarget.style.transform = 'translateY(-2px)';
                                e.currentTarget.style.boxShadow = 'var(--shadow-md)';
                            }
                        }}
                        onMouseOut={(e) => {
                            if (!loading && input.trim()) {
                                e.currentTarget.style.transform = 'translateY(0)';
                                e.currentTarget.style.boxShadow = 'var(--shadow-sm)';
                            }
                        }}
                    >
                        {loading ? (
                            <>
                                <span 
                                    style={{
                                        display: 'inline-block',
                                        width: '18px',
                                        height: '18px',
                                        border: '2px solid rgba(255,255,255,0.3)',
                                        borderRadius: '50%',
                                        borderTopColor: 'white',
                                        animation: 'spin 1s linear infinite',
                                        marginRight: '10px'
                                    }}
                                />
                                Processing...
                            </>
                        ) : "Send"}
                    </button>                    <VoiceInputButton
                        onTranscript={handleVoiceTranscript}
                        onFinalTranscript={handleVoiceFinalTranscript}
                        onStop={handleVoiceStop}
                        disabled={loading}
                    />
                    <button
                        type="button"
                        onClick={handleReset}
                        style={{
                            padding: '12px 24px',
                            background: '#dc3545',
                            color: 'white',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '16px'
                        }}
                    >
                        Reset
                    </button>
                </div>
            </form>
            
            <div 
                ref={responseContainerRef}
                className="textbox-response-container response-container"
                style={{
                    padding: '28px',
                    background: darkMode ? 
                      'linear-gradient(135deg, rgba(31, 41, 55, 0.95), rgba(17, 24, 39, 0.95))' : 
                      'linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(240, 249, 255, 0.95))',
                    minHeight: '200px',
                    maxHeight: '60vh',
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    boxShadow: 'var(--shadow-lg)',
                    border: 'var(--card-border)',
                    marginTop: '24px',
                    backgroundSize: '400% 400%',
                    animation: 'gradientShift 15s ease infinite',
                    width: '100%',
                    maxWidth: '100%',
                    boxSizing: 'border-box'
                }}
            >
                <div 
                    className="textbox-response-content"
                    style={{ 
                        lineHeight: '1.7',
                        fontSize: '16px',
                        color: darkMode ? 'var(--text-primary)' : '#1e293b',
                        fontFamily: 'Inter, Arial, sans-serif', 
                        width: '100%',
                        maxWidth: '100%',
                        overflowWrap: 'break-word',
                        wordWrap: 'break-word',
                        wordBreak: 'break-word',
                        boxSizing: 'border-box',
                        hyphens: 'auto',
                        padding: '0',
                        margin: '0'
                    }}
                >
                    {response || (loading ? "Analyzing your request and crafting response..." : "Your response will appear here...")}
                    <div ref={responseEndRef} />
                </div>
            </div>
            
            <style>{`
                @keyframes spin {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(360deg); }
                }
            `}</style>
        </div>
    );
};
