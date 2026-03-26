# PHP SDK for Voxygen TTS HTTP API

This folder provides lightweight PHP tools for interacting with a **Voxygen TTS HTTP API**, including the Voxygen Cloud API (https://api.voxygen.fr/) or local deployments.


---

## PHP SDK Structure

```
├─/examples             # Examples of common usages for the PHP client
├─/tts
    └─ Client.php       # PHP client
├─/php-urljoin          # Required 'urljoin' PHP library       
├─ speak.php            # PHP CLI
└─ README.md            # This documentation
```

---

## Installation & Prerequisites

Requires **PHP 8.1 or later** (enums are used).

### 1. Install Dependencies

This project requires the `urljoin` dependency.

It is already included in the `urljoin/` directory, or you may install/provide your own compatible version if preferred.

---

## Client usage example in your own PHP application

### Convert text to audio stream

```php
<?php

require 'tts/Client.php';

use Voxygen\TTS\Client;
use Voxygen\TTS\ClientMimeType as MimeType;

$parameters = [
    "voice" => "Mary",      // Select a voice available on your subscription
    "language" => "en-US",
    "text"  => "Hello. How can I help you today ?",     // Text to synthesize
    "header" => "headerless",
];

$client = new Client(token: "YOUR_TOKEN");

$query  = $client->buildRequest($parameters);
$stream = $client->open($query);
$headers = Client::response_headers($stream);

$chunkSize = 4096;
while (!feof($stream)) {
    $data = fread($stream, $chunkSize);
    if ($data === false || $data === '') {
        break;
    }
    echo "received chunk of audio data: " . strlen($data) . " bytes" . PHP_EOL;
}

Client::close($stream);
```

### Retrieve all available voices from your subscription

```php
<?php
require 'tts/Client.php';

use Voxygen\TTS\Client;

$client = new Client("YOUR_TOKEN", "info");

$request = $client->buildRequest(["event" => "0"]);
$stream  = $client->open($request);

$headers = Client::response_headers($stream);
$status  = $headers["response_status"] ?? 0;

if ($status !== 200) {
    echo "Error: $status\n";
    echo stream_get_contents($stream);
    Client::close($stream);
    exit(1);
}

$payload = json_decode(stream_get_contents($stream), true);

if (is_array($payload) && isset($payload["voices"]) && is_array($payload["voices"])) {
    echo "Available voices:\n";
    foreach ($payload["voices"] as $voice) {
        if (is_array($voice) && isset($voice["name"])) {
            echo $voice["name"] . "\n";
        }
    }
}

Client::close($stream);
```

---

## Command-Line usage examples

The `speak` CLI tool can call any compatible Voxygen TTS webservice, either built locally or using the Voxygen Cloud service.

### **Quick Start example**

```bash
export VOXYGEN_TOKEN="your_token_here"
php -f speak.php -- \
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Judith \
  -o output.wav \
  ```

>If you don’t want to pass the `-t (--token)` option for each command, you can modify the default value of `token` near the beginning of the `speak.php` file

---

### **Text from file**

```bash
php -f speak.php -- \
  -i input-text.txt \
  -o output.wav
```

---

### **JSON response mode**

```bash
php -f speak.php -- \
  -j \
  -p text="Hello as JSON" \
  -p voice=Mary
```
---

### **Retrieve from `/tts/info` request**

```bash
php -f speak.php -- info
```

---

## CLI options

| Option         | Long                | Description                 |
| -------------- | ------------------- | --------------------------- |
| `-t TOKEN`     | `--token TOKEN`     | Bearer token authentication |
| `-p key=value` | `--param key=value` | TTS parameters              |
| `-i file`      | —                   | Read text from file         |
| `-o file`      | —                   | Save audio to file          |
| `-j`           | `--json`            | Request JSON response       |
| `-h`           | `--help`            | Help text                   |

---

## Troubleshooting

### **401 Unauthorized**

* Token not provided
* Wrong token
* Token expired or revoked

---

### **400 Bad Request**

Common causes:

* Missing required TTS parameters
* Incorrect endpoint URL (e.g., missing or extra `/tts`)
* Unsupported parameter values

### **503 Service Unavailable**

The service may be temporarily unavailable due to high load or maintenance.

The .NET client automatically retries requests when a **`503 Service Unavailable`** response is returned.

**Default retry settings:**

* **6 retries**
* **10 seconds** between attempts

You can customize this behavior:

```php
$client->setRetryPolicy(2, 30);
```

> Retries apply only to `503` responses. Other errors are returned immediately.

If the issue persists after multiple retries, wait a few minutes before sending a new request.
