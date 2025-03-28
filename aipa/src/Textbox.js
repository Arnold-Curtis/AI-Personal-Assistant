import React, { useState, useRef } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);

    const extractResponse = (fullResponse) => {
        try {
            // Clean up the response first
            const cleanedResponse = fullResponse
                .replace(/\\n/g, '\n')
                .replace(/\\"/g, '"')
                .replace(/\\'/g, "'")
                .replace(/\\t/g, '    ');

            // Check if this is a structured response that needs parsing
            const isStructured = cleanedResponse.includes(')*!') || 
                                cleanedResponse.includes('Part 2:') ||
                                cleanedResponse.includes('Categories:');

            if (!isStructured) {
                // For casual responses, return as-is
                return cleanedResponse;
            }

            // Attempt to extract Part 2 if structured response
            const part2Match = cleanedResponse.match(
                /(?:Part 2: Response|\(\*Part 2\*\))([\s\S]*?)(?:Part 3:|Categories:|$)/i
            );

            if (part2Match && part2Match[1]) {
                return part2Match[1]
                    .replace(/^[\s:-]+|[\s;-]+$/g, '')
                    .trim();
            }

            // Fallback to returning the cleaned response if parsing fails
            return cleanedResponse;
        } catch (e) {
            console.error("Response processing error:", e);
            return fullResponse; // Return original if any error occurs
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

            setResponse(extractResponse(fullResponse));
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
                minHeight: '100px',
                maxHeight: '500px',
                overflowY: 'auto'
            }}>
                <strong style={{ display: 'block', marginBottom: '10px' }}>Response:</strong>
                <div style={{ whiteSpace: 'pre-wrap' }}>
                    {response || (loading ? "Waiting for response..." : "")}
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