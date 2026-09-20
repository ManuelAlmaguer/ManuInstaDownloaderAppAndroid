<?php
declare(strict_types=1);

/**
 * Download worker (CLI only).
 *
 *     php lib/worker.php <job-id>
 *
 * It runs yt-dlp, parses the progress stream, keeps the job JSON updated (that is what the
 * app reads through SSE/polling) and stores metadata + thumbnail for the library.
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(403);
    exit('CLI only');
}

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/util.php';
require_once __DIR__ . '/store.php';
require_once __DIR__ . '/ytdlp.php';
require_once __DIR__ . '/media.php';

$jobId = $argv[1] ?? '';
if ($jobId === '') {
    fwrite(STDERR, "Uso: php lib/worker.php <job-id>\n");
    exit(2);
}

reeldrop_bootstrap_dirs();

$job = reeldrop_job_load($jobId);
if (!$job) {
    fwrite(STDERR, "Trabajo no encontrado: $jobId\n");
    exit(3);
}
if (in_array($job['status'], ['canceled', 'completed'], true)) {
    exit(0);
}

// ---- wait for a free slot (protects a phone from being hammered by parallel downloads) ----
$maxConcurrent = max(1, (int) reeldrop_config_value('max_concurrent_jobs', 3));
$waitedSeconds = 0;
while (reeldrop_jobs_active_count() > $maxConcurrent && $waitedSeconds < 600) {
    $job = reeldrop_job_load($jobId);
    if (!$job || $job['status'] === 'canceled') {
        exit(0);
    }
    $job['status'] = 'queued';
    $job['message'] = 'Esperando turno…';
    reeldrop_job_save($job);
    sleep(2);
    $waitedSeconds += 2;
}

$job = reeldrop_job_load($jobId);
if (!$job || $job['status'] === 'canceled') {
    exit(0);
}

$command = reeldrop_build_command((string) $job['url'], (string) ($job['quality'] ?? 'best'));
if ($command === null) {
    $job['status'] = 'failed';
    $job['error'] = 'yt-dlp no está instalado en el servidor. Ejecuta: pkg install python && pip install -U yt-dlp';
    $job['message'] = 'yt-dlp ausente';
    $job['finished_at'] = time();
    reeldrop_job_save($job);
    exit(4);
}

$job['status'] = 'downloading';
$job['message'] = 'Conectando con la plataforma…';
$job['started_at'] = $job['started_at'] ?? time();
$job['pid'] = getmypid();
$job['attempts'] = (int) ($job['attempts'] ?? 0) + 1;
reeldrop_job_save($job);
reeldrop_job_log($jobId, 'Ejecutando: ' . $command);

$descriptors = [
    0 => ['pipe', 'r'],
    1 => ['pipe', 'w'],
    2 => ['pipe', 'w'],
];
$process = @proc_open($command, $descriptors, $pipes);
if (!is_resource($process)) {
    $job['status'] = 'failed';
    $job['error'] = 'No se pudo iniciar yt-dlp.';
    $job['finished_at'] = time();
    reeldrop_job_save($job);
    exit(5);
}

fclose($pipes[0]);
stream_set_blocking($pipes[1], false);
stream_set_blocking($pipes[2], false);

$lastSave = 0.0;
$buffer = '';
$finalFile = null;
$pendingError = null;
$phase = 'Descargando…';

$flush = function (array $patch) use (&$job, &$lastSave, $jobId): void {
    $fresh = reeldrop_job_load($jobId) ?? $job;
    if (in_array($fresh['status'] ?? '', ['canceled'], true)) {
        return;
    }
    $job = array_merge($fresh, $patch);
    reeldrop_job_save($job);
    $lastSave = microtime(true);
};

while (true) {
    $status = proc_get_status($process);
    $changed = false;

    foreach ([$pipes[1], $pipes[2]] as $pipe) {
        $chunk = @stream_get_contents($pipe);
        if ($chunk === false || $chunk === '') {
            continue;
        }
        $buffer .= $chunk;
        while (($pos = strpos($buffer, "\n")) !== false) {
            $line = substr($buffer, 0, $pos);
            $buffer = substr($buffer, $pos + 1);
            $event = reeldrop_parse_line($line);
            if ($event === null) {
                continue;
            }
            switch ($event['type']) {
                case 'progress':
                    $job['status'] = 'downloading';
                    $job['progress'] = $event['progress'];
                    $job['speed'] = $event['speed'];
                    $job['speed_bps'] = $event['speed_bps'];
                    $job['eta'] = $event['eta'];
                    $job['eta_seconds'] = $event['eta_seconds'];
                    $job['downloaded_bytes'] = $event['downloaded_bytes'];
                    $job['total_bytes'] = $event['total_bytes'];
                    $job['message'] = 'Descargando…';
                    $changed = true;
                    break;
                case 'file':
                    $finalFile = $event['filepath'];
                    break;
                case 'error':
                    $pendingError = $event['error'];
                    reeldrop_job_log($jobId, 'ERROR: ' . $event['error']);
                    break;
                default:
                    if (!empty($event['message'])) {
                        $job['message'] = $event['message'];
                        $phase = $event['message'];
                        $changed = true;
                    }
                    reeldrop_job_log($jobId, $line);
                    break;
            }
        }
    }

    $now = microtime(true);
    if ($changed && ($now - $lastSave) > 0.35) {
        $flush([
            'status' => $job['status'],
            'progress' => $job['progress'],
            'speed' => $job['speed'],
            'speed_bps' => $job['speed_bps'],
            'eta' => $job['eta'],
            'eta_seconds' => $job['eta_seconds'],
            'downloaded_bytes' => $job['downloaded_bytes'],
            'total_bytes' => $job['total_bytes'],
            'message' => $job['message'],
            'child_pid' => $status['pid'] ?? null,
        ]);
        $changed = false;
    }

    if (!$status['running']) {
        break;
    }
    usleep(120000);
}

// Drain whatever is left in the pipes.
foreach ([$pipes[1], $pipes[2]] as $pipe) {
    if (!is_resource($pipe)) {
        continue;
    }
    $rest = @stream_get_contents($pipe);
    if ($rest !== false && $rest !== '') {
        $buffer .= $rest;
    }
    fclose($pipe);
}
foreach (explode("\n", $buffer) as $line) {
    if ($line === '') {
        continue;
    }
    $event = reeldrop_parse_line($line);
    if ($event === null) {
        continue;
    }
    if ($event['type'] === 'file') {
        $finalFile = $event['filepath'];
    } elseif ($event['type'] === 'error') {
        $pendingError = $event['error'];
    }
}

$exitCode = proc_close($process);
$job = reeldrop_job_load($jobId) ?? $job;

if ($job['status'] === 'canceled') {
    reeldrop_job_log($jobId, 'Cancelado por el usuario.');
    exit(0);
}

// ---- metadata + thumbnail for the library -------------------------------------------
$filePath = $finalFile;
if ($filePath === null || !is_file($filePath)) {
    $filePath = reeldrop_pick_newest_media();
}

if ($exitCode === 0 && $filePath !== null && is_file($filePath)) {
    $file = basename($filePath);
    $meta = reeldrop_capture_metadata($filePath);
    reeldrop_store_thumbnail($file, (string) $meta['thumbnail'], $filePath);

    $job['status'] = 'completed';
    $job['progress'] = 100.0;
    $job['speed'] = null;
    $job['speed_bps'] = 0;
    $job['eta'] = null;
    $job['eta_seconds'] = null;
    $job['filename'] = $file;
    $job['file_url'] = 'api.php?action=file&file=' . rawurlencode($file);
    $job['thumbnail'] = is_file(reeldrop_thumb_path($file))
        ? 'api.php?action=thumb&file=' . rawurlencode($file)
        : null;
    $job['title'] = $job['title'] ?: ($meta['title'] ?? null);
    $job['author'] = $job['author'] ?: ($meta['author'] ?? null);
    $job['total_bytes'] = (int) @filesize($filePath);
    $job['downloaded_bytes'] = (int) @filesize($filePath);
    $job['message'] = 'Completado';
    $job['error'] = null;
} else {
    $job['status'] = 'failed';
    $job['error'] = $pendingError ?: 'yt-dlp terminó con código ' . $exitCode . ' sin generar el archivo.';
    $job['message'] = 'Error';
}
$job['pid'] = null;
$job['child_pid'] = null;
$job['finished_at'] = time();
reeldrop_job_save($job);

// Cleanup the info.json sidecar yt-dlp leaves behind.
foreach (glob(reeldrop_downloads_dir() . '/*.info.json') ?: [] as $infoFile) {
    @unlink($infoFile);
}

exit($job['status'] === 'completed' ? 0 : 1);

/** Newest media file in the downloads folder (fallback when --print gives nothing). */
function reeldrop_pick_newest_media(): ?string
{
    $best = null;
    $bestTime = 0;
    foreach (glob(reeldrop_downloads_dir() . '/*') ?: [] as $path) {
        if (!is_file($path) || !reeldrop_is_media($path)) {
            continue;
        }
        $time = (int) @filemtime($path);
        if ($time > $bestTime) {
            $bestTime = $time;
            $best = $path;
        }
    }
    return $best;
}

