<?php
declare(strict_types=1);

/**
 * Manu ReelDrop server API.
 *
 * One entry point, one action per request:
 *
 *   health, analyze, job-create, job, jobs, events, job-cancel, job-retry, job-delete,
 *   job-cancel-all, library, library-delete, file, thumb, cleanup
 *   temporary, temporary-delete
 *
 * plus the legacy actions of the original web app (download-stream, list, delete, download)
 * so the old index.php keeps working.
 */

require_once __DIR__ . '/lib/config.php';
require_once __DIR__ . '/lib/http.php';
require_once __DIR__ . '/lib/util.php';
require_once __DIR__ . '/lib/store.php';
require_once __DIR__ . '/lib/media.php';
require_once __DIR__ . '/lib/ytdlp.php';

reeldrop_bootstrap_dirs();

$action = strtolower(trim((string) reeldrop_param('action', '')));
$publicActions = ['health'];

if (!in_array($action, $publicActions, true)) {
    reeldrop_require_token();
}

switch ($action) {
    // ------------------------------------------------------------------ health
    case 'health':
        $downloads = reeldrop_downloads_dir();
        $space = reeldrop_disk_space($downloads);
        $ytdlp = reeldrop_ytdlp();
        reeldrop_json([
            'ok' => true,
            'app' => 'Manu ReelDrop Server',
            'version' => (string) reeldrop_config_value('app_version', '2.2.0'),
            'ytdlp' => $ytdlp,
            'ytdlp_version' => reeldrop_binary_version($ytdlp),
            'ffmpeg' => reeldrop_ffmpeg() !== null,
            'ffmpeg_version' => reeldrop_binary_version(reeldrop_ffmpeg()),
            'tokenRequired' => (string) reeldrop_config_value('api_token', '') !== '',
            'free_space' => $space['free_space'],
            'total_space' => $space['total_space'],
            'active_jobs' => reeldrop_jobs_active_count(),
            'library_count' => count(reeldrop_library_list()),
            'downloads_dir' => $downloads,
            'max_concurrent_jobs' => (int) reeldrop_config_value('max_concurrent_jobs', 3),
            'time' => time(),
            'message' => $ytdlp === null ? 'yt-dlp no está instalado: ejecuta pkg install python && pip install -U yt-dlp' : null,
        ]);

    // --------------------------------------------------------------- analysis
    case 'analyze':
        $body = reeldrop_request_body();
        $url = trim((string) ($body['url'] ?? reeldrop_param('url', '')));
        if (!reeldrop_is_allowed_url($url)) {
            reeldrop_error('Solo se permiten enlaces de Instagram, YouTube o Facebook.', 400);
        }
        if (reeldrop_ytdlp() === null) {
            reeldrop_error('yt-dlp no está instalado en el servidor.', 503);
        }
        $info = reeldrop_analyze_url($url);
        if ($info === null) {
            reeldrop_error('No se pudo analizar el enlace. Comprueba yt-dlp, cookies y conectividad.', 422);
        }
        reeldrop_json(['ok' => true, 'media' => reeldrop_analysis_payload($info)]);

    // ------------------------------------------------------------------ jobs
    case 'job-create':
        $body = reeldrop_request_body();
        $url = trim((string) ($body['url'] ?? reeldrop_param('url', '')));
        $quality = trim((string) ($body['quality'] ?? reeldrop_param('quality', '')));
        if ($quality === '') {
            $quality = (string) reeldrop_config_value('default_quality', 'best');
        }
        if (!reeldrop_is_allowed_url($url)) {
            reeldrop_error('Solo se permiten enlaces de Instagram, YouTube o Facebook.', 400);
        }
        if (reeldrop_ytdlp() === null) {
            reeldrop_error('yt-dlp no está instalado en el servidor.', 503);
        }
        $job = reeldrop_job_create($url, $quality);
        if (!reeldrop_spawn_worker((string) $job['id'])) {
            reeldrop_error('No se pudo iniciar el proceso de descarga en el servidor.', 500);
        }
        reeldrop_json(['ok' => true, 'job' => reeldrop_job_payload($job)]);

    case 'job':
        $id = (string) reeldrop_param('id', '');
        $job = $id === '' ? null : reeldrop_job_load($id);
        if (!$job) {
            reeldrop_error('Trabajo no encontrado.', 404);
        }
        reeldrop_json(['ok' => true, 'job' => reeldrop_job_payload($job)]);

    case 'jobs':
        $limit = (int) reeldrop_param('limit', '50');
        $status = trim((string) reeldrop_param('status', ''));
        $jobs = reeldrop_jobs_all(max(1, min(400, $limit)));
        if ($status !== '') {
            $wanted = array_filter(array_map('trim', explode(',', strtolower($status))));
            $jobs = array_values(array_filter($jobs, static fn(array $job): bool => in_array(strtolower((string) $job['status']), $wanted, true)));
        }
        reeldrop_json(['ok' => true, 'jobs' => array_map('reeldrop_job_payload', $jobs)]);

    case 'events':
        $id = (string) reeldrop_param('id', '');
        if ($id === '') {
            reeldrop_error('Falta el identificador del trabajo.', 400);
        }
        reeldrop_job_stream($id);

    case 'job-cancel':
        $body = reeldrop_request_body();
        $id = (string) ($body['id'] ?? reeldrop_param('id', ''));
        $job = $id === '' ? null : reeldrop_job_load($id);
        if (!$job) {
            reeldrop_error('Trabajo no encontrado.', 404);
        }
        reeldrop_kill_tree(isset($job['pid']) ? (int) $job['pid'] : null, isset($job['child_pid']) ? (int) $job['child_pid'] : null);
        $job['status'] = 'canceled';
        $job['message'] = 'Cancelado';
        $job['speed'] = null;
        $job['speed_bps'] = 0;
        $job['eta'] = null;
        $job['eta_seconds'] = null;
        $job['pid'] = null;
        $job['child_pid'] = null;
        $job['finished_at'] = time();
        reeldrop_job_save($job);
        reeldrop_json(['ok' => true, 'job' => reeldrop_job_payload($job)]);

    case 'job-retry':
        $body = reeldrop_request_body();
        $id = (string) ($body['id'] ?? reeldrop_param('id', ''));
        $job = $id === '' ? null : reeldrop_job_load($id);
        if (!$job) {
            reeldrop_error('Trabajo no encontrado.', 404);
        }
        $fresh = reeldrop_job_create((string) $job['url'], (string) $job['quality']);
        reeldrop_job_save(array_merge($fresh, ['attempts' => (int) ($job['attempts'] ?? 0) + 1]));
        if (!reeldrop_spawn_worker((string) $fresh['id'])) {
            reeldrop_error('No se pudo reiniciar la descarga.', 500);
        }
        reeldrop_json(['ok' => true, 'job' => reeldrop_job_payload($fresh)]);

    case 'job-delete':
        $body = reeldrop_request_body();
        $id = (string) ($body['id'] ?? reeldrop_param('id', ''));
        if ($id === '') {
            reeldrop_error('Falta el identificador del trabajo.', 400);
        }
        reeldrop_job_delete($id);
        reeldrop_json(['ok' => true]);

    case 'job-cancel-all':
        $canceled = 0;
        foreach (reeldrop_jobs_all(400) as $job) {
            if (!in_array($job['status'], ['queued', 'downloading', 'processing'], true)) {
                continue;
            }
            reeldrop_kill_tree(isset($job['pid']) ? (int) $job['pid'] : null, isset($job['child_pid']) ? (int) $job['child_pid'] : null);
            $job['status'] = 'canceled';
            $job['message'] = 'Cancelado';
            $job['finished_at'] = time();
            reeldrop_job_save($job);
            $canceled++;
        }
        reeldrop_json(['ok' => true, 'canceled' => $canceled]);

    // ------------------------------------------------------------------ library
    case 'library':
        $query = (string) reeldrop_param('q', '');
        reeldrop_json(['ok' => true, 'items' => reeldrop_library_list($query)]);

    case 'library-delete':
        $body = reeldrop_request_body();
        $file = reeldrop_safe_name((string) ($body['file'] ?? reeldrop_param('file', '')));
        if ($file === null || !reeldrop_is_media($file)) {
            reeldrop_error('Archivo inválido.', 400);
        }
        $path = reeldrop_downloads_dir() . '/' . $file;
        if (!is_file($path)) {
            reeldrop_error('El archivo no existe.', 404);
        }
        @unlink($path);
        if (is_file(reeldrop_meta_path($file))) {
            @unlink(reeldrop_meta_path($file));
        }
        if (is_file(reeldrop_thumb_path($file))) {
            @unlink(reeldrop_thumb_path($file));
        }
        reeldrop_json(['ok' => true]);

    case 'temporary':
        reeldrop_json(['ok' => true, 'items' => reeldrop_temporary_list()]);

    case 'temporary-delete':
        $body = reeldrop_request_body();
        $file = reeldrop_safe_name((string) ($body['file'] ?? reeldrop_param('file', '')));
        if ($file === null || !reeldrop_is_temporary($file)) {
            reeldrop_error('Fichero temporal inválido.', 400);
        }
        $path = reeldrop_downloads_dir() . '/' . $file;
        if (!is_file($path)) {
            reeldrop_error('El fichero temporal no existe.', 404);
        }
        if (!@unlink($path)) {
            reeldrop_error('No se pudo eliminar el fichero temporal.', 500);
        }
        reeldrop_json(['ok' => true]);

    case 'thumb':
        $file = reeldrop_safe_name((string) reeldrop_param('file', ''));
        if ($file === null) {
            reeldrop_error('Archivo inválido.', 400);
        }
        $media = reeldrop_downloads_dir() . '/' . $file;
        if (!is_file($media)) {
            reeldrop_error('El archivo no existe.', 404);
        }
        $thumb = reeldrop_generate_thumbnail($media, $file);
        if ($thumb === null) {
            reeldrop_error('No se pudo generar la miniatura (¿ffmpeg instalado?).', 404);
        }
        header('Cache-Control: public, max-age=86400');
        header('Content-Type: image/jpeg');
        header('Content-Length: ' . (string) filesize($thumb));
        readfile($thumb);
        exit;

    case 'file':
        $file = reeldrop_safe_name((string) reeldrop_param('file', ''));
        if ($file === null) {
            reeldrop_error('Archivo inválido.', 400);
        }
        reeldrop_serve_file(reeldrop_downloads_dir() . '/' . $file, (string) reeldrop_param('download', '') === '1');

    case 'cleanup':
        $result = reeldrop_cleanup();
        reeldrop_json(['ok' => true, 'deleted' => $result['deleted'], 'bytes' => $result['bytes']]);

    // ------------------------------------------------------------------ legacy web API
    case 'download-stream':
        $body = reeldrop_request_body();
        $url = trim((string) ($body['url'] ?? reeldrop_param('url', '')));
        if (!reeldrop_is_allowed_url($url)) {
            reeldrop_sse_start();
            reeldrop_sse(['type' => 'error', 'error' => 'Solo se permiten enlaces de Instagram, YouTube o Facebook.']);
            exit;
        }
        reeldrop_legacy_stream($url);

    case 'list':
        reeldrop_json(['ok' => true, 'items' => reeldrop_library_list()]);

    case 'delete':
        $body = reeldrop_request_body();
        $file = reeldrop_safe_name((string) ($body['file'] ?? reeldrop_param('file', '')));
        if ($file === null) {
            reeldrop_json(['ok' => false, 'error' => 'Archivo inválido.']);
        }
        $path = reeldrop_downloads_dir() . '/' . $file;
        if (is_file($path)) {
            @unlink($path);
        }
        reeldrop_json(['ok' => true]);

    case 'download':
        $body = reeldrop_request_body();
        $url = trim((string) ($body['url'] ?? reeldrop_param('url', '')));
        if (!reeldrop_is_allowed_url($url)) {
            reeldrop_json(['ok' => false, 'error' => 'URL no válida.']);
        }
        $job = reeldrop_job_create($url, (string) reeldrop_config_value('default_quality', 'best'));
        if (!reeldrop_spawn_worker((string) $job['id'])) {
            reeldrop_json(['ok' => false, 'error' => 'No se pudo iniciar la descarga.']);
        }
        reeldrop_json(['ok' => true, 'job' => reeldrop_job_payload($job), 'id' => $job['id']]);

    default:
        reeldrop_error('Acción desconocida: ' . $action, 404);
}

