from voxygen.tts import Client

# TTS request parameters
parameters = {
    "voice": "Mary",  # Select a voice available on your subscription
    "language": "en-US",
    "text": "Hello. How can I help you today ?",  # Text to synthesize
    "header": "headerless",
}

with Client(token="YOUR_TOKEN") as client:
    request = client.build_request(parameters)
    response = client.send(request)

    if client.content_type(response) == Client.MimeType.AUDIO:
        for data in client.iter_data(response):
            print(f"received chunk of audio data: {len(data)} bytes")
