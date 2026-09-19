<?php
declare(strict_types=1);

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/util.php';

/** Library listing, metadata sidecars, thumbnails and file streaming with Range support. */

function reeldrop_downloads_dir(): string
{
    return rtrim((string) reeldrop_config_value('downloads_dir'), '/');
}

function reeldrop_data_dir(): string
{
    return rtrim((string) reeldrop_config_value('data_dir'), '/');
}

function reeldrop_media_extensions(): array
{
    return ['mp4', 'mkv', 'webm', 'mov', 'm4v', '3gp', 'mp3', 'm4a', 'opus', 'aac'];
}

function reeldrop_is_media(string $file): bool
{
    $ext = strtolower(pathinfo($file, PATHINFO_EXTENSION));
    return in_array($ext, reeldrop_media_extensions(), true);
}

function reeldrop_meta_path(string $file): string
{
    return reeldrop_data_dir() . '/meta/' . $file . '.json';
}

function reeldrop_thumb_path(string $file): string
{
    return reeldrop_data_dir() . '/thumbs/' . $file . '.jpg';
}

function reeldrop_library_list(string $query = ''): array
{
    $dir = reeldrop_downloads_dir();
    if (!is_dir($dir)) {
        return [];
    }
    $items = [];
    $query = mb_strtolower(trim($query));

    foreach (glob($dir . '/*') ?: [] as $path) {
        if (!is_file($path) || !reeldrop_is_media($path)) {
            continue;
        }
        $file = basename($path);
        if ($query !== '' && !str_contains(mb_strtolower($file), $query)) {
            continue;
        }
        $meta = reeldrop_read_json(reeldrop_meta_path($file)) ?? [];
        $thumbFile = reeldrop_thumb_path($file);
        $items[] = [
            'file' => $file,
            'size' => (int) @filesize($path),
            'mtime' => (int) @filemtime($path),
            'duration' => isset($meta['duration']) ? (float) $meta['duration'] : null,
            'width' => isset($meta['width']) ? (int) $meta['width'] : null,
            'height' => isset($meta['height']) ? (int) $meta['height'] : null,
            'title' => $meta['title'] ?? null,
            'author' => $meta['author'] ?? null,
            'thumb' => is_file($thumbFile) ? 'api.php?action=thumb&file=' . rawurlencode($file) : null,
            'url' => 'api.php?action=file&file=' . rawurlencode($file),
        ];
    }

    usort($items, static fn(array $a, array $b): int => ($b['mtime'] ?? 0) <=> ($a['mtime'] ?? 0));
    return $items;
}

/** Extracts duration/resolution with ffprobe when the sidecar has no data yet. */
function reeldrop_probe_media(string $path): array
{
    $ffprobe = reeldrop_ffprobe();
    if (!$ffprobe) {
        return [];
    }
    $cmd = escapeshellarg($ffprobe)
        . ' -v quiet -print_format json -show_format -show_streams '
        . escapeshellarg($path) . ' 2>/dev/null';
    $raw = (string) @shell_exec($cmd);
    if ($raw === '') {
        return [];
    }
    $data = json_decode($raw, true);
    if (!is_array($data)) {
        return [];
    }
    $duration = isset($data['format']['duration']) ? (float) $data['format']['duration'] : null;
    $width = null;
    $height = null;
    foreach (($data['streams'] ?? []) as $stream) {
        if (($stream['codec_type'] ?? '') === 'video') {
            $width = isset($stream['width']) ? (int) $stream['width'] : null;
            $height = isset($stream['height']) ? (int) $stream['height'] : null;
            break;
        }
    }
    return array_filter([
        'duration' => $duration,
        'width' => $width,
        'height' => $height,
    ], static fn($value) => $value !== null);
}

/** Generates a JPEG thumbnail with ffmpeg, cached in data/thumbs. */
function reeldrop_generate_thumbnail(string $path, string $file): ?string
{
    $target = reeldrop_thumb_path($file);
    if (is_file($target) && filesize($target) > 0) {
        return $target;
    }
    $ffmpeg = reeldrop_ffmpeg();
    if (!$ffmpeg) {
        return null;
    }
    if (!is_dir(dirname($target))) {
        @mkdir(dirname($target), 0775, true);
    }
    $cmd = escapeshellarg($ffmpeg)
        . ' -y -ss 00:00:01 -i ' . escapeshellarg($path)
        . ' -frames:v 1 -vf ' . escapeshellarg('scale=480:-2')
        . ' ' . escapeshellarg($target) . ' >/dev/null 2>&1';
    @shell_exec($cmd);
    return (is_file($target) && filesize($target) > 0) ? $target : null;
}

