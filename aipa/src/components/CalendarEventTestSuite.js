import React, { useState } from 'react';
import { toast } from 'react-toastify';

const CalendarEventTestSuite = () => {
    const [testResults, setTestResults] = useState([]);
    const [isRunning, setIsRunning] = useState(false);

    const testCases = [
        {
            name: "Wedding in 2 weeks",
            input: "I have a wedding in 2 weeks",
            expectedEvents: 1,
            expectedDays: 14,
            expectedTitle: "Wedding"
        },
        {
            name: "Car meet next Friday",
            input: "Hey man I have a car meet next Friday",
            expectedEvents: 1,
            expectedDays: 5, 
            expectedTitle: "Car Meet"
        },
        {
            name: "Birthday tomorrow",
            input: "My birthday is tomorrow",
            expectedEvents: 1,
            expectedDays: 1,
            expectedTitle: "Birthday"
        },
        {
            name: "Multiple events",
            input: "I have a meeting tomorrow and a wedding in 3 weeks",
            expectedEvents: 2,
            expectedDays: [1, 21],
            expectedTitle: ["Meeting", "Wedding"]
        },
        {
            name: "Specific date",
            input: "My birthday is on March 23",
            expectedEvents: 1,
            expectedTitle: "Birthday"
        },
        {
            name: "No events",
            input: "What is 1 + 1?",
            expectedEvents: 0
        }
    ];

    const runTests = async () => {
        setIsRunning(true);
        setTestResults([]);
        
        for (const testCase of testCases) {
            try {
                console.log(`🧪 Running test: ${testCase.name}`);
                
                const response = await fetch('/api/generate', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${localStorage.getItem('authToken')}`
                    },
                    body: JSON.stringify({
                        prompt: testCase.input,
                        history: []
                    })
                });

                const responseData = await response.text();
                console.log(`📝 Response for "${testCase.name}":`, responseData);
                
                
                const events = extractCalendarEventsFromResponse(responseData);
                
                const result = {
                    name: testCase.name,
                    input: testCase.input,
                    response: responseData,
                    extractedEvents: events,
                    passed: validateTestResult(testCase, events),
                    details: generateTestDetails(testCase, events)
                };
                
                setTestResults(prev => [...prev, result]);
                console.log(`✅ Test "${testCase.name}" completed:`, result);
                
                
                await new Promise(resolve => setTimeout(resolve, 1000));
                
            } catch (error) {
                console.error(`❌ Test "${testCase.name}" failed:`, error);
                setTestResults(prev => [...prev, {
                    name: testCase.name,
                    input: testCase.input,
                    error: error.message,
                    passed: false
                }]);
            }
        }
        
        setIsRunning(false);
        toast.success('Calendar event tests completed!');
    };

    const extractCalendarEventsFromResponse = (responseText) => {
        const events = [];
        const cleanText = responseText.replace(/\)\*!/g, '').replace(/\.!\.\./g, '');
        
        
        const categoriesRegex = /\*\*Part 3: Categories\*\*([\s\S]*?)(?:\*\*|$)/i;
        const categoriesMatch = categoriesRegex.exec(cleanText);
        
        if (categoriesMatch) {
            const categoriesText = categoriesMatch[1];
            const calendarRegex = /Calendar:\s*(\d+)\s*days?\s*from\s*today\s+([^\n\r.!]+)/gi;
            let match;
            
            while ((match = calendarRegex.exec(categoriesText)) !== null) {
                const days = parseInt(match[1]);
                let title = match[2].trim();
                
                title = title
                    .replace(/\.!\.\./g, '')
                    .replace(/\)\*!/g, '')
                    .replace(/Plan X!@:.*$/i, '')
                    .replace(/[.!]*$/, '')
                    .trim();
                
                if (title && title.length > 0 && !title.includes('Part ') && !title.includes('X!@')) {
                    events.push({ title, days });
                }
            }
        }
        
        return events;
    };

    const validateTestResult = (testCase, extractedEvents) => {
        if (testCase.expectedEvents !== extractedEvents.length) {
            return false;
        }
        
        if (testCase.expectedEvents === 0) {
            return true; 
        }
        
        
        if (testCase.expectedEvents === 1) {
            const event = extractedEvents[0];
            if (!event) return false;
            
            const titleMatch = testCase.expectedTitle && 
                event.title.toLowerCase().includes(testCase.expectedTitle.toLowerCase());
            
            const daysMatch = !testCase.expectedDays || 
                Math.abs(event.days - testCase.expectedDays) <= 1; 
            
            return titleMatch && daysMatch;
        }
        
        
        if (Array.isArray(testCase.expectedTitle)) {
            return testCase.expectedTitle.every(expectedTitle => 
                extractedEvents.some(event => 
                    event.title.toLowerCase().includes(expectedTitle.toLowerCase())
                )
            );
        }
        
        return true;
    };

    const generateTestDetails = (testCase, extractedEvents) => {
        return {
            expected: {
                count: testCase.expectedEvents,
                titles: testCase.expectedTitle,
                days: testCase.expectedDays
            },
            actual: {
                count: extractedEvents.length,
                events: extractedEvents
            }
        };
    };

    return (
        <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
            <h2>🧪 Calendar Event Test Suite</h2>
            
            <button 
                onClick={runTests}
                disabled={isRunning}
                style={{
                    padding: '10px 20px',
                    backgroundColor: isRunning ? '#ccc' : '#007bff',
                    color: 'white',
                    border: 'none',
                    borderRadius: '5px',
                    cursor: isRunning ? 'not-allowed' : 'pointer',
                    marginBottom: '20px'
                }}
            >
                {isRunning ? 'Running Tests...' : 'Run Calendar Tests'}
            </button>

            {testResults.length > 0 && (
                <div>
                    <h3>Test Results ({testResults.filter(r => r.passed).length}/{testResults.length} passed)</h3>
                    
                    {testResults.map((result, index) => (
                        <div 
                            key={index}
                            style={{
                                border: `2px solid ${result.passed ? '#28a745' : '#dc3545'}`,
                                borderRadius: '5px',
                                padding: '15px',
                                marginBottom: '15px',
                                backgroundColor: result.passed ? '#f8fff8' : '#fff8f8'
                            }}
                        >
                            <h4 style={{ margin: '0 0 10px 0' }}>
                                {result.passed ? '✅' : '❌'} {result.name}
                            </h4>
                            
                            <p><strong>Input:</strong> {result.input}</p>
                            
                            {result.error ? (
                                <p style={{ color: '#dc3545' }}><strong>Error:</strong> {result.error}</p>
                            ) : (
                                <>
                                    <p><strong>Expected:</strong> {JSON.stringify(result.details?.expected, null, 2)}</p>
                                    <p><strong>Actual:</strong> {JSON.stringify(result.details?.actual, null, 2)}</p>
                                    
                                    <details style={{ marginTop: '10px' }}>
                                        <summary style={{ cursor: 'pointer' }}>Raw Response</summary>
                                        <pre style={{ 
                                            backgroundColor: '#f8f9fa', 
                                            padding: '10px', 
                                            borderRadius: '3px',
                                            overflow: 'auto',
                                            fontSize: '12px'
                                        }}>
                                            {result.response?.substring(0, 1000)}...
                                        </pre>
                                    </details>
                                </>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default CalendarEventTestSuite;

