# Python SDK for Voxygen TTS HTTP API

This folder provides lightweight Python tools for interacting with a **Voxygen TTS HTTP API**, including the Voxygen Cloud API (https://api.voxygen.fr/) or local deployments.

---

## Python SDK Structure

```
├─/examples       # Examples of common usages for the Python Client
├─/tts            # Python package
| └─ client.py    # Python Client
├─ speak.py       # Python CLI
└─ README.md      # This documentation
```

---

## Installation

Install the official Voxygen Python client from PyPI:

```bash
pip install voxygen-tts
```

---

## Client usage examples in your own Python application

### Convert text to audio stream
```python
from voxygen.tts import Client

parameters = {
    "text": "Hello. How can I help you ?",
    "language": "en-US",
    "voice": "Mary",
    "header": "headerless",
}

with Client(token="YOUR_TOKEN") as client:
    request = client.build_request(parameters)
    response = client.send(request)

    if client.content_type(response) == voxygen_client.MimeType.AUDIO:
        while data := client.read_data(response):
            print(f"received chunk of audio data: {len(data)} bytes")

```

### Retrieve all available voices from your subscription

```python
from voxygen.tts import Client

with Client(token="YOUR_TOKEN", url="info") as client:

    request = client.build_request({})
    response = client.send(request)

    if response.status == 200:
        info = client.read_json(response)

        print("Available voices:")
        for voice in info.get("voices", []):
            print(voice["name"])
    else:
        print("Error:", response.status, response.read().decode())

```

---

## Command-Line usage examples

The CLI tool is called `voxygen-speak` and can call any compatible Voxygen TTS API.

### **Quick Start example**

```bash
export VOXYGEN_TOKEN="your_token_here"
voxygen-speak 
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Mary \
  -o output.wav
```

>If you don’t want to pass the `-t (--token)` option for each command, you can modify the default value of `token` in `main()` from the `speak.py` file

You can also run the CLI tool directly with Python:
```bash
python speak.py [options]
```

---

### **Generate an audio from a text file**

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
### ** Retrieve from `/tts/info` request **

```bash
voxygen-speak info
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

The Python client automatically retries requests when a **`503 Service Unavailable`** response is returned.

**Default retry settings:**

* **6 retries**
* **10 seconds** between attempts

You can customize this behavior:

```python
client.set_retry_policy(max_retries=2, retry_after=5)
```

> Retries apply only to `503` responses. Other errors are returned immediately.

If the issue persists after multiple retries, wait a few minutes before sending a new request.
