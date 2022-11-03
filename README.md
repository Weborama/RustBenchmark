# Rust benchmark demo

This repo contains a bunch of several implementation on the same logic:

 * Implements a HTTP endpoint waiting for a JSON looking like `{"id": 1, "data": "foo"}` as a POST body
 * Hash the `.data` field of the body using the [Black2B](https://en.wikipedia.org/wiki/BLAKE_(hash_function)#BLAKE2) hash function
 * Retrieve a *client* from postgres using the `.id` as the key
 * Build a JSON message with the *client.name* in the `name` field and the hash in the `hash` field
 * Send this message on RabbitMQ
 * Return this message in the HTTP response

## Why?

For fun of course! Initially, the rust implementation was built for a presentation by @fistons but then he wanted to compare rust performance against other languages

## Fnu! I want to add my favorite langage!

Easy! Create a subdirectory in this repo, with your code and a Dockerfile and add it to the docker-compose.
Here are the infos and credentials for the others services:
 * **RabbitMQ**:
 	* *host*: `rabbitmq`
 	* *username*: `guest`
 	* *password*: `guest`
 * **Postgreql**:
  	* *host*: `postgres`
 	* *username*: `rust`
 	* *password*: `rust`

Then, just add your service in the `docker-compose.yml` file, binding a specific port to the host and let's roll!

## What about the database model?

The database migration and filling is taking care of in the docker compose.

Here is the `CREATE TABLE`:

```sql
CREATE TABLE clients (
  id serial primary key,
  name varchar(100) not null unique
);
```

**Please note that there is only one client loaded by the migration:**

| id | name        |
| -- | ----------- | 
| 1  | Uber Client |


## How to publish on rabbitmq?

 * Exchange name: `toRustDemo`
 * Routing key: `rust`

## Have fun!

## Implementations notes

 * The reactive java implementation uses old fashion blocking jdbc because the postgres r2dbc driver [is slow as hell](https://github.com/spring-projects/spring-data-r2dbc/issues/203)
 
