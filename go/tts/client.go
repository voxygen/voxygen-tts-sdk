package tts

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"errors"
	"io"
	"mime"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

type MimeType uint8

const (
	PLAIN_TEXT MimeType = iota
	AUDIO
	JSON
	URL_ENCODED
)

func (mime MimeType) String() string {
	switch mime {
	case PLAIN_TEXT:
		return "PLAIN_TEXT"
	case AUDIO:
		return "AUDIO"
	case JSON:
		return "JSON"
	case URL_ENCODED:
		return "URL_ENCODED"
	}
	return ""
}

type Client struct {
	token      string
	url        *url.URL
	maxRetries int
	retryAfter time.Duration
	httpClient *http.Client
	bodyType   MimeType
	acceptType MimeType
}

func NewClient(token string, urlStr string) (*Client, error) {
	if token == "" {
		return nil, errors.New("token must be provided")
	}
	baseURL, _ := url.Parse("https://api.voxygen.fr/tts/")
	relativeURL, err := url.Parse(urlStr)
	if err != nil {
		return nil, errors.New("failed to parse URL")
	}
	serverURL := baseURL.ResolveReference(relativeURL)
	customTransport := http.DefaultTransport.(*http.Transport)
	ip, _ := net.LookupIP(serverURL.Hostname())
	if len(ip) > 0 && (ip[0].IsLoopback() || ip[0].IsPrivate()) {
		// certificate checking is disabled for connections on local network, allowing self-certification
		customTransport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	}
	httpClient := &http.Client{Transport: customTransport}
	// default retry policy : 6 times with a 10 second wait
	// default request content type : JSON
	// default accept content type : AUDIO
	return &Client{token: token, url: serverURL, maxRetries: 6, retryAfter: 10 * time.Second, httpClient: httpClient, bodyType: JSON, acceptType: AUDIO}, nil
}

func (c *Client) SetRetryPolicy(maxRetries int, retryAfter time.Duration) {
	c.maxRetries = maxRetries
	c.retryAfter = retryAfter
}

func (c *Client) SetRequestContentType(mimeType MimeType) {
	c.bodyType = mimeType
}

func (c *Client) SetAcceptContentType(mimeType MimeType) {
	c.acceptType = mimeType
}

func (c *Client) BuildRequest(arguments map[string]string) url.Values {
	// initialize query from host url
	query := c.url.Query()
	c.url.RawQuery = "" // empty URL search, since it's now in the request body
	// add arguments to request query (argument values take priority over existing parameters)
	for key, value := range arguments {
		query.Set(key, value)
	}
	// NOTE: rfc2046 section-4.1.1 "MUST always represent a line break as a CRLF sequence"
	RegexCRLF := regexp.MustCompile(`(\r?\n)`)
	for key, values := range query {
		query.Set(key, RegexCRLF.ReplaceAllString(values[len(values)-1], "\r\n"))
	}
	return query
}

func (c *Client) Send(query url.Values) (*http.Response, error) {
	var bodyReader *bytes.Reader
	var contentType string
	switch c.bodyType {
	case JSON:
		j, err := json.Marshal(query)
		if err != nil {
			return nil, err
		}
		bodyReader = bytes.NewReader(j)
		// NOTE: rfc8259 section-8.1 "JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8"
		contentType = "application/json"
	case URL_ENCODED:
		bodyReader = bytes.NewReader([]byte(query.Encode()))
		contentType = "application/x-www-form-urlencoded; charset=utf-8"
	default:
		return nil, errors.New("unsupported request content type")
	}
	req, err := http.NewRequest("POST", c.url.String(), bodyReader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", contentType)
	switch c.acceptType {
	case AUDIO:
		req.Header.Set("Accept", "audio/*; q=1.0, application/octet-stream; q=0.8, */*; q=0.1")
	case JSON:
		req.Header.Set("Accept", "application/json, */*; q=0.1")
	default:
		return nil, errors.New("unsupported accept content type")
	}
	req.Header.Set("User-Agent", "Voxygen-TTS-Client/1.4.0 (go)")
	req.Header.Set("Authorization", "Bearer "+c.token)
	response, err := c.httpClient.Do(req)
	retries := 0
	for err == nil && response.StatusCode == http.StatusServiceUnavailable && retries < c.maxRetries {
		if response.Body != nil {
			io.Copy(io.Discard, response.Body) // purge response body
			response.Body.Close()
		}
		time.Sleep(c.retryAfter)
		if _, err = bodyReader.Seek(0, io.SeekStart); err != nil { // rewind request reader
			return nil, err
		}
		response, err = c.httpClient.Do(req)
		retries++
	}
	return response, err
}

func ContentType(response *http.Response) (MimeType, error) {
	content_type := response.Header.Get("Content-Type")
	media_type, _, err := mime.ParseMediaType(content_type)
	if err != nil {
		return PLAIN_TEXT, err
	}
	if strings.HasPrefix(media_type, "audio/") || media_type == "application/octet-stream" {
		return AUDIO, nil
	} else if media_type == "application/json" {
		return JSON, nil
	}
	return PLAIN_TEXT, nil
}
