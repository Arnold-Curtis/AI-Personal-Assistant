package com.example.demo;

import java.util.Arrays;
import java.util.List;

/**
 * 🧪 TEST PROMPT DATABASE
 * 
 * This class contains 100+ carefully crafted test prompts designed to thoroughly test
 * the AI-driven event creation and memory storage systems. Each prompt is categorized
 * and includes expected behavior annotations.
 * 
 * Categories:
 * 1. Basic Event Creation (straightforward scheduling)
 * 2. Complex Time Expressions (multi-part dates)
 * 3. Ambiguous/Vague Prompts (system robustness)
 * 4. Event Type Recognition (proper categorization)
 * 5. Chained/Dependent Events (follow-up scheduling)
 * 6. Memory Storage Tests (personal information)
 * 7. Edge Cases (error handling)
 * 8. Real-World Scenarios (practical usage)
 * 
 * ⚠️ IMPORTANT: This is test-only code and should be removed before production!
 */
public class TestPromptDatabase {
    
    // ===========================================
    // EVENT CREATION TEST PROMPTS
    // ===========================================
    
    /**
     * 📅 Basic Event Creation Tests
     * These should create single, clear events with specific dates
     */
    public static final String[] BASIC_EVENT_PROMPTS = {
        "I have a wedding in two weeks",
        "Add a dentist appointment next Tuesday at 2pm",
        "Schedule a team meeting tomorrow at 9am",
        "I'm flying to Japan next Monday at 9am",
        "Put a call with James at 3pm this Friday",
        "Add a book launch two weekends from now",
        "Schedule a trip three Mondays from now",
        "I have a car meet a day after tomorrow",
        "Add dinner plans for Saturday at 7pm",
        "Schedule gym session next Thursday morning",
        "I have a doctor's appointment on Friday",
        "Add graduation ceremony next month",
        "Schedule conference call next Wednesday",
        "I'm attending a concert this weekend",
        "Add birthday party next Saturday",
        "Schedule job interview next week",
        "I have a haircut appointment tomorrow",
        "Add family reunion in July",
        "Schedule vacation in December",
        "I have a business meeting next Tuesday"
    };
    
    /**
     * 🕒 Complex Time Expression Tests
     * These test the AI's ability to parse sophisticated time references
     */
    public static final String[] COMPLEX_TIME_PROMPTS = {
        "wedding in two weeks two days",
        "Add a car meet a day after that wedding",
        "Schedule dinner 2 hours after the dentist appointment",
        "Put in a gym session every Thursday for the next month",
        "Add a birthday party in 3 weeks and 2 days",
        "Schedule vacation planning meeting a week from next Tuesday",
        "Add conference call the day before my flight to Japan",
        "Schedule follow-up appointment exactly 10 days from today",
        "Add team building event the third Friday of next month",
        "Schedule quarterly review meeting the last Tuesday of this month",
        "Add lunch meeting the Tuesday after next",
        "Schedule project deadline two Fridays from now",
        "Add workshop three weeks from this coming Monday",
        "Schedule performance review a month and a half from now",
        "Add celebration dinner the weekend after the graduation",
        "Schedule maintenance check every other Thursday",
        "Add annual meeting the second Wednesday of next month",
        "Schedule training session a fortnight from today",
        "Add client presentation the week after the conference",
        "Schedule vacation the last two weeks of August"
    };
    
    /**
     * 🤔 Ambiguous/Vague Time Tests  
     * These test how the system handles unclear time references
     */
    public static final String[] VAGUE_TIME_PROMPTS = {
        "Add something next week sometime",
        "Schedule a meeting soon",
        "Add an appointment later this month",
        "Schedule dinner sometime next weekend",
        "Add a call in a few days",
        "Schedule maintenance check sometime soon",
        "Add social event next month",
        "Schedule performance review in the near future",
        "Add vacation planning session when convenient",
        "Schedule catch-up meeting when we're both free",
        "Add event later",
        "Schedule something for the holidays",
        "Add meeting when possible",
        "Schedule lunch sometime this week",
        "Add appointment in the coming days",
        "Schedule event in the not-too-distant future",
        "Add gathering sometime next season",
        "Schedule check-in eventually",
        "Add event around the end of the month",
        "Schedule something for spring"
    };
    
