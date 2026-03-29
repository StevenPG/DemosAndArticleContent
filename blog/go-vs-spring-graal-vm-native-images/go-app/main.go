package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
)

// echoURL is the downstream service called for every API request and Kafka message.
// Both the HTTP handler and the Kafka consumer call this same target,
// so the workload is identical regardless of which path triggered it.
const echoURL = "http://localhost:9000/echo"

// httpClient is shared across all goroutines.
// Go's http.Client is safe for concurrent use and manages a connection pool internally.
// Setting MaxIdleConnsPerHost prevents connection exhaustion under high concurrency.
var httpClient = &http.Client{
	Timeout: 5 * time.Second,
	Transport: &http.Transport{
		MaxIdleConns:        0,
		MaxIdleConnsPerHost: 0,
		IdleConnTimeout:     0 * time.Second,
	},
}

func main() {
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	// Launch the Kafka consumer as a background goroutine.
	// It runs independently of the HTTP server — no shared state, no coordination needed.
	go runKafkaConsumer()

	// Register the single API route.
	// Go's net/http server automatically handles each incoming request in its own goroutine.
	// There's no configuration required for this — it's the default behavior.
	http.HandleFunc("/api/process", handleProcess)

	// Log the exact moment the server is ready. We use this timestamp to measure startup time.
	log.Printf("ready at %s", time.Now().Format(time.RFC3339Nano))
	log.Fatal(http.ListenAndServe(":8080", nil))
}

// handleProcess handles incoming HTTP requests.
// Each call runs on its own goroutine — Go's scheduler multiplexes goroutines onto
// OS threads, so 50 concurrent requests means 50 goroutines running in parallel.
// If the echo call blocks on I/O, the goroutine parks and the OS thread is reused
// for another goroutine immediately.
func handleProcess(w http.ResponseWriter, r *http.Request) {
	start := time.Now()

	resp, err := httpClient.Get(echoURL)
	if err != nil {
		http.Error(w, fmt.Sprintf("downstream error: %v", err), http.StatusServiceUnavailable)
		return
	}
	defer resp.Body.Close()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"status":     "ok",
		"elapsed_ms": time.Since(start).Milliseconds(),
	})
}

// runKafkaConsumer consumes messages from "benchmark-topic" starting at the earliest offset.
// Records are grouped by partition. Each partition gets one goroutine that processes its
// messages sequentially — one HTTP call completes before the next message is picked up.
// Across partitions, goroutines run concurrently (up to 5 in flight simultaneously).
// This is the standard Kafka consumer model: respect partition ordering, parallelize
// across partitions.
func runKafkaConsumer() {
	client, err := kgo.NewClient(
		kgo.SeedBrokers("localhost:9092"),
		// "go-consumer-group" is distinct from Spring's "spring-consumer-group".
		// Each app independently reads all 1000 messages from the beginning of the topic.
		kgo.ConsumerGroup("go-consumer-group"),
		kgo.ConsumeTopics("benchmark-topic"),
		// "earliest" means start from the first message in the topic, not just new ones.
		// This lets us pre-load messages and measure drain time from a cold start.
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()),
	)
	if err != nil {
		log.Fatalf("kafka client error: %v", err)
	}
	defer client.Close()

	var (
		processed atomic.Int64
		firstAt   atomic.Int64 // UnixMilli of first completed message
		lastAt    atomic.Int64 // UnixMilli of most recently completed message
	)

	log.Println("kafka consumer ready")

	for {
		// PollFetches blocks until records are available, then returns them all.
		// With 5 partitions and 1000 pre-loaded messages, the first poll may return
		// hundreds of records at once.
		fetches := client.PollFetches(context.Background())
		if fetches.IsClientClosed() {
			break
		}
		fetches.EachError(func(t string, p int32, err error) {
			log.Printf("kafka error %s/%d: %v", t, p, err)
		})

		// Group records by partition. We'll give each partition its own goroutine
		// and process that partition's records one at a time.
		partitions := make(map[int32][]*kgo.Record)
		fetches.EachRecord(func(rec *kgo.Record) {
			partitions[rec.Partition] = append(partitions[rec.Partition], rec)
		})

		// Fan out: one goroutine per partition.
		// Within each goroutine, records are processed sequentially — the HTTP call
		// blocks until the echo server responds before moving to the next record.
		// WaitGroup ensures all partitions finish before we poll and commit again.
		var wg sync.WaitGroup
		for _, recs := range partitions {
			wg.Add(1)
			go func(records []*kgo.Record) {
				defer wg.Done()
				for range records {
					resp, err := httpClient.Get(echoURL)
					if err != nil {
						log.Printf("echo error: %v", err)
						continue
					}
					resp.Body.Close()

					n := processed.Add(1)
					now := time.Now().UnixMilli()
					// CAS: only the very first completion sets firstAt (0 → now).
					firstAt.CompareAndSwap(0, now)
					lastAt.Store(now)

					if n%100 == 0 {
						log.Printf("processed %d messages", n)
					}
				}
			}(recs)
		}
		wg.Wait()
	}

	if n := processed.Load(); n > 0 {
		log.Printf("DONE: processed %d messages in %dms", n, lastAt.Load()-firstAt.Load())
	}
}
