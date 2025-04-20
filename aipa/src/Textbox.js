import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { toast } from 'react-toastify';

export const Textbox = ({ onCalendarEventDetected, onPlanDetected }) => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    // Keep chat history in state but don't display it
    const [chatHistory, setChatHistory] = useState([]);
    const abortControllerRef = useRef(null);
    const intervalRef = useRef(null);
    const responseBuilder = useRef('');
    const responseEndRef = useRef(null);
    const responseContainerRef = useRef(null);

    // Cleanup intervals on unmount
    useEffect(() => {
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, []);

    // Auto-scroll to bottom only if user hasn't manually scrolled up
    useEffect(() => {
        if (responseEndRef.current && responseContainerRef.current) {
            const container = responseContainerRef.current;
            const isNearBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 100;
            
            if (isNearBottom || loading) {
                responseEndRef.current.scrollIntoView({ 
                    behavior: 'auto',
                    block: 'nearest'
                });
            }
        }
    }, [response, loading]);

    const extractCalendarEvents = (text) => {
        const calendarRegex = /Calendar: (\d+) days (?:from today|a) (.+?)\.!\./g;
        const events = [];
        let match;
        
        while ((match = calendarRegex.exec(text)) !== null) {
            const days = parseInt(match[1]);
            const title = match[2].trim();
            const startDate = new Date();
            startDate.setDate(startDate.getDate() + days);
            
            events.push({
                title,
                start: startDate.toISOString().split('T')[0],
                isAllDay: true,
                eventColor: "#3b82f6", // Default blue color
                description: `Event generated from AI: ${title}`
            });
        }
        
        const hasEvents = events.length > 0;
        return events;
    };
    
    const extractPlanSteps = (text) => {
        // Check if plan exists and is not marked with X!@
        if (text.includes("Plan X!@:") || !text.includes("Plan:")) {
            return [];
        }
        
        try {
            // Extract the plan section
            const planRegex = /Plan: \[(.*?)\]([\s\S]*?)\.\.!\./;
            const planMatch = planRegex.exec(text);
            
            if (!planMatch || !planMatch[1]) return [];
            
            const planTitle = planMatch[1].trim();
            const planContent = planMatch[2].trim();
            const steps = [];
            
            // Parse each step
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
            
            // Return both title and steps
            return {
                title: planTitle,
                steps: steps
            };
        } catch (error) {
            console.error('Error parsing plan steps:', error);
            return { title: '', steps: [] };
        }
    };

    const extractResponse = (responseData) => {
        try {
            const responseText = typeof responseData === 'string' 
                ? responseData 
                : JSON.stringify(responseData, null, 2);

            const calendarEvents = extractCalendarEvents(responseText);
            if (calendarEvents.length > 0 && onCalendarEventDetected) {
                onCalendarEventDetected(calendarEvents);
            }
            
            // Extract plan data
            const planData = extractPlanSteps(responseText);
            if (planData.steps && planData.steps.length > 0 && onPlanDetected) {
                onPlanDetected(planData);
            }

            const part2Match = responseText.match(/\*\*Part 2: Response\*\*([\s\S]*?)(?:\*\*Part 3:|$)/i);
            return part2Match && part2Match[1] 
                ? cleanResponseText(part2Match[1]) 
                : cleanResponseText(responseText);

        } catch (e) {
            console.error("Response parsing error:", e);
            return "An error occurred while processing the response";
        }
    };

    const cleanResponseText = (text) => {
        return text
            .replace(/\*\*Part \d+:.*?\*\*/g, '')
            .replace(/\)\*!/g, '')
            .replace(/[*■#!→\-]/g, '')
            .replace(/\\n/g, '\n')
            .replace(/\\"/g, '"')
            .replace(/\\u[\dA-F]{4}/gi, m => 
                String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16)))
            .split('\n')
            .map(line => line.trim())
            .filter(line => line && !line.match(/^[0-9.]+/) && !line.startsWith('-'))
            .join('\n')
            .trim();
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
                // Add the AI response to chat history when streaming completes
                // but don't display it in the UI
                setChatHistory(prev => [...prev, { text, isUser: false }]);
            }
        }, 20);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (abortControllerRef.current) abortControllerRef.current.abort();
        if (!input.trim()) return;
        
        const controller = new AbortController();
        abortControllerRef.current = controller;
        setLoading(true);
        setResponse('');
        
        // Add user message to history (for context) but don't display it
        const userMessage = input.trim();
        setChatHistory(prev => [...prev, { text: userMessage, isUser: true }]);

        try {
            // Send the current message along with the chat history
            const response = await axios.post('/api/generate', 
                { 
                    prompt: userMessage,
                    history: chatHistory
                }, 
                { signal: controller.signal }
            );

            // axios automatically parses JSON, so we can directly access response.data
            const responseText = typeof response.data === 'string'
                ? response.data
                : JSON.stringify(response.data, null, 2);

            const processed = extractResponse(responseText);
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
                
                // Add error message to chat history
                setChatHistory(prev => [...prev, { 
                    text: `ERROR: ${error.message || 'Unknown error'}`, 
                    isUser: false 
                }]);
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
            setInput(''); // Clear input field after submission
        }
    };

    const handleReset = () => {
        if (abortControllerRef.current) abortControllerRef.current.abort();
        if (intervalRef.current) clearInterval(intervalRef.current);
        setInput('');
        setResponse('');
        responseBuilder.current = '';
        // Clear chat history when user resets
        setChatHistory([]);
        // Also clear plan data
        if (onPlanDetected) {
            onPlanDetected([]);
        }
    };

    return (
        <div style={{ 
            padding: '20px', 
            margin: '0 auto',
            fontFamily: 'Arial, sans-serif'
        }}>
            <form onSubmit={handleSubmit} style={{ marginBottom: '20px' }}>
                <textarea 
                    value={input} 
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="What's on your mind? I'm here to help..."
                    style={{ 
                        width: '100%',
                        padding: '12px',
                        fontSize: '16px',
                        marginBottom: '15px',
                        border: '1px solid #ddd',
                        borderRadius: '4px',
                        minHeight: '120px',
                        resize: 'vertical',
                        fontFamily: 'inherit'
                    }}
                    disabled={loading}
                />
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button 
                        type="submit" 
                        disabled={loading || !input.trim()}
                        style={{
                            padding: '12px 24px',
                            background: loading || !input.trim() ? '#6c757d' : '#007bff',
                            color: 'white',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: loading || !input.trim() ? 'not-allowed' : 'pointer',
                            fontSize: '16px',
                            transition: 'background 0.3s ease',
                            flex: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
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
                        ) : "Get Help"}
                    </button>
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
                style={{
                    padding: '20px',
                    background: '#f8f9fa',
                    borderRadius: '8px',
                    minHeight: '200px',
                    maxHeight: '60vh',
                    overflowY: 'auto',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                    border: '1px solid #dee2e6',
                    textAlign: 'right',
                    direction: 'rtl'
                }}
            >
                <div style={{ 
                    whiteSpace: 'pre-wrap',
                    lineHeight: '1.6',
                    fontSize: '16px',
                    color: '#212529',
                    fontFamily: 'monospace',
                    direction: 'ltr',
                    unicodeBidi: 'bidi-override',
                    textAlign: 'left'
                }}>
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