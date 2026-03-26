# Java SDK for Voxygen TTS HTTP API

This folder provides lightweight Java tools for interacting with a **Voxygen TTS HTTP API**, including the Voxygen Cloud API ([https://api.voxygen.fr/](https://api.voxygen.fr/)) or local deployments.

---

## Java SDK Structure

```
├─ examples/                        # Examples of common usages for the Java client
├─ voxygen/
│  ├─ synthttpclient/
│  │  ├─ Main.java                  # Java CLI
│  │  └─ SyntHTTPClient.java        # Java client
│  └─ url/data/                     # Support for data: URLs (used when JSON response returns data:audio/...)
├─ json-20251224.jar                # org.json dependency
└─ README.md                        # This documentation
```

---

## Installation

### Requirements

* **Java 13 or later** (required for text blocks used in the CLI usage text)
* **org.json** dependency (JSON-java)

You can download JSON-java here: [https://github.com/stleary/JSON-java](https://github.com/stleary/JSON-java)
(Use the latest release `.jar`, or compile it from source.)

You can also directly use the jar provided in this repository: `json-20251224.jar`.

---

### Compilation

From the `java/` directory:

```bash
javac -Xlint -cp json-20251224.jar $(find . -name "*.java")
```

---

## Client usage examples in your own Java application

### Convert text to audio stream

```java
import voxygen.tts.Client;

import java.util.HashMap;
import java.util.Map;

public class Streaming {

    public static void main(String[] args) {
 
        Map<String, String> parameters = new HashMap<>();
        parameters.put("voice", "Mary");  // Select a voice available on your subscription
        parameters.put("language", "en-US");
        parameters.put("text", "Hello. How can I help you today ?");  // Text to synthesize
        parameters.put("header", "headerless");

        try (Client client = new Client("YOUR_TOKEN","")) {

            client.open();

            Map<String, String> request = client.buildRequest(parameters);
            client.send(request);

            byte[] chunk;
            while ((chunk = client.readData()).length > 0) {
                System.out.println("received chunk of audio data: " + chunk.length + " bytes");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

### Retrieve all available voices from your subscription
```java
import voxygen.tts.Client;
import voxygen.tts.Client.MimeType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Info {

    public static void main(String[] args) {

        try (Client client = new Client("YOUR_TOKEN", "info")) {

            client.setAcceptContentType(MimeType.JSON);
            client.open();
            Map<String, String> request = client.buildRequest(new HashMap<>());
            client.send(request);

            JSONObject payload = client.readJson();
            JSONArray voices = payload.getJSONArray("voices");

            System.out.println("Available voices:");
            for (int i = 0; i < voices.length(); i++) {
                JSONObject voice = voices.getJSONObject(i);
                if (voice.has("name")) {
                    System.out.println(voice.getString("name"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## Command-Line usage examples

The CLI tool can call any compatible Voxygen TTS API.

### **Quick Start example**

```bash
export VOXYGEN_TOKEN="your_token_here"
java -cp .:json-20251224.jar voxygen.speak.Main \
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Mary 
```
>If you don’t want to pass the `-t (--token)` option for each command, you can modify the default value of `token` near the beginning of the `Main.java` file inside the `speak` package.

---

### **Generate an audio from a text file**

```bash
java -cp .:json-20251224.jar voxygen.speak.Main \
  -i input-text.txt \
  -p voice=Mary \
  -o output.wav 
```

---

### **JSON response mode**

```bash
java -cp .:json-20251224.jar voxygen.speak.Main \
  -j \
  -p text="Hello as JSON" \
  -p voice=Mary
```

---

### **Retrieve from `/tts/info` request**

```bash
java -cp .:json-20251224.jar voxygen.speak.Main info
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

The Java client automatically retries requests when a **`503 Service Unavailable`** response is returned.

Default retry settings:

* **6 retries**
* **10 seconds** between attempts

You can customize this behavior:

```java
client.setRetryPolicy(2, 5);
```

Retries apply only to `503` responses. Other errors are returned immediately.

If the issue persists after multiple retries, wait a few minutes before sending a new request.
