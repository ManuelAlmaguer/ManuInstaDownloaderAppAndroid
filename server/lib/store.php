<?php
declare(strict_types=1);

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/util.php';

/** Persistence for jobs: one JSON file per job in data/jobs, written atomically. */

function reeldrop_jobs_dir(): string
{
    return rtrim((string) reeldrop_config_value('data_dir'), '/') . '/jobs';
}

function reeldrop_job_path(string $id): string
{
    $safe = reeldrop_safe_name($id);
    if ($safe === null) {
        reeldrop_error('Identificador de trabajo inválido.', 400);
    }
    return reeldrop_jobs_dir() . '/' . $safe . '.json';
}

function reeldrop_job_save(array $job): bool
{
    $job['updated_at'] = time();
    return reeldrop_write_json_atomic(reeldrop_job_path((string) $job['id']), $job);
}

function reeldrop_job_load(string $id): ?array
{
    $path = reeldrop_job_path($id);
    $job = reeldrop_read_json($path);
    if (!$job) {
        return null;
    }
    return reeldrop_job_reap($job);
}

/** Detects jobs whose worker died (phone rebooted, OOM kill, Termux closed). */
function reeldrop_job_reap(array $job): array
{
    $active = in_array($job['status'] ?? '', ['queued', 'downloading', 'processing'], true);
    if (!$active) {
        return $job;
    }
    $heartbeat = (int) ($job['updated_at'] ?? 0);
    $staleSeconds = (int) reeldrop_config_value('stale_job_seconds', 45);
    $pid = isset($job['pid']) ? (int) $job['pid'] : 0;

    $noProcess = $pid > 0 && !reeldrop_process_alive($pid);
    $timedOut = $heartbeat > 0 && (time() - $heartbeat) > max(60, $staleSeconds * 3);
    if ($noProcess || ($pid === 0 && $timedOut)) {
        $job['status'] = 'failed';
        $job['error'] = $job['error'] ?? 'El proceso de descarga se interrumpió (¿se cerró Termux?).';
        $job['message'] = 'Interrumpido';
        $job['finished_at'] = time();
        $job['pid'] = null;
        reeldrop_job_save($job);
    }
    return $job;
}

function reeldrop_jobs_all(int $limit = 100): array
{
    $dir = reeldrop_jobs_dir();
    if (!is_dir($dir)) {
        return [];
    }
    $jobs = [];
    foreach (glob($dir . '/*.json') ?: [] as $path) {
        $job = reeldrop_read_json($path);
        if ($job) {
            $jobs[] = reeldrop_job_reap($job);
        }
    }
    usort($jobs, static fn(array $a, array $b): int => ($b['created_at'] ?? 0) <=> ($a['created_at'] ?? 0));
    return array_slice($jobs, 0, max(1, $limit));
}

function reeldrop_jobs_active_count(): int
{
    $count = 0;
    foreach (reeldrop_jobs_all(400) as $job) {
        if (in_array($job['status'] ?? '', ['queued', 'downloading', 'processing'], true)) {
            $count++;
        }
    }
    return $count;
}

function reeldrop_job_create(string $url, string $quality): array
{
    $job = [
        'id' => reeldrop_new_job_id(),
        'url' => $url,
        'quality' => $quality,
        'status' => 'queued',
        'progress' => 0.0,
        'speed' => null,
        'speed_bps' => 0,
        'eta' => null,
        'eta_seconds' => null,
        'downloaded_bytes' => 0,
        'total_bytes' => 0,
        'title' => null,
        'author' => null,
        'thumbnail' => null,
        'filename' => null,
        'file_url' => null,
        'error' => null,
        'message' => 'En cola',
        'attempts' => 0,
        'pid' => null,
        'child_pid' => null,
        'created_at' => time(),
        'updated_at' => time(),
        'started_at' => null,
        'finished_at' => null,
    ];
    reeldrop_job_save($job);
    return $job;
}

/** Removes a job record (used when the user swipes it out of the app queue). */
function reeldrop_job_delete(string $id): bool
{
    $path = reeldrop_job_path($id);
    return is_file($path) ? @unlink($path) : false;
}

function reeldrop_job_log(string $id, string $line): void
{
    $dir = (string) reeldrop_config_value('logs_dir');
    if (!is_dir($dir)) {
        @mkdir($dir, 0775, true);
    }
    @file_put_contents(
        $dir . '/' . reeldrop_safe_name($id) . '.log',
        '[' . date('H:i:s') . '] ' . $line . PHP_EOL,
        FILE_APPEND,
    );
}
