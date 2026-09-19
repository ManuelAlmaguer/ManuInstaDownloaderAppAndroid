<?php
/**
 * ReelDrop server configuration.
 *
 * Copy this file to `config.php` and edit it:
 *
 *     cp config.example.php config.php
 *
 * Every value is optional: the defaults are tuned for Termux on the same phone
 * where the Android app runs.
 */

return [
    /**
     * Optional shared secret. When set, every request must send it either as
     * `X-Api-Token: <token>` header (the app does this automatically) or as
     * `?token=<token>` (useful from a browser).
     *
     * Generate one with:  openssl rand -hex 16
     * Leave empty ONLY if the server never leaves 127.0.0.1.
     */
    'api_token' => '',

    /** Where downloaded videos are stored (served to the app and the web UI). */
    'downloads_dir' => __DIR__ . '/downloads',

    /** Internal state: job queue, metadata sidecars and thumbnails. */
    'data_dir' => __DIR__ . '/data',

    /** Trace log of every yt-dlp run (one file per job). */
    'logs_dir' => __DIR__ . '/logs',

    /** How many downloads may run at the same time on the server. */
    'max_concurrent_jobs' => 3,

    /** Leave empty to auto-detect yt-dlp/ffmpeg (Termux, PATH, common locations). */
    'ytdlp_path' => '',
    'ffmpeg_path' => '',
    'ffprobe_path' => '',

    /**
     * Optional: path to a cookies.txt exported from a logged-in browser.
     * Needed for private accounts or age-restricted content.
     */
    'cookies_file' => '',

    /** Quality used when the app does not send one: best|1080|720|480|audio */
    'default_quality' => 'best',

    /** Delete files older than N days (0 = keep everything). */
    'retention_days' => 0,

    /** Only these hosts are accepted, as a safety net against SSRF. */
    'allowed_hosts' => ['instagram.com', 'instagr.am', 'ig.me', 'www.instagram.com'],

    /** Seconds a job may stay in "downloading" without a heartbeat before it is marked failed. */
    'stale_job_seconds' => 45,
];
