<?php

declare(strict_types=1);

require __DIR__ . '/../tts/Client.php';

use Voxygen\TTS\Client;
use Voxygen\TTS\ClientMimeType as MimeType;

// TTS request parameters
$parameters = [
    "voice" => "Mary",  // Select a voice available on your subscription
    "language" => "en-US",
    "text" => "Hello. How can I help you today ?", // Text to synthesize
    "header" => "wav-header",
    "event" => "2", // Returned events information level
];

$client = new Client(token: "YOUR_TOKEN");
$client->setAcceptContentType(MimeType::JSON);

$query = $client->buildRequest($parameters);
$stream = $client->open($query);

$headers = Client::response_headers($stream);
$status = $headers["response_status"] ?? 0;

$body = stream_get_contents($stream);
Client::close($stream);

$payload = json_decode($body, true);

// Display JSON response
echo "Duration: " . ($payload["duration"] ?? "n/a") . PHP_EOL;

$warnings = $payload["warnings"] ?? null;
echo "Warnings: " . (is_array($warnings) ? json_encode($warnings) : (string) ($warnings ?? "n/a")) . PHP_EOL;

echo "Events (first 5):" . PHP_EOL;
$events = $payload["events"] ?? [];
if (is_array($events)) {
    foreach (array_slice($events, 0, 5) as $event) {
        echo (is_array($event) ? json_encode($event) : (string) $event) . PHP_EOL;
    }
}

// Optional: Download the generated audio
$audioUrl = $payload["url"] ?? null;
if (is_string($audioUrl) && $audioUrl !== "") {
    $audioData = @file_get_contents($audioUrl);
    file_put_contents("output.wav", $audioData);
    echo "Audio saved to output.wav" . PHP_EOL;
}
