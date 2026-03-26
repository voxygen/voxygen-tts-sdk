![LOGO](/assets/voxygen-banner.png)
# Voxygen TTS API

This repository contains a **software development kit** with **command-line interfaces** and **client libraries** in multiple languages for interacting with a **Voxygen TTS HTTP API**, including the [**Voxygen Cloud Text-To-Speech API**](https://www.voxygen.fr/voxygen-tts-cloud) or the [on-premise deployment](https://www.voxygen.fr/voxygen-tts-server) of the same API.

---

## Voxygen Cloud TTS API Overview

The **Voxygen Cloud TTS API** is a secure, scalable HTTP Text-To-Speech service designed for real-time and batch speech synthesis.

It provides advanced speech synthesis features, including:

* Language and voice selection, control over pauses, speech rate, intonation...
* Low-latency real-time audio streaming
* Downloadable generated audio files

The Cloud TTS API is available at `https://api.voxygen.fr` for our subscribers. Full API documentation is available [here](https://api.voxygen.fr/documentation.html).

The same API can be exposed locally for **on-premise deployments**, ensuring full compatibility between cloud and local installations.

---

## Supported Languages

This repository provides a multi-language SDK for interacting with a **Voxygen TTS HTTP API**.

Currently supported languages are:

* [**C#**](/c%23)
* [**Go**](/go)
* [**HTML / JavaScript**](/html)
* [**Java**](/java)
* [**Node.js**](/node.js)
* [**PHP**](/php)
* [**Python**](/python)

Each language directory contains:

* A `client` library providing a simple interface for calling the TTS API.
* A `CLI` implementation: `speak`. To use for testing the API from the command line.
* An `examples/` directory with sample code demonstrating common use cases such as streaming, file generation, and JSON responses.
* A dedicated `README` documentation with installation instructions and usage examples.

---

## Common Features

All clients support:

### HTTP POST requests to `/tts`
The Text-to-Speech (TTS) service converts the value of the text parameter into an audio file.

* Required TTS parameters are:

  * `text`
  * `voice`
  * `language`

  To get an exhaustive list of available parameters, refer to the
  [TTS API documentation](https://api.voxygen.fr/documentation.html#tts-api).

> **Two response modes are available**:
>
>  1. By default, the API **streams the generated speech directly in the HTTP response body**, enabling low-latency playback and real-time integration.
>
>  2. Alternatively, client applications can request a **JSON response**. 
> In this mode, the API returns a JSON object containing metadata about the synthesis, including a URL from which the generated audio file can be downloaded.
> Please refer to the documentation specific to each language for implementation details.

---

### HTTP POST requests to `/tts/info`

Requests to this endpoint return **account and engine information** in JSON format, allowing you to discover the configuration and capabilities associated with your TTS API subscription.

The response typically includes:

* **Available voices**, exposed as a list of voice definitions.

* **Engine information**, including the engine version.

* **Available resources**, such as: lexicons, normalisers an audio files.

---

### Secure Authentication

All requests are using **token authentication** to securely access the Voxygen TTS API.

Simply provide your token into the desired client as recommended inside its documentation, and it will automatically include it in the HTTP header as:

```http
Authorization: Bearer <TOKEN>
```

Tokens are provided as part of your Voxygen subscription and must be kept confidential and handled only in secure backend environments.

---

## Support

For questions, integration help, or issues:

* Please use the **GitHub Issues** tab of this repository to report bugs, ask questions, or request enhancements.
* Refer to the official documentation associated with your deployment.

---

## License

This repository is licensed under the MIT License. See the `LICENSE` file for details.

### Usage Notice

These clients are designed to interface with a **Voxygen TTS HTTP API**.  
Use of the SDK does not grant rights to any underlying text-to-speech (TTS) technologies, which may be subject to separate licensing terms.
