# PHP Examples - Voxygen TTS Client

This directory contains example scripts demonstrating how to use the **Voxygen TTS PHP Client** via the `Client.php` library.

These examples cover common Text-to-Speech workflows:

- Streaming audio data  
- Saving generated audio to a file  
- Retrieving synthesis metadata as JSON  

---

## Server Setup and Authentication

Before running the examples, ensure you have valid credentials and access to a TTS server.

The client requires two parameters:

- **token** – Your authentication token used to send requests  
- **url** – The base URL of the TTS server (by default, `https://api.voxygen.fr/tts`)

Replace the `"YOUR_TOKEN"` placeholder in each script:

```php
$client = new SyntHTTPClient(token: "YOUR_TOKEN",);
```

---

## Examples Overview

### 1. `streaming.php`

Streams audio data directly from the TTS server without storing it locally.

**Use case:**
Ideal for low-latency applications such as conversational agents, telephony backends, or live speech systems.

---

### 2. `audio-response.php`

Generates speech and saves it as a `.wav` file.

**Use case:**
Best suited for batch generation, audio archives, media workflows, or offline playback.

---

### 3. `json-response.php`

Requests synthesis metadata instead of raw audio.

**Use case:**
Useful when you need timing data, warnings, or event markers for subtitles, avatars, analytics pipelines, or synchronization with other media.

---

## Request Parameters Example

All scripts use a parameter array with three mandatory parameters `voice`, `language` and `text` as such:

```php
$parameters = [
    "voice" => "Mary_NTTS",
    "language" => "en-US",
    "text"  => "Hello. How can I help you today?"
];
```

Available voices depend on your subscription.

---

## Running the Examples

From this directory:

```bash
php streaming.php
php audio-response.php
php json-response.php
```

Make sure the path to `Client.php` is correctly configured in each example (typically using `require_once`).

---

## Troubleshooting

**401 / 403 errors**

* Verify your API token is valid and active.

**Connection errors**

* Confirm the API URL is correct and reachable from your network.
* If connecting to a local or on-premise server, ensure the host and port are accessible.

**Unexpected response type**

* Check that the requested `Accept` content type matches the expected output (audio or JSON).

**No audio returned**

* Ensure the selected voice is available for your subscription.

---

For the full PHP SDK documentation, refer to the [Voxygen PHP README](../README.md).
