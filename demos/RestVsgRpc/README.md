# RestVsgRpc
A repository containing a deployable client/server sample for REST and gRPC using Java for comparison and demonstration purposes.

The goal is to publish 100 entites to a database, and GET the entity after
creating it, resulting in roughly 200 entities being written and read from a remote database over a RESTful interface, as well as a gRPC interface.

## TODO

- [X] Define shared spec that REST API and gRPC will test with (moderate size)
- [X] Rest API server with GET/POST endpoints
- [X] Rest API client that calls itself
- [X] Rest API client takes CLI args to target
- [X] Rest API client is wrapped in timer, prints out result time
---------------------------
- [X] gRPC server with accept/read endpoints
- [X] gRPC client that calls itself
- [X] gRPC client that takes CLI args to target
- [X] gRPC client is wrapped in timer, prints out result time
---------------------------
- [ ] Build script results in runnable binary
- [ ] Deploy in remote EC2, call from local and test timing to do X requests
- [ ] Run many intervals and document
- [ ] Docker configuration that will deploy postgres instance both apps will connect to using their respective DB connection strategies.

- [ ] Add writeup into seperate README file
- [ ] Write article for publish with simple results

### gRPC

    protoc --plugin=protoc-gen-grpc-java=C:\protoc-3.17.3\bin\protoc-gen-grpc-java-1.40.1.exe -I="." --java_out=./grpc-svc/src/main/java --grpc-java_out=./grpc-svc/src/main/java .\message.proto
