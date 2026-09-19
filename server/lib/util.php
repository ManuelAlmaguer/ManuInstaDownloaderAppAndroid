<?php
declare(strict_types=1);

/** Small helpers: process detection, binary discovery, disk space, safe file names. */

function reeldrop_process_alive(?int $pid): bool
{
    if (!$pid || $pid <= 0) {
        return false;
    }
    if (function_exists('posix_kill')) {
        return @posix_kill($pid, 0);
    }
    return is_dir('/proc/' . $pid);
}

function reeldrop_kill_tree(?int $pid, ?int $childPid = null): void
{
    foreach ([$childPid, $pid] as $target) {
        if (!$target || $target <= 0) {
            continue;
        }
        if (function_exists('posix_kill')) {
            @posix_kill(-$target, SIGTERM);
            @posix_kill($target, SIGTERM);
        } else {
            @shell_exec('kill -TERM ' . (int) $target . ' 2>/dev/null');
        }
        @shell_exec('pkill -TERM -P ' . (int) $target . ' 2>/dev/null');
    }
    usleep(300000);
    foreach ([$childPid, $pid] as $target) {
        if (!$target || $target <= 0) {
            continue;
        }
        if (reeldrop_process_alive($target)) {
            if (function_exists('posix_kill')) {
                @posix_kill(-$target, SIGKILL);
                @posix_kill($target, SIGKILL);
            } else {
                @shell_exec('kill -KILL ' . (int) $target . ' 2>/dev/null');
            }
        }
    }
}

function reeldrop_find_binary(array $candidates, string $name): ?string
{
    $fromPath = trim((string) @shell_exec('command -v ' . escapeshellarg($name) . ' 2>/dev/null'));
    if ($fromPath !== '' && @is_executable($fromPath)) {
        return $fromPath;
    }
    foreach ($candidates as $candidate) {
        if ($candidate === '') {
            continue;
        }
        if (@is_executable($candidate)) {
            return $candidate;
        }
    }
    return null;
}

function reeldrop_ytdlp(): ?string
{
    $configured = (string) reeldrop_config_value('ytdlp_path', '');
    if ($configured !== '' && @is_executable($configured)) {
        return $configured;
    }
    $prefix = getenv('PREFIX') ?: '';
    return reeldrop_find_binary([
        $prefix . '/bin/yt-dlp',
        '/data/data/com.termux/files/usr/bin/yt-dlp',
        '/usr/local/bin/yt-dlp',
        '/usr/bin/yt-dlp',
        getenv('HOME') . '/.local/bin/yt-dlp',
    ], 'yt-dlp');
}

function reeldrop_ffmpeg(): ?string
{
    $configured = (string) reeldrop_config_value('ffmpeg_path', '');
    if ($configured !== '' && @is_executable($configured)) {
        return $configured;
    }
    $prefix = getenv('PREFIX') ?: '';
    return reeldrop_find_binary([
        $prefix . '/bin/ffmpeg',
        '/data/data/com.termux/files/usr/bin/ffmpeg',
        '/usr/bin/ffmpeg',
    ], 'ffmpeg');
}

function reeldrop_ffprobe(): ?string
{
    $configured = (string) reeldrop_config_value('ffprobe_path', '');
    if ($configured !== '' && @is_executable($configured)) {
        return $configured;
    }
    $prefix = getenv('PREFIX') ?: '';
    return reeldrop_find_binary([
        $prefix . '/bin/ffprobe',
        '/data/data/com.termux/files/usr/bin/ffprobe',
        '/usr/bin/ffprobe',
    ], 'ffprobe');
}

function reeldrop_binary_version(?string $binary): ?string
{
    if (!$binary) {
        return null;
    }
    $output = (string) @shell_exec(escapeshellarg($binary) . ' --version 2>/dev/null | head -n 1');
    $output = trim($output);
    if ($output === '') {
        return null;
    }
    if (preg_match('/(\d+\.\d+(\.\d+)?)/', $output, $m)) {
        return $m[1];
    }
    return $output;
}

function reeldrop_disk_space(string $dir): array
{
    $free = @disk_free_space($dir);
    $total = @disk_total_space($dir);
    return [
        'free_space' => $free === false ? 0 : (int) $free,
        'total_space' => $total === false ? 0 : (int) $total,
    ];
}

function reeldrop_safe_name(?string $name): ?string
{
    if ($name === null) {
        return null;
    }
    $name = basename($name);
    if ($name === '' || $name === '.' || $name === '..') {
        return null;
    }
    if (str_contains($name, '..') || str_contains($name, '/') || str_contains($name, '\\')) {
        return null;
    }
    return $name;
}

function reeldrop_is_allowed_url(string $url): bool
{
    if (!filter_var($url, FILTER_VALIDATE_URL)) {
        return false;
    }
    $host = strtolower((string) parse_url($url, PHP_URL_HOST));
    if ($host === '') {
        return false;
    }
    $allowed = (array) reeldrop_config_value('allowed_hosts', ['instagram.com']);
    foreach ($allowed as $candidate) {
        $candidate = strtolower(trim((string) $candidate));
        if ($candidate === '') {
            continue;
        }
        if ($host === $candidate || str_ends_with($host, '.' . $candidate)) {
            return true;
        }
    }
    return false;
}

function reeldrop_new_job_id(): string
{
    return date('Ymd-His') . '-' . bin2hex(random_bytes(4));
}

function reeldrop_write_json_atomic(string $path, array $data): bool
{
    $tmp = $path . '.tmp';
    $encoded = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    if ($encoded === false) {
        return false;
    }
    if (@file_put_contents($tmp, $encoded, LOCK_EX) === false) {
        return false;
    }
    return @rename($tmp, $path);
}

function reeldrop_read_json(string $path): ?array
{
    if (!is_file($path)) {
        return null;
    }
    $raw = @file_get_contents($path);
    if ($raw === false || $raw === '') {
        return null;
    }
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : null;
}

function reeldrop_bytes_human(int|float $bytes): string
{
    $bytes = (float) $bytes;
    if ($bytes < 1024) {
        return round($bytes) . ' B';
    }
    $units = ['KB', 'MB', 'GB', 'TB'];
    $index = -1;
    do {
        $bytes /= 1024;
        $index++;
    } while ($bytes >= 1024 && $index < count($units) - 1);
    return round($bytes, 2) . ' ' . $units[$index];
}
