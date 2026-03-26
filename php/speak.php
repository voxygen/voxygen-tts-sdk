<?php

declare(strict_types=1);

require 'tts/Client.php';

use Voxygen\TTS\Client;
use Voxygen\TTS\ClientMimeType as MimeType;

### ------------------------------------------------------------------------ ###

if ('cli' === PHP_SAPI && realpath(__FILE__) === realpath($_SERVER['SCRIPT_FILENAME'])) {

    // URL to TTS Server
    $url = "https://api.voxygen.fr/tts"; // may be overwritten by command line positional argument e.g. "https://localhost:8443/tts"
    // Credentials
    $token = "token_provided_by_voxygen"; # may be overwritten by --token|-t token

    // Default TTS parameters, may be overwritten by command line --param|-p key=value
    $tts_parameters = array(
        'text' => 'Enter some text.',
        'voice' => 'Jenny',
        'header' => 'wav-stream-header',
    );

    // Command line
    $default_params = array();
    array_walk($tts_parameters, function ($v, $k) use (&$default_params) {
        array_push($default_params, "'$k=$v'");
    });
    $usage = "
php -f speak.php -- [-h] [j] [[-p key=value] ...] [-i filename] [-o filename] [-t token] [url]
Client to TTS Server via HTTP(S)

positional arguments:
  url                   URL to TTS server (default: $url)

optional arguments:
  -h, --help            show this help message and exit
  -j, --json            request JSON content type in response from server (default: audio)
  -p key=value, --param key=value, ...
                        set request parameters (default: [" . implode(', ', $default_params) . "])
  -i filename           input file containing text to be read (takes precedence over -p text=\"Something to read.\" (default: None)
  -o filename           output audio file (default: None)
  -t token, --token token
                        authorization token
";

    // parse command line arguments
    $shortopts = "hjp:i:o:t:";
    $longopts = ['help', 'json', 'param:', 'token:'];
    /**
     * @var array $options Contains options from the command line argument list
     */
    $options = getopt($shortopts, $longopts, $rest_index);
    if ($rest_index < $argc) {  // positional arguments
        $url = $argv[$rest_index++];
    }
    if ($rest_index < $argc) {
        echo "too many positional arguments: $argv[$rest_index] ...\r\n";
        echo $usage;
        exit(64);
    }

    // -help or -h requested from command line
    if (array_key_exists('h', $options) || array_key_exists('help', $options)) {
        echo $usage;
        return;
    }

    // --token/-t token given optionally from command line
    foreach (['t', 'token'] as $opt) {
        if (array_key_exists($opt, $options)) {
            if (is_array($options[$opt]))
                throw new Exception("-$opt used more than once");
            $token = $options[$opt];
        }
    }

    // --param or -p key=value update dictionary of TTS parameters
    $params = array(); // --param and -p options may be repeated, make key=value array from all options
    foreach (['p', 'param'] as $opt) {
        if (array_key_exists($opt, $options)) {
            if (is_array($options[$opt])) {
                $params = array_merge($params, $options[$opt]);
            } else {
                array_push($params, $options[$opt]);
            }
        }
    }
    foreach ($params as $p) {
        $kv = explode('=', $p, 2);
        $kv[] = '';
        list($key, $val) = $kv;
        $tts_parameters[$key] = $val;
    }

    // -i file content overrides 'text' parameter
    if (array_key_exists('i', $options)) {
        $tts_parameters['text'] = file_get_contents($options['i']);
        if (!$tts_parameters['text'])
            exit(-1);
    }

    try {
        // Create a client instance
        $client = new Client($token, $url);

        // (optional) change retry policy
        //$client->setRetryPolicy(2, 30);
        // (optional) change request content type
        //$client->setRequestContentType(MimeType::URL_ENCODED);
        if (array_key_exists('j', $options) || array_key_exists('json', $options)) {
            // (optional) change preferred accepted content type
            $client->setAcceptContentType(MimeType::JSON);
        }

        // Build request query string from dictionary of TTS parameters
        $request = $client->buildRequest($tts_parameters);
        echo "Request: ";
        print_r($request);

        // Send request and open response stream
        $stream = $client->open($request);
        if (!$stream)
            return;

        // Read response headers
        $headers = $client->response_headers($stream);
        echo "Received response status: ", $headers['response_status'], " ", $headers['response_reason'], "\r\n";
        if ($headers['response_status'] !== 200) {
            echo stream_get_contents($stream);
            return;
        }
        $tts_content_type = $client->content_type($headers);
        echo "Response content type: ", $tts_content_type->toString(), "\r\n";

        // Process response body
        switch ($tts_content_type) {
            case MimeType::AUDIO:
                if (array_key_exists('o', $options)) {
                    $out = fopen($options['o'], 'wb');
                    if (!$out)
                        exit(-1);
                    stream_copy_to_stream($stream, $out);
                    fclose($out);
                } else {
                    while (($data = fread($stream, 0x2000)) !== false) {
                        if (strlen($data) === 0)
                            break;
                        echo "  received chunk of audio data: ", strlen($data), "bytes\r\n";
                    }
                }
                break;
            case MimeType::JSON:
                $json_reply = json_decode(stream_get_contents($stream), true);
                if (array_key_exists('url', $json_reply)) {
                    if (array_key_exists('o', $options)) {
                        $in = fopen($json_reply['url'], 'rb');
                        if (!$in)
                            exit(-1);
                        $out = fopen($options['o'], 'wb');
                        if (!$out)
                            exit(-1);
                        stream_copy_to_stream($in, $out);
                        fclose($out);
                        fclose($in);
                    } else {
                        echo $json_reply['url'];
                        echo "\r\n";
                    }
                    echo "  received audio signal, duration: ", $json_reply['duration'], "\r\n";
                    echo "  warnings:\r\n";
                    foreach ($json_reply['warnings'] as $warning)
                        echo "\t", $warning, "\r\n";
                } else {
                    print_r(json_encode($json_reply));
                }
                break;
            default:
                echo stream_get_contents($stream);
                break;
        }

        // Close response stream
        $client->close($stream);
    } catch (Exception $e) {
        echo 'error: ', $e->getMessage(), "\n";
    }
}

### ------------------------------------------------------------------------ ###
