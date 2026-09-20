<?php
declare(strict_types=1);

/**
 * Loads config.php (when present) merged over the safe defaults.
 */

function reeldrop_config(): array
{
    static $config = null;
    if ($config !== null) {
        return $config;
    }

    $defaults = [
        'api_token' => '',
        'downloads_dir' => dirname(__DIR__) . '/downloads',
        'data_dir' => dirname(__DIR__) . '/data',
        'logs_dir' => dirname(__DIR__) . '/logs',
        'max_concurrent_jobs' => 3,
        'ytdlp_path' => '',
        'ffmpeg_path' => '',
        'ffprobe_path' => '',
        'cookies_file' => '',
        'default_quality' => 'best',
        'retention_days' => 0,
        'allowed_hosts' => [
            'instagram.com', 'instagr.am', 'ig.me',
            'youtube.com', 'youtu.be', 'youtube-nocookie.com',
            'facebook.com', 'fb.watch',
        ],
        'stale_job_seconds' => 45,
        'app_version' => '2.1.0',
    ];

    $file = dirname(__DIR__) . '/config.php';
    $user = [];
    if (is_file($file)) {
        $loaded = require $file;
        if (is_array($loaded)) {
            $user = $loaded;
        }
    }

    $config = array_merge($defaults, $user);
    // Older config.php files only listed Instagram. Keep any custom entries, but always
    // include the first-party platforms supported by this release during the migration.
    $config['allowed_hosts'] = array_values(array_unique(array_merge(
        (array) $defaults['allowed_hosts'],
        (array) ($user['allowed_hosts'] ?? []),
    )));
    return $config;
}

function reeldrop_config_value(string $key, mixed $default = null): mixed
{
    $config = reeldrop_config();
    return $config[$key] ?? $default;
}

/** Creates the runtime folders on first run so nothing has to be configured by hand. */
function reeldrop_bootstrap_dirs(): void
{
    foreach (['downloads_dir', 'data_dir', 'logs_dir'] as $key) {
        $dir = (string) reeldrop_config_value($key, '');
        if ($dir === '') {
            continue;
        }
        if (!is_dir($dir)) {
            @mkdir($dir, 0775, true);
        }
        foreach (['jobs', 'meta', 'thumbs'] as $sub) {
            if ($key === 'data_dir' && !is_dir($dir . '/' . $sub)) {
                @mkdir($dir . '/' . $sub, 0775, true);
            }
        }
    }
}