// ---------------------------------------------------------------------- helpers

/** Adds the URLs the app needs to a raw job record. */
function reeldrop_job_payload(array $job): array
{
    if (!empty($job['filename'])) {
        $file = (string) $job['filename'];
        $job['file_url'] = 'api.php?action=file&file=' . rawurlencode($file);
        if (is_file(reeldrop_thumb_path($file))) {
            $job['thumbnail'] = 'api.php?action=thumb&file=' . rawurlencode($file);
        }
    }
    return $job;
}

/** Reduces the large yt-dlp metadata response to what the Android picker needs. */
function reeldrop_analysis_payload(array $info): array
{
    return [
        'id' => isset($info['id']) ? (string) $info['id'] : null,
        'title' => isset($info['title']) ? (string) $info['title'] : null,
        'author' => isset($info['uploader']) ? (string) $info['uploader'] : (
            isset($info['channel']) ? (string) $info['channel'] : null
        ),
        'thumbnail' => isset($info['thumbnail']) ? (string) $info['thumbnail'] : null,
        'duration' => isset($info['duration']) ? (float) $info['duration'] : null,
        'qualities' => reeldrop_quality_options($info),
    ];
}

/** Streams a job through SSE until it reaches a terminal state. */
function reeldrop_job_stream(string $id): void
{
    reeldrop_sse_start();
    $deadline = time() + 1800;
    $lastHash = '';
    $lastPing = time();

    while (time() < $deadline) {
        if (function_exists('connection_aborted') && connection_aborted() !== 0) {
            break;
        }
        $job = reeldrop_job_load($id);
        if (!$job) {
            reeldrop_sse(['type' => 'error', 'error' => 'Trabajo no encontrado.']);
            break;
        }
        $hash = md5(implode('|', [
            (string) $job['status'],
            (string) $job['progress'],
            (string) ($job['downloaded_bytes'] ?? ''),
            (string) ($job['speed'] ?? ''),
            (string) ($job['message'] ?? ''),
        ]));
        if ($hash !== $lastHash) {
            $lastHash = $hash;
            $event = reeldrop_job_payload($job);
            $event['type'] = match ($job['status']) {
                'completed' => 'done',
                'failed' => 'error',
                default => 'progress',
            };
            reeldrop_sse($event);
        }
        if (in_array($job['status'], ['completed', 'failed', 'canceled'], true)) {
            break;
        }
        if ((time() - $lastPing) >= 10) {
            $lastPing = time();
            reeldrop_sse_comment('ping ' . time());
        }
        usleep(300000);
    }
    exit;
}

