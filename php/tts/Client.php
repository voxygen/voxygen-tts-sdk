<?php

declare(strict_types=1);

namespace Voxygen\TTS;

require __DIR__ . '/../php-urljoin/urljoin.php';

### ------------------------------------------------------------------------ ###

enum ClientMimeType
{
    case PLAIN_TEXT;
    case AUDIO;
    case JSON;
    case URL_ENCODED;
    public function toString(): string
    {
        return match ($this) {
            ClientMimeType::PLAIN_TEXT => "PLAIN_TEXT",
            ClientMimeType::AUDIO => "AUDIO",
            ClientMimeType::JSON => "JSON",
            ClientMimeType::URL_ENCODED => "URL_ENCODED",
            default => "",
        };
    }
}

class Client
{

    private $token;
    private $url;
    private $max_retries;
    private $retry_after;
    private $retry_on_error = false;
    private $ssl_options = array();
    private $body_type;
    private $accept_type;

    public function __construct(string $token, string $url = '')
    {
        if (!$token)
            throw new \Exception("token must be provided");
        $this->token = $token;
        $url = urljoin('https://api.voxygen.fr/tts/', $url);
        $this->url = parse_url($url);
        if (!is_array($this->url))
            throw new \Exception("failed to parse URL");
        if (!array_key_exists('scheme', $this->url))
            $this->url['scheme'] = 'https';
        if (!array_key_exists('host', $this->url))
            $this->url['host'] = 'localhost';
        $ip = gethostbyname($this->url['host']);
        if (array_key_exists('port', $this->url))
            $this->url['host'] = implode(':', [$this->url['host'], $this->url['port']]);
        if (!array_key_exists('path', $this->url))
            $this->url['path'] = '';
        // default retry policy : 6 times with a 10 second wait
        $this->setRetryPolicy(6, 10 /* seconds */);
        // default request content type : JSON
        $this->setRequestContentType(ClientMimeType::JSON);
        // default accept content type : AUDIO
        $this->setAcceptContentType(ClientMimeType::AUDIO);
        if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE) === false) {
            // certificate checking is disabled for connections on local network, allowing self-certification
            $this->ssl_options = array(
                'verify_peer_name' => false,
                'verify_peer' => false,
                'allow_self_signed' => true
            );
        }
    }

    public function setRetryPolicy(int $max_retries, int $retry_after)
    {
        $this->max_retries = $max_retries;
        $this->retry_after = $retry_after;
    }

    public function setRequestContentType(ClientMimeType $mime_type)
    {
        $this->body_type = $mime_type;
    }

    public function setAcceptContentType(ClientMimeType $mime_type)
    {
        $this->accept_type = $mime_type;
    }

    public function buildRequest(array $arguments): array
    {
        $query = array();
        // initialize query from host url
        if (array_key_exists('query', $this->url)) {
            parse_str($this->url['query'], $queryParams);
            foreach ($queryParams as $key => $value) {
                $query[$key] = $value;
            }
        }
        // add arguments to request query (argument values take priority over existing parameters)
        foreach ($arguments as $key => $value) {
            $query[$key] = $value;
        }
        // NOTE: rfc2046 section-4.1.1 "MUST always represent a line break as a CRLF sequence"
        foreach ($query as $key => $value) {
            if (is_string($value)) {
                $query[$key] = preg_replace("/(\r?\n)/", "\r\n", $value);
            }
        }
        return $query;
    }

    private function e_warning_handler(int $errno, string $errstr, string $errfile, int $errline): bool
    {
        if (str_ends_with(trim($errstr), '503 Service Unavailable')) {
            sleep($this->retry_after);
            $this->retry_on_error = true;
            return true;
        }
        return false;
    }

    public function open(array $query)
    {
        $this->retry_on_error = false;
        $url = $this->url['scheme'] . '://' . $this->url['host'] . $this->url['path'];
        switch ($this->body_type) {
            case ClientMimeType::JSON:
                // NOTE: rfc8259 section-8.1 "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
                $contentType = 'application/json';
                $content = json_encode($query);
                break;
            case ClientMimeType::URL_ENCODED:
                $contentType = 'application/x-www-form-urlencoded; charset=utf-8';
                $content = http_build_query($query);
                break;
            default:
                throw new \Exception("unsupported body type");
                break;
        }
        switch ($this->accept_type) {
            case ClientMimeType::AUDIO:
                $acceptHeader = 'audio/*; q=1.0, application/octet-stream; q=0.8, */*; q=0.1';
                break;
            case ClientMimeType::JSON:
                $acceptHeader = 'application/json, */*; q=0.1';
                break;
            default:
                throw new \Exception("unsupported accept content type");
                break;
        }
        $httpheaders = [
            "Content-Type: $contentType",
            "Accept: $acceptHeader",
            "Authorization: Bearer $this->token",
            "User-Agent: Voxygen-TTS-Client/1.4.0 (php)"
        ];
        $options = [
            'ssl' => $this->ssl_options,
            'http' => [
                'header'  => $httpheaders,
                'method'  => 'POST',
                'content' => $content,
            ],
        ];
        $context = stream_context_create($options);
        $old_error_handler = set_error_handler(array($this, 'e_warning_handler'), E_WARNING);
        $retries = 0;
        do {
            $stream = fopen($url, 'r', false, $context);
            $retries++;
        } while (!$stream && $this->retry_on_error && $retries <= $this->max_retries);
        set_error_handler($old_error_handler, E_WARNING);
        return $stream;
    }

    public static function response_headers($stream): array
    {
        $meta_data = stream_get_meta_data($stream);
        $headers = array();
        foreach (array_values($meta_data['wrapper_data']) as $data) {
            $kv = explode(':', $data, 2);
            if (isset($kv[1])) {
                $headers[strtolower(trim($kv[0]))] = trim($kv[1]);
            } else if (preg_match("#HTTP/[0-9\.]+\s+([0-9]+)(.*)#", $data, $out)) {
                $headers['response_status'] = intval($out[1]);
                $headers['response_reason'] = trim($out[2]);
            } else {
                $headers[] = $data;
            }
        }
        return $headers;
    }

    public static function content_type(array $headers): ClientMimeType
    {
        if (array_key_exists('content-type', $headers)) {
            $contentType = $headers['content-type'];
            $mediaType = explode(';', $contentType, 2);
            if (str_starts_with($mediaType[0], 'audio/') || $mediaType[0] === 'application/octet-stream') {
                return ClientMimeType::AUDIO;
            } else if ($mediaType[0] === 'application/json') {
                return ClientMimeType::JSON;
            }
        }
        return ClientMimeType::PLAIN_TEXT;
    }

    public static function close($stream)
    {
        fclose($stream);
    }
}

### ------------------------------------------------------------------------ ###
