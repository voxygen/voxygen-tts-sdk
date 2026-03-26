import { writeFile } from "node:fs/promises";
import Client from "@voxygen/tts";

// TTS request parameters
const parameters = {
    voice: "Mary",                              // Select a voice available on your subscription
    language: "en-US",
    text: "Hello. How can I help you today ?",  // Text to synthesize
    header: "wav-header"
};

const client = new Client("YOUR_TOKEN");

const request = await client.buildRequest(parameters);
const response = await client.fetch(request);

if (response.status === 200) {
    const audioBuffer = Buffer.from(await response.arrayBuffer());
    await writeFile("output.wav", audioBuffer);
    console.log("Audio saved to output.wav");
} else {
    const errorText = await response.text();
    console.log("Error:", response.status, errorText);
}
