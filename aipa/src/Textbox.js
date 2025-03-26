import React, { useState, useRef } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);

    const extractPart2 = (fullResponse) => {
        try {
            // Handle both raw and escaped JSON responses
            const cleanedResponse = fullResponse
                .replace(/\\n/g, '\n')
                .replace(/\\"/g, '"');

            // Flexible parsing for different response formats
            const part2Pattern = /(?:Part 2|\(Part 2\))[\s\S]*?Response([\s\S]*?)(?:Part 3|\(Part 3\))/i;
            const match = cleanedResponse.match(part2Pattern);
            
            if (match && match[1]) {
                return match[1]
                    .replace(/^[\s:]+|[\s;]+$/g, '')
                    .trim();
            }
            
            // Fallback: Try to find natural language response
            const naturalLanguageMatch = cleanedResponse.match(/"response":"([\s\S]*?)"/);
            if (naturalLanguageMatch) {
                return naturalLanguageMatch[1];
            }
            
            return "Could not parse response. Showing raw:\n" + cleanedResponse.substring(0, 300);
        } catch (e) {
            console.error("Parsing error:", e);
            return "Error processing response";
        }
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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: input }),
                signal: controller.signal
            });

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let fullResponse = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                fullResponse += decoder.decode(value, { stream: true });
            }

            setResponse(extractPart2(fullResponse));
        } catch (error) {
            if (error.name === 'AbortError') {
                setResponse("Request cancelled");
            } else {
                console.error('API Error:', error);
                setResponse("Error: " + error.message);
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
        }
    };

    return (
        <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
            <form onSubmit={handleSubmit} style={{ marginBottom: '20px' }}>
                <input 
                    type="text" 
                    value={input} 
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="What's up? Tell me Anything. I promise to try and help. I'm Here for you"
                    style={{ 
                        width: '100%',
                        padding: '10px',
                        fontSize: '16px',
                        marginBottom: '10px'
                    }}
                />
                <button 
                    type="submit" 
                    disabled={loading}
                    style={{
                        padding: '10px 20px',
                        background: loading ? '#ccc' : '#007bff',
                        color: 'white',
                        border: 'none',
                        borderRadius: '4px',
                        cursor: 'pointer'
                    }}
                >
                    {loading ? (
                        <>
                            <span 
                                style={{
                                    display: 'inline-block',
                                    width: '16px',
                                    height: '16px',
                                    border: '2px solid rgba(255,255,255,0.3)',
                                    borderRadius: '50%',
                                    borderTopColor: 'white',
                                    animation: 'spin 1s linear infinite',
                                    marginRight: '8px'
                                }}
                            />
                            Processing...
                        </>
                    ) : "Submit"}
                </button>
            </form>
            <div style={{
                padding: '15px',
                background: '#f8f9fa',
                borderRadius: '4px',
                minHeight: '100px'
            }}>
                <strong style={{ display: 'block', marginBottom: '10px' }}>Response:</strong>
                <div style={{ whiteSpace: 'pre-wrap' }}>
                    {response || (loading ? "Waiting for response..." : "")}
                </div>
            </div>
        </div>
    );
};