# Testing the Calendar Event Robustness Fix

## Quick Test Commands

Once the application is running, you can test the new routing system using these curl commands:

### 1. Test Sample Routing Decisions

```bash
curl -X POST http://localhost:8080/api/debug/test-sample-inputs \
  -H "Content-Type: application/json"
```

This will test all the sample inputs from the requirements and show routing decisions.

### 2. Test Individual Routing

```bash
# Test calendar event routing
curl -X POST http://localhost:8080/api/debug/test-routing \
  -H "Content-Type: application/json" \
  -d '{"input": "I have a wedding in 2 weeks"}'

# Test memory routing  
curl -X POST http://localhost:8080/api/debug/test-routing \
  -H "Content-Type: application/json" \
  -d '{"input": "My favorite artist is Burna Boy"}'

# Test question filtering
curl -X POST http://localhost:8080/api/debug/test-routing \
  -H "Content-Type: application/json" \
  -d '{"input": "When is my birthday?"}'
```

### 3. Test Calendar Event Creation (requires authenticated user)

```bash
# You'll need to replace "test@example.com" with an actual user email from your database
curl -X POST http://localhost:8080/api/debug/test-calendar-creation \
  -H "Content-Type: application/json" \
  -d '{"input": "I have a book reading in 2 weeks 2 days", "userEmail": "test@example.com"}'
```

## Expected Results

### Sample Inputs Test Results

The `/api/debug/test-sample-inputs` endpoint should return something like:

```json
{
  "I have a book reading event in 2 weeks 2 days.": {
    "destination": "CALENDAR_ONLY",
    "reasoning": "Strong temporal context detected with event indicators",
    "confidence": 0.8
  },
  "My girlfriend's birthday is November 3rd.": {
    "destination": "CALENDAR_ONLY", 
    "reasoning": "Clear calendar event with temporal context",
    "confidence": 0.7
  },
  "My girlfriend's name is Alisha.": {
    "destination": "MEMORY_ONLY",
    "reasoning": "Personal information or preferences without scheduling",
    "confidence": 0.6
  },
  "I'm going to Mombasa this Saturday.": {
    "destination": "CALENDAR_ONLY",
    "reasoning": "Clear calendar event with temporal context", 
    "confidence": 0.8
  },
  "My favorite artist is Burna Boy.": {
    "destination": "MEMORY_ONLY",
    "reasoning": "Personal information or preferences without scheduling",
    "confidence": 0.6
  },
  "When is my birthday?": {
    "destination": "NEITHER",
    "reasoning": "User input is a question asking for information",
    "confidence": 0.0
  }
}
```

## What This Demonstrates

1. **Mutual Exclusivity**: Each input goes to either CALENDAR_ONLY, MEMORY_ONLY, or NEITHER - never both
2. **Smart Routing**: Temporal context (dates, timeframes) routes to calendar
3. **Personal Info Detection**: Names, preferences, facts route to memory  
4. **Question Filtering**: Questions are filtered out completely
5. **High Confidence**: The system provides confidence scores for its decisions

## Monitoring Logs

Watch the application logs for detailed routing information:

```bash
tail -f application.log
```

You'll see logs like:
- `🎯 Routing Decision: CALENDAR_ONLY with confidence 0.8`
- `📅 Created 1 calendar events directly`
- `🚫 Memory processing skipped: routed_to_calendar`

This confirms the mutual exclusivity is working correctly.