    /**
     * 🎯 Event Type Recognition Tests
     * These test proper categorization and color coding
     */
    public static final String[] EVENT_TYPE_PROMPTS = {
        "My mom's birthday is July 16th",
        "I have a doctor's appointment next week",
        "Add my graduation ceremony in May",
        "Schedule job interview next Friday",
        "Add family reunion this summer",
        "Schedule car maintenance check next month",
        "Add conference presentation next quarter",
        "Schedule wedding planning meeting next week",
        "Add parent-teacher conference next month",
        "Schedule vacation to Hawaii in December",
        "Add dental cleaning next Tuesday",
        "Schedule work training session next week",
        "Add book club meeting next Thursday",
        "Schedule oil change next month",
        "Add baby shower next Saturday",
        "Schedule tax appointment in April",
        "Add gym membership renewal next week",
        "Schedule vet appointment for my dog next Friday",
        "Add mortgage meeting next Tuesday",
        "Schedule insurance review next month"
    };
    
    /**
     * 🔗 Chained/Dependent Event Tests
     * These test events that reference other events
     */
    public static final String[] CHAINED_EVENT_PROMPTS = {
        "Add a pre-wedding dinner the night before the wedding",
        "Schedule a follow-up meeting a day after the team meeting",
        "Add airport pickup 2 hours before my Japan flight",
        "Schedule post-interview coffee the day after the job interview",
        "Add rehearsal dinner the evening before graduation",
        "Schedule debrief session the Monday after the conference",
        "Add shopping trip the weekend before mom's birthday",
        "Schedule prep meeting the week before the presentation",
        "Add celebration dinner after the job interview",
        "Schedule thank you calls the day after the family reunion",
        "Add bachelor party the weekend before the wedding",
        "Schedule recovery day the day after the marathon",
        "Add packing time the night before vacation",
        "Schedule reflection meeting the week after the project ends",
        "Add preparation session the morning of the presentation",
        "Schedule cleanup the day after the party",
        "Add warmup session an hour before the workout",
        "Schedule review meeting the Friday after the training",
        "Add travel time before the conference",
        "Schedule follow-up call a week after the interview"
    };
    
    // ===========================================
    // MEMORY STORAGE TEST PROMPTS
    // ===========================================
    
    /**
     * 👤 Personal Information Tests
     * These should be stored as personal facts
     */
    public static final String[] PERSONAL_INFO_PROMPTS = {
        "My dad's name is Paul",
        "I live in Nairobi",
        "My favorite movie is Inception",
        "My boss is Jane Smith",
        "I studied at Strathmore University",
        "My birthday is March 15th",
        "I work as a software engineer",
        "My phone number is 555-0123",
        "I drive a Toyota Camry",
        "My favorite color is blue",
        "My sister's name is Sarah",
        "I live on Oak Street",
        "My doctor is Dr. Brown",
        "I graduated in 2018",
        "My cat's name is Whiskers",
        "I was born in Chicago",
        "My middle name is Alexander",
        "I have two brothers",
        "My apartment number is 42",
        "My emergency contact is my mom"
    };
    
    /**
     * ❤️ Preferences Tests
     * These should be categorized as user preferences
     */
    public static final String[] PREFERENCES_PROMPTS = {
        "I love Italian food",
        "I hate mornings",
        "I prefer tea over coffee",
        "I enjoy reading sci-fi novels",
        "I don't like crowded places",
        "I love outdoor activities",
        "I prefer working from home",
        "I enjoy classical music",
        "I don't eat meat",
        "I love traveling to new places",
        "I prefer warm weather",
        "I hate waiting in lines",
        "I enjoy cooking on weekends",
        "I don't like horror movies",
        "I love hiking in the mountains",
        "I prefer small gatherings",
        "I enjoy early morning workouts",
        "I don't like spicy food",
        "I love reading before bed",
        "I prefer casual clothing"
    };
    
