CREATE TABLE IF NOT EXISTS items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(10) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
  title VARCHAR(120) NOT NULL,
  description VARCHAR(2000),
  category VARCHAR(50) NOT NULL,
  location VARCHAR(150) NOT NULL,
  event_date DATE NOT NULL,
  contact_name VARCHAR(100) NOT NULL,
  contact_info VARCHAR(150) NOT NULL,
  manage_code VARCHAR(20) NOT NULL,
  image_path VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_items_created_at (created_at),
  INDEX idx_items_type_status (type, status)
);
