using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;
using Voxygen.Tts;

namespace Examples;

public static class AudioFileExample
{
    public static async Task Main()
    {
        var outputPath = "output.wav";

        using Client client = new("YOUR_TOKEN");

        // Default is AUDIO, but explicit is clearer
        client.SetAcceptContentType(Client.MimeType.AUDIO);

        var parameters = new Dictionary<string, string>
        {
            ["text"] = "Hello. How can I help you ?",
            ["language"] = "en-US",
            ["voice"] = "Mary",
            ["header"] = "wav-header"
        };

        var request = client.BuildRequest(parameters);
        using HttpResponseMessage response = await client.Send(request);

        Console.WriteLine($"HTTP {(int)response.StatusCode} {response.ReasonPhrase}");

        if (!response.IsSuccessStatusCode)
        {
            Console.WriteLine(await response.Content.ReadAsStringAsync());
            return;
        }

        var contentType = Client.GetContentType(response);
        Console.WriteLine($"Content-Type interpreted as: {contentType}");

        if (contentType == Client.MimeType.AUDIO)
        {
            await using var audioStream = await response.Content.ReadAsStreamAsync();
            await using var file = new FileStream(
                outputPath,
                FileMode.Create,
                FileAccess.Write,
                FileShare.None);

            await audioStream.CopyToAsync(file);

            Console.WriteLine($"Saved WAV file to: {outputPath}");
        }
        else
        {
            // If server returned JSON or text instead of audio
            Console.WriteLine(await response.Content.ReadAsStringAsync());
        }
    }
}
