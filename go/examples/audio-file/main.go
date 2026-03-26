package main

import (
	"io"
	"log"
	"os"

	"voxygen/tts"
)

func main() {

	parameters := map[string]string{
		"voice":    "Mary",
		"language": "en-US",
		"text":     "Hello. This audio will be saved to a file.",
		"header":   "wav-stream-header",
	}

	client, err := tts.NewClient("YOUR_TOKEN", "")
	if err != nil {
		log.Fatal(err)
	}

	request := client.BuildRequest(parameters)

	response, err := client.Send(request)
	if err != nil {
		log.Fatal(err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		body, _ := io.ReadAll(response.Body)
		log.Fatalf("Error: %s\n%s", response.Status, string(body))
	}

	contentType, err := tts.ContentType(response)
	if err != nil {
		log.Fatal(err)
	}

	if contentType != tts.AUDIO {
		log.Fatal("Response is not audio")
	}

	outFile, err := os.Create("output.wav")
	if err != nil {
		log.Fatal(err)
	}
	defer outFile.Close()

	_, err = io.Copy(outFile, response.Body)
	if err != nil {
		log.Fatal(err)
	}

	log.Println("Audio saved to output.wav")
}
