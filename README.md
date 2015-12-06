# twitter-realtime-sentiment

twitter-sentiment-stream is a POC of sentiment analysis for the Twitter stream. The project uses:

- Apache Spark: Spark Streaming is used to receive the Twitter stream, aggregate and convert to sentiments
- Apache Cassandra: used as aggregator of the sentiments in multiple views (aggregated views)
- Akka: used to expose a WebSocket with the latest view of the data
- d3.js / jQuery: visualize the latest version of the sentiment analysis

## Run the project

```
sbt assembly
```

to build the jar with the project. You will then need a running Cassandra listening on 127.0.0.1, an Spark installation 
and two bash windows open (I personally have used brew on my Mac to install them, as well as Scala and SBT):

In the first:

```
./run_stream.sh
```

will start the Spark Streaming job.

In the second:

```
./run_ws.sh
```

will start the HTTP WebSocket server.

Done that, you are ready to open the static/index.html file in your browser and see the cloud being refreshed!

## References

- http://bl.ocks.org/ericcoopey/6382449#d3.layout.cloud.js
- https://dzone.com/articles/create-reactive-websocket
- http://blog.scalac.io/2015/07/30/websockets-server-with-akka-http.html
- http://www.smartjava.org/content/create-reactive-websocket-server-akka-streams
- https://dzone.com/articles/real-time-twitter-heat-map
- https://github.com/comsysto/twitter-realtime-heatmap
- http://blog.comsysto.com/2012/07/10/real-time-twitter-heat-map-with-mongodb/