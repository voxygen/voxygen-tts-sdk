# Java Examples - Voxygen TTS Client

This directory contains example scripts demonstrating how to use the **Voxygen TTS Java Client**.

These examples cover common Text-to-Speech workflows:

* Streaming audio data
* Saving generated audio to a file
* Retrieving synthesis metadata as JSON

---

## Server Setup and Authentication

Before running the examples, make sure you have valid credentials and access to a TTS server.

The client requires two parameters:

* **token** – Your authentication token used to send requests
* **url** – The base URL or endpoint path of the TTS server (by default `https://api.voxygen.fr/tts`)

Replace the `"YOUR_TOKEN"` placeholder in each example:

```java
Client client = new Client("YOUR_TOKEN", "")
```
---

## Examples Overview

### 1. `StreamingExample.java`

Streams audio data directly from the TTS server without storing it locally.

**Use case:**
Ideal for low-latency applications such as conversational agents, telephony backends, or live speech systems.

---

### 2. `AudioFileExample.java`

Generates speech and saves it as a `.wav` file.

**Use case:**
Best for batch generation, audio archives, media workflows, or offline playback.

---

### 3. `JsonResponseExample.java`

Requests synthesis metadata instead of raw audio.

**Use case:**
Useful when you need timing data, warnings, or event markers for subtitles, avatars, analytics pipelines, or synchronization with other media.

---

## Request Parameters Example

All scripts use a parameter map with three mandatory parameters `voice`, `language`, and `text` as follows:

```java
Map<String, String> parameters = new HashMap<>();
parameters.put("voice", "Mary");
parameters.put("language", "en-US");
parameters.put("text", "Hello. How can I help you today ?");
```

Available voices depend on your subscription and can be retrieved by sending a request to the `/tts/info` endpoint (for example `https://api.voxygen.fr/tts/info`).

---

## Running the Examples

From the `java` directory:

### 1. Compile the client and examples

```bash
javac -cp json-20251224.jar $(find . -name "*.java")
```

### 2. Run an example

After compilation, run any example using:

```bash
java -cp .:json-20251224.jar examples.<ExampleClassName>
```

The `json-20251224.jar` dependency is provided in the root of the `java/` directory and must be included in the classpath when compiling and running.

---

For the full Java SDK documentation, refer to the [main repository README](../README.md).
