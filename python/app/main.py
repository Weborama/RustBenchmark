import uvicorn
import os
from hashlib import blake2b
from fastapi import FastAPI
from pydantic import BaseModel
from psycopg_pool import ConnectionPool
import pika
import json


class InputData(BaseModel):
    id: int
    data: str

def get_rabbitMQ_connection():
    connection = pika.BlockingConnection(pika.URLParameters(os.getenv("DEMO_RABBITMQ")))
    return connection

app = FastAPI()
#PostgreSQL
pool = ConnectionPool(os.getenv("DEMO_POSTGRES"))
# RabbitMQ
connection = get_rabbitMQ_connection()

@app.post("/hash")
async def hash(input: InputData):

    with pool.connection() as conn:
        cur = conn.execute(
            "SELECT name FROM clients WHERE id = %s",
            (input.id,))
        name = cur.fetchone()[0]

    encoded_data = blake2b(input.data.encode('UTF-8')).hexdigest()
    message = {"name": name, "hash": encoded_data}

    global connection
    try:
        connection.channel().basic_publish(exchange='toRustDemo',routing_key='rust', body=json.dumps(message))
    except(pika.exceptions.StreamLostError, pika.exceptions.NoFreeChannels):
        connection = get_rabbitMQ_connection()
        connection.channel().basic_publish(exchange='toRustDemo',routing_key='rust', body=json.dumps(message))

    return message
