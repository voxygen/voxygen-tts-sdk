from voxygen.tts import Client

# TTS request parameters
parameters = {
    "voice": "Mary",  # Select a voice available on your subscription
    "language": "en-US",
    "text": "Hello. How can I help you today ?",  # Text to synthesize
    "header": "wav-header",  # Header value to output a wav file
}

with Client(token="YOUR_TOKEN") as client:
    request = client.build_request(parameters)
    response = client.send(request)

    if response.status == 200:
        with open("output.wav", "wb") as f:
            f.write(response.read())
        print("Audio saved to output.wav")
    else:
        print("Error:", response.status, response.read().decode(errors="replace"))
