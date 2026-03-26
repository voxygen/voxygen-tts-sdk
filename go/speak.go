package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"strings"

	"voxygen/tts"
)

func main() {
	// URL to TTS Server
	url := "https://api.voxygen.fr/tts" // may be overwritten by command line positional argument e.g. "https://localhost:8443/tts"
	// Credentials
	token := "token_provided_by_voxygen" // may be overwritten by --token|-t token

	// Default TTS parameters, may be overwritten by command line --param|-p key=value
	ttsParameters := map[string]string{
		"text":   "Enter some text.",
		"voice":  "Jenny",
		"header": "wav-stream-header",
	}

	// Command line
	var defaultParams []string
	for key, value := range ttsParameters {
		defaultParams = append(defaultParams, fmt.Sprintf("%s=%s", key, value))
	}
	usage := fmt.Sprintf(`
usage: go run speak.go [-h] [-j] [[-p key=value ] ...] [-i filename] [-o filename] [-t token] [url]

Client to TTS Server via HTTP(S)

positional arguments:
  url                   URL to TTS server (default: %s)

optional arguments:
  -h, --help            show this help message and exit
  -j, --json            request JSON content type in response from server (default: audio)
  -p key=value, --param key=value, ...
                        set request parameters (default: [%s])
  -i filename           input file containing text to be read (takes precedence over -p text="Something to read." (default: None)
  -o filename           output audio file (default: None)
  -t token, --token token
                        authorization token
`, url, strings.Join(defaultParams, ", "))

	// parse command line arguments
	checkArgAttribute := func(i int, args []string) {
		if i+1 >= len(args) || strings.HasPrefix(args[i+1], "-") {
			fmt.Printf("error: %s missing argument value\n", args[i])
			fmt.Println(usage)
			os.Exit(64)
		}
	}
	checkError := func(err error) {
		if err != nil {
			fmt.Printf("error: %s\n", err)
			os.Exit(-1)
		}
	}

	var outputFileName string
	hasJSONArgument := false
	args := os.Args[1:]
	for i := 0; i < len(args); i++ {
		arg := args[i]
		switch arg {
		case "-h", "--help":
			// -help or -h requested from command line
			fmt.Println(usage)
			return
		case "-j", "--json":
			// --json or -j request JSON content type in response from server
			hasJSONArgument = true
		case "-p", "--param":
			// --param or -p key=value update dictionary of TTS parameters
			checkArgAttribute(i, args)
			i++
			params := strings.SplitN(args[i], "=", 2)
			if len(params) == 2 {
				ttsParameters[params[0]] = params[1]
			} else {
				ttsParameters[params[0]] = ""
			}
		case "-i":
			// -i file content overrides 'text' parameter
			checkArgAttribute(i, args)
			i++
			content, err := os.ReadFile(args[i])
			checkError(err)
			ttsParameters["text"] = string(content)
		case "-o":
			checkArgAttribute(i, args)
			i++
			outputFileName = args[i]
		case "-t", "--token":
			// --token/-t token or --user/-u username given optionally from command line
			checkArgAttribute(i, args)
			i++
			token = args[i]
		default:
			if strings.HasPrefix(arg, "-") {
				fmt.Printf("error: %s unknown argument\n", arg)
				fmt.Println(usage)
				os.Exit(64)
			}
			url = arg
			i++
			if i < len(args) {
				fmt.Printf("too many positional arguments: %s ...\n", args[i])
				fmt.Println(usage)
				os.Exit(64)
			}
		}
	}

	// Create a client instance
	client, err := tts.NewClient(token, url)
	checkError(err)

	// (optional) change retry policy
	//client.SetRetryPolicy(2, 30)
	// (optional) change request content type
	//client.SetRequestContentType(tts.URL_ENCODED)
	if hasJSONArgument {
		// (optional) change preferred accepted content type
		client.SetAcceptContentType(tts.JSON)
	}

	// Build request query string from dictionary of TTS parameters
	ttsRequest := client.BuildRequest(ttsParameters)
	fmt.Printf("Request: %v\n", ttsRequest)

	// Send request and open response stream
	ttsResponse, err := client.Send(ttsRequest)
	checkError(err)
	defer ttsResponse.Body.Close()

	// Read response headers
	fmt.Printf("Received response status: %v\n", ttsResponse.Status)
	ttsContentType, err := tts.ContentType(ttsResponse)
	checkError(err)
	fmt.Printf("Response content type: %v\n", ttsContentType)

	// Process response body
	if ttsResponse.StatusCode != http.StatusOK {
		body, err := io.ReadAll(ttsResponse.Body)
		checkError(err)
		fmt.Println(string(body))
	} else {
		switch ttsContentType {
		case tts.AUDIO:
			if outputFileName != "" {
				outFile, err := os.Create(outputFileName)
				checkError(err)
				defer outFile.Close()
				_, err = io.Copy(outFile, ttsResponse.Body)
				checkError(err)
			} else {
				data := make([]byte, 0x2000)
				for {
					n, err := ttsResponse.Body.Read(data)
					if err == io.EOF {
						break
					}
					checkError(err)
					fmt.Println("  received chunk of audio data:", n, "bytes")
				}
			}
		case tts.JSON:
			decoder := json.NewDecoder(ttsResponse.Body)
			var jsonReply map[string]any
			decoder.Decode(&jsonReply)
			if url, ok := jsonReply["url"].(string); ok {
				if outputFileName != "" {
					outFile, err := os.Create(outputFileName)
					checkError(err)
					defer outFile.Close()
					// support data: scheme
					RegexURL := regexp.MustCompile(`data:(.+?);base64,(.+)`)
					SubmatchURL := RegexURL.FindStringSubmatch(url)
					if SubmatchURL != nil {
						audioData, err := base64.StdEncoding.DecodeString(SubmatchURL[2])
						checkError(err)
						_, err = outFile.Write(audioData)
						checkError(err)
					} else {
						// http(s): scheme
						audioResponse, err := http.Get(url)
						checkError(err)
						_, err = io.Copy(outFile, audioResponse.Body)
						checkError(err)
					}
				} else {
					fmt.Println(url)
				}
				fmt.Println("  received audio signal, duration:", jsonReply["duration"])
				fmt.Println("  warnings:")
				for _, warning := range jsonReply["warnings"].([]any) {
					fmt.Println("\t", warning)
				}
			} else {
				fmt.Println(jsonReply)
			}
		default:
			body, err := io.ReadAll(ttsResponse.Body)
			checkError(err)
			fmt.Println(string(body))
		}
	}

	// Display trailing headers
	if len(ttsResponse.Trailer) > 0 {
		fmt.Println("Trailers:", ttsResponse.Trailer)
	}
}
