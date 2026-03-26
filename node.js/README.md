# Node.js SDK for Voxygen TTS HTTP API

This repository provides lightweight Node.js tools for interacting with a **Voxygen TTS API**, including the Voxygen Cloud API at `api.voxygen.fr` or local deployments.

---

## Voxygen Node.js repository structure

```
├─/examples         # Examples of common usages for Node.js
├─/tts
│ └─ Client.mjs     # Node.js client
├─ package.json     # Package configuration
├─ README.md        # This documentation
└─ speak.mjs        # Node.js CLI
```

---

## Installation & prerequisites

### 1. Node.js requirements

Requires **Node.js 18 or later** (native fetch() and Web Streams are used).

---
### 2. Install `@voxygen/tts`

Using npm:

```bash
npm install @voxygen/tts
```

Using yarn:

```bash
yarn add @voxygen/tts
```

---
## Client usage examples in your own Node.js application

### Convert text to audio stream
```javascript
import Client from "@voxygen/tts";

const parameters = {
    voice: "Mary",    // Select a voice available on your subscription
    language: "en-US",                      
    text: "Hello. How can I help you today ?",    // Text to synthesize
    header: "headerless",
};

const client = new Client("YOUR_TOKEN");

const request = await client.buildRequest(parameters);
const response = await client.fetch(request);
  
const reader = response.body.getReader();

while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    if (value) {
        console.log(`received chunk of audio data: ${value.byteLength} bytes`);
    }
}
```

### Retrieve all available voices from your subscription
```javascript
import Client from "@voxygen/tts";

const client = new Client("YOUR_TOKEN","info");
const request = await client.buildRequest({});
const response = await client.fetch(request);

const payload = await response.json();

if (Array.isArray(payload?.voices)) {
    console.log("Available voices:");
    for (const voice of payload.voices) {
        console.log(voice.name);
    }
}

```

---

## Command-Line usage examples

The CLI tool is called `voxygen-speak` and can call any compatible Voxygen TTS API.

### **Quick Start example**

```bash
export VOXYGEN_TOKEN="your_token_here"
npx @voxygen/tts voxygen-speak \
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Mary \
  -o output.wav
```

If installed globally via `npm install -g @voxygen/tts`:

```bash
export VOXYGEN_TOKEN="your_token_here"
voxygen-speak \
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Mary \
  -o output.wav
```
>If you don’t want to pass the `-t (--token)` option for each command, you can modify the default value of `token` near the beginning of the `speak.mjs` file.
---

### **Convert text from file**

```bash
voxygen-speak \
  -i input-text.txt \
  -o output.wav
```

---

### **JSON response mode**

```bash
voxygen-speak \
  -j \
  -p text="Hello as JSON" \
  -p voice=Mary
```

---

### **Retrieve from `/tts/info` request**

```bash
voxygen-speak info
```

---

## CLI Options

| Option         | Long                | Description                 |
| -------------- | ------------------- | --------------------------- |
| `-t TOKEN`     | `--token TOKEN`     | Bearer token authentication |
| `-p key=value` | `--param key=value` | TTS parameters              |
| `-i file`      | —                   | Convert text from file      |
| `-o file`      | —                   | Save audio to file          |
| `-j`           | `--json`            | Request JSON response       |
| `-h`           | `--help`            | Help text                   |

---

## Troubleshooting

### **401 Unauthorized**

Common causes:

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

```javascript
client.setRetryPolicy(2, 30);
```

If the issue persists after multiple retries, wait a few minutes before sending a new request.
