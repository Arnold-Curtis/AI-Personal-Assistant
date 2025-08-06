package com.example.demo;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "email")
       })
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    @NotBlank
    @Size(max = 120)
    private String password;

    @Size(max = 100)
    private String fullName;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "memory_password")
    private String memoryPassword;

    @Column(name = "account_created", updatable = false)
    private LocalDateTime accountCreated;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CalendarEvent> events = new ArrayList<>();

    public enum AuthProvider {
        LOCAL, GOOGLE, GITHUB
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    public User() {
        this.accountCreated = LocalDateTime.now();
        this.verificationToken = UUID.randomUUID().toString();
    }

    public User(String email, String password) {
        this.email = email;
        this.password = password;
        this.accountCreated = LocalDateTime.now();
        this.verificationToken = UUID.randomUUID().toString();
    }

    
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public LocalDateTime getAccountCreated() {
        return accountCreated;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    public List<CalendarEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CalendarEvent> events) {
        this.events = events;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider;
    }

    public String getMemoryPassword() {
        return memoryPassword;
    }

    public void setMemoryPassword(String memoryPassword) {
        this.memoryPassword = memoryPassword;
    }

    
    public void addEvent(CalendarEvent event) {
        events.add(event);
        event.setUser(this);
    }

    public void removeEvent(CalendarEvent event) {
        events.remove(event);
        event.setUser(null);
    }

    @PrePersist
    protected void onCreate() {
        this.accountCreated = LocalDateTime.now();
        if (this.verificationToken == null) {
            this.verificationToken = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.verificationToken == null) {
            this.verificationToken = UUID.randomUUID().toString();
        }
    }

    
    public String getProfileImageUrl() {
        if (this.profileImage != null && !this.profileImage.isEmpty()) {
            return "/uploads/" + this.profileImage;
        }
        return "https:
               (this.fullName != null ? 
                   this.fullName.substring(0, 1) : 
                   this.email.substring(0, 1)) + 
               "&background=3b82f6&color=fff";
    }
}