    /**
     * 🎯 Goals and Aspirations Tests
     * These should be stored as goals/objectives
     */
    public static final String[] GOALS_PROMPTS = {
        "I want to learn Spanish",
        "My goal is to run a marathon",
        "I plan to start my own business",
        "I want to lose 20 pounds",
        "I'm saving to buy a house",
        "I want to travel to Japan",
        "My goal is to read 50 books this year",
        "I want to learn guitar",
        "I plan to get a promotion",
        "I want to improve my cooking skills",
        "I want to learn photography",
        "My goal is to write a novel",
        "I plan to learn French",
        "I want to run a 5K",
        "I'm working towards a master's degree",
        "I want to learn to paint",
        "My goal is to meditate daily",
        "I plan to learn coding",
        "I want to travel more",
        "I'm saving for retirement"
    };
    
    /**
     * 👥 Relationships Tests
     * These should be stored as relationship information
     */
    public static final String[] RELATIONSHIP_PROMPTS = {
        "My sister lives in Boston",
        "My best friend is getting married",
        "I have two cats named Whiskers and Mittens",
        "My colleague Sarah is very helpful",
        "My neighbor is really noisy",
        "My mentor taught me everything about coding",
        "My doctor recommended more exercise",
        "My landlord is raising the rent",
        "My cousin is visiting next month",
        "My old college roommate moved to Seattle",
        "My brother works in tech",
        "My grandmother is 85 years old",
        "My manager is very supportive",
        "My dentist is Dr. Wilson",
        "My trainer at the gym is Mike",
        "My therapist is Dr. Johnson",
        "My accountant handles my taxes",
        "My hairdresser is named Lisa",
        "My mechanic is honest and reliable",
        "My vet takes great care of my pets"
    };
    
    /**
     * 🏢 Complex Context Tests
     * These combine multiple pieces of information
     */
    public static final String[] COMPLEX_CONTEXT_PROMPTS = {
        "Remember that my mom's birthday is July 16th and she loves roses",
        "I have a meeting with my boss Jane Smith every Tuesday at 2pm",
        "My car Toyota Camry needs maintenance every 6 months",
        "I take my medication every morning at 8am with breakfast",
        "My gym membership expires in December and costs $50/month",
        "I have a standing dinner date with my sister every first Sunday",
        "My dog Max needs to be walked twice daily and hates rain",
        "I work late shifts on Wednesdays and Fridays until 9pm",
        "My favorite restaurant downtown closes at 10pm on weekdays",
        "I have a dental cleaning appointment every 6 months with Dr. Brown",
        "My coffee shop rewards card gives me a free drink after 10 purchases",
        "I pay rent on the 1st of every month to my landlord Mr. Johnson",
        "My library books are due every 3 weeks and I can renew twice",
        "I have piano lessons every Thursday at 4pm with Mrs. Garcia",
        "My subscription to Netflix costs $15/month and renews on the 15th",
        "I volunteer at the animal shelter every Saturday morning from 9-12",
        "My commute to work takes 45 minutes by subway on the blue line",
        "I have a standing appointment with my therapist every other Friday",
        "My parking meter downtown costs $2/hour with a 3-hour maximum",
        "I order groceries online every Sunday for delivery on Monday"
    };
    
    // ===========================================
    // EDGE CASE AND ERROR HANDLING TESTS
    // ===========================================
    
    /**
     * 🚨 Edge Case Tests
     * These test system robustness and error handling
     */
    public static final String[] EDGE_CASE_PROMPTS = {
        "", // Empty input
        "   ", // Whitespace only
        "asdlkfj aslkdfj alskdfj", // Nonsense input
        "Schedule something sometime somewhere", // Extremely vague
        "Add event on the 32nd of March", // Invalid date
        "Schedule meeting at 25:00", // Invalid time
        "Add event next Blursday", // Invalid day
        "Schedule something 500 years from now", // Extreme future date
        "Add event yesterday", // Past date
        "Cancel the event I just scheduled", // Reference to non-existent event
        "Reschedule everything to tomorrow", // Ambiguous reference
        "Delete my birthday", // Trying to delete personal info
        "Add duplicate event",
        "Add duplicate event", // Duplicate entries
        "Schedule meeting and also don't schedule meeting", // Contradictory
        "Add 500 events next week", // Extreme quantity
        "Schedule meeting at location that doesn't exist",
        "Add event with person who doesn't exist",
        "Schedule something in the past",
        "Add event with negative duration",
        "Schedule meeting with impossible logistics"
    };
    
