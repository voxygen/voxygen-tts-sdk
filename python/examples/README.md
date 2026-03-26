# Python Examples - Voxygen TTS Client

This directory contains example scripts demonstrating how to use the **Voxygen TTS Python Client** via the [`voxygen`](https://test.pypi.org/project/voxygen-tts/) Pypi package.

These examples cover common Text-to-Speech workflows:

- Streaming audio data
- Saving generated audio to a file
- Retrieving synthesis metadata as JSON

---

## Server Setup and Authentication

Before running the examples, make sure you have valid credentials and access to a TTS server.

The client requires two parameters:

- **token** – Your authentication token used to send requests  
- **url** – The base URL of the TTS server (by default `https://api.voxygen.fr/tts`)

Replace the `"YOUR_TOKEN"` placeholder in each script:

```python
token="YOUR_TOKEN"
```

---

## Examples Overview

### 1. `streaming.py`

Streams audio data directly from the TTS server without storing it locally.

**Use case:**
Ideal for low-latency applications such as conversational agents or live systems.

---

### 2. `audio-response.py`

Generates speech and saves it as a `.wav` file.

**Use case:**
Best for batch generation, audio archives, or offline playback.


---

### 3. `json-response.py`

Requests synthesis metadata instead of raw audio.

**Use case:**
Useful when you need timing data, warnings, or event markers for subtitles, avatars, or analytics.

---

## Request Parameters Example

All scripts use a parameter array with three mandatory parameters `voice`, `language` and `text` as such:

```python
parameters = {
    "voice": "Mary",
    "language": "en-US",
    "text": "Hello. How can I help you today ?"
}
```

Available voices depend on your subscription and can be retrieved by sending a request to the `/tts/info` endpoint (e.g. `https://api.voxygen.fr/tts/info`).

---

For the full Python SDK documentation, refer to the [Voxygen Python README](../README.md).
