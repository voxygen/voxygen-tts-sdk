# Node.js Examples - Voxygen TTS Client

This directory contains example scripts demonstrating how to use the **Voxygen TTS Node.js Client** via the `@voxygen/tts` package.

These examples cover common Text-to-Speech workflows:

- Streaming audio data  
- Saving generated audio to a file  
- Retrieving synthesis metadata as JSON  

---

## Server Setup and Authentication

Before running the examples, ensure you have valid credentials and access to a TTS server.

The client requires two parameters:

- **token** – Your authentication token used to send requests  
- **url** – The base URL of the TTS server (by default: `https://api.voxygen.fr/tts`)

Replace the `"YOUR_TOKEN"` placeholder in each script:

```js
const client = new Client("YOUR_TOKEN");
```

---

## Examples Overview

### 1. `streaming.js`

Streams audio data directly from the TTS server without storing it locally.

**Use case:**
Ideal for low-latency applications such as conversational agents, telephony services, real-time assistants, or live speech systems.

---

### 2. `audio-response.js`

Generates speech and saves it as a `.wav` file.

**Use case:**
Best suited for batch generation, audio archives, media workflows, server-side audio creation, or offline playback.

---

### 3. `json-response.js`

Requests synthesis metadata instead of raw audio.

**Use case:**
Useful when you need timing data, warnings, or event markers for subtitles, avatars, analytics pipelines, or synchronization with other media.

---

## Request Parameters Example

All scripts use a parameter array with three mandatory parameters `voice`, `language` and `text` as such:

```js
const parameters = {
  voice: "Mary",
  language: "en-US",
  text: "Hello. How can I help you today?"
};
```

Available voices depend on your subscription and can be retrieved by sending a request to the `/tts/info` endpoint (for example `https://api.voxygen.fr/tts/info`).

---

## Running the Examples

From this directory:

```bash
node streaming.js
node audio-response.js
node json-response.js
```

>Ensure you installed the `@voxygen/tts` package beforehand.

You can install this package using npm:

```bash
npm install @voxygen/tts
```

---

For the full Node.js SDK documentation, refer to the [Voxygen Node.js README](../README.md).
