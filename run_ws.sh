#!/bin/bash

spark-submit --master local[2] --class com.da.webapp.WSServer target/scala-2.10/twitter-realtime-sentiment-assembly-1.0.jar