/**
 * Reads the JSON metadata yt-dlp wrote next to the video and stores a small sidecar
 * (title, author, duration, resolution, thumbnail url) used by the library endpoint.
 */
function reeldrop_capture_metadata(string $filePath): array
{
    $file = basename($filePath);
    $meta = reeldrop_read_json(reeldrop_meta_path($file)) ?? [];

    $infoCandidates = [
        reeldrop_downloads_dir() . '/' . $file . '.info.json',
        preg_replace('/\.[A-Za-z0-9]+$/', '.info.json', $filePath),
    ];
    foreach ($infoCandidates as $candidate) {
        if (!is_string($candidate) || !is_file($candidate)) {
            continue;
        }
        $info = reeldrop_read_json($candidate);
        if (!$info) {
            continue;
        }
        $meta = array_merge($meta, array_filter([
            'title' => $info['title'] ?? null,
            'author' => $info['uploader'] ?? $info['channel'] ?? null,
            'duration' => $info['duration'] ?? null,
            'width' => $info['width'] ?? null,
            'height' => $info['height'] ?? null,
            'thumbnail' => $info['thumbnail'] ?? null,
            'webpage_url' => $info['webpage_url'] ?? null,
        ], static fn($value) => $value !== null));
        break;
    }

    if (!isset($meta['duration'], $meta['width'], $meta['height'])) {
        $meta = array_merge(reeldrop_probe_media($filePath), $meta);
    }
    $meta['file'] = $file;
    $meta['size'] = (int) @filesize($filePath);
    $meta['saved_at'] = time();

    if (!is_dir(dirname(reeldrop_meta_path($file)))) {
        @mkdir(dirname(reeldrop_meta_path($file)), 0775, true);
    }
    reeldrop_write_json_atomic(reeldrop_meta_path($file), $meta);
    return $meta;
}

/** Downloads the source cover picture once, cached in data/thumbs. */
function reeldrop_store_thumbnail(string $file, string $remoteUrl, string $filePath): void
{
    $target = reeldrop_thumb_path($file);
    if ($remoteUrl !== '' && !is_file($target)) {
        if (!is_dir(dirname($target))) {
            @mkdir(dirname($target), 0775, true);
        }
        $data = @file_get_contents($remoteUrl);
        if (is_string($data) && $data !== '') {
            @file_put_contents($target, $data);
        }
    }
    if (!is_file($target)) {
        reeldrop_generate_thumbnail($filePath, $file);
    }
}
