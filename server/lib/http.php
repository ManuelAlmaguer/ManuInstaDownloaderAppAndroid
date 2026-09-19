<?php
declare(strict_types=1);

/** JSON/SSE plumbing, CORS and token authentication shared by every endpoint. */

function reeldrop_json(array $payload, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function reeldrop_error(string $message, int $status = 400): never
{
    reeldrop_json(['ok' => false, 'error' => $message], $status);
}

function reeldrop_request_body(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') {
        return [];
    }
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : [];
}

function reeldrop_param(string $key, ?string $default = null): ?string
{
    $value = $_GET[$key] ?? $_POST[$key] ?? null;
    if ($value === null) {
        return $default;
    }
    return is_string($value) ? $value : $default;
}

function reeldrop_token_from_request(): string
{
    $header = $_SERVER['HTTP_X_API_TOKEN'] ?? '';
    if ($header === '' && function_exists('getallheaders')) {
        foreach (getallheaders() as $name => $value) {
            if (strcasecmp((string) $name, 'X-Api-Token') === 0) {
                $header = (string) $value;
                break;
            }
        }
    }
    if ($header !== '') {
        return $header;
    }
    return (string) reeldrop_param('token', '');
}

function reeldrop_require_token(): void
{
    $expected = (string) reeldrop_config_value('api_token', '');
    if ($expected === '') {
        return;
    }
    $provided = reeldrop_token_from_request();
    if (!hash_equals($expected, $provided)) {
        reeldrop_error('Token de API incorrecto o ausente.', 401);
    }
}

function reeldrop_sse_start(): void
{
    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    @ini_set('zlib.output_compression', '0');
    @ini_set('output_buffering', '0');
    @ini_set('implicit_flush', '1');
    set_time_limit(0);

    header('Content-Type: text/event-stream; charset=utf-8');
    header('Cache-Control: no-cache, no-store, must-revalidate');
    header('Pragma: no-cache');
    header('X-Accel-Buffering: no');
    header('Connection: keep-alive');
}

function reeldrop_sse(array $data): void
{
    echo 'data: ' . json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "\n\n";
    @ob_flush();
    @flush();
}

function reeldrop_sse_comment(string $text): void
{
    echo ': ' . $text . "\n\n";
    @ob_flush();
    @flush();
}