/** Serves a media file with HTTP Range support so the app can seek and resume downloads. */
function reeldrop_serve_file(string $path, bool $asAttachment = false): void
{
    if (!is_file($path)) {
        http_response_code(404);
        exit('Not found');
    }

    $size = (int) filesize($path);
    $start = 0;
    $end = $size - 1;
    $partial = false;

    $rangeHeader = $_SERVER['HTTP_RANGE'] ?? '';
    if ($rangeHeader !== '' && preg_match('/bytes=(\d*)-(\d*)/', $rangeHeader, $m)) {
        $partial = true;
        if ($m[1] !== '') {
            $start = (int) $m[1];
        }
        if ($m[2] !== '') {
            $end = (int) $m[2];
        }
        $end = min($end, $size - 1);
        if ($start > $end || $start >= $size) {
            http_response_code(416);
            header('Content-Range: bytes */' . $size);
            exit;
        }
    }

    $length = $end - $start + 1;
    http_response_code($partial ? 206 : 200);
    header('Content-Type: ' . reeldrop_mime_for($path));
    header('Accept-Ranges: bytes');
    header('Content-Length: ' . $length);
    header('Cache-Control: public, max-age=3600');
    header('Last-Modified: ' . gmdate('D, d M Y H:i:s', (int) filemtime($path)) . ' GMT');
    if ($partial) {
        header('Content-Range: bytes ' . $start . '-' . $end . '/' . $size);
    }
    if ($asAttachment) {
        header('Content-Disposition: attachment; filename="' . basename($path) . '"');
    }

    $handle = @fopen($path, 'rb');
    if ($handle === false) {
        http_response_code(500);
        exit;
    }
    fseek($handle, $start);
    $remaining = $length;
    while ($remaining > 0 && !feof($handle)) {
        $chunk = fread($handle, (int) min(262144, $remaining));
        if ($chunk === false || $chunk === '') {
            break;
        }
        echo $chunk;
        $remaining -= strlen($chunk);
        @flush();
    }
    fclose($handle);
    exit;
}

function reeldrop_mime_for(string $path): string
{
    return match (strtolower(pathinfo($path, PATHINFO_EXTENSION))) {
        'mp4', 'm4v' => 'video/mp4',
        'mkv' => 'video/x-matroska',
        'webm' => 'video/webm',
        'mov' => 'video/quicktime',
        '3gp' => 'video/3gpp',
        'mp3' => 'audio/mpeg',
        'm4a' => 'audio/mp4',
        'aac' => 'audio/aac',
        'opus' => 'audio/opus',
        'jpg', 'jpeg' => 'image/jpeg',
        'png' => 'image/png',
        default => 'application/octet-stream',
    };
}

/** Deletes temporary leftovers and, optionally, files older than retention_days. */
function reeldrop_cleanup(): array
{
    $dir = reeldrop_downloads_dir();
    $deleted = 0;
    $bytes = 0;
    foreach (['*.part', '*.ytdl', '*.tmp', '*.part-Frag*', '*.info.json'] as $pattern) {
        foreach (glob($dir . '/' . $pattern) ?: [] as $path) {
            if (!is_file($path)) {
                continue;
            }
            $bytes += (int) @filesize($path);
            if (@unlink($path)) {
                $deleted++;
            }
        }
    }

    $retention = (int) reeldrop_config_value('retention_days', 0);
    if ($retention > 0) {
        $limit = time() - ($retention * 86400);
        foreach (glob($dir . '/*') ?: [] as $path) {
            if (!is_file($path) || @filemtime($path) > $limit) {
                continue;
            }
            $bytes += (int) @filesize($path);
            if (@unlink($path)) {
                $deleted++;
                $name = basename($path);
                if (is_file(reeldrop_meta_path($name))) {
                    @unlink(reeldrop_meta_path($name));
                }
                if (is_file(reeldrop_thumb_path($name))) {
                    @unlink(reeldrop_thumb_path($name));
                }
            }
        }
    }

    return ['deleted' => $deleted, 'bytes' => $bytes];
}
