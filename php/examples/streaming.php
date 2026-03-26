<?php

declare(strict_types=1);

require __DIR__ . '/../tts/Client.php';

use Voxygen\TTS\Client;

// TTS request parameters
$parameters = [
    "voice" => "Mary", // Select a voice available on your subscription
    "language" => "en-US",
    "text" => "Hello. How can I help you today ?", // Text to synthesize
    "header" => "headerless",
];

$client = new Client(token: "YOUR_TOKEN");

$query = $client->buildRequest($parameters);
$stream = $client->open($query);
$headers = Client::response_headers($stream);

// Read and report audio chunks
$chunkSize = 4096;
while (!feof($stream)) {
    $data = fread($stream, $chunkSize);
    if ($data === false || $data === '') {
        break;
    }
    echo "received chunk of audio data: " . strlen($data) . " bytes" . PHP_EOL;
}

Client::close($stream);
