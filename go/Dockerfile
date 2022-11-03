FROM golang:latest AS golang

COPY . /go/src
WORKDIR /go/src

RUN go build ./...

FROM debian:latest

EXPOSE 8080

COPY --from=golang /go/src/daemon /opt/bin/

ENTRYPOINT ["/opt/bin/daemon"]
CMD ["-v"]
