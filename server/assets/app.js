/* Manu ReelDrop Server - panel web. Habla con la API de trabajos (api.php?action=…). */

const API = 'api.php';
const TOKEN = (window.REELDROP_TOKEN || '').trim();

const $urlInput = document.getElementById('urlInput');
const $downloadBtn = document.getElementById('downloadBtn');
const $status = document.getElementById('status');
const $library = document.getElementById('library');
const $refreshBtn = document.getElementById('refreshBtn');

function withToken(url) {
  if (!TOKEN) return url;
  return url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(TOKEN);
}

function apiUrl(action, params = {}) {
  const query = new URLSearchParams({ action, ...params });
  return withToken(`${API}?${query.toString()}`);
}

function showStatus(message, type = 'info') {
  $status.textContent = message;
  $status.className = 'status show ' + type;
}

function hideStatus() {
  $status.className = 'status';
}

function formatSize(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return value + ' B';
  if (value < 1048576) return (value / 1024).toFixed(1) + ' KB';
  if (value < 1073741824) return (value / 1048576).toFixed(2) + ' MB';
  return (value / 1073741824).toFixed(2) + ' GB';
}

function formatEta(seconds) {
  if (!seconds || seconds <= 0) return '—';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m`;
  if (m > 0) return `${m}m ${String(s).padStart(2, '0')}s`;
  return `${s}s`;
}

/* ------------------------------- modal ------------------------------- */

const modal = {
  el: document.getElementById('downloadModal'),
  ring: document.getElementById('ringProgress'),
  number: document.getElementById('ringNumber'),
  title: document.getElementById('modalTitle'),
  phase: document.getElementById('modalPhase'),
  speed: document.getElementById('statSpeed'),
  size: document.getElementById('statSize'),
  eta: document.getElementById('statEta'),
  actions: document.getElementById('modalActions'),
  jobId: null,
  timer: null,
  C: 2 * Math.PI * 52,

  open() {
    this.el.hidden = false;
    requestAnimationFrame(() => this.el.classList.add('open'));
    this.setPercent(0);
    this.title.textContent = 'Descargando';
    this.phase.textContent = 'Iniciando…';
    this.speed.textContent = '—';
    this.size.textContent = '—';
    this.eta.textContent = '—';
    this.actions.innerHTML = '<button class="btn-secondary" id="modalCancelBtn">Cancelar</button>';
    document.getElementById('modalCancelBtn').onclick = () => this.cancel();
  },

  close() {
    this.el.classList.remove('open');
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    setTimeout(() => { this.el.hidden = true; }, 260);
  },

  async cancel() {
    if (this.jobId) {
      try {
        await fetch(withToken(`${API}?action=job-cancel`), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ id: this.jobId }),
        });
      } catch (error) { /* el servidor ya lo habrá cancelado */ }
    }
    this.close();
  },

  setPercent(value) {
    const clamped = Math.max(0, Math.min(100, Number(value) || 0));
    this.number.textContent = Math.round(clamped);
    this.ring.style.strokeDashoffset = this.C * (1 - clamped / 100);
  },

  apply(job) {
    this.jobId = job.id;
    this.setPercent(job.progress || 0);
    this.phase.textContent = job.message || job.status;
    this.speed.textContent = job.speed_bps > 0 ? formatSize(job.speed_bps) + '/s' : '—';
    this.eta.textContent = formatEta(job.eta_seconds);
    this.size.textContent = job.total_bytes > 0
      ? `${formatSize(job.downloaded_bytes)} / ${formatSize(job.total_bytes)}`
      : formatSize(job.downloaded_bytes);
  },

  showDone(job) {
    this.setPercent(100);
    this.title.textContent = '¡Listo!';
    this.phase.textContent = job.filename || 'Descarga completada';
    this.actions.innerHTML = `
      <a class="btn-primary" href="${withToken('api.php?action=file&file=' + encodeURIComponent(job.filename || ''))}" download>Guardar</a>
      <button class="btn-secondary" id="modalCancelBtn">Cerrar</button>`;
    document.getElementById('modalCancelBtn').onclick = () => this.close();
    loadLibrary();
  },

  showError(message) {
    this.title.textContent = 'Error';
    this.phase.textContent = message;
    this.actions.innerHTML = '<button class="btn-secondary" id="modalCancelBtn">Cerrar</button>';
    document.getElementById('modalCancelBtn').onclick = () => this.close();
  },
};

document.getElementById('modalCloseBtn').onclick = () => modal.cancel();
document.querySelectorAll('[data-close]').forEach((el) => { el.onclick = () => modal.cancel(); });
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && !modal.el.hidden) modal.cancel();
});

/* ------------------------------ library ------------------------------ */

function renderLibrary(items) {
  if (!items.length) {
    $library.innerHTML = '<div class="empty"><p>Aún no hay descargas</p></div>';
    return;
  }
  $library.innerHTML = '';
  items.forEach((item) => {
    const row = document.createElement('div');
    row.className = 'item';

    const thumb = document.createElement('div');
    thumb.className = 'item-thumb';
    if (item.thumb) {
      const img = document.createElement('img');
      img.src = withToken(item.thumb);
      img.alt = '';
      img.loading = 'lazy';
      thumb.appendChild(img);
    } else {
      thumb.textContent = '▶';
    }

    const meta = document.createElement('div');
    meta.className = 'item-meta';
    const name = document.createElement('div');
    name.className = 'item-name';
    name.textContent = item.title || item.file;
    name.title = item.file;
    const sub = document.createElement('div');
    sub.className = 'item-sub';
    const date = new Date(item.mtime * 1000);
    sub.textContent = `${formatSize(item.size)} · ${date.toLocaleString()}`;
    meta.append(name, sub);

    const actions = document.createElement('div');
    actions.className = 'item-actions';
    const download = document.createElement('a');
    download.className = 'icon-btn primary';
    download.href = withToken(item.url);
    download.setAttribute('download', '');
    download.textContent = '↓';
    const remove = document.createElement('button');
    remove.className = 'icon-btn danger';
    remove.textContent = '🗑';
    remove.onclick = async () => {
      if (!confirm(`¿Eliminar "${item.file}"?`)) return;
      await fetch(withToken(`${API}?action=library-delete`), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ file: item.file }),
      });
      loadLibrary();
    };
    actions.append(download, remove);

    row.append(thumb, meta, actions);
    $library.appendChild(row);
  });
}

async function loadLibrary() {
  try {
    const response = await fetch(apiUrl('library'));
    const data = await response.json();
    if (data.ok) renderLibrary(data.items || []);
  } catch (error) {
    console.error(error);
  }
}

/* ------------------------------ download ----------------------------- */

async function startDownload() {
  const url = ($urlInput.value || '').trim();
  if (!url) {
    showStatus('Pega primero un enlace de Instagram, YouTube o Facebook.', 'error');
    return;
  }
  hideStatus();
  modal.open();
  $downloadBtn.classList.add('loading');

  try {
    const response = await fetch(withToken(`${API}?action=job-create`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url }),
    });
    const data = await response.json();
    if (!data.ok) {
      modal.showError(data.error || 'No se pudo iniciar la descarga.');
      return;
    }
    const job = data.job;
    modal.apply(job);
    trackJob(job.id);
  } catch (error) {
    modal.showError('Error de conexión con el servidor: ' + error.message);
  } finally {
    $downloadBtn.classList.remove('loading');
  }
}

async function trackJob(jobId) {
  modal.jobId = jobId;
  if (modal.timer) clearInterval(modal.timer);

  modal.timer = setInterval(async () => {
    try {
      const response = await fetch(apiUrl('job', { id: jobId }));
      const data = await response.json();
      if (!data.ok || !data.job) return;
      const job = data.job;
      modal.apply(job);
      if (job.status === 'completed') {
        clearInterval(modal.timer);
        modal.timer = null;
        modal.showDone(job);
      } else if (job.status === 'failed' || job.status === 'canceled') {
        clearInterval(modal.timer);
        modal.timer = null;
        modal.showError(job.error || 'La descarga no terminó correctamente.');
      }
    } catch (error) {
      /* el servidor puede estar ocupado: se reintenta en el siguiente tick */
    }
  }, 900);
}

$downloadBtn.addEventListener('click', startDownload);
$urlInput.addEventListener('keydown', (event) => { if (event.key === 'Enter') startDownload(); });
$refreshBtn.addEventListener('click', loadLibrary);

loadLibrary();
