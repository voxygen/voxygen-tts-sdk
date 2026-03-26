import Client from "@voxygen/tts";

// TTS request parameters
const parameters = {
    voice: "Mary",                              // Select a voice available on your subscription
    language: "en-US",
    text: "Hello. How can I help you today ?",  // Text to synthesize
    header: "headerless",
};

const client = new Client("YOUR_TOKEN");

const request = await client.buildRequest(parameters);
const response = await client.fetch(request);

const reader = response.body.getReader();

while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    if (value) {
        console.log(`received chunk of audio data: ${value.byteLength} bytes`);
    }
}
