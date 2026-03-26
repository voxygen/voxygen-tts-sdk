"use strict";

export default class Client {
    static MimeType = Object.freeze({
        PLAIN_TEXT: Symbol("PLAIN_TEXT"),
        AUDIO: Symbol("AUDIO"),
        JSON: Symbol("JSON"),
        URL_ENCODED: Symbol("URL_ENCODED"),
        MULTIPART: Symbol("MULTIPART")
    })
    #token
    #url
    #max_retries
    #retry_after
    #body_type
    #accept_type
    constructor(token, url = '') {
        if (typeof token !== 'string' || token === '')
            throw new Error("token must be provided");
        this.#token = token;
        this.#url = new URL(url, 'https://api.voxygen.fr/tts/');
        // default retry policy : 6 times with a 10 second wait
        this.setRetryPolicy(6, 10 /* seconds */);
        // default request content type : JSON
        this.setRequestContentType(Client.MimeType.JSON);
        // default accept content type : AUDIO
        this.setAcceptContentType(Client.MimeType.AUDIO);
    }

    setRetryPolicy(max_retries, retry_after) {
        this.#max_retries = max_retries;
        this.#retry_after = retry_after;
    }

    setRequestContentType(mime_type) {
        this.#body_type = mime_type;
    }

    setAcceptContentType(mime_type) {
        this.#accept_type = mime_type;
    }

    async buildRequest(anObject) {
        let request = {};
        // initialize query from host url
        this.#url.searchParams.forEach((value, key) => request[key] = value);
        this.#url.search = ""; // empty URL search, since it's now in the request body
        // add arguments to request query (argument values take priority over existing parameters)
        for (const [key, value] of Object.entries(anObject)) {
            request[key] = value;
        }
        // NOTE: rfc2046 section-4.1.1 "MUST always represent a line break as a CRLF sequence"
        for (const [key, value] of Object.entries(request)) {
            if (typeof value === 'string')
                request[key] = value.replace(/(\r?\n)/g, '\r\n');
        }
        return request;
    }

    #sleep = (sec) => new Promise((resolve) => setTimeout(() => resolve(), sec * 1000))

    fetch(aRequest, options = {}) {
        options.method = "POST";
        if (!options.headers)
            options.headers = new Headers();
        options.headers.append("User-Agent", "Voxygen-TTS-Client/1.4.0 (javascript)");
        let aBody;
        switch (this.#body_type) {
            case Client.MimeType.JSON:
                aBody = JSON.stringify(aRequest);
                // NOTE: rfc8259 section-8.1 "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
                options.headers.append("Content-Type", "application/json");
                break;
            case Client.MimeType.MULTIPART:
                aBody = new FormData(); // fetch() will produce multipart/form-data Content-Type from a FormData body
                for (const [key, value] of Object.entries(aRequest)) {
                    aBody.append(key, value);
                }
                break;
            case Client.MimeType.URL_ENCODED:
                aBody = new URLSearchParams(); // fetch() will produce application/x-www-form-urlencoded Content-Type from a URLSearchParams body
                for (const [key, value] of Object.entries(aRequest)) {
                    aBody.append(key, value);
                }
                break;
            default:
                throw new Error(`unsupported body type`);
                break;
        }
        switch (this.#accept_type) {
            case Client.MimeType.AUDIO:
                options.headers.append("Accept", "audio/*; q=1.0, application/octet-stream; q=0.8, */*; q=0.1");
                break;
            case Client.MimeType.JSON:
                options.headers.append("Accept", "application/json, */*; q=0.1");
                break;
            default:
                throw new Error(`unsupported accept content type`);
                break;
        }
        options.headers.append("Authorization", `Bearer ${this.#token}`);
        options.body = aBody;
        return new Promise((resolve, reject) => {
            const wrapper = (n) => {
                fetch(this.#url.toString(), options)
                    .then((response) => {
                        if (response.status == 503 /* Service Unavailable */ && n) {
                            this.#sleep(this.#retry_after).then(() => wrapper(--n));
                        } else {
                            resolve(response);
                        }
                    })
                    .catch(async (err) => reject(err))
            };
            wrapper(this.#max_retries);
        });
    }

    getContentType(aResponse) {
        const contentType = aResponse.headers.get("Content-Type");
        if (contentType != null) {
            if (contentType.startsWith("audio/") || contentType === 'application/octet-stream') {
                return Client.MimeType.AUDIO;
            } else if (contentType === 'application/json') {
                return Client.MimeType.JSON;
            }
        }
        return Client.MimeType.PLAIN_TEXT;
    }

}