/** Compatibility endpoint: live yt-dlp output for the original web UI. */
function reeldrop_legacy_stream(string $url): void
{
    $command = reeldrop_build_command($url, (string) reeldrop_config_value('default_quality', 'best'));
    if ($command === null) {
        reeldrop_sse_start();
        reeldrop_sse(['type' => 'error', 'error' => 'yt-dlp no está instalado en el servidor.']);
        exit;
    }

    reeldrop_sse_start();
    reeldrop_sse(['type' => 'start', 'message' => 'Conectando con la plataforma…']);

    $descriptors = [0 => ['pipe', 'r'], 1 => ['pipe', 'w'], 2 => ['pipe', 'w']];
    $process = @proc_open($command, $descriptors, $pipes);
    if (!is_resource($process)) {
        reeldrop_sse(['type' => 'error', 'error' => 'No se pudo iniciar yt-dlp.']);
        exit;
    }
    fclose($pipes[0]);
    stream_set_blocking($pipes[1], false);
    stream_set_blocking($pipes[2], false);

    $buffer = '';
    $finalFile = null;
    $lastEvent = 0.0;

    while (true) {
        $status = proc_get_status($process);
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
                if ($event['type'] === 'file') {
                    $finalFile = $event['filepath'];
                    continue;
                }
                if ($event['type'] === 'error') {
                    reeldrop_sse(['type' => 'error', 'error' => $event['error']]);
                    continue;
                }
                $now = microtime(true);
                if (($now - $lastEvent) < 0.35) {
                    continue;
                }
                $lastEvent = $now;
                $payload = ['type' => $event['type'], 'phase' => $event['message'] ?? null];
                if ($event['type'] === 'progress') {
                    $payload = array_merge($payload, [
                        'percent' => $event['progress'] . '%',
                        'speed' => $event['speed'],
                        'eta' => $event['eta'],
                        'downloaded' => $event['downloaded_bytes'],
                        'total' => $event['total_bytes'],
                    ]);
                } else {
                    $payload['line'] = $event['message'] ?? '';
                }
                reeldrop_sse($payload);
            }
        }
        if (!$status['running']) {
            break;
        }
        if (function_exists('connection_aborted') && connection_aborted() !== 0) {
            reeldrop_kill_tree($status['pid'] ?? null);
            proc_close($process);
            exit;
        }
        usleep(150000);
    }

    fclose($pipes[1]);
    fclose($pipes[2]);
    $exitCode = proc_close($process);

    if ($finalFile !== null && is_file($finalFile)) {
        $file = basename($finalFile);
        reeldrop_sse([
            'type' => 'done',
            'file' => $file,
            'size' => (int) filesize($finalFile),
            'url' => 'api.php?action=file&file=' . rawurlencode($file),
        ]);
    } else {
        reeldrop_sse(['type' => 'error', 'error' => 'No se pudo descargar el archivo (código ' . $exitCode . ').']);
    }
    exit;
}

/** Starts the worker in the background, detached from the web request. */
function reeldrop_spawn_worker(string $jobId): bool
{
    $php = PHP_BINARY;
    if ($php === '' || !@is_executable($php)) {
        $php = trim((string) @shell_exec('command -v php 2>/dev/null')) ?: 'php';
    }
    $worker = __DIR__ . '/lib/worker.php';
    $logDir = (string) reeldrop_config_value('logs_dir');
    if (!is_dir($logDir)) {
        @mkdir($logDir, 0775, true);
    }
    $log = $logDir . '/' . reeldrop_safe_name($jobId) . '.out';
    $command = sprintf(
        'nohup %s %s %s >> %s 2>&1 &',
        escapeshellarg($php),
        escapeshellarg($worker),
        escapeshellarg($jobId),
        escapeshellarg($log),
    );
    $handle = @popen($command, 'r');
    if ($handle === false) {
        return false;
    }
    pclose($handle);
    return true;
}
