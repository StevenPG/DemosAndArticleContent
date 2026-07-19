# Multi-stage build for the payment/inventory stub.
FROM golang:1.26.5 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /out/stub .

FROM gcr.io/distroless/static-debian12
COPY --from=build /out/stub /stub
# 9095 = payment, 9096 = inventory
EXPOSE 9095 9096
ENTRYPOINT ["/stub"]
