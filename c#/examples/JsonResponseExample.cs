using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using Voxygen.Tts;

namespace Examples;

public static class JsonResponseExample
{
    public static async Task Main()
    {
        using Client client = new("YOUR_TOKEN");

        // Request JSON instead of streamed audio
        client.SetAcceptContentType(Client.MimeType.JSON);

        var parameters = new Dictionary<string, string>
        {
            ["voice"] = "Mary",
            ["language"] = "en-US",
            ["text"] = "Hello. How can I help you today ?",
            ["header"] = "wav-header",
            ["event"] = "2"
        };

        var request = client.BuildRequest(parameters);
        using var response = await client.Send(request);

        var body = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            Console.WriteLine(body);
            return;
        }

        using var doc = JsonDocument.Parse(body);
        var root = doc.RootElement;

        Console.WriteLine("Duration: " + root.GetProperty("duration"));
        Console.WriteLine("Warnings: " + root.GetProperty("warnings"));

        if (root.TryGetProperty("events", out var events) &&
            events.ValueKind == JsonValueKind.Array)
        {
            Console.WriteLine("Events (first 5):");
            foreach (var ev in events.EnumerateArray().Take(5))
                Console.WriteLine(ev);
        }

        // Download WAV from returned URL
        if (root.TryGetProperty("url", out var urlElement))
        {
            var audioUrl = urlElement.GetString();

            if (!string.IsNullOrEmpty(audioUrl))
            {
                using var http = new HttpClient();
                var bytes = await http.GetByteArrayAsync(audioUrl);
                await File.WriteAllBytesAsync("output.wav", bytes);

                Console.WriteLine("Audio saved to output.wav");
            }
        }
    }
}