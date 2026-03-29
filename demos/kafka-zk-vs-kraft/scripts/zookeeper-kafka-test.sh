echo "Printing Broker Metadata"
kcat -b localhost:9092,localhost:9093,localhost:9094 -L
echo "Creating topic and publishing message"
echo "test" | kcat -b localhost:9092,localhost:9093,localhost:9094 -t test_topic -P