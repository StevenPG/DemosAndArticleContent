# kafka-zk-vs-kraft
Small project POCing Zookeeper vs Kraft Kafka multi-broker deployment configurations

## Goal

My local testing of Kraft has resulted in some strange and inconsistent behavior.
The purpose of this repository is to build a reproducible example that contains a proof of concept of both 
consensus mechanism supported by kafka.

## TODO

- [ ] Create ZooKeeper/Kafka cluster 3-node
- [ ] Create Kafka Kraft cluster 1-node
- [ ] Create Kafka Kraft cluster 3-node
- [ ] Create Kafka Kraft cluster 6-node (3 broker, 3 controller)
- [ ] Kafkacat Scripts for publishing and consuming
- [ ] Kafdrop deployment for each that hooks into each cluster

## Executing

TODO - fill out with execution instructions for each section