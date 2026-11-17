package com.campus.lostfound.api;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.ItemType;
import com.campus.lostfound.repository.ItemRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ItemApiController {
    private static final List<String> CATEGORIES = List.of("Electronics", "ID & Cards", "Books & Notes", "Keys", "Bags", "Wallets & Money", "Clothing", "Water Bottles", "Jewellery", "Other");
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private final ItemRepository repo;

    @Value("${lostfound.upload-dir:uploads}") private String uploadDir;
    @Value("${server.port:8080}") private String serverPort;

    public ItemApiController(ItemRepository repo) { this.repo = repo; }

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("status", "ok", "service", "CampusFind JDBC API"); }

    @GetMapping("/items")
    public List<Map<String, Object>> list(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false, name = "q") String query,
                                          @RequestParam(defaultValue = "false") boolean showResolved) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        ItemType t = parseType(type);
        return repo.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> showResolved || i.getStatus() == ItemStatus.OPEN)
                .filter(i -> t == null || i.getType() == t)
                .filter(i -> category == null || category.isBlank() || category.equals(i.getCategory()))
                .filter(i -> q.isEmpty() || has(i.getTitle(), q) || has(i.getDescription(), q) || has(i.getLocation(), q))
                .map(this::publicItem).toList();
    }

    @GetMapping("/items/{id}")
    public Map<String, Object> get(@PathVariable Long id) { return publicItem(find(id)); }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        long lost = repo.countByTypeAndStatus(ItemType.LOST, ItemStatus.OPEN);
        long found = repo.countByTypeAndStatus(ItemType.FOUND, ItemStatus.OPEN);
        long resolved = repo.countByStatus(ItemStatus.RESOLVED);
        return Map.of("totalPosts", lost + found + resolved, "lostCount", lost, "foundCount", found, "resolvedCount", resolved);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody CreateItemRequest body) {
        Item item = new Item();
        item.setType(parseType(body.type));
        item.setStatus(ItemStatus.OPEN);
        item.setTitle(trim(body.title));
        item.setDescription(trim(body.description));
        item.setCategory(trim(body.category));
        item.setLocation(trim(body.location));
        item.setEventDate(body.eventDate == null || body.eventDate.isBlank() ? null : parseDate(body.eventDate));
        item.setContactName(trim(body.contactName));
        item.setContactInfo(trim(body.contactInfo));
        item.setManageCode(body.manageCode == null ? "" : body.manageCode.trim());
        validate(item);
        return publicItem(repo.save(item));
    }

    @PutMapping("/items/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable Long id, @RequestBody CodeRequest body) {
        Item item = find(id);
        verifyCode(item, body.code);
        item.setStatus(ItemStatus.RESOLVED);
        return publicItem(repo.save(item));
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestBody CodeRequest body) {
        Item item = find(id);
        verifyCode(item, body.code);
        deleteImage(item.getImagePath());
        repo.delete(item);
    }

    @PostMapping(value = "/items/{id}/photo", consumes = "multipart/form-data")
    public Map<String, Object> uploadPhoto(@PathVariable Long id, @RequestParam("image") MultipartFile image) throws IOException {
        Item item = find(id);
        if (image == null || image.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image.");
        String original = image.getOriginalFilename() == null ? "" : image.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!IMAGE_EXT.contains(ext) || image.getContentType() == null || !image.getContentType().startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be JPG, PNG, GIF or WEBP.");
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + "." + ext;
        image.transferTo(dir.resolve(filename));
        item.setImagePath(filename);
        return publicItem(repo.save(item));
    }

    private void validate(Item i) {
        if (i.getTitle() == null || i.getTitle().isBlank()) bad("Item name is required.");
        if (!CATEGORIES.contains(i.getCategory())) bad("Choose a valid category.");
        if (i.getLocation() == null || i.getLocation().isBlank()) bad("Location is required.");
        if (i.getEventDate() == null) bad("Date is required.");
        if (i.getContactName() == null || i.getContactName().isBlank()) bad("Contact name is required.");
        if (i.getContactInfo() == null || i.getContactInfo().isBlank()) bad("Contact email or phone is required.");
        if (i.getManageCode() == null || i.getManageCode().length() < 4 || i.getManageCode().length() > 20) bad("Secret code must be 4 to 20 characters.");
    }

    private Map<String, Object> publicItem(Item i) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", i.getId()); out.put("type", i.getType().name()); out.put("status", i.getStatus().name());
        out.put("title", i.getTitle()); out.put("description", i.getDescription()); out.put("category", i.getCategory());
        out.put("location", i.getLocation()); out.put("eventDate", i.getEventDate()); out.put("contactName", i.getContactName());
        out.put("contactInfo", i.getContactInfo()); out.put("imageUrl", i.getImagePath() == null ? null : "/uploads/" + i.getImagePath());
        out.put("createdAt", i.getCreatedAt());
        return out; // Never expose the private manage code in public API responses.
    }

    private Item find(Long id) { return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.")); }
    private void verifyCode(Item i, String code) { if (code == null || !code.trim().equals(i.getManageCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Secret code does not match."); }
    private void deleteImage(String name) { if (name != null) try { Files.deleteIfExists(Paths.get(uploadDir).toAbsolutePath().normalize().resolve(name)); } catch (IOException ignored) {} }
    private static ItemType parseType(String type) { if (type == null || type.isBlank()) return null; try { return ItemType.valueOf(type.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type must be LOST or FOUND."); } }
    private static LocalDate parseDate(String s) { try { return LocalDate.parse(s); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date must use YYYY-MM-DD format."); } }
    private static boolean has(String s, String q) { return s != null && s.toLowerCase(Locale.ROOT).contains(q); }
    private static String trim(String s) { return s == null ? null : s.trim(); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public static class CreateItemRequest {
        @JsonAlias({"reportType"}) public String type;
        @JsonAlias({"itemName", "name"}) public String title;
        public String description;
        public String category;
        public String location;
        @JsonAlias({"date"}) public String eventDate;
        public String contactName;
        @JsonAlias({"contactEmail", "contact"}) public String contactInfo;
        public String manageCode;
    }
    public static class CodeRequest { public String code; }
}
