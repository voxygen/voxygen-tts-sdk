using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;

namespace Voxygen.Tts;

public class Client : IDisposable
{
    public enum MimeType
    {
        PLAIN_TEXT = 0,
        AUDIO,
        JSON,
        URL_ENCODED
    }
    private readonly Uri _url;
    private readonly HttpRetryHandler _retryHandler;
    private readonly HttpClient _httpClient;
    private MimeType _bodyType;
    private MimeType _acceptType;
    static private readonly List<IPNetwork> _privateNetworks =
        [
            IPNetwork.Parse("127.0.0.0/8"),
            IPNetwork.Parse("192.168.0.0/24"),
            IPNetwork.Parse("172.16.0.0/12"),
            IPNetwork.Parse("10.0.0.0/16"),
            IPNetwork.Parse("fc00::/7"),
            IPNetwork.Parse("fd00::/8"),
            IPNetwork.Parse("fe80::/10")
        ];

    public void Dispose()
    {
        ((IDisposable)_retryHandler).Dispose();
        ((IDisposable)_httpClient).Dispose();
    }

    private class HttpRetryHandler : DelegatingHandler
    {
        private int _maxRetries;
        private TimeSpan _retryAfter;

        public HttpRetryHandler(HttpMessageHandler innerHandler) : base(innerHandler)
        {
            // default retry policy : 6 times with a 10 second wait
            SetRetryPolicy(6, TimeSpan.FromSeconds(10));
        }

        public void SetRetryPolicy(int maxRetries, TimeSpan retryAfter)
        {
            _maxRetries = maxRetries;
            _retryAfter = retryAfter;
        }

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var response = await base.SendAsync(request, cancellationToken);
            int retries = 0;
            while (response.StatusCode == HttpStatusCode.ServiceUnavailable && retries < _maxRetries)
            {
                await Task.Delay(_retryAfter, cancellationToken);
                response = await base.SendAsync(request, cancellationToken);
                retries++;
            }
            return response;
        }
    }

    public Client(string token, string url = "")
    {
        if (string.IsNullOrEmpty(token))
            throw new ArgumentException("token must be provided");
        var baseURL = new Uri(@"https://api.voxygen.fr/tts/");
        _url = new Uri(baseURL, url);
        var handler = new HttpClientHandler();
        if (_url.Scheme == Uri.UriSchemeHttps)
        {
            // certificate checking is disabled for connections on local network, allowing self-certification
            IPAddress[] addresslist = Dns.GetHostAddresses(_url.DnsSafeHost);
            bool isLocal = addresslist.All(ip =>
                ip.IsIPv6SiteLocal || ip.IsIPv6LinkLocal || IPAddress.IsLoopback(ip) ||
                    _privateNetworks.Any(network => network.Contains(ip)));
            if (isLocal)
            {
                handler.ServerCertificateCustomValidationCallback = HttpClientHandler.DangerousAcceptAnyServerCertificateValidator;
            }
        }
        else if (_url.Scheme != Uri.UriSchemeHttp)
            throw new ArgumentException($"Unsupported URL scheme {_url.Scheme}: Expected https or http.");
        _retryHandler = new HttpRetryHandler(handler);
        _httpClient = new HttpClient(_retryHandler);
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("Voxygen-TTS-Client/1.4.0 (csharp)");
        _httpClient.DefaultRequestHeaders.Add("Authorization", $"Bearer {token}");
        // default request content type : JSON
        SetRequestContentType(MimeType.JSON);
        // default accept content type : AUDIO
        SetAcceptContentType(MimeType.AUDIO);
    }

    public void SetRetryPolicy(int maxRetries, TimeSpan retryAfter)
    {
        _retryHandler.SetRetryPolicy(maxRetries, retryAfter);
    }

    public void SetRequestContentType(MimeType mimeType)
    {
        _bodyType = mimeType;
    }

    public void SetAcceptContentType(MimeType mimeType)
    {
        _acceptType = mimeType;
    }

    public Dictionary<string, string?> BuildRequest(Dictionary<string, string> arguments)
    {
        var query = new Dictionary<string, string?>();
        // initialize query from host url
        string url_query = _url.Query;
        if (!string.IsNullOrEmpty(url_query))
        {
            string[] queryStrings = url_query.TrimStart('?').Split('&');
            foreach (string q in queryStrings)
            {
                string[] parts = q.Split(['='], 2);
                query[WebUtility.UrlDecode(parts[0])] = parts.Length == 2 ? WebUtility.UrlDecode(parts[1]) : "";
            }
        }
        // add arguments to request query (argument values take priority over existing parameters)
        foreach (var arg in arguments)
            query[arg.Key] = arg.Value;
        // NOTE: rfc2046 section-4.1.1 "MUST always represent a line break as a CRLF sequence"
        foreach (var entry in query.ToList())
        {
            string? value = entry.Value;
            if (value != null)
                query[entry.Key] = value.Replace("\r\n", "\r\n").Replace("\n", "\r\n");
        }
        // build the query string
        return query;
    }

    public Task<HttpResponseMessage> Send(Dictionary<string, string?> request) => Send(request, CancellationToken.None);
    public Task<HttpResponseMessage> Send(Dictionary<string, string?> request, CancellationToken cancellationToken)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, _url.GetLeftPart(UriPartial.Path));
        switch (_bodyType)
        {
            case MimeType.JSON:
                req.Content = JsonContent.Create(request);
                req.Content.LoadIntoBufferAsync(cancellationToken);
                break;
            case MimeType.URL_ENCODED:
                req.Content = new FormUrlEncodedContent(request);
                break;
            default:
                throw new ArgumentException("unsupported body type");
        }
        switch (_acceptType)
        {
            case MimeType.AUDIO:
                req.Headers.Accept.Add(MediaTypeWithQualityHeaderValue.Parse("audio/*; q=1.0"));
                req.Headers.Accept.Add(MediaTypeWithQualityHeaderValue.Parse("application/octet-stream; q=0.8"));
                req.Headers.Accept.Add(MediaTypeWithQualityHeaderValue.Parse("*/*; q=0.1"));
                break;
            case MimeType.JSON:
                req.Headers.Accept.Add(MediaTypeWithQualityHeaderValue.Parse("application/json; q=1.0"));
                req.Headers.Accept.Add(MediaTypeWithQualityHeaderValue.Parse("*/*; q=0.1"));
                break;
            default:
                throw new ArgumentException("unsupported accept content type");
        }
        return _httpClient.SendAsync(req, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
    }

    public static MimeType GetContentType(HttpResponseMessage response)
    {
        string? contentMimeType = response.Content.Headers.ContentType?.MediaType;
        if (contentMimeType != null)
        {
            if (contentMimeType.StartsWith("audio/") || contentMimeType.Equals("application/octet-stream"))
                return MimeType.AUDIO;
            else if (contentMimeType.Equals("application/json"))
                return MimeType.JSON;
        }
        return MimeType.PLAIN_TEXT;
    }
}
