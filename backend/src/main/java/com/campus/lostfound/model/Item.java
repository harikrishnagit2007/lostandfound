package com.campus.lostfound.model;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Plain Java model persisted through JdbcTemplate (JDBC), not JPA. */
public class Item {
    private Long id;
    private ItemType type = ItemType.LOST;
    private ItemStatus status = ItemStatus.OPEN;
    private String title;
    private String description;
    private String category;
    private String location;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate eventDate = LocalDate.now();
    private String contactName;
    private String contactInfo;
    private String manageCode;
    private String imagePath;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }
    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
    public String getManageCode() { return manageCode; }
    public void setManageCode(String manageCode) { this.manageCode = manageCode; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
