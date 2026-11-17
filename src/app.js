import { supabase } from './supabase.js';

const $ = (s) => document.querySelector(s);
let selectedType = 'ALL';
let allItems = [];

const esc = (s) =>
  String(s ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
  );

const dateText = (s) => {
  if (!s) return '';
  const d = new Date(s + (s.length <= 10 ? 'T00:00:00' : ''));
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
};

function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 3000);
}

function compressImage(file, maxDim = 600, quality = 0.7) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        let { width, height } = img;
        if (width > height && width > maxDim) {
          height = (height * maxDim) / width;
          width = maxDim;
        } else if (height > maxDim) {
          width = (width * maxDim) / height;
          height = maxDim;
        }
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, width, height);
        resolve(canvas.toDataURL('image/jpeg', quality));
      };
      img.onerror = reject;
      img.src = e.target.result;
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

async function load() {
  try {
    const { data, error } = await supabase
      .from('items')
      .select('*')
      .order('created_at', { ascending: false });

    if (error) throw error;

    allItems = data || [];
    updateStats();
    filterRender();
    $('#apiNotice').textContent = `Connected — ${allItems.length} report${allItems.length === 1 ? '' : 's'} in the shared database.`;
  } catch (e) {
    $('#apiNotice').textContent = 'Could not connect to the database: ' + e.message;
    render([]);
  }
}

function updateStats() {
  const lost = allItems.filter((i) => i.type === 'LOST' && i.status === 'OPEN').length;
  const found = allItems.filter((i) => i.type === 'FOUND' && i.status === 'OPEN').length;
  const resolved = allItems.filter((i) => i.status === 'RESOLVED').length;
  $('#totalPosts').textContent = allItems.length;
  $('#lostCount').textContent = lost;
  $('#foundCount').textContent = found;
  if ($('#resolvedCount')) $('#resolvedCount').textContent = resolved;
}

function filterRender() {
  const q = $('#searchInput').value.trim().toLowerCase();
  const cat = $('#categoryFilter').value;
  const sort = $('#sortFilter').value;
  let a = allItems.filter(
    (x) =>
      (selectedType === 'ALL' || x.type === selectedType) &&
      (!cat || x.category === cat) &&
      (!q ||
        [x.title, x.description, x.location, x.category].some((v) =>
          (v || '').toLowerCase().includes(q)
        ))
  );
  a.sort((x, y) =>
    sort === 'old'
      ? new Date(x.created_at) - new Date(y.created_at)
      : new Date(y.created_at) - new Date(x.created_at)
  );
  render(a);
}

function render(items) {
  const grid = $('#itemsGrid');
  if (!items.length) {
    grid.innerHTML =
      '<div class="empty"><div class="empty-icon">⌕</div><h3>No matching items yet</h3><p>Try another search, or add a report to help the community.</p><button class="btn btn-dark" data-open>＋ Report an item</button></div>';
    grid.querySelector('[data-open]')?.addEventListener('click', openReportModal);
    return;
  }
  grid.innerHTML = items
    .map(
      (x) =>
        `<article class="item-card${x.status === 'RESOLVED' ? ' resolved' : ''}" data-id="${x.id}"><div class="item-photo">${
          x.image_data
            ? `<img src="${esc(x.image_data)}" alt="${esc(x.title)}">`
            : '⌕'
        }</div><div class="item-content"><div class="item-meta"><span class="tag ${x.type === 'FOUND' ? 'found' : 'lost'}">${x.type}</span>${
          x.status === 'RESOLVED' ? '<span class="tag" style="background:#e5e7eb;color:#374151">RESOLVED</span>' : ''
        }<span class="item-location">${esc(x.category)}</span></div><h3>${esc(x.title)}</h3><p>${esc(x.description || 'No additional description provided.')}</p><div class="item-location">⌖ ${esc(x.location)} · ${dateText(x.event_date)}</div><div class="item-foot"><span>${esc(x.contact_name || 'Campus member')}</span><a href="#detail" data-detail="${x.id}">View details ↗</a></div></div></article>`
    )
    .join('');

  grid.querySelectorAll('[data-detail]').forEach((a) =>
    a.addEventListener('click', (e) => {
      e.preventDefault();
      openDetailModal(a.dataset.detail);
    })
  );
  grid.querySelectorAll('.item-card').forEach((c) =>
    c.addEventListener('click', () => openDetailModal(c.dataset.id))
  );
}

