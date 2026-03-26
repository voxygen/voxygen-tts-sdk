using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Threading.Tasks;
using Voxygen.Tts;

namespace Examples;

public static class StreamingExample
{
    public static async Task Main()
    {
        // Parameters sent to the /tts endpoint
        var parameters = new Dictionary<string, string>
        {
            ["voice"] = "Mary",
            ["language"] = "en-US",
            ["text"] = "Hello. How can I help you ?",
            ["header"] = "headerless"
        };

        using Client client = new("YOUR_TOKEN");
        client.SetAcceptContentType(Client.MimeType.AUDIO);

        var request = client.BuildRequest(parameters);
        using HttpResponseMessage response = await client.Send(request);

        Console.WriteLine($"Status: {(int)response.StatusCode} {response.ReasonPhrase}");

        var contentType = Client.GetContentType(response);
        Console.WriteLine($"Content-Type interpreted as: {contentType}");

        if (contentType == Client.MimeType.AUDIO)
        {
            await using var stream = await response.Content.ReadAsStreamAsync();
            byte[] buffer = new byte[8192];
            int read;

            while ((read = await stream.ReadAsync(buffer, 0, buffer.Length)) > 0)
            {
                Console.WriteLine($"received chunk of audio data: {read} bytes");
            }
        }
        else
        {
            Console.WriteLine(await response.Content.ReadAsStringAsync());
        }
    }
}
