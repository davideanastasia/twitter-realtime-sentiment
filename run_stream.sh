#!/bin/bash

spark-submit --master local[*] --class com.da.stream.TwitterStream target/scala-2.10/twitter-realtime-sentiment-assembly-1.0.jar ./twitter.properties ./data/feelings.dict.csv