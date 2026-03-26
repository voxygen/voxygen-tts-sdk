<?php

declare(strict_types=1);

require __DIR__ . '/../tts/Client.php';

use Voxygen\TTS\Client;

// TTS request parameters
$parameters = [
    "voice" => "Mary", // Select a voice available on your subscription
    "language" => "en-US",
    "text" => "Hello. How can I help you today ?", // Text to synthesize
    "header" => "wav-header"
];

$client = new Client(token: "YOUR_TOKEN");

$query = $client->buildRequest($parameters);
$stream = $client->open($query);

$headers = Client::response_headers($stream);
$status = $headers["response_status"] ?? 0;

$outFile = "output.wav";
$out = fopen($outFile, "wb");

// Stream copy: read from server stream, write to file
$chunkSize = 8192;
while (!feof($stream)) {
    $chunk = fread($stream, $chunkSize);
    if ($chunk === false || $chunk === '') {
        break;
    }
    fwrite($out, $chunk);
}

fclose($out);
Client::close($stream);

echo "Audio saved to $outFile" . PHP_EOL;
