<?php
declare(strict_types=1);

require_once __DIR__ . '/lib/config.php';
require_once __DIR__ . '/lib/util.php';
require_once __DIR__ . '/lib/media.php';

reeldrop_bootstrap_dirs();

$token = (string) reeldrop_config_value('api_token', '');
$ytdlp = reeldrop_ytdlp();
$space = reeldrop_disk_space(reeldrop_downloads_dir());
$items = reeldrop_library_list();
$tokenSuffix = $token === '' ? '' : '&token=' . rawurlencode($token);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<meta name="theme-color" content="#0b0b10">
<title>ReelDrop Server</title>
<link rel="stylesheet" href="assets/style.css">
</head>
<body>
<div class="bg-glow"></div>
<main class="container">
  <header class="header">
    <div class="logo">
      <svg viewBox="0 0 24 24" fill="none" stroke="url(#g)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <defs>
          <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#a855f7"/>
            <stop offset="50%" stop-color="#ec4899"/>
            <stop offset="100%" stop-color="#f97316"/>
          </linearGradient>
        </defs>
        <rect x="2" y="2" width="20" height="20" rx="5"/>
        <path d="M12 6v9m0 0l-3.5-3.5M12 15l3.5-3.5M7.5 18.5h9"/>
      </svg>
    </div>
    <h1>ReelDrop Server</h1>
    <p class="subtitle">
      Panel del servidor · v<?= htmlspecialchars((string) reeldrop_config_value('app_version', '2.0.0')) ?>
      · <?= $ytdlp ? 'yt-dlp OK' : 'yt-dlp no instalado' ?>
      · libre <?= htmlspecialchars(reeldrop_bytes_human((float) $space['free_space'])) ?>
    </p>
  </header>

  <section class="card">
    <label class="input-wrap">
      <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
        <path d="M14 11a5 5 0 0 0-7.54.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
      </svg>
      <input type="url" id="urlInput" placeholder="https://www.instagram.com/reel/..." autocomplete="off" spellcheck="false" inputmode="url">
    </label>
    <button id="downloadBtn" class="btn-primary">
      <span class="btn-text">Descargar Reel</span>
      <span class="btn-loader"></span>
    </button>
    <div id="status" class="status"></div>
    <?php if ($token !== ''): ?>
      <p class="subtitle">Este servidor usa token de API. La app Android debe tenerlo configurado en Ajustes.</p>
    <?php endif; ?>
  </section>

  <div id="downloadModal" class="modal" hidden>
    <div class="modal-backdrop" data-close></div>
    <div class="modal-panel" role="dialog" aria-modal="true">
      <button class="modal-close" id="modalCloseBtn" aria-label="Cerrar">×</button>
      <div class="modal-ring">
        <svg viewBox="0 0 120 120">
          <defs>
            <linearGradient id="ringGradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#a855f7"/>
              <stop offset="50%" stop-color="#ec4899"/>
              <stop offset="100%" stop-color="#f97316"/>
            </linearGradient>
          </defs>
          <circle class="ring-track" cx="60" cy="60" r="52"/>
          <circle class="ring-progress" id="ringProgress" cx="60" cy="60" r="52"/>
        </svg>
        <div class="ring-label">
          <span class="ring-number" id="ringNumber">0</span><span class="ring-pct">%</span>
        </div>
      </div>
      <h3 class="modal-heading" id="modalTitle">Descargando</h3>
      <p class="modal-phase" id="modalPhase">Iniciando…</p>
      <div class="modal-stats">
        <div class="stat"><span class="stat-label">Velocidad</span><span class="stat-value" id="statSpeed">—</span></div>
        <div class="stat"><span class="stat-label">Tamaño</span><span class="stat-value" id="statSize">—</span></div>
        <div class="stat"><span class="stat-label">Restante</span><span class="stat-value" id="statEta">—</span></div>
      </div>
      <div class="modal-actions" id="modalActions"></div>
    </div>
  </div>

  <section class="library">
    <div class="library-header">
      <h2>Biblioteca (<?= count($items) ?>)</h2>
      <button id="refreshBtn" class="btn-ghost" aria-label="Refrescar">↻</button>
    </div>
    <div id="library" class="library-list">
      <?php if (!$items): ?>
        <div class="empty"><p>Aún no hay descargas</p></div>
      <?php else: ?>
        <?php foreach ($items as $item): ?>
          <div class="item">
            <div class="item-thumb">
              <?php if (!empty($item['thumb'])): ?>
                <img src="<?= htmlspecialchars($item['thumb'] . $tokenSuffix) ?>" alt="" loading="lazy">
              <?php else: ?>▶<?php endif; ?>
            </div>
            <div class="item-meta">
              <div class="item-name" title="<?= htmlspecialchars($item['file']) ?>"><?= htmlspecialchars($item['file']) ?></div>
              <div class="item-sub">
                <?= htmlspecialchars(reeldrop_bytes_human((float) $item['size'])) ?>
                · <?= htmlspecialchars(date('d/m/Y H:i', (int) $item['mtime'])) ?>
                <?php if (!empty($item['duration'])): ?> · <?= (int) round(((float) $item['duration']) / 60) ?> min<?php endif; ?>
              </div>
            </div>
            <div class="item-actions">
              <a class="icon-btn primary" href="<?= htmlspecialchars($item['url'] . $tokenSuffix) ?>" download title="Descargar">↓</a>
              <button class="icon-btn danger" data-delete="<?= htmlspecialchars($item['file']) ?>" title="Eliminar">🗑</button>
            </div>
          </div>
        <?php endforeach; ?>
      <?php endif; ?>
    </div>
  </section>

  <footer class="subtitle" style="margin-top:24px;text-align:center">
    ReelDrop · <?= htmlspecialchars(date('Y')) ?> · desarrollado por Manuel Almaguer Sosa
  </footer>
</main>
<script>
  window.REELDROP_TOKEN = <?= json_encode($token) ?>;
</script>
<script src="assets/app.js"></script>
</body>
</html>
