# Go SDK for Voxygen TTS HTTP API

This folder provides lightweight Go tools for interacting with a **Voxygen TTS HTTP API**, including the Voxygen Cloud API ([https://api.voxygen.fr/](https://api.voxygen.fr/)) or compatible deployments.

---

## Go SDK Structure

```
├─/examples				# Examples of common usages for the Go Client
├─/tts
│  └─ client.go   # Go client (package tts)
├─ go.mod         # Go module definition (module voxygen)
├─ speak.go       # Go CLI
└─ README.md      # This documentation
```

---

## Installation

Use the SDK directly from this repository.

This folder is a Go module:

* `module voxygen` (see `go.mod`)

So you can either:

* work from this repository directly, or
* copy the `tts/` package into your own Go project.

---

## Client usage examples in your own Go application

### Convert text to audio stream

```go
import (
	"fmt"
	"io"
	"log"
	"voxygen/tts"
)

func main() {

	parameters := map[string]string{
		"voice": "Mary",
    "language": "en-US",
		"text":  "Hello. How can I help you today ?",
		"header": "headerless",
	}

	client, err := tts.NewClient("YOUR_TOKEN", "")
	if err != nil {
		log.Fatal(err)
	}

	request := client.BuildRequest(parameters)

	resp, err := client.Send(request)
	if err != nil {
		log.Fatal(err)
	}
	defer resp.Body.Close()

	mimeType, err := tts.ContentType(resp)
	if err != nil {
		log.Fatal(err)
	}

  buf := make([]byte, 4096)

  for {
    n, rerr := resp.Body.Read(buf)

    if n > 0 {
      fmt.Printf("received chunk of audio data: %d bytes\n", n)
    }

    if rerr == io.EOF {
      break
    }

    if rerr != nil {
      log.Fatal(rerr)
    }
  }
}
```

---

### Retrieve all available voices from your subscription (`/tts/info`)

To query the `/tts/info` endpoint, pass the full endpoint URL:

```go
import (
	"encoding/json"
	"fmt"
	"log"
	"voxygen/tts"
)

func main() {

	client, err := tts.NewClient("YOUR_TOKEN", "info")
	if err != nil {
		log.Fatal(err)
	}

	client.SetAcceptContentType(tts.JSON)
	request := client.BuildRequest(map[string]string{})
	response, err := client.Send(request)
	if err != nil {
		log.Fatal(err)
	}

	defer response.Body.Close()

	var payload map[string]any
	fmt.Println("Available voices:")
	if voices, ok := payload["voices"].([]any); ok {
		for _, v := range voices {
			if voice, ok := v.(map[string]any); ok {
				fmt.Println(voice["name"])
			}
		}
	}
}
```
---

## Command-Line usage examples

The CLI tool is `speak.go` and can call any compatible Voxygen TTS API.

### Run directly

```bash
go run speak.go --help
```

### Compile then run

```bash
go build -o bin/voxygen-speak speak.go
./bin/voxygen-speak --help
```

---

### **Quick Start example**

```bash
export VOXYGEN_TOKEN="your_token_here"
go run speak.go \
  -t $VOXYGEN_TOKEN \
  -p text="Hello world" \
  -p voice=Mary \
  -o output.wav \
```

> If you don’t want to pass the `-t (--token)` option for each command, you can modify the default value of `token` in `main()` inside `speak.go`.

---

### **Generate an audio from a text file**

```bash
go run speak.go \
  -i input-text.txt \
  -p voice=Mary \
  -o output.wav \
```

---

### **JSON response mode**

```bash
go run speak.go \
  -j \
  -p text="Hello as JSON" \
  -p voice=Mary
```

---

### **Retrieve from `/tts/info` request**

```bash
go run speak.go info
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

The Go client automatically retries requests when a **`503 Service Unavailable`** response is returned.

Default retry settings:

* **6 retries**
* **10 seconds** between attempts

You can customize this behavior:

```go
client.SetRetryPolicy(2, 5*time.Second)
```

Retries apply only to `503` responses. Other errors are returned immediately.

If the issue persists after multiple retries, wait a few minutes before sending a new request.
