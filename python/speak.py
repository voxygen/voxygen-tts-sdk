#!/usr/bin/env python3
#-*- coding: utf-8 -*-

import argparse
import http
import re
import sys
import urllib.request
import voxygen.tts as tts

# pylint: disable=line-too-long

def main():
    # URL to TTS Server
    url = 'https://api.voxygen.fr/tts' # may be overwritten by command line positional argument e.g. 'https://localhost:8443/tts'
    # Credentials
    token = 'token_provided_by_voxygen' # may be overwritten by --token|-t token

    # Default TTS parameters, may be overwritten by command line --param|-p key=value
    tts_parameters = {
        'text': "Enter some text.",
        'voice': 'Jenny',
        'header': 'wav-stream-header'
    }

    # Command line
    default_params = list(map(lambda p: f"{p}={tts_parameters[p]}", tts_parameters))
    parser = argparse.ArgumentParser(description='Client to TTS Server via HTTP(S)',formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('url', nargs='?', help='URL to TTS server', default=url)
    parser.add_argument('-j', '--json', help='request JSON content type in response from server (default: audio)', action='store_true', default=argparse.SUPPRESS)

    parser.add_argument('-p', '--param', metavar='key=value, ...', help='set request parameter', action='append', default=default_params)
    parser.add_argument('-i', metavar='filename', help='input file containing text to be read (takes precedence over -p text="Something to read."', default=None)
    parser.add_argument('-o', metavar='filename', help='output audio file', default=None)
    parser.add_argument('-t', '--token', metavar='token', help='authorization token', default=argparse.SUPPRESS)

    # parse command line arguments
    args = parser.parse_args()

    # --token/-t token given optionally from command line
    if hasattr(args, 'token'):
        token = args.token

    # --param or -p key=value update dictionary of TTS parameters
    tts_parameters.update(dict(map(lambda a : (re.split(r'=', a, maxsplit=1)+[''])[:2], args.param)))

    # -i file content overrides 'text' parameter
    if args.i:
        try:
            with open(args.i, 'r', encoding='utf-8') as fin:
                tts_parameters['text'] = fin.read()
        except OSError as e:
            print(f'error: {e}', file=sys.stderr)
            sys.exit(e.errno)

    try:
        # Create a client instance
        with tts.Client(token, args.url) as client:

            # (optional) change retry policy
            #client.set_retry_policy(2, 30);
            # (optional) change request content type
            #client.set_request_content_type(tts.Client.MimeType.URL_ENCODED);
            if hasattr(args, 'json') and args.json:
                # (optional) change preferred accepted content type
                client.set_accept_content_type(tts.Client.MimeType.JSON)

            # Build request query string from dictionary of TTS parameters
            tts_request = client.build_request(tts_parameters)
            print("Request:", tts_request)

            tts_response = client.send(tts_request)
            print(f"Received response status: {tts_response.status} {tts_response.reason}")

            tts_content_type = client.content_type(tts_response)
            print(f"Response content type: {tts_content_type}")

            if tts_response.status != http.HTTPStatus.OK:
                print(tts_response.read().decode(), file=sys.stderr)
            else:
                if tts_content_type == tts.Client.MimeType.AUDIO:
                    if args.o:
                        fout = open(args.o, 'wb')
                    else:
                        fout = None
                    while data := client.read_data(tts_response):
                        if fout:
                            fout.write(data)
                        else:
                            print("  received chunk of audio data: ", len(data), "bytes")
                    if fout:
                        fout.close()
                elif tts_content_type == tts.Client.MimeType.JSON:
                    json_reply = client.read_json(tts_response)
                    if 'url' in json_reply:
                        if args.o:
                            with urllib.request.urlopen(json_reply['url']) as faudio, open(args.o, 'wb') as fout:
                                fout.write(faudio.read())
                        else:
                            print(json_reply['url'])
                            print('  received audio signal, duration:', json_reply['duration'])
                            print('  warnings:', json_reply['warnings'])
                    else:
                        print(json_reply)
                else:
                    print(tts_response.read().decode())
                # Display trailing headers
                print("Trailer: ", tts_response.trailers)


    except OSError as e:
        print(f'error: {e}', file=sys.stderr)
        sys.exit(e.errno)
    except Exception as e: # pylint: disable=broad-exception-caught
        print(f"error: {e}", file=sys.stderr)
        sys.exit(-1)

# run main ....
if __name__ == '__main__':
    main()
