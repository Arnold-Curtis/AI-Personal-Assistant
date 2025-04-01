import React, { useState, useRef } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);

    const extractResponse = (responseData) => {
        try {
            const responseText = typeof responseData === 'string' 
                ? responseData 
                : JSON.stringify(responseData, null, 2);

            // Phase 1: Try JSON parsing first
            try {
                const jsonResponse = JSON.parse(responseText);
                if (jsonResponse.error) return `ERROR: ${jsonResponse.error}`;
                if (jsonResponse.text) return jsonResponse.text;
                if (jsonResponse.candidates?.[0]?.content?.parts) {
                    return jsonResponse.candidates[0].content.parts[0].text;
                }
                return JSON.stringify(jsonResponse, null, 2);
            } catch (e) {
                // Not JSON, proceed to text processing
            }

            // Phase 2: Precise extraction between )*! markers
            const sections = responseText.split(/\s*\)\*!\s*/);
            if (sections.length >= 4) {
                // The actual response is between the second and third )*! markers
                const rawResponse = sections[2];
                return cleanResponseText(rawResponse);
            }

            // Phase 3: Fallback to header-based extraction
            const part2Regex = /(\*\*Part 2: Response\*\*)([\s\S]*?)(?=\*\*Part 3:|\)\*!)/i;
            const part2Match = responseText.match(part2Regex);
            if (part2Match && part2Match[2]) {
                return cleanResponseText(part2Match[2]);
            }

            // Final fallback: Clean entire response
            return cleanResponseText(responseText);

        } catch (e) {
            console.error("Response parsing error:", e);
            return "An error occurred while processing the response";
        }
    };

    const cleanResponseText = (text) => {
        return text
            // Remove all headers and artifacts
            .replace(/(\*\*Part \d+:.*|\*!|\)!|Calendar:.*|Plan:.*|Step \d+:.*|Analysis:.*)/gi, '')
            // Clean up formatting characters
            .replace(/[\*■#!\-→]/g, '')
            // Handle escaped characters
            .replace(/\\n/g, '\n')
            .replace(/\\"/g, '"')
            .replace(/\\u[\dA-F]{4}/gi, m => 
                String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16))
            )
            // Normalize whitespace and remove unwanted lines
            .split('\n')
            .map(line => line.trim())
            .filter(line => line && !line.match(/^[0-9\.]+/) && !line.startsWith('-'))
            .join('\n')
            .trim();
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }
        
        const controller = new AbortController();
        abortControllerRef.current = controller;
        setLoading(true);
        setResponse('');

        try {
            const response = await fetch('http://localhost:8080/api/generate', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ prompt: input }),
                signal: controller.signal
            });

            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(
                    responseText.startsWith('{') 
                        ? JSON.parse(responseText).error 
                        : `Server error: ${response.status}`
                );
            }

            setResponse(extractResponse(responseText));

        } catch (error) {
            if (error.name === 'AbortError') {
                setResponse("Request cancelled by user");
            } else {
                console.error('API Error:', error);
                setResponse(`ERROR: ${error.message}`);
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
        }
    };

    return (
        <div style={{ 
            padding: '20px', 
            maxWidth: '800px', 
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
                        disabled={loading}
                        style={{
                            padding: '12px 24px',
                            background: loading ? '#6c757d' : '#007bff',
                            color: 'white',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
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
                        onClick={() => {
                            if (abortControllerRef.current) {
                                abortControllerRef.current.abort();
                            }
                            setInput('');
                            setResponse('');
                        }}
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
            
            <div style={{
                padding: '20px',
                background: '#f8f9fa',
                borderRadius: '8px',
                minHeight: '200px',
                maxHeight: '60vh',
                overflowY: 'auto',
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                border: '1px solid #dee2e6'
            }}>
                <div style={{ 
                    whiteSpace: 'pre-wrap',
                    lineHeight: '1.6',
                    fontSize: '16px',
                    color: '#212529',
                    fontFamily: 'monospace'
                }}>
                    {response || (loading ? "Analyzing your request and crafting response..." : "Your response will appear here...")}
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