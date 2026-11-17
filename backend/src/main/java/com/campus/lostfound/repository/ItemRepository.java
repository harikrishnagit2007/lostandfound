package com.campus.lostfound.repository;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.ItemType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class ItemRepository {
    private final JdbcTemplate jdbc;

    public ItemRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String COLUMNS = "id, type, status, title, description, category, location, event_date, contact_name, contact_info, manage_code, image_path, created_at";

    private Item map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Item i = new Item();
        i.setId(rs.getLong("id"));
        i.setType(ItemType.valueOf(rs.getString("type")));
        i.setStatus(ItemStatus.valueOf(rs.getString("status")));
        i.setTitle(rs.getString("title"));
        i.setDescription(rs.getString("description"));
        i.setCategory(rs.getString("category"));
        i.setLocation(rs.getString("location"));
        Date eventDate = rs.getDate("event_date");
        i.setEventDate(eventDate == null ? null : eventDate.toLocalDate());
        i.setContactName(rs.getString("contact_name"));
        i.setContactInfo(rs.getString("contact_info"));
        i.setManageCode(rs.getString("manage_code"));
        i.setImagePath(rs.getString("image_path"));
        Timestamp created = rs.getTimestamp("created_at");
        i.setCreatedAt(created == null ? null : created.toLocalDateTime());
        return i;
    }

    public List<Item> findAllByOrderByCreatedAtDesc() {
        return jdbc.query("SELECT " + COLUMNS + " FROM items ORDER BY created_at DESC", this::map);
    }

    public Optional<Item> findById(Long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM items WHERE id = ?", this::map, id).stream().findFirst();
    }

    public long countByTypeAndStatus(ItemType type, ItemStatus status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE type = ? AND status = ?", Long.class, type.name(), status.name());
        return n == null ? 0 : n;
    }

    public long countByStatus(ItemStatus status) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE status = ?", Long.class, status.name());
        return n == null ? 0 : n;
    }

    public Item save(Item item) {
        if (item.getCreatedAt() == null) item.setCreatedAt(java.time.LocalDateTime.now());
        if (item.getId() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO items (type,status,title,description,category,location,event_date,contact_name,contact_info,manage_code,image_path,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
                bind(ps, item, false);
                return ps;
            }, keys);
            Number key = keys.getKey();
            if (key != null) item.setId(key.longValue());
        } else {
            jdbc.update("UPDATE items SET type=?, status=?, title=?, description=?, category=?, location=?, event_date=?, contact_name=?, contact_info=?, manage_code=?, image_path=?, created_at=? WHERE id=?",
                item.getType().name(), item.getStatus().name(), item.getTitle(), item.getDescription(), item.getCategory(), item.getLocation(),
                item.getEventDate() == null ? null : Date.valueOf(item.getEventDate()), item.getContactName(), item.getContactInfo(), item.getManageCode(), item.getImagePath(), Timestamp.valueOf(item.getCreatedAt()), item.getId());
        }
        return item;
    }

    private void bind(PreparedStatement ps, Item i, boolean includeId) throws java.sql.SQLException {
        ps.setString(1, i.getType().name()); ps.setString(2, i.getStatus().name()); ps.setString(3, i.getTitle());
        ps.setString(4, i.getDescription()); ps.setString(5, i.getCategory()); ps.setString(6, i.getLocation());
        if (i.getEventDate() == null) ps.setDate(7, null); else ps.setDate(7, Date.valueOf(i.getEventDate()));
        ps.setString(8, i.getContactName()); ps.setString(9, i.getContactInfo()); ps.setString(10, i.getManageCode());
        ps.setString(11, i.getImagePath()); ps.setTimestamp(12, Timestamp.valueOf(i.getCreatedAt()));
    }

    public void delete(Item item) { jdbc.update("DELETE FROM items WHERE id = ?", item.getId()); }
}
