FROM rust:1.63 AS chef
ARG DEBIAN_FRONTEND=noninteractive

RUN cargo install cargo-chef
RUN update-ca-certificates

WORKDIR /app

FROM chef AS planner
COPY Cargo.* ./
RUN cargo chef prepare --recipe-path recipe.json


FROM chef AS builder
COPY --from=planner /app/recipe.json recipe.json
# Build dependencies - this is the caching Docker layer!
RUN cargo chef cook --release --recipe-path recipe.json
# Build application
ENV SQLX_OFFLINE true
COPY sqlx-data.json .
COPY src/ src/
RUN touch src/main.rs
RUN cargo build --release --bin rust-demo

FROM debian:11-slim
LABEL maintainer=emercier@weborama.com

RUN addgroup rust-demo && useradd -g rust-demo rust-demo

COPY --from=builder /app/target/release/rust-demo /usr/local/bin
EXPOSE 8080
USER rust-demo

ENTRYPOINT ["rust-demo"]
