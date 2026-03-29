echo "Printing Broker Metadata"
kcat -b localhost:9094,localhost:9095,localhost:9096 -X broker.address.family=v4 -L
echo "Creating topic and publishing message"
echo "test" | kcat -b localhost:9094,localhost:9095,localhost:9096 -t test_topic -X broker.address.family=v4 -P