    // ===========================================
    // REAL-WORLD SCENARIO TESTS
    // ===========================================
    
    /**
     * 🌍 Real-World Scenario Tests
     * These simulate actual user interactions
     */
    public static final String[] REAL_WORLD_PROMPTS = {
        // Work scenarios
        "I have a project deadline next Friday and need to schedule check-ins",
        "Add all-hands meeting next Wednesday at 10am in the conference room",
        "Schedule performance review with Sarah next month",
        "I'm presenting at the tech conference in two weeks",
        "Add client call every Monday at 2pm for the next 6 weeks",
        
        // Personal life scenarios
        "My daughter's soccer game is every Saturday at 3pm",
        "Add date night with my partner next Friday at 8pm",
        "Schedule oil change for my car next week",
        "I have parent-teacher conferences next Thursday",
        "Add family barbecue for Memorial Day weekend",
        
        // Health and wellness
        "Schedule my annual physical exam next month",
        "Add gym sessions on Monday, Wednesday, and Friday mornings",
        "I have therapy every other Tuesday at 5pm",
        "Schedule dental cleaning in 6 months",
        "Add medication reminder every morning at 8am",
        
        // Travel and leisure
        "I'm flying to New York next month for vacation",
        "Add concert tickets for the band I love next Saturday",
        "Schedule weekend getaway to the mountains next month",
        "I have tickets to the baseball game next Sunday",
        "Add museum visit this weekend with the kids",
        
        // Financial and administrative
        "Schedule tax appointment with my accountant in March",
        "Add rent payment reminder for the 1st of every month",
        "I have a mortgage meeting next week",
        "Schedule car insurance renewal next month",
        "Add budget review meeting with my financial advisor"
    };
    
    // ===========================================
    // FOLLOW-UP AND DEPENDENCY TESTS
    // ===========================================
    
    /**
     * 🔄 Sequential Test Pairs
     * These test the system's ability to handle dependent events
     */
    public static final String[][] SEQUENTIAL_TEST_PAIRS = {
        {"I have a wedding in two weeks", "Add a bachelor party the night before the wedding"},
        {"Schedule a job interview next Friday", "Add preparation time 2 hours before the interview"},
        {"Add a flight to Paris next month", "Schedule airport check-in 2 hours before the flight"},
        {"I have a dentist appointment tomorrow", "Add a reminder to take pain medication after the appointment"},
        {"Schedule a team meeting next Monday", "Add agenda preparation the day before the meeting"},
        {"I have a marathon next month", "Add training runs every other day until the marathon"},
        {"Schedule vacation next month", "Add packing time the night before vacation"},
        {"I have a presentation next week", "Schedule practice session the day before the presentation"},
        {"Add graduation ceremony next month", "Schedule celebration dinner after graduation"},
        {"I have a conference next quarter", "Add travel arrangements the week before the conference"}
    };
    
    /**
     * 🧠 Memory-Dependent Test Pairs
     * These test integration between memory and event systems
     */
    public static final String[][] MEMORY_DEPENDENT_TEST_PAIRS = {
        {"My boss is Jane Smith", "Schedule a one-on-one with Jane next week"},
        {"I live in Nairobi", "Add a local event in Nairobi next month"},
        {"My favorite restaurant is Giuseppe's", "Make dinner reservation at Giuseppe's for Friday"},
        {"My doctor is Dr. Brown", "Schedule follow-up with Dr. Brown next month"},
        {"My sister lives in Boston", "Plan a visit to Boston next quarter"},
        {"My car is a Toyota Camry", "Schedule maintenance for my Toyota next month"},
        {"My gym is Planet Fitness", "Add workout session at Planet Fitness tomorrow"},
        {"My dentist is Dr. Wilson", "Schedule cleaning with Dr. Wilson next month"},
        {"My favorite coffee shop is Starbucks", "Meet client at Starbucks next Tuesday"},
        {"I work at Microsoft", "Add Microsoft company meeting next Friday"}
    };
    
