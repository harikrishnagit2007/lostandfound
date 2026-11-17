import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

const DEFAULT_ITEMS = [
  {
    id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    type: 'LOST',
    status: 'OPEN',
    title: 'Navy Blue Hydro Flask with Stickers',
    description: 'Left near the 2nd floor silent study carrels in the Main Library. Has stickers including a NASA logo and an astronomy club decal.',
    category: 'Water Bottles',
    location: 'Main Library 2nd Floor',
    event_date: '2026-11-15',
    contact_name: 'Alex Rivera',
    contact_info: 'alex.rivera@campus.edu',
    manage_code: '1234',
    image_data: null,
    created_at: '2026-11-15T14:30:00.000Z',
  },
  {
    id: 'c9a646d3-9c61-4cb7-bfcd-66022e0e5a96',
    type: 'FOUND',
    status: 'OPEN',
    title: 'Student ID Card - Dept of Engineering',
    description: 'Found on the bench outside Lecture Hall 3 after the afternoon CS101 class. Handed to building security desk reception.',
    category: 'ID & Cards',
    location: 'Engineering Building Block B',
    event_date: '2026-11-16',
    contact_name: 'Campus Security Desk',
    contact_info: 'security-desk@campus.edu',
    manage_code: '5678',
    image_data: null,
    created_at: '2026-11-16T11:15:00.000Z',
  },
  {
    id: '7b22a0f8-3e42-4f8e-a9b0-9f5b244d2112',
    type: 'LOST',
    status: 'OPEN',
    title: 'Apple AirPods Pro in Black Silicone Case',
    description: 'Lost somewhere between the dining hall and the student union recreational center. Charging case has a small carabiner clip.',
    category: 'Electronics',
    location: 'Student Union / Dining Hall Path',
    event_date: '2026-11-14',
    contact_name: 'Jordan Chen',
    contact_info: 'jordan.chen@campus.edu',
    manage_code: '9999',
    image_data: null,
    created_at: '2026-11-14T18:45:00.000Z',
  },
  {
    id: '3d8a7c21-124b-4b10-91a3-2c1b4f5e6a78',
    type: 'FOUND',
    status: 'RESOLVED',
    title: 'Set of Dorm Keys with Red Lanyard',
    description: 'Found on the lawn outside dorm residence hall. Reunited with owner!',
    category: 'Keys',
    location: 'North Dorm Lawn',
    event_date: '2026-11-13',
    contact_name: 'Maya Patel',
    contact_info: 'maya.patel@campus.edu',
    manage_code: '4321',
    image_data: null,
    created_at: '2026-11-13T09:20:00.000Z',
  },
];

function createMockClient() {
  const STORAGE_KEY = 'campusfind_items';

  function getStoredItems() {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed) && parsed.length > 0) {
            return parsed;
          }
        }
      }
    } catch (e) {
      console.warn('CampusFind local storage unavailable:', e);
    }
    return [...DEFAULT_ITEMS];
  }

  function saveStoredItems(items) {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
      }
    } catch (e) {
      console.warn('CampusFind failed to save items:', e);
    }
  }

  // Ensure default items exist on first load
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      if (!window.localStorage.getItem(STORAGE_KEY)) {
        saveStoredItems(DEFAULT_ITEMS);
      }
    }
  } catch (e) {
    // Ignore storage errors in restricted contexts
  }

  return {
    from(table) {
      if (table !== 'items') {
        return {
          select: async () => ({ data: [], error: null }),
          insert: async () => ({ data: [], error: null }),
        };
      }
      return {
        select(cols = '*') {
          return {
            order(col, { ascending = false } = {}) {
              const items = getStoredItems();
              items.sort((a, b) => {
                const va = a[col] ?? '';
                const vb = b[col] ?? '';
                if (ascending) {
                  return va > vb ? 1 : va < vb ? -1 : 0;
                }
                return va < vb ? 1 : va > vb ? -1 : 0;
              });
              return Promise.resolve({ data: items, error: null });
            },
            then(resolve, reject) {
              return this.order('created_at', { ascending: false }).then(resolve, reject);
            },
          };
        },
        async insert(payload) {
          const items = getStoredItems();
          const newItem = {
            id:
              typeof crypto !== 'undefined' && crypto.randomUUID
                ? crypto.randomUUID()
                : 'item-' + Math.random().toString(36).substring(2, 11),
            status: 'OPEN',
            image_data: null,
            created_at: new Date().toISOString(),
            ...payload,
          };
          items.unshift(newItem);
          saveStoredItems(items);
          return { data: [newItem], error: null };
        },
      };
    },
    async rpc(fn, args = {}) {
      const items = getStoredItems();
      const { p_id, p_code } = args;
      const index = items.findIndex((i) => i.id === p_id);
      if (index === -1) {
        return { data: { success: false, error: 'Item not found' }, error: null };
      }
      const item = items[index];
      if (String(item.manage_code || '').trim() !== String(p_code || '').trim()) {
        return { data: { success: false, error: 'Secret code does not match' }, error: null };
      }
      if (fn === 'resolve_item') {
        item.status = 'RESOLVED';
        items[index] = item;
        saveStoredItems(items);
        return { data: { success: true }, error: null };
      }
      if (fn === 'delete_item') {
        items.splice(index, 1);
        saveStoredItems(items);
        return { data: { success: true }, error: null };
      }
      return { data: { success: false, error: 'Unknown function' }, error: null };
    },
  };
}

let clientInstance;

const hasValidSupabaseEnv =
  Boolean(supabaseUrl) &&
  Boolean(supabaseAnonKey) &&
  typeof supabaseUrl === 'string' &&
  supabaseUrl.startsWith('http') &&
  !supabaseUrl.includes('YOUR-PROJECT-URL');

if (hasValidSupabaseEnv) {
  try {
    clientInstance = createClient(supabaseUrl, supabaseAnonKey);
  } catch (err) {
    console.warn('Could not initialize Supabase client, falling back to local storage:', err);
    clientInstance = createMockClient();
  }
} else {
  console.info('[CampusFind] Supabase credentials not provided in environment; using local in-memory/localStorage store.');
  clientInstance = createMockClient();
}

export const supabase = clientInstance;
