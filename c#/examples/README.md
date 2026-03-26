# C# Examples - Voxygen TTS Client

This directory contains example scripts demonstrating how to use the **Voxygen TTS C# Client**.

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

```csharp
using Client client = new("YOUR_TOKEN");
```
---

## Examples Overview

### 1. `StreamingExample.cs`

Streams audio data directly from the TTS server without storing it locally.

**Use case:**
Ideal for low-latency applications such as conversational agents, telephony backends, or live speech systems.

---

### 2. `AudioResponseExample.cs`

Generates speech and saves it as a `.wav` file.

**Use case:**
Best for batch generation, audio archives, media workflows, or offline playback.

---

### 3. `JsonResponseExample.cs`

Requests synthesis metadata instead of raw audio.

**Use case:**
Useful when you need timing data, warnings, or event markers for subtitles, avatars, analytics pipelines, or synchronization with other media.

---

## Request Parameters Example

All scripts use a parameter dictionary with three mandatory parameters `voice`, `language`, and `text` as follows:

```csharp
var parameters = new Dictionary<string, string>
{
    ["voice"] = "Mary",
    ["language"] = "en-US",
    ["text"] = "Hello. How can I help you today ?"
};
```

Available voices depend on your subscription and can be retrieved by sending a request to the `/tts/info` endpoint (for example `https://api.voxygen.fr/tts/info`).

---

## Running the Examples

Each example is a simple .NET console application.

1. Create a new project:

```bash
dotnet new console -n VoxygenExample
cd VoxygenExample
```

2. Copy the `/tts` and its content into the project folder.

3. Replace the contents of `Program.cs` with one of the example files and set your API token.

4. Run:

```bash
dotnet run
```

---

For the full C# SDK documentation, refer to the [main repository README](../README.md).