function openReportModal() {
  $('#reportModal').classList.add('open');
  $('#reportModal').setAttribute('aria-hidden', 'false');
  document.body.style.overflow = 'hidden';
}
function closeReportModal() {
  $('#reportModal').classList.remove('open');
  $('#reportModal').setAttribute('aria-hidden', 'true');
  document.body.style.overflow = '';
}

function openDetailModal(id) {
  const item = allItems.find((x) => x.id === id);
  if (!item) return;
  const c = $('#detailContent');
  c.innerHTML = `
    <button class="close" id="closeDetail">×</button>
    <div class="item-photo" style="height:240px;border-radius:8px;margin-bottom:16px">${
      item.image_data ? `<img src="${esc(item.image_data)}" alt="${esc(item.title)}" style="width:100%;height:100%;object-fit:cover">` : '⌕'
    }</div>
    <div class="item-meta" style="margin-bottom:12px">
      <span class="tag ${item.type === 'FOUND' ? 'found' : 'lost'}">${item.type}</span>
      ${item.status === 'RESOLVED' ? '<span class="tag" style="background:#e5e7eb;color:#374151">RESOLVED</span>' : ''}
      <span class="item-location">${esc(item.category)}</span>
    </div>
    <h2 style="font-family:'Playfair Display';font-size:28px;font-weight:500;margin:0 0 12px">${esc(item.title)}</h2>
    <p class="muted" style="margin:0 0 16px">${esc(item.description || 'No additional description provided.')}</p>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:16px">
      <div><strong>Location</strong><br><span class="muted">${esc(item.location)}</span></div>
      <div><strong>Date</strong><br><span class="muted">${dateText(item.event_date)}</span></div>
      <div><strong>Posted by</strong><br><span class="muted">${esc(item.contact_name)}</span></div>
      <div><strong>Contact</strong><br><span class="muted">${esc(item.contact_info)}</span></div>
    </div>
    ${
      item.status === 'OPEN'
        ? `<a class="btn btn-dark" href="${item.contact_info.includes('@') ? 'mailto:' + encodeURIComponent(item.contact_info) : '#'}" ${item.contact_info.includes('@') ? '' : 'data-copy="' + esc(item.contact_info) + '"'}>Contact ${esc(item.contact_name)} ↗</a>`
        : '<p class="muted" style="margin-top:8px">This item has been resolved.</p>'
    }
    <details style="margin-top:20px;border-top:1px solid var(--line);padding-top:16px">
      <summary style="cursor:pointer;font-weight:600;font-size:12px">This is my post — manage it</summary>
      <p class="muted" style="font-size:11px;margin:10px 0">Enter your secret code to mark this post as resolved or delete it.</p>
      <div style="display:flex;gap:10px;flex-wrap:wrap;margin:10px 0">
        <input type="password" id="detailCode" placeholder="Secret code" minlength="4" maxlength="20" style="flex:1;min-width:160px;padding:11px;border:1px solid var(--line);border-radius:5px;font-size:12px">
        <button class="btn btn-dark" id="resolveBtn">Mark resolved</button>
        <button class="btn btn-outline" id="deleteBtn" style="border-color:#b24e3b;color:#b24e3b">Delete</button>
      </div>
      <p id="detailError" class="form-error"></p>
    </details>
  `;
  $('#detailModal').classList.add('open');
  $('#detailModal').setAttribute('aria-hidden', 'false');
  document.body.style.overflow = 'hidden';

  $('#closeDetail').addEventListener('click', closeDetailModal);
  $('#resolveBtn').addEventListener('click', () => manageItem(item.id, 'resolve'));
  $('#deleteBtn').addEventListener('click', () => manageItem(item.id, 'delete'));
  const copyEl = c.querySelector('[data-copy]');
  if (copyEl)
    copyEl.addEventListener('click', (e) => {
      e.preventDefault();
      navigator.clipboard?.writeText(item.contact_info);
      toast('Contact info copied to clipboard');
    });
}

