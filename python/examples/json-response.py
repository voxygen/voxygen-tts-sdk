import urllib.request

from voxygen.tts import Client

parameters = {
    "voice": "Mary",  # Select a voice available on your subscription
    "language": "en-US",
    "text": "Hello. How can I help you today ?",  # Text to synthesize
    "header": "wav-header",
    "event": "2",  # Returned events information level
}

with Client(token="YOUR_TOKEN") as client:
    client.set_accept_content_type(Client.MimeType.JSON)

    request = client.build_request(parameters)
    response = client.send(request)

    if response.status != 200:
        print("Error:", response.status, response.read().decode())
        raise SystemExit(1)

    payload = client.read_json(response)


# Display JSON response
print("Duration:", payload.get("duration"))
print("Warnings:", payload.get("warnings"))
print("Events (first 5):")
for event in payload.get("events", [])[:5]:
    print(event)

# Optional: Download the generated audio
audio_url = payload.get("url")
if audio_url:
    with urllib.request.urlopen(audio_url) as faudio, open("output.wav", "wb") as f:
        f.write(faudio.read())
    print("Audio saved to output.wav")
