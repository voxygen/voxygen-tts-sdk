package examples

import (
	"fmt"
	"io"
	"log"
	"voxygen/tts"
)

func streaming() {

	parameters := map[string]string{
		"voice":    "Mary",
		"language": "en-US",
		"text":     "Hello. How can I help you today ?",
		"header":   "headerless",
	}

	client, err := tts.NewClient("YOUR_TOKEN", "")
	if err != nil {
		log.Fatal(err)
	}

	request := client.BuildRequest(parameters)

	resp, err := client.Send(request)
	if err != nil {
		log.Fatal(err)
	}
	defer resp.Body.Close()

	mimeType, err := tts.ContentType(resp)
	if err != nil {
		log.Fatal(err)
	}

	if mimeType == tts.AUDIO {
		buf := make([]byte, 4096)

		for {
			n, rerr := resp.Body.Read(buf)

			if n > 0 {
				fmt.Printf("received chunk of audio data: %d bytes\n", n)
			}

			if rerr == io.EOF {
				break
			}

			if rerr != nil {
				log.Fatal(rerr)
			}
		}
	} else {
		body, _ := io.ReadAll(resp.Body)
		fmt.Println(string(body))
	}
}
