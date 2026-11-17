/*
# Create items table for CampusFind Lost & Found

## Overview
Creates the items table and helper functions for the campus lost & found portal.
This is a no-auth (single-tenant) app — anyone can browse, post, and manage items
using a secret management code chosen at posting time.

## New Tables
- `items`: stores lost & found reports
  - id (uuid, primary key, auto-generated)
  - type (text: 'LOST' or 'FOUND')
  - status (text: 'OPEN' or 'RESOLVED', default 'OPEN')
  - title (text, item name)
  - description (text, optional details)
  - category (text, one of predefined categories)
  - location (text, where item was lost/found)
  - event_date (date, when it happened)
  - contact_name (text, reporter's name)
  - contact_info (text, email or phone)
  - manage_code (text, secret code for managing the post — NOT readable by clients)
  - image_data (text, base64-encoded resized photo or null)
  - created_at (timestamptz, default now())

## Security
- RLS enabled on `items`
- Column-level SELECT: anon/authenticated can read all columns EXCEPT manage_code
- Column-level INSERT: anon/authenticated can write all columns including manage_code
- No direct UPDATE or DELETE grants — those go through SECURITY DEFINER functions
  that verify the management code server-side
- SELECT and INSERT policies use USING(true) / WITH CHECK(true) because the data
  is intentionally public/shared (no-auth app)

## Functions
- `resolve_item(p_id uuid, p_code text)`: verifies manage_code, sets status to RESOLVED
- `delete_item(p_id uuid, p_code text)`: verifies manage_code, deletes the row
- Both are SECURITY DEFINER (run as owner, bypass RLS), callable by anon + authenticated
*/

CREATE TABLE IF NOT EXISTS items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  type text NOT NULL CHECK (type IN ('LOST', 'FOUND')),
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
  title text NOT NULL,
  description text,
  category text NOT NULL,
  location text NOT NULL,
  event_date date NOT NULL,
  contact_name text NOT NULL,
  contact_info text NOT NULL,
  manage_code text NOT NULL,
  image_data text,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE items ENABLE ROW LEVEL SECURITY;

-- Column-level SELECT: exclude manage_code from public reads
GRANT SELECT (id, type, status, title, description, category, location, event_date, contact_name, contact_info, image_data, created_at) ON items TO anon, authenticated;

-- Column-level INSERT: allow writing all columns including manage_code
GRANT INSERT (id, type, status, title, description, category, location, event_date, contact_name, contact_info, manage_code, image_data, created_at) ON items TO anon, authenticated;

-- No UPDATE or DELETE grants for anon/authenticated.
-- Those operations go through SECURITY DEFINER functions only.

-- Policies for direct table access (public/shared data, no auth)
DROP POLICY IF EXISTS "anon_select_items" ON items;
CREATE POLICY "anon_select_items" ON items FOR SELECT
  TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "anon_insert_items" ON items;
CREATE POLICY "anon_insert_items" ON items FOR INSERT
  TO anon, authenticated WITH CHECK (true);

-- SECURITY DEFINER functions for code-verified operations
CREATE OR REPLACE FUNCTION resolve_item(p_id uuid, p_code text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_item RECORD;
BEGIN
  SELECT manage_code, status INTO v_item FROM items WHERE id = p_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('success', false, 'error', 'Item not found');
  END IF;
  IF v_item.manage_code != trim(p_code) THEN
    RETURN jsonb_build_object('success', false, 'error', 'Secret code does not match');
  END IF;
  UPDATE items SET status = 'RESOLVED' WHERE id = p_id;
  RETURN jsonb_build_object('success', true);
END;
$$;

CREATE OR REPLACE FUNCTION delete_item(p_id uuid, p_code text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_item RECORD;
BEGIN
  SELECT manage_code INTO v_item FROM items WHERE id = p_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('success', false, 'error', 'Item not found');
  END IF;
  IF v_item.manage_code != trim(p_code) THEN
    RETURN jsonb_build_object('success', false, 'error', 'Secret code does not match');
  END IF;
  DELETE FROM items WHERE id = p_id;
  RETURN jsonb_build_object('success', true);
END;
$$;

-- Index for sorting by created_at
CREATE INDEX IF NOT EXISTS idx_items_created_at ON items (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_items_type_status ON items (type, status);