function closeDetailModal() {
  $('#detailModal').classList.remove('open');
  $('#detailModal').setAttribute('aria-hidden', 'true');
  document.body.style.overflow = '';
}

async function manageItem(id, action) {
  const code = $('#detailCode')?.value?.trim();
  const err = $('#detailError');
  if (!code) {
    if (err) err.textContent = 'Please enter your secret code.';
    return;
  }
  const fn = action === 'resolve' ? 'resolve_item' : 'delete_item';
  try {
    const { data, error } = await supabase.rpc(fn, { p_id: id, p_code: code });
    if (error) throw error;
    if (data && data.success === false) throw new Error(data.error);
    closeDetailModal();
    toast(action === 'resolve' ? 'Post marked as resolved!' : 'Post deleted.');
    await load();
  } catch (e) {
    if (err) err.textContent = e.message;
  }
}

// Event listeners
document.querySelectorAll('[data-open]').forEach((b) =>
  b.addEventListener('click', openReportModal)
);
$('#closeModal').addEventListener('click', closeReportModal);
$('#cancelBtn').addEventListener('click', closeReportModal);
$('#reportModal').addEventListener('click', (e) => {
  if (e.target === $('#reportModal')) closeReportModal();
});
$('#detailModal').addEventListener('click', (e) => {
  if (e.target === $('#detailModal')) closeDetailModal();
});

document.querySelectorAll('[data-type]').forEach((b) =>
  b.addEventListener('click', () => {
    selectedType = b.dataset.type;
    document
      .querySelectorAll('[data-type]')
      .forEach((x) => x.classList.toggle('active', x === b));
    filterRender();
  })
);

['searchInput', 'categoryFilter', 'sortFilter'].forEach((id) =>
  $('#' + id).addEventListener(id === 'searchInput' ? 'input' : 'change', filterRender)
);

$('#reportForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.currentTarget;
  const fd = new FormData(f);
  const err = $('#formError');
  const btn = $('#publishBtn');
  err.textContent = '';
  btn.disabled = true;
  btn.textContent = 'Publishing…';

  try {
    const payload = {
      type: fd.get('type'),
      category: fd.get('category'),
      title: fd.get('title'),
      location: fd.get('location'),
      event_date: fd.get('eventDate'),
      description: fd.get('description') || null,
      contact_name: fd.get('contactName'),
      contact_info: fd.get('contactInfo'),
      manage_code: fd.get('manageCode'),
    };

    const file = fd.get('image');
    if (file && file.size > 0) {
      payload.image_data = await compressImage(file);
    }

    const { error: insertError } = await supabase.from('items').insert(payload);
    if (insertError) throw insertError;

    f.reset();
    closeReportModal();
    toast('Your report has been published!');
    await load();
  } catch (ex) {
    err.textContent = ex.message;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Publish report ↗';
  }
});

$('#themeBtn').addEventListener('click', () => {
  document.body.classList.toggle('dark');
  $('#themeBtn').textContent = document.body.classList.contains('dark') ? '☀' : '☾';
});

$('#menuBtn').addEventListener('click', () => {
  const nav = document.querySelector('.topbar nav');
  nav.style.display = 'flex';
  nav.style.position = 'absolute';
  nav.style.top = '70px';
  nav.style.right = '12px';
  nav.style.background = 'var(--paper)';
  nav.style.padding = '18px';
  nav.style.flexDirection = 'column';
});

load();
