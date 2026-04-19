# syntax=docker/dockerfile:1
ARG GO_VERSION=1.25
FROM golang:${GO_VERSION} AS build
ARG SERVICE

WORKDIR /src

# Copy shared modules first for layer caching
COPY services/common/go.mod services/common/go.sum services/common/
COPY generated/go/go.mod generated/go/go.sum generated/go/

# Copy the target service module files for dependency download
COPY services/${SERVICE}/go.mod services/${SERVICE}/go.sum services/${SERVICE}/

# Download dependencies (cached unless go.mod/go.sum change)
WORKDIR /src/services/${SERVICE}
RUN go mod download

# Copy source code
WORKDIR /src
COPY services/common/ services/common/
COPY generated/go/ generated/go/
COPY services/${SERVICE}/ services/${SERVICE}/

# Build static binary
WORKDIR /src/services/${SERVICE}
RUN CGO_ENABLED=0 go build -o /server .

FROM gcr.io/distroless/base-debian12
COPY --from=build /server /server
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/server"]
