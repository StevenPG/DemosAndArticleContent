package com.stevenpg.aot.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tracks")
class TrackController {

    record Position(long t,
                    @DecimalMin("-90") @DecimalMax("90") double lat,
                    @DecimalMin("-180") @DecimalMax("180") double lon,
                    double altFt, double speedKt) {
    }

    record TrackRequest(@NotBlank String icao24, @Size(min = 2, max = 5_000) List<@Valid Position> points) {
    }

    /** CPU-bound: Jackson parse of ~300 points, bean validation, then numeric analysis. */
    @PostMapping("/analyze")
    TrackAnalyzer.TrackSummary analyze(@Valid @RequestBody TrackRequest req) {
        return TrackAnalyzer.analyze(req.icao24(), req.points());
    }
}
