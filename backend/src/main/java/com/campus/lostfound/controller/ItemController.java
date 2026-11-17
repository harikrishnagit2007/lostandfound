package com.campus.lostfound.controller;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.ItemType;
import com.campus.lostfound.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Controller
public class ItemController {

    public static final List<String> CATEGORIES = List.of(
            "Electronics", "ID & Cards", "Books & Notes", "Keys", "Bags",
            "Wallets & Money", "Clothing", "Water Bottles", "Jewellery", "Other");

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final ItemRepository repo;

    @Value("${lostfound.upload-dir:uploads}")
    private String uploadDir;

    public ItemController(ItemRepository repo) {
        this.repo = repo;
    }

    // ---------- Home / browse ----------

    @GetMapping("/")
    public String home(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean showResolved,
                       Model model) {

        ItemType typeFilter = parseType(type);
        String query = q == null ? "" : q.trim().toLowerCase();

        List<Item> items = repo.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> showResolved || i.getStatus() == ItemStatus.OPEN)
                .filter(i -> typeFilter == null || i.getType() == typeFilter)
                .filter(i -> category == null || category.isBlank() || i.getCategory().equals(category))
                .filter(i -> query.isEmpty()
                        || contains(i.getTitle(), query)
                        || contains(i.getDescription(), query)
                        || contains(i.getLocation(), query))
                .toList();

        model.addAttribute("items", items);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("type", typeFilter == null ? "" : typeFilter.name());
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("showResolved", showResolved);
        model.addAttribute("lostCount", repo.countByTypeAndStatus(ItemType.LOST, ItemStatus.OPEN));
        model.addAttribute("foundCount", repo.countByTypeAndStatus(ItemType.FOUND, ItemStatus.OPEN));
        model.addAttribute("resolvedCount", repo.countByStatus(ItemStatus.RESOLVED));
        return "index";
    }

    // ---------- Report form ----------

    @GetMapping("/items/new")
    public String newForm(@RequestParam(required = false) String type, Model model) {
        Item item = new Item();
        ItemType t = parseType(type);
        if (t != null) item.setType(t);
        model.addAttribute("item", item);
        model.addAttribute("categories", CATEGORIES);
        return "form";
    }

    @PostMapping("/items")
    public String create(@ModelAttribute Item item,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         Model model,
                         RedirectAttributes redirect) {

        List<String> errors = new ArrayList<>();
        if (isBlank(item.getTitle())) errors.add("Please give the item a short title.");
        if (isBlank(item.getCategory()) || !CATEGORIES.contains(item.getCategory())) errors.add("Please choose a category.");
        if (isBlank(item.getLocation())) errors.add("Please tell us where it was lost or found.");
        if (item.getEventDate() == null) errors.add("Please pick a date.");
        if (isBlank(item.getContactName())) errors.add("Please enter your name.");
        if (isBlank(item.getContactInfo())) errors.add("Please enter an email or phone number so people can reach you.");
        String code = item.getManageCode() == null ? "" : item.getManageCode().trim();
        if (code.length() < 4 || code.length() > 20) errors.add("Choose a secret code of 4 to 20 characters. You'll need it to close or delete this post.");

        String imagePath = null;
        if (image != null && !image.isEmpty()) {
            try {
                imagePath = saveImage(image);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            } catch (IOException e) {
                errors.add("Sorry, the photo could not be saved. Try again or post without it.");
            }
        }

        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("categories", CATEGORIES);
            return "form";
        }

        item.setId(null);
        item.setStatus(ItemStatus.OPEN);
        item.setManageCode(code);
        item.setImagePath(imagePath);
        item.setTitle(item.getTitle().trim());
        Item saved = repo.save(item);

        redirect.addFlashAttribute("flash", "Your post is live. Keep your secret code safe!");
        return "redirect:/items/" + saved.getId();
    }

    // ---------- Detail ----------

    @GetMapping("/items/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("item", find(id));
        return "detail";
    }

    @PostMapping("/items/{id}/resolve")
    public String resolve(@PathVariable Long id, @RequestParam String code, RedirectAttributes redirect) {
        Item item = find(id);
        if (!codeMatches(item, code)) {
            redirect.addFlashAttribute("error", "That code doesn't match this post.");
            return "redirect:/items/" + id;
        }
        item.setStatus(ItemStatus.RESOLVED);
        repo.save(item);
        redirect.addFlashAttribute("flash", "Marked as resolved. Glad it worked out!");
        return "redirect:/items/" + id;
    }

    @PostMapping("/items/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam String code, RedirectAttributes redirect) {
        Item item = find(id);
        if (!codeMatches(item, code)) {
            redirect.addFlashAttribute("error", "That code doesn't match this post.");
            return "redirect:/items/" + id;
        }
        deleteImageFile(item.getImagePath());
        repo.delete(item);
        redirect.addFlashAttribute("flash", "Post deleted.");
        return "redirect:/";
    }

    // ---------- Helpers ----------

    private Item find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    private boolean codeMatches(Item item, String code) {
        return code != null && item.getManageCode().equals(code.trim());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static boolean contains(String field, String q) {
        return field != null && field.toLowerCase().contains(q);
    }

    private static ItemType parseType(String type) {
        if (type == null || type.isBlank()) return null;
        try { return ItemType.valueOf(type.toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }

    private String saveImage(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase() : "";
        String ct = file.getContentType() == null ? "" : file.getContentType();
        if (!IMAGE_EXT.contains(ext) || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Photo must be a JPG, PNG, GIF or WEBP image.");
        }
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + ext;
        file.transferTo(dir.resolve(name));
        return name;
    }

    private void deleteImageFile(String name) {
        if (name == null) return;
        try {
            Files.deleteIfExists(Paths.get(uploadDir).toAbsolutePath().normalize().resolve(name));
        } catch (IOException ignored) { }
    }
}
