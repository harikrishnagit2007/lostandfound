# Campus Lost & Found

Spring Boot 4 + Thymeleaf + JPA + MySQL.

## Run
1. Start MySQL and edit `src/main/resources/application.properties` (username/password).
   The `lostfound` database and tables are created automatically.
2. `./mvnw spring-boot:run`  (Windows: `mvnw.cmd spring-boot:run`)
3. Open http://localhost:8080

## Features
- Browse, search, filter (lost/found, category, show resolved)
- Report lost/found items with optional photo
- Post detail page with contact info
- Owner secret code to mark resolved or delete a post (no login needed)
- Photos saved to `./uploads`
