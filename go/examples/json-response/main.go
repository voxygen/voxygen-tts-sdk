package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"

	"voxygen/tts"
)

func main() {

	parameters := map[string]string{
		"voice":    "Mary",
		"language": "en-US",
		"text":     "Hello as JSON response",
		"header":   "wav-header",
	}

	client, err := tts.NewClient("YOUR_TOKEN", "")
	if err != nil {
		log.Fatal(err)
	}

	client.SetAcceptContentType(tts.JSON)

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

	var payload map[string]any
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		log.Fatal(err)
	}

	fmt.Println("JSON response:")
	fmt.Println(payload)

	if url, ok := payload["url"].(string); ok {
		fmt.Println("Audio URL:", url)
	}
}
