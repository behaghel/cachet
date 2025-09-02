# syntax=docker/dockerfile:1
FROM golang:1.22 AS build
WORKDIR /app
COPY . .
RUN go build -o server ./main.go
FROM gcr.io/distroless/base-debian12
WORKDIR /
COPY --from=build /app/server /server
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/server"]
