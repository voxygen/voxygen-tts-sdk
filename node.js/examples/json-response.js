import { writeFile } from "node:fs/promises";
import Client from "@voxygen/tts";

// TTS request parameters
const parameters = {
    voice: "Mary",                              // Select a voice available on your subscription
    language: "en-US",
    text: "Hello. How can I help you today ?",  // Text to synthesize
    header: "wav-header",
    event: "2",                                 // Returned events information level
};

const client = new Client("YOUR_TOKEN");
client.setAcceptContentType(Client.MimeType.JSON);

const request = await client.buildRequest(parameters);
const response = await client.fetch(request);

const payload = await response.json();

// Display JSON response
console.log("Duration:", payload?.duration);
console.log("Warnings:", payload?.warnings);
console.log("Events (first 5):");
for (const ev of (payload?.events ?? []).slice(0, 5)) {
    console.log(ev);
}

// Optional: Download the generated audio
const audioUrl = payload?.url;
if (audioUrl) {
    const audioRes = await fetch(audioUrl);
    const audioBuffer = Buffer.from(await audioRes.arrayBuffer());
    await writeFile("output.wav", audioBuffer);
    console.log("Audio saved to output.wav");
}
