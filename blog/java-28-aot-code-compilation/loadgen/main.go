// loadgen is a tiny closed-loop HTTP load generator for warmup curves.
//
// It is written in Go on purpose: a Java load generator would be warming up its own JIT
// at the exact moment we're trying to measure the server's warmup. Go is compiled ahead
// of time, so the client's behaviour is constant from the first request.
//
// Time zero is the moment the JVM under test was launched (-launch-ns), not the moment the
// first request succeeds. Each output row is one second of wall time since launch, so a
// single chart shows startup (rows with zero throughput) AND warmup (the ramp) together.
package main

import (
	"bytes"
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math"
	"math/rand/v2"
	"net/http"
	"os"
	"slices"
	"strconv"
	"sync"
	"time"
)

type sample struct {
	doneMs  int64 // completion time, ms since launch
	latency time.Duration
	ok      bool
}

type request struct {
	method, path string
	body         []byte
}

var operators = []string{"AAL", "DAL", "UAL", "SWA", "JBU", "ASA", "FFT", "NKS", "SKW", "RPA"}

// trackBody builds a deterministic flight track: climb, cruise, descent.
func trackBody(rng *rand.Rand, id int) []byte {
	type pt struct {
		T       int64   `json:"t"`
		Lat     float64 `json:"lat"`
		Lon     float64 `json:"lon"`
		AltFt   float64 `json:"altFt"`
		SpeedKt float64 `json:"speedKt"`
	}
	n := 300
	pts := make([]pt, n)
	lat, lon := 30+rng.Float64()*15, -120+rng.Float64()*45
	for i := range n {
		phase := float64(i) / float64(n)
		alt := 35000.0
		if phase < 0.2 {
			alt = 35000 * phase / 0.2
		} else if phase > 0.8 {
			alt = 35000 * (1 - phase) / 0.2
		}
		lat += 0.02 + rng.Float64()*0.01
		lon += 0.03 + rng.Float64()*0.01
		pts[i] = pt{int64(1_700_000_000 + i*10), lat, lon, alt + rng.Float64()*200, 250 + rng.Float64()*200}
	}
	b, _ := json.Marshal(map[string]any{"icao24": fmt.Sprintf("%06x", 0xA00000+id), "points": pts})
	return b
}

func buildMix(mix string) []request {
	rng := rand.New(rand.NewPCG(42, 42))
	var fleet, tracks []request
	for _, op := range operators {
		fleet = append(fleet, request{"GET", "/api/aircraft?operator=" + op, nil})
	}
	for _, y := range []int{2005, 2010, 2015, 2020} {
		fleet = append(fleet, request{"GET", "/api/report/fleet?builtAfter=" + strconv.Itoa(y), nil})
	}
	for i := range 16 {
		tracks = append(tracks, request{"POST", "/api/tracks/analyze", trackBody(rng, i)})
	}
	switch mix {
	case "full": // 14 DB/report-shaped + 16 CPU-shaped requests, picked uniformly
		return append(fleet, tracks...)
	case "fleet":
		return fleet
	case "tracks":
		return tracks
	}
	fmt.Fprintln(os.Stderr, "unknown -mix:", mix)
	os.Exit(2)
	return nil
}

func percentile(sorted []time.Duration, p float64) time.Duration {
	if len(sorted) == 0 {
		return 0
	}
	idx := int(math.Ceil(p/100*float64(len(sorted)))) - 1
	return sorted[max(0, min(idx, len(sorted)-1))]
}

func main() {
	base := flag.String("base", "http://127.0.0.1:8080", "server base URL")
	duration := flag.Duration("duration", 120*time.Second, "run until launch + duration")
	conc := flag.Int("c", 8, "concurrent connections (closed loop)")
	mix := flag.String("mix", "full", "request mix: full | fleet | tracks")
	launchNs := flag.Int64("launch-ns", 0, "unix ns when the server JVM was launched (default: now)")
	out := flag.String("out", "", "per-second CSV output (default stdout)")
	flag.Parse()

	launch := time.Now()
	if *launchNs > 0 {
		launch = time.Unix(0, *launchNs)
	}
	deadline := launch.Add(*duration)
	reqs := buildMix(*mix)

	client := &http.Client{
		Timeout:   10 * time.Second,
		Transport: &http.Transport{MaxIdleConnsPerHost: *conc, MaxConnsPerHost: *conc, DisableCompression: true},
	}

	var mu sync.Mutex
	var samples []sample
	var firstOK time.Time
	var wg sync.WaitGroup
	for w := range *conc {
		wg.Add(1)
		go func() {
			defer wg.Done()
			rng := rand.New(rand.NewPCG(uint64(w), 7))
			local := make([]sample, 0, 1<<16)
			for time.Now().Before(deadline) {
				r := reqs[rng.IntN(len(reqs))]
				req, _ := http.NewRequest(r.method, *base+r.path, bytes.NewReader(r.body))
				if r.body != nil {
					req.Header.Set("Content-Type", "application/json")
				}
				t0 := time.Now()
				resp, err := client.Do(req)
				if err != nil { // server not listening yet: this is still "startup", not an error
					time.Sleep(2 * time.Millisecond)
					continue
				}
				io.Copy(io.Discard, resp.Body)
				resp.Body.Close()
				t1 := time.Now()
				ok := resp.StatusCode < 300
				local = append(local, sample{t1.Sub(launch).Milliseconds(), t1.Sub(t0), ok})
				if ok {
					mu.Lock()
					if firstOK.IsZero() || t1.Before(firstOK) {
						firstOK = t1
					}
					mu.Unlock()
				}
			}
			mu.Lock()
			samples = append(samples, local...)
			mu.Unlock()
		}()
	}
	wg.Wait()

	secs := int(duration.Seconds())
	buckets := make([][]time.Duration, secs)
	errs := make([]int, secs)
	for _, s := range samples {
		b := int(s.doneMs / 1000)
		if b < 0 || b >= secs {
			continue
		}
		if s.ok {
			buckets[b] = append(buckets[b], s.latency)
		} else {
			errs[b]++
		}
	}

	f := os.Stdout
	if *out != "" {
		var err error
		if f, err = os.Create(*out); err != nil {
			panic(err)
		}
		defer f.Close()
	}
	w := csv.NewWriter(f)
	w.Write([]string{"second", "requests", "errors", "p50_ms", "p99_ms", "max_ms"})
	for i := range secs {
		lat := buckets[i]
		slices.Sort(lat)
		ms := func(d time.Duration) string { return strconv.FormatFloat(float64(d.Microseconds())/1000, 'f', 2, 64) }
		var mx time.Duration
		if len(lat) > 0 {
			mx = lat[len(lat)-1]
		}
		w.Write([]string{strconv.Itoa(i + 1), strconv.Itoa(len(lat)), strconv.Itoa(errs[i]),
			ms(percentile(lat, 50)), ms(percentile(lat, 99)), ms(mx)})
	}
	w.Flush()

	total := 0
	for _, b := range buckets {
		total += len(b)
	}
	firstMs := -1.0
	if !firstOK.IsZero() {
		firstMs = float64(firstOK.Sub(launch).Microseconds()) / 1000
	}
	fmt.Fprintf(os.Stderr, "first_ok_ms=%.1f total_ok=%d\n", firstMs, total)
}
