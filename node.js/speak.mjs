"use strict";

import Client from "./tts/Client.mjs";

import { lookup } from "node:dns/promises";
import { BlockList } from "node:net";
import { readFileSync, createWriteStream } from "node:fs";
import { Readable } from "node:stream";
import { inspect } from "node:util";

// URL to TTS Server
let url = "https://api.voxygen.fr/tts"; // may be overwritten by command line positional argument e.g. "https://localhost:8443/tts"
// Credentials
let token = "token_provided_by_voxygen"; // may be overwritten by --token|-t token

// Default TTS parameters, may be overwritten by command line --param|-p key=value
let tts_parameters = {
    'text': "Enter some text.",
    'voice': 'Jenny',
    'header': 'wav-stream-header'
};

// Command line
const default_params = Object.keys(tts_parameters).map(p => `${p}=${tts_parameters[p]}`);
const usage = `
usage: node speak.mjs [-h] [-j] [[-p key=value ] ...] [-i filename] [-o filename] [-t token] [url]

Client to TTS Server via HTTP(S)

positional arguments:
  url                   URL to TTS server (default: ${url})

optional arguments:
  -h, --help            show this help message and exit
  -j, --json            request JSON content type in response from server (default: audio)
  -p key=value, --param key=value, ...
                        set request parameters (default: ${default_params})
  -i filename           input file containing text to be read (takes precedence over -p text="Something to read." (default: None)
  -o filename           output audio file (default: None)
  -t token, --token token
                        authorization token
`;

// parse command line arguments
let options = {};
try {
    const argv = [...process.argv.slice(2)];
    const optRe = /-([hjpiot])|--(h)elp|--(j)son|--(p)aram|--(t)oken/;
    let arg;
    while (typeof (arg = argv.shift()) !== 'undefined') {
        const re = optRe.exec(arg);
        if (re) {
            const shortopt = re.slice(1).filter((a) => a !== undefined)[0];
            if ('hj'.includes(shortopt)) { // flag options => boolean
                options[shortopt] = true;
            } else { // options with a following argument
                if (!argv.length || argv[0].startsWith('-'))
                    throw new Error(`${arg} missing argument value`);
                if (shortopt === 'p') { // repeatable option => array
                    if (typeof options[shortopt] !== 'undefined')
                        options[shortopt].push(argv.shift());
                    else
                        options[shortopt] = [argv.shift()];
                } else {                // none repeatable option => string
                    if (typeof options[shortopt] !== 'undefined')
                        throw new Error(`${arg} repeated argument`);
                    else
                        options[shortopt] = argv.shift();
                }
            }
        } else if (arg.startsWith('-')) {
            throw new Error(`${arg} unknown argument`);
        } else {
            argv.unshift(arg);
            break;
        }
    }
    if (argv.length)            // positional arguments
        url = argv.shift();
    if (argv.length)
        throw new Error(`too many positional arguments: ${argv[0]} ...`);
} catch (error) {
    console.error(`error: ${error.message}`);
    console.info(usage);
    process.exit(64);
}

// -help or -h requested from command line
if (options['h']) {
    console.info(usage);
    process.exit(0);
}

// --token/-t token given optionally from command line
if (typeof options['t'] !== 'undefined')
    token = options['t'];

// --param or -p key=value update dictionary of TTS parameters
if (options['p']) {
    options['p'].forEach(p => {
        const [key, ...parts] = p.split('=');
        let val;
        if (typeof parts === 'undefined')
            val = '';
        else
            val = parts.join('=');
        tts_parameters[key] = val;
    });
}

// -i file content overrides 'text' parameter
if (options['i'])
    tts_parameters.text = readFileSync(options['i'], 'utf8');

// certificate checking is disabled for connections on local network, allowing self-certification
const urlObj = new URL(url);
if (urlObj.protocol === 'https:') {
    await lookup(urlObj.hostname).then((result) => {
        const privateNetworks = new BlockList();
        privateNetworks.addSubnet('127.0.0.0', 8, 'ipv4');
        privateNetworks.addSubnet('192.168.0.0', 24, 'ipv4');
        privateNetworks.addSubnet('172.16.0.0', 12, 'ipv4');
        privateNetworks.addSubnet('10.0.0.0', 16, 'ipv4');
        privateNetworks.addSubnet('fc00::', 7, 'ipv6');
        privateNetworks.addSubnet('fd00::', 8, 'ipv6');
        privateNetworks.addSubnet('fe80::', 10, 'ipv6');
        privateNetworks.addAddress('::1', 'ipv6');
        if (privateNetworks.check(result.address, result.family === 4 ? 'ipv4' : 'ipv6'))
            process.env["NODE_TLS_REJECT_UNAUTHORIZED"] = 0;
    });
}

async function streamReader(aResponse) {
    const reader = aResponse.body.getReader();
    while (true) {
        const { done, value } = await reader.read();
        if (done)
            break;
        console.debug(`  received chunk of audio data: ${value.length} bytes`);
    }
}

// Create a client instance
let client;
try {
    client = new Client(token, url);
    // (optional) change retry policy
    //client.setRetryPolicy(2, 30);
    // (optional) change request content type
    //client.setRequestContentType(Client.MimeType.URL_ENCODED);
    if (options['j']) {
        // (optional) change preferred accepted content type
        client.setAcceptContentType(Client.MimeType.JSON);
    }
} catch (error) {
    console.error(`error: ${error.message}`);
    process.exit(1);
}
client.buildRequest(tts_parameters) // Build request query string from dictionary of TTS parameters
    .then((request) => {
        console.debug(`Request: ${inspect(request, { breakLength: Infinity })}`);
        return client.fetch(request);
    }) // Send request and open response stream
    .then((response) => {
        // Read response headers
        if (!response.ok) {
            throw new Error(`Response status: ${response.status} ${response.statusText}`);
        }
        const responseType = client.getContentType(response);
        console.debug(`Response content type: ${responseType.description}`);
        // Process response body
        switch (responseType) {
            case Client.MimeType.AUDIO:
                if (options['o']) {
                    const writer = createWriteStream(options['o']);
                    Readable.fromWeb(response.body).pipe(writer);
                } else {
                    streamReader(response);
                }
                break;
            case Client.MimeType.JSON:
                response.json().then((myJson) => {
                    if (myJson.hasOwnProperty('url')) {
                        fetch(myJson.url)
                            .then((audioresponse) => {
                                if (!audioresponse.ok) {
                                    throw new Error(`Response status: ${audioresponse.status} ${audioresponse.statusText}`);
                                }
                                if (options['o']) {
                                    const writer = createWriteStream(options['o']);
                                    Readable.fromWeb(audioresponse.body).pipe(writer);
                                } else {
                                    console.log(myJson.url);
                                }
                            });
                    } else {
                        console.log(myJson);
                    }
                });
                break;
            default:
                response.text().then((text) => {
                    console.log(text);
                });
                break;
        }
    })
    .catch((error) => console.error(`error: ${error.message}`));
