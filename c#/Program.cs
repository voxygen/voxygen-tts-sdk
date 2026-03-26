using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

using Voxygen.Tts;

internal partial class Program
{
    static async Task Main(string[] args)
    {

        // URL to TTS server
        string url = "https://api.voxygen.fr/tts"; // may be overwritten by command line positional argument e.g. "https://localhost:8443/tts"
        // Credentials
        string token = "token_provided_by_voxygen"; // may be overwritten by --token|-t token

        // Default TTS parameters, may be overwritten by command line --param|-p key=value
        var ttsParameters = new Dictionary<string, string>{
            { "text", "Enter some text." },
            { "voice", "Jenny" },
            { "header", "wav-stream-header" }
        };

        // Command line
        string defaultParams = string.Join(", ", ttsParameters.Keys.Select(p => $"'{p}={ttsParameters[p]}'"));
        string usage = $"""
usage: speak [-h] [j] [[-p key=value ] ...] [-i filename] [-o filename] [-t token] [url]

Client to TTS Server via HTTP(S)

positional arguments:
  url                   URL to TTS server (default: {url})

optional arguments:
  -h, --help            show this help message and exit
  -j, --json            request JSON content type in response from server (default: audio)
  -p key=value, --param key=value, ...
                        set request parameters (default: [{defaultParams}])
  -i filename           input file containing text to be read (takes precedence over -p text=\"Something to read.\" (default: None)
  -o filename           output audio file (default: None)
  -t token, --token token
                        authorization token
""";

        // parse command line arguments
        var options = new Dictionary<string, object>();
        try
        {
            var optRe = OptionsRegex();
            int index = 0;
            while (index < args.Length)
            {
                string arg = args[index++];
                var m = optRe.Match(arg);
                if (m.Success)
                {
                    string? shortOpt = null;
                    for (int g = 1; g <= m.Groups.Count - 1; g++)
                    {
                        if (m.Groups[g].Success)
                        {
                            shortOpt = m.Groups[g].Value;
                            break;
                        }
                    }
                    if (shortOpt == null) throw new NullReferenceException(nameof(shortOpt));
                    if ("hj".Contains(shortOpt)) // flag options => boolean
                    {
                        options[shortOpt] = true;
                    }
                    else // options with a following argument
                    {
                        if (index >= args.Length || args[index].StartsWith('-'))
                            throw new ArgumentException($"{arg} missing argument value");
                        if ("p".Contains(shortOpt)) // repeatable option => list
                        {
                            if (!options.ContainsKey(shortOpt))
                                options[shortOpt] = new List<string>();
                            ((List<string>)options[shortOpt]).Add(args[index++]);
                        }
                        else // non-repeatable option => string
                        {
                            if (options.ContainsKey(shortOpt))
                                throw new ArgumentException($"{arg} repeated argument");
                            options[shortOpt] = args[index++];
                        }
                    }
                }
                else if (arg.StartsWith('-'))
                {
                    throw new ArgumentException($"{arg} unknown argument");
                }
                else if (index != 0)
                {
                    index--;
                    break;
                }
            }
            if (index < args.Length) // positional arguments
                url = args[index++];
            if (index < args.Length)
                throw new ArgumentException($"too many positional arguments: {args[index]} ...");
        }
        catch (Exception error)
        {
            Console.Error.WriteLine($"error: {error}");
            Console.Write(usage);
            Environment.Exit(64);
        }

        // -help or -h requested from command line
        if (options.ContainsKey("h"))
        {
            Console.Write(usage);
            Environment.Exit(0);
        }

        // --token/-t token given optionally from command line
        if (options.TryGetValue("t", out object? token_opt))
            token = (string)token_opt;

        // --param or -p key=value update dictionary of TTS parameters
        if (options.TryGetValue("p", out object? parameters))
        {
            foreach (string param in (List<string>)parameters)
            {
                string[] parts = param.Split(['='], 2);
                ttsParameters[parts[0]] = parts.Length == 2 ? parts[1] : "";
            }
        }

        // -i file content overrides 'text' parameter
        if (options.TryGetValue("i", out object? inputFile))
        {
            try
            {
                byte[] inData = File.ReadAllBytes((string)inputFile);
                ttsParameters["text"] = Encoding.UTF8.GetString(inData);
            }
            catch (IOException error)
            {
                Console.Error.WriteLine("error: " + error);
                Environment.Exit(1);
            }
        }

        try
        {
            // Create a client instance
            using Client client = new(token, url);

            // (optional) change retry policy
            //client.SetRetryPolicy(2, TimeSpan.FromSeconds(30));
            // (optional) change request content type
            //client.SetRequestContentType(Client.MimeType.URL_ENCODED);
            if (options.ContainsKey("j"))
            {
                // (optional) change preferred accepted content type
                client.SetAcceptContentType(Client.MimeType.JSON);
            }

            // Build request query string from map of TTS parameters
            var ttsRequest = client.BuildRequest(ttsParameters);
            Console.Write("Request: ");
            foreach (KeyValuePair<string, string?> element in ttsRequest)
            {
                Console.Write(element);
            }
            Console.WriteLine("");
            // Send request and open response stream
            var cancelationTokenSource = new CancellationTokenSource();
            var ttsResponse = await client.Send(ttsRequest, cancelationTokenSource.Token);
            cancelationTokenSource.Token.ThrowIfCancellationRequested();
            // Read response headers
            Console.WriteLine("Received response status: " + (int)ttsResponse.StatusCode + " " + ttsResponse.ReasonPhrase);
            ttsResponse.EnsureSuccessStatusCode();
            Client.MimeType ttsContentType = Client.GetContentType(ttsResponse);
            Console.WriteLine("Response content type: " + ttsContentType);

            switch (ttsContentType)
            {
                case Client.MimeType.AUDIO:
                    if (options.TryGetValue("o", out object? outputStreamFile))
                    {
                        using FileStream fout = new((string)outputStreamFile, FileMode.Create, FileAccess.Write);
                        await ttsResponse.Content.CopyToAsync(fout);
                    }
                    else
                    {
                        await StreamReader(ttsResponse);
                    }
                    break;
                case Client.MimeType.JSON:
                    string? result = await ttsResponse.Content.ReadAsStringAsync(cancelationTokenSource.Token);
                    cancelationTokenSource.Token.ThrowIfCancellationRequested();
                    JsonDocument jsonReply = JsonDocument.Parse(result);
                    if (jsonReply.RootElement.TryGetProperty("url", out JsonElement audioUrl))
                    {
                        if (options.TryGetValue("o", out object? outputUrlFile))
                        {
                            using FileStream fout = new((string)outputUrlFile, FileMode.Create, FileAccess.Write);
                            var uri = audioUrl.ToString();
                            // support data: scheme
                            var base64Data = DataSchemeRegex().Match(uri)?.Groups["data"].Value;
                            if (base64Data != null)
                            {
                                fout.Write(Convert.FromBase64String(base64Data));
                            }
                            // http(s): scheme
                            else
                            {
                                using var httpClient = new HttpClient();
                                using var faudio = await httpClient.GetStreamAsync(uri, cancelationTokenSource.Token);
                                cancelationTokenSource.Token.ThrowIfCancellationRequested();
                                await faudio.CopyToAsync(fout);
                            }
                        }
                        else
                        {
                            Console.WriteLine(audioUrl.ToString());
                        }
                        Console.WriteLine("  received audio signal, duration: " + jsonReply.RootElement.GetProperty("duration"));
                        Console.WriteLine("  warnings: " + jsonReply.RootElement.GetProperty("warnings"));
                    }
                    else
                    {
                        Console.WriteLine(jsonReply);
                    }
                    break;
                default:
                    var text = await ttsResponse.Content.ReadAsStringAsync(cancelationTokenSource.Token);
                    cancelationTokenSource.Token.ThrowIfCancellationRequested();
                    Console.WriteLine(text);
                    break;
            }
            // Display trailing headers
            Console.WriteLine(ttsResponse.TrailingHeaders);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine("error: " + e);
        }
    }

    // regular expression for command-line options
    [GeneratedRegex(@"-([hjpiot])|--(h)elp|--(j)son|--(p)aram|--(t)oken")]
    private static partial Regex OptionsRegex();

    // regular expression for URI data: scheme with base64 encoding
    [GeneratedRegex(@"data:(?<type>.+?);base64,(?<data>.+)")]
    private static partial Regex DataSchemeRegex();

    private static Task StreamReader(HttpResponseMessage response) => StreamReader(response, CancellationToken.None);
    private static async Task StreamReader(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        var reader = await response.Content.ReadAsStreamAsync(cancellationToken);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = reader.Read(buffer, 0, buffer.Length)) > 0)
        {
            Console.WriteLine($"  received chunk of audio data: {bytesRead} bytes");
        }
    }

}
