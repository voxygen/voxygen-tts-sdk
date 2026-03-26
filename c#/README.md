
# .NET SDK for Voxygen TTS HTTP API

This folder provides lightweight **.NET (C#)** tools for interacting with a **Voxygen TTS HTTP API**, including the Voxygen Cloud API (`https://api.voxygen.fr/`) or local deployments.

---

## .NET SDK Structure

```
├─/examples         # Examples of common usages for the Python Client
├─/tts
| └── Client.cs     # C# client
├─ Program.cs       # C# CLI
├─ Speak.csproj     # Project file
├─ Voxygen.sln      # Project solution file
└── README.md       # This documentation
```

---

## Installation

Copy the `/tts` directory and its content into your project and reference it from your code.

---

## Client usage examples in your own .NET application

### Convert text to audio stream

```csharp
using Voxygen.Tts;

// Parameters sent to the /tts endpoint
var parameters = new Dictionary<string, string>
{
    ["text"] = "Hello. How can I help you ?",
    ["language"] = "en-US",
    ["voice"] = "Mary"
};

using Client client = new(token: "YOUR_TOKEN");

var request = client.BuildRequest(parameters);
var response = await client.Send(request);

if (Client.GetContentType(response) == Client.MimeType.AUDIO)
{
    await using var stream = await response.Content.ReadAsStreamAsync();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = await stream.ReadAsync(buffer)) > 0)
    {
        Console.WriteLine($"received chunk of audio data: {read} bytes");
    }
}
```

---

### Retrieve all available voices from your subscription

This call targets `/tts/info` and returns JSON metadata such as available voices.

```csharp
using System.Text.Json;
using Voxygen.Tts;

using Client client = new(token: "YOUR_TOKEN", url: "info");

client.SetAcceptContentType(Client.MimeType.JSON);

var request = client.BuildRequest(new Dictionary<string, string>());
var response = await client.Send(request);

if (response.IsSuccessStatusCode)
{
    var json = await response.Content.ReadAsStringAsync();
    using var doc = JsonDocument.Parse(json);

    if (doc.RootElement.TryGetProperty("voices", out var voices))
    {
        Console.WriteLine("Available voices:");
        foreach (var v in voices.EnumerateArray())
            Console.WriteLine(v.GetProperty("name").GetString());
    }
}
else
{
    Console.WriteLine($"Error: {(int)response.StatusCode} {response.ReasonPhrase}");
    Console.WriteLine(await response.Content.ReadAsStringAsync());
}
```

---

## CLI Installation

### 1. Install required NuGet packages

This SDK uses:

* Newtonsoft JSON
* IPNetwork utility class

Install them:

```bash
dotnet add package Newtonsoft.Json
dotnet add package System.Net.IPNetwork
```

---

### 2. Build

```bash
dotnet build
```

## Command-Line usage examples

The `speak` CLI tool can call any compatible Voxygen TTS API.

### Quick Start example

```bash
export VOXYGEN_TOKEN="your_token_here"
dotnet run -- \
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Mary \
```
> If you don’t want to pass the `-t (--token)` option for each command, you can modify the default value of `token` near the beginning of the `Program.cs` file.

---

### Generate audio from a text file

```bash
dotnet run -- \
  -i input-text.txt \
  -p voice=Mary \
  -o output.wav
```

---

### JSON response mode

```bash
dotnet run -- \
  -j \
  -p text="Hello as JSON" \
  -p voice=Mary
```
---

### Retrieve from `/tts/info`

```bash
dotnet run -- info
```

---

## CLI options

| Option         | Long                | Description                             |
| -------------- | ------------------- | --------------------------------------- |
| `-t TOKEN`     | `--token TOKEN`     | Bearer token authentication             |
| `-p key=value` | `--param key=value` | TTS parameters (repeatable)             |
| `-i file`      | —                   | Read text from file (overrides `text=`) |
| `-o file`      | —                   | Save audio to file                      |
| `-j`           | `--json`            | Request JSON response                   |
| `-h`           | `--help`            | Help text                               |

---

## Troubleshooting

### **401 Unauthorized**

Common causes:

* Token not provided
* Invalid token
* Token expired or revoked

---

### **400 Bad Request**

Common causes:

* Missing required TTS parameters
* Incorrect endpoint URL (for example, missing or extra `/tts`)
* Unsupported parameter values

---

### **503 Service Unavailable**

The service may be temporarily unavailable due to high load or maintenance.

The .NET client automatically retries requests when a **`503 Service Unavailable`** response is returned.

**Default retry settings:**

* **6 retries**
* **10 seconds** between attempts

You can customize this behavior:

```csharp
client.SetRetryPolicy(maxRetries: 2, retryAfter: TimeSpan.FromSeconds(5));
```

> Retries apply only to `503` responses. Other errors are returned immediately.

If the issue persists after multiple retries, wait a few minutes before sending a new request.
