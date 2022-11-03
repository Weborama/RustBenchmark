package com.weborama.demo.groovy

import com.rabbitmq.client.Channel as RmqChannel
import com.rabbitmq.client.ConnectionFactory
import com.tambapps.http.garcon.ContentType
import com.tambapps.http.garcon.Garcon
import groovy.json.JsonOutput
import groovy.sql.Sql
import groovy.transform.Field
import ove.crypto.digest.Blake2b

import java.util.concurrent.LinkedBlockingQueue

@Field // Blake2b is not thread safe.
final ThreadLocal<Blake2b.Digest> threadLocalBlake2b = ThreadLocal.withInitial(Blake2b.Digest.&newInstance)
@Field
Pool<Sql> sqlConnectionPool
@Field
Pool<RmqChannel> rmqChannelPool

sqlConnectionPool = Pool.createPool(Integer.parseInt(System.getenv('SQL_MAX_POOL_SIZE') ?: '10')) {
  Sql.newInstance(System.getenv('DEMO_POSTGRES') ?: 'jdbc:postgresql://localhost/rust?user=guest&password=guest')
}
println 'Connected successfully to sql database'
final ConnectionFactory rmqConnectionFactory = new ConnectionFactory().tap {
  setUri(System.getenv('DEMO_RABBITMQ') ?: 'amqp://guest:guest@127.0.0.1:5672/%2f')
}
rmqChannelPool = Pool.createPool(Integer.parseInt(System.getenv('RMQ_MAX_POOL_SIZE') ?: '10')) {
  rmqConnectionFactory.newConnection().createChannel()
}
println 'Successfully created rmq channels'

new Garcon('0.0.0.0', 8080).tap {
  maxThreads = Integer.parseInt(System.getenv('GARCON_MAX_THREADS') ?: '200') // default of spring-mvc
  println("Using $maxThreads threads")
  requestReadTimeoutMillis = 500
  onStart = { address, port -> println("Server successfully started on $address:$port") }
  onServerError = { Exception e -> println("An error occurred, server has to stop ${e.class.simpleName}: ${e.message}") }
  onExchangeError = { Exception e -> println("A connection occurred ${e.class.simpleName}: ${e.message}") }
}.serve {
  post '/hash',  accept: ContentType.JSON, contentType: ContentType.JSON, {
    def body = (Map) parsedRequestBody
    response.setBody(handle(body.id, body.data.toString()))
  }
}

String handle(def id, String data) {
  String hash = Base64.encoder.encodeToString(threadLocalBlake2b.get().digest(data.bytes))
  def row = sqlConnectionPool.acquire { Sql sql -> sql.firstRow('SELECT * FROM clients WHERE id = ?', [id]) }
  String json = JsonOutput.toJson([name: row['name'], hash: hash])
  rmqChannelPool.acquire { RmqChannel channel -> channel.basicPublish('toRustDemo', 'rust', null, json.bytes) }
  return json
}

class Pool<T> {
  private final LinkedBlockingQueue<T> queue

   static <T> Pool<T> createPool(int maxSize, Closure<T> objectCreator) {
     LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<>()
     maxSize.times { queue << objectCreator.call() }
     return new Pool<T>(queue)
   }

  Pool(LinkedBlockingQueue<T> queue) {
    this.queue = queue
  }

  T acquire() {
    return queue.take()
  }

  public <U> U acquire(Closure<U> closure) {
    def object = acquire()
    try {
      return closure.call(object)
    } finally {
      release(object)
    }
  }

  void release(T object) {
    queue.add(object)
  }
}