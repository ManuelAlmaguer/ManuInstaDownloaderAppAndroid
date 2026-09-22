<?php
declare(strict_types=1);

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/util.php';

/**
 * yt-dlp command construction and progress parsing.
 * Kept in one place so the worker and the legacy streaming endpoint behave identically.
 */

function reeldrop_quality_format(string $quality): array
{
    if (preg_match('/^[0-9]{3,4}$/', $quality) === 1) {
        $height = max(144, min(4320, (int) $quality));
        return [
            'bestvideo*[height<=' . $height . ']+bestaudio/best[height<=' . $height . ']',
            'mp4',
        ];
    }
    return match ($quality) {
        '1080' => ['bestvideo*[height<=1080]+bestaudio/best[height<=1080]', 'mp4'],
        '720' => ['bestvideo*[height<=720]+bestaudio/best[height<=720]', 'mp4'],
        '480' => ['bestvideo*[height<=480]+bestaudio/best[height<=480]', 'mp4'],
        'audio' => ['bestaudio/best', null],
        default => ['bestvideo*+bestaudio/best', 'mp4'],
    };
}

function reeldrop_progress_template(): string
{
    return 'download:%(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s|'
        . '%(progress.downloaded_bytes)s|%(progress.total_bytes_estimate)s|%(progress.total_bytes)s';
}

function reeldrop_output_template(): string
{
    $dir = rtrim((string) reeldrop_config_value('downloads_dir'), '/');
    return $dir . '/%(title).70s [%(id)s].%(ext)s';
}

/** Builds the full shell command for one download, or null when yt-dlp is missing. */
function reeldrop_build_command(string $url, string $quality): ?string
{
    $ytdlp = reeldrop_ytdlp();
    if (!$ytdlp) {
        return null;
    }
    [$format, $mergeContainer] = reeldrop_quality_format($quality);
    $ffmpeg = reeldrop_ffmpeg();

    $parts = [
        escapeshellarg($ytdlp),
        '--newline',
        '--no-playlist',
        '--no-warnings',
        '--restrict-filenames',
        '--no-simulate',
        '--progress',
        '--write-info-json',
        '--retries 5',
        '--fragment-retries 5',
        '--socket-timeout 20',
        '--progress-template ' . escapeshellarg(reeldrop_progress_template()),
        '--print ' . escapeshellarg('after_move:filepath'),
    ];

    if ($quality === 'audio') {
        $parts[] = '-f ' . escapeshellarg($format);
        $parts[] = '-x';
        if ($ffmpeg) {
            $parts[] = '--audio-format mp3';
            $parts[] = '--audio-quality 0';
        }
    } else {
        $parts[] = '-f ' . escapeshellarg($format);
        if ($mergeContainer && $ffmpeg) {
            $parts[] = '--merge-output-format ' . escapeshellarg($mergeContainer);
        }
    }

    $cookies = (string) reeldrop_config_value('cookies_file', '');
    if ($cookies !== '' && is_file($cookies)) {
        $parts[] = '--cookies ' . escapeshellarg($cookies);
    }
    if ($ffmpeg) {
        $parts[] = '--ffmpeg-location ' . escapeshellarg($ffmpeg);
    }

    $parts[] = '-o ' . escapeshellarg(reeldrop_output_template());
    $parts[] = escapeshellarg($url);
    $parts[] = '2>&1';

    return implode(' ', $parts);
}

/** Parses one line of yt-dlp output into a structured event. */
function reeldrop_parse_line(string $line): ?array
{
    $line = rtrim($line, "\r\n");
    if ($line === '') {
        return null;
    }

    if (str_starts_with($line, 'download:')) {
        $parts = explode('|', substr($line, 9));
        $percentRaw = (string) preg_replace('/[^0-9.]/', '', trim($parts[0] ?? ''));
        $speed = trim($parts[1] ?? '');
        $eta = trim($parts[2] ?? '');
        $downloaded = (int) ($parts[3] ?? 0);
        $estimate = (int) ($parts[4] ?? 0);
        $total = (int) ($parts[5] ?? 0);
        if ($total <= 0) {
            $total = $estimate;
        }
        return [
            'type' => 'progress',
            'progress' => max(0.0, min(100.0, (float) $percentRaw)),
            'speed' => $speed,
            'speed_bps' => reeldrop_speed_to_bps($speed),
            'eta' => $eta,
            'eta_seconds' => reeldrop_eta_to_seconds($eta),
            'downloaded_bytes' => $downloaded,
            'total_bytes' => $total,
        ];
    }

    if (stripos($line, 'Extracting URL') !== false) {
        return ['type' => 'log', 'message' => 'Analizando el enlace…'];
    }
    if (stripos($line, 'Downloading webpage') !== false || stripos($line, 'Downloading API JSON') !== false) {
        return ['type' => 'log', 'message' => 'Obteniendo datos del video…'];
    }
    if (stripos($line, '[Merger]') !== false || stripos($line, 'Merging formats') !== false) {
        return ['type' => 'log', 'message' => 'Fusionando video y audio…'];
    }
    if (stripos($line, '[ExtractAudio]') !== false) {
        return ['type' => 'log', 'message' => 'Extrayendo el audio…'];
    }
    if (stripos($line, 'has already been downloaded') !== false) {
        return ['type' => 'log', 'message' => 'Ya estaba descargado'];
    }
    if (stripos($line, 'ERROR:') !== false) {
        return ['type' => 'error', 'error' => trim((string) preg_replace('/^.*?ERROR:\s*/i', '', $line))];
    }
    if (str_starts_with($line, '[')) {
        return ['type' => 'log', 'message' => mb_substr($line, 0, 200)];
    }
    if (@is_file($line)) {
        return ['type' => 'file', 'filename' => basename($line), 'filepath' => $line];
    }
    return ['type' => 'log', 'message' => mb_substr($line, 0, 200)];
}

function reeldrop_speed_to_bps(?string $speed): int
{
    $speed = trim((string) $speed);
    if ($speed === '' || strcasecmp($speed, 'N/A') === 0 || strcasecmp($speed, 'Unknown') === 0) {
        return 0;
    }
    if (!preg_match('/([0-9]+(?:\.[0-9]+)?)/', $speed, $m)) {
        return 0;
    }
    $value = (float) $m[1];
    $lower = strtolower($speed);
    $multiplier = match (true) {
        str_contains($lower, 'gib'), str_contains($lower, 'gb/s') => 1024 ** 3,
        str_contains($lower, 'mib'), str_contains($lower, 'mb/s') => 1024 ** 2,
        str_contains($lower, 'kib'), str_contains($lower, 'kb/s') => 1024,
        default => 1,
    };
    return (int) round($value * $multiplier);
}

function reeldrop_eta_to_seconds(?string $eta): ?int
{
    $eta = trim((string) $eta);
    if ($eta === '' || strcasecmp($eta, 'N/A') === 0 || strcasecmp($eta, 'Unknown') === 0) {
        return null;
    }
    $parts = array_map('intval', explode(':', $eta));
    return match (count($parts)) {
        3 => $parts[0] * 3600 + $parts[1] * 60 + $parts[2],
        2 => $parts[0] * 60 + $parts[1],
        1 => $parts[0],
        default => null,
    };
}

/**
 * Performs a metadata-only yt-dlp run for the quality picker.
 * The process is bounded so a platform that does not answer cannot block the PHP worker forever.
 */
