package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "calendar_events")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate start;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_all_day")
    private Boolean isAllDay = true;

    @Column(name = "event_color")
    private String eventColor;
    
    @Column(name = "plan_title")
    private String planTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore  // Add this annotation to break the circular reference
    private User user;

    // Constructors
    public CalendarEvent() {
    }

    public CalendarEvent(String title, LocalDate start) {
        this.title = title;
        this.start = start;
    }

    public CalendarEvent(String title, LocalDate start, User user) {
        this.title = title;
        this.start = start;
        this.user = user;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getStart() {
        return start;
    }

    public void setStart(LocalDate start) {
        this.start = start;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getAllDay() {
        return isAllDay;
    }

    public void setAllDay(Boolean allDay) {
        isAllDay = allDay;
    }

    public String getEventColor() {
        return eventColor;
    }

    public void setEventColor(String eventColor) {
        this.eventColor = eventColor;
    }

    public String getPlanTitle() {
        return planTitle;
    }

    public void setPlanTitle(String planTitle) {
        this.planTitle = planTitle;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Updates this event's properties from another event
     * @param updatedEvent The event containing updated properties
     */
    public void updateFrom(CalendarEvent updatedEvent) {
        if (updatedEvent.getTitle() != null) {
            this.title = updatedEvent.getTitle();
        }
        if (updatedEvent.getStart() != null) {
            this.start = updatedEvent.getStart();
        }
        if (updatedEvent.getDescription() != null) {
            this.description = updatedEvent.getDescription();
        }
        if (updatedEvent.getAllDay() != null) {
            this.isAllDay = updatedEvent.getAllDay();
        }
        if (updatedEvent.getEventColor() != null) {
            this.eventColor = updatedEvent.getEventColor();
        }
        if (updatedEvent.getPlanTitle() != null) {
            this.planTitle = updatedEvent.getPlanTitle();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarEvent that = (CalendarEvent) o;
        return Objects.equals(id, that.id) ||
               (Objects.equals(title, that.title) && 
                Objects.equals(start, that.start) && 
                Objects.equals(user, that.user));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, start, user);
    }

    @Override
    public String toString() {
        return "CalendarEvent{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", start=" + start +
                ", description='" + description + '\'' +
                ", isAllDay=" + isAllDay +
                ", eventColor='" + eventColor + '\'' +
                ", planTitle='" + planTitle + '\'' +
                '}';
    }
}