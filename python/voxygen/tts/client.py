#!/usr/bin/env python3
#-*- coding: utf-8 -*-

from enum import Enum
import http
import ipaddress
import json
import re
import socket
import ssl
import time
from types import TracebackType
from typing import Any
import urllib.parse
import urllib3_future as urllib3

# pylint: disable=line-too-long

### ------------------------------------------------------------------------ ###

class Client:

    class MimeType(Enum):
        PLAIN_TEXT = 0
        AUDIO = 1
        JSON = 2
        URL_ENCODED = 3
        def __str__(self):
            return f'{self.name}'

    def __enter__(self):
        self.open()
        return self

    def __exit__(self, exc_type: type[BaseException] | None, exc_value: BaseException | None, traceback: TracebackType | None) -> bool | None:
        self.close()
        return False

    def __init__(self, token:str, url:str=''):
        if not token:
            raise ValueError("token must be provided")
        self._token = token
        url = urllib.parse.urljoin('https://api.voxygen.fr/tts/', url)
        self._url = urllib.parse.urlparse(url, allow_fragments=False)
        # default retry policy : 6 times with a 10 second wait
        self.set_retry_policy(6, 10)
        # default request content type : JSON
        self.set_request_content_type(Client.MimeType.JSON)
        # default accept content type : AUDIO
        self.set_accept_content_type(Client.MimeType.AUDIO)
        self._connection = None

    def set_retry_policy(self, max_retries:int, retry_after:int):
        self._max_retries = max_retries
        self._retry_after = retry_after

    def set_request_content_type(self, mime_type:MimeType):
        self._body_type = mime_type

    def set_accept_content_type(self, mime_type:MimeType):
        self._accept_type = mime_type

    def open(self):
        """Open HTTP(S) connection with server.

        Certificate checking is disabled for connections on local network, allowing self-certification
        """
        if self._connection:
            return
        hostname = self._url.hostname
        if not hostname or hostname == '':
            hostname = 'localhost'
        if self._url.scheme.casefold() in ['', 'https']:
            context = urllib3.util.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            context.load_default_certs(ssl.Purpose.CLIENT_AUTH)
            try:
                ip = ipaddress.ip_address(socket.gethostbyname(hostname))
                if ip in ipaddress.ip_network('127.0.0.0/8') or \
                   ip in ipaddress.ip_network('192.168.0.0/24') or \
                   ip in ipaddress.ip_network('172.16.0.0/12') or \
                   ip in ipaddress.ip_network('10.0.0.0/16') or \
                   ip in ipaddress.ip_network('fc00::/7') or \
                   ip in ipaddress.ip_network('fd00::/8') or \
                   ip in ipaddress.ip_network('fe80::/10') or \
                   ip == ipaddress.ip_address('::1') :
                    # disable certificate checking when host in private loopback or local network
                    context.check_hostname = False
                    context.verify_mode = ssl.CERT_NONE
            except ValueError:
                pass
            self._connection = urllib3.connection.HTTPSConnection(host=hostname, port=self._url.port, ssl_context=context, timeout=60)
        elif self._url.scheme.casefold() == 'http':
            self._connection = urllib3.connection.HTTPConnection(host=hostname, port=self._url.port, timeout=60)
        else:
            raise ValueError(f'Unsupported URL scheme "{self._url.scheme}": Expected https or http.')

    def close(self):
        """Close HTTP(S) connection with server.
        """
        if not self._connection:
            return
        self._connection.close()
        self._connection = None

    def build_request(self, arguments:dict[str,Any]) -> dict[str, Any]:
        """Build URL request query string.

        Keyword arguments:
        arguments -- a dict of TTS control parameters e.g. {'voice':'Jenny','text':'Hello world!',...}
        """
        query:dict[str,Any] = {}
        # initialize query from host url
        for key, value in urllib.parse.parse_qs(self._url.query, keep_blank_values=True).items():
            query[key] = value.pop()
        # add arguments to request query (argument values take priority over existing parameters)
        query.update(arguments)
        # NOTE: rfc2046 section-4.1.1 "MUST always represent a line break as a CRLF sequence"
        for key, value in query.items():
            if isinstance(value, str):
                query[key] = re.sub(r'(\r?\n)', '\r\n', value)
        return query

    def send(self, request:Any) -> urllib3.response.HTTPResponse:
        """Send request to server.

        Keyword arguments:
        request -- from build_request()
        """
        if not self._connection:
            raise ValueError("no connection")
        if self._body_type == Client.MimeType.JSON:
            # NOTE: rfc8259 section-8.1 "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
            contentType = 'application/json'
            request = json.dumps(request)
        elif self._body_type == Client.MimeType.URL_ENCODED:
            contentType = 'application/x-www-form-urlencoded; charset=utf-8'
            request = urllib.parse.urlencode(request)
        else:
            raise ValueError("unsupported body type")
        if self._accept_type == Client.MimeType.AUDIO:
            acceptHeader = 'audio/*; q=1.0, application/octet-stream; q=0.8, */*; q=0.1'
        elif self._accept_type == Client.MimeType.JSON:
            acceptHeader = 'application/json, */*; q=0.1'
        else:
            raise ValueError("unsupported accept content type")
        httpheaders = {'Content-Type': contentType,
                       'Accept': acceptHeader,
                       'Authorization': f"Bearer {self._token}",
                       'User-Agent': 'Voxygen-TTS-Client/1.4.0 (python)'}
        self._connection.request('POST', self._url.path, body=request, headers=httpheaders, preload_content=False)
        response = self._connection.getresponse()
        retries = 0
        while response.status == http.HTTPStatus.SERVICE_UNAVAILABLE and retries < self._max_retries:
            self._connection.close()
            time.sleep(self._retry_after)
            self._connection.request('POST', self._url.path, body=request, headers=httpheaders)
            response = self._connection.getresponse()
            retries += 1
        return response

    @staticmethod
    def content_type(response:urllib3.response.HTTPResponse) -> MimeType:
        """Get response content type from its headers.

        Return:
        'AUDIO' -- response contains audio media
        'JSON' -- response contains JSON data
        """
        headers = response.getheaders() # case-insensitive headers
        if 'content-type' in headers:
            if headers['content-type'].startswith('audio/') or headers['content-type'] == 'application/octet-stream':
                return Client.MimeType.AUDIO
            if headers['content-type'] == 'application/json':
                return Client.MimeType.JSON
        return Client.MimeType.PLAIN_TEXT

    @staticmethod
    def read_data(response:urllib3.response.HTTPResponse) -> bytes:
        """Read a block of data from the response
        """
        if response.chunked:    # HTTP1.1 Transfer-Encoding:chunked
            response.read(0)    # read chunk size from connection but get no data
            chunk = response.read(response.chunk_left) # read the entire HTTP chunk
        else:
            chunk = response.read(0x2000) # Read at most the default block of data
        return chunk

    @staticmethod
    def read_json(response:urllib3.response.HTTPResponse) -> Any:
        """Read entire response body and return as a json structure
        """
        return response.json()

### ------------------------------------------------------------------------ ###
