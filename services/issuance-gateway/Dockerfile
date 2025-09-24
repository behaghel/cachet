# syntax=docker/dockerfile:1
FROM golang:1.22 AS build

WORKDIR /workspace

# Prime module cache with go.mod replacements
COPY services/issuance-gateway/go.mod services/issuance-gateway/go.sum ./services/issuance-gateway/
COPY services/common/go.mod services/common/go.sum ./services/common/

RUN cd services/issuance-gateway \
    && go mod download

# Copy sources required for build (service + shared module)
COPY services/common ./services/common
COPY services/issuance-gateway ./services/issuance-gateway
COPY config ./config

RUN cd services/issuance-gateway \
    && go build -o /out/server .

FROM gcr.io/distroless/base-debian12
WORKDIR /
COPY --from=build /out/server /server
COPY config /config
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/server"]
