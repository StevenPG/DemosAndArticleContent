# Multi-stage build producing a tiny static image for the Go order service.
FROM golang:1.26.5 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /out/go-order-service .

FROM gcr.io/distroless/static-debian12
COPY --from=build /out/go-order-service /go-order-service
EXPOSE 8081
ENTRYPOINT ["/go-order-service"]
