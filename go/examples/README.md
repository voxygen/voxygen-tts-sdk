# Go Examples - Voxygen TTS Client

This directory contains example programs demonstrating how to use the **Voxygen TTS Go Client** provided in this repository (`tts/client.go`).

These examples cover common Text-to-Speech workflows:

* Streaming audio data
* Saving generated audio to a file
* Retrieving synthesis metadata as JSON

---

## Server Setup and Authentication

Before running the examples, make sure you have valid credentials and access to a TTS server.

The client requires two parameters:

* **token** – Your authentication token used to send requests
* **url** – The base URL of the TTS server (by default `https://api.voxygen.fr/tts`)

Replace the `"YOUR_TOKEN"` placeholder in each example:

```go
client, err := tts.NewClient("YOUR_TOKEN", "")
```

---

## Running the Examples

From the `go/` directory (where `go.mod` is located):

```bash
go run ./examples/streaming
go run ./examples/audio-file
go run ./examples/json-response
```

Alternatively, you can build them:

```bash
go build -o bin/streaming ./examples/streaming
./bin/streaming
```

---

## Examples Overview

### 1. `streaming`

Streams audio data directly from the TTS server without storing it locally.

**Use case:**
Ideal for low-latency applications such as conversational agents, live systems, or real-time processing.

---

### 2. `audio-file`

Generates speech and saves it as a `.wav` file.

This example uses the `header=wav-stream-header` parameter to directly retrieve a valid WAV file from the HTTP response stream.

**Use case:**
Best for batch generation, audio archives, or offline playback.

---

### 3. `json-response`

Requests synthesis metadata instead of raw audio.

**Use case:**
Useful when you need timing data, warnings, or event markers for subtitles, avatars, or analytics.

---

## Request Parameters Example

All examples use a parameter map including three essential parameters:

```go
parameters := map[string]string{
	"voice":    "Mary",
	"language": "en-US",
	"text":     "Hello. How can I help you today ?",
}
```

Available voices depend on your subscription and can be retrieved by sending a request to the `/tts/info` endpoint (e.g. `https://api.voxygen.fr/tts/info`).

---

For the full Go SDK documentation, refer to the [main repository README](../README.md).