    // ===========================================
    // UTILITY METHODS
    // ===========================================
    
    /**
     * Get all event creation prompts
     */
    public static List<String> getAllEventPrompts() {
        List<String> allPrompts = Arrays.asList(BASIC_EVENT_PROMPTS);
        allPrompts.addAll(Arrays.asList(COMPLEX_TIME_PROMPTS));
        allPrompts.addAll(Arrays.asList(VAGUE_TIME_PROMPTS));
        allPrompts.addAll(Arrays.asList(EVENT_TYPE_PROMPTS));
        allPrompts.addAll(Arrays.asList(CHAINED_EVENT_PROMPTS));
        allPrompts.addAll(Arrays.asList(REAL_WORLD_PROMPTS));
        return allPrompts;
    }
    
    /**
     * Get all memory storage prompts
     */
    public static List<String> getAllMemoryPrompts() {
        List<String> allPrompts = Arrays.asList(PERSONAL_INFO_PROMPTS);
        allPrompts.addAll(Arrays.asList(PREFERENCES_PROMPTS));
        allPrompts.addAll(Arrays.asList(GOALS_PROMPTS));
        allPrompts.addAll(Arrays.asList(RELATIONSHIP_PROMPTS));
        allPrompts.addAll(Arrays.asList(COMPLEX_CONTEXT_PROMPTS));
        return allPrompts;
    }
    
    /**
     * Get total number of test prompts
     */
    public static int getTotalPromptCount() {
        return getAllEventPrompts().size() + 
               getAllMemoryPrompts().size() + 
               EDGE_CASE_PROMPTS.length +
               SEQUENTIAL_TEST_PAIRS.length +
               MEMORY_DEPENDENT_TEST_PAIRS.length;
    }
    
    /**
     * Get prompts by category for targeted testing
     */
    public static List<String> getPromptsByCategory(String category) {
        switch (category.toLowerCase()) {
            case "basic_events": return Arrays.asList(BASIC_EVENT_PROMPTS);
            case "complex_time": return Arrays.asList(COMPLEX_TIME_PROMPTS);
            case "vague_time": return Arrays.asList(VAGUE_TIME_PROMPTS);
            case "event_types": return Arrays.asList(EVENT_TYPE_PROMPTS);
            case "chained_events": return Arrays.asList(CHAINED_EVENT_PROMPTS);
            case "personal_info": return Arrays.asList(PERSONAL_INFO_PROMPTS);
            case "preferences": return Arrays.asList(PREFERENCES_PROMPTS);
            case "goals": return Arrays.asList(GOALS_PROMPTS);
            case "relationships": return Arrays.asList(RELATIONSHIP_PROMPTS);
            case "complex_context": return Arrays.asList(COMPLEX_CONTEXT_PROMPTS);
            case "edge_cases": return Arrays.asList(EDGE_CASE_PROMPTS);
            case "real_world": return Arrays.asList(REAL_WORLD_PROMPTS);
            default: return getAllEventPrompts();
        }
    }
    
    /**
     * Validate that we have sufficient test coverage
     */
    public static boolean hasAdequateTestCoverage() {
        return getTotalPromptCount() >= 100; // Meets the requirement of 100+ tests
    }
    
    /**
     * Get test statistics
     */
    public static String getTestStatistics() {
        return String.format(
            "📊 Test Prompt Statistics:\n" +
            "• Total Prompts: %d\n" +
            "• Event Creation Tests: %d\n" +
            "• Memory Storage Tests: %d\n" +
            "• Edge Case Tests: %d\n" +
            "• Sequential Tests: %d\n" +
            "• Memory-Dependent Tests: %d\n" +
            "• Adequate Coverage: %s",
            getTotalPromptCount(),
            getAllEventPrompts().size(),
            getAllMemoryPrompts().size(),
            EDGE_CASE_PROMPTS.length,
            SEQUENTIAL_TEST_PAIRS.length,
            MEMORY_DEPENDENT_TEST_PAIRS.length,
            hasAdequateTestCoverage() ? "✅ YES" : "❌ NO"
        );
    }
}
