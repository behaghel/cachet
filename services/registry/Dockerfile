# syntax=docker/dockerfile:1
FROM golang:1.25 AS build
WORKDIR /app
# Copy go mod and sum files first for better layer caching
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN go build -o server .
FROM gcr.io/distroless/base-debian12
WORKDIR /
COPY --from=build /app/server /server
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/server"]