function reeldrop_analyze_url(string $url): ?array
{
    @set_time_limit(50);
    $ytdlp = reeldrop_ytdlp();
    if ($ytdlp === null) {
        return null;
    }

    $parts = [
        escapeshellarg($ytdlp),
        '--dump-single-json',
        '--skip-download',
        '--no-warnings',
        '--no-playlist',
        '--socket-timeout 20',
    ];
    $cookies = (string) reeldrop_config_value('cookies_file', '');
    if ($cookies !== '' && is_file($cookies)) {
        $parts[] = '--cookies ' . escapeshellarg($cookies);
    }
    $parts[] = '--';
    $parts[] = escapeshellarg($url);

    $descriptors = [
        0 => ['pipe', 'r'],
        1 => ['pipe', 'w'],
        2 => ['pipe', 'w'],
    ];
    $process = @proc_open(implode(' ', $parts), $descriptors, $pipes);
    if (!is_resource($process)) {
        return null;
    }

    fclose($pipes[0]);
    stream_set_blocking($pipes[1], false);
    stream_set_blocking($pipes[2], false);
    $stdout = '';
    $stderr = '';
    $deadline = microtime(true) + 45.0;
    $timedOut = false;

    while (true) {
        foreach ([1, 2] as $index) {
            $chunk = @stream_get_contents($pipes[$index]);
            if ($chunk === false || $chunk === '') {
                continue;
            }
            if ($index === 1) {
                $stdout .= $chunk;
            } else {
                $stderr .= $chunk;
            }
        }
        $status = proc_get_status($process);
        if (!$status['running']) {
            break;
        }
        if (microtime(true) >= $deadline) {
            $timedOut = true;
            @proc_terminate($process);
            break;
        }
        usleep(100000);
    }

    foreach ([1, 2] as $index) {
        $chunk = @stream_get_contents($pipes[$index]);
        if (is_string($chunk) && $chunk !== '') {
            if ($index === 1) {
                $stdout .= $chunk;
            } else {
                $stderr .= $chunk;
            }
        }
        @fclose($pipes[$index]);
    }
    @proc_close($process);

    if ($timedOut || trim($stdout) === '') {
        return null;
    }
    $decoded = json_decode(trim($stdout), true);
    if (!is_array($decoded)) {
        $start = strpos($stdout, '{');
        $end = strrpos($stdout, '}');
        if ($start !== false && $end !== false && $end > $start) {
            $decoded = json_decode(substr($stdout, $start, $end - $start + 1), true);
        }
    }
    return is_array($decoded) ? $decoded : null;
}

/** Builds a stable, user-facing list from the formats returned by yt-dlp. */
function reeldrop_quality_options(array $info): array
{
    $options = [[
        'id' => 'best',
        'label' => 'Mejor calidad',
        'description' => 'Máxima disponible · video y audio',
        'height' => null,
        'kind' => 'video',
    ]];
    $heights = [];
    $hasAudio = false;
    foreach (($info['formats'] ?? []) as $format) {
        if (!is_array($format)) {
            continue;
        }
        $height = (int) ($format['height'] ?? 0);
        $videoCodec = strtolower((string) ($format['vcodec'] ?? ''));
        $audioCodec = strtolower((string) ($format['acodec'] ?? ''));
        if ($height > 0 && $videoCodec !== '' && $videoCodec !== 'none') {
            $heights[$height] = true;
        }
        if ($audioCodec !== '' && $audioCodec !== 'none' && ($videoCodec === '' || $videoCodec === 'none')) {
            $hasAudio = true;
        }
    }
    krsort($heights, SORT_NUMERIC);
    foreach (array_slice(array_keys($heights), 0, 10) as $height) {
        $options[] = [
            'id' => (string) $height,
            'label' => $height . 'p',
            'description' => 'Video y audio hasta ' . $height . 'p',
            'height' => $height,
            'kind' => 'video',
        ];
    }
    if ($hasAudio) {
        $options[] = [
            'id' => 'audio',
            'label' => 'Solo audio',
            'description' => 'Audio disponible en el enlace',
            'height' => null,
            'kind' => 'audio',
        ];
    }
    return $options;
}
