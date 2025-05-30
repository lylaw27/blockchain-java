# Proof of Work Blockchain in Java

## Overview
This project implements a Proof of Work (PoW) blockchain in Java with the following key features:
- Peer-to-peer communication using gRPC
- Chain reorganization (reorg) handling
- Mempool for transaction management
- Comprehensive transaction and block validation
- Consensus mechanism based on PoW

## Features

### 1. Node-to-Node Communication (gRPC)
- Uses gRPC for efficient node communication
- Protocol buffers define message formats for blocks, transactions, and network messages
- Supports peer discovery and network propagation

### 2. Chain Reorganization
- Handles forks in the blockchain
- Automatically switches to the longest valid chain
- Reverts transactions from orphaned blocks and applies new ones

### 3. Mempool System
- Temporarily stores unconfirmed transactions
- Implements transaction prioritization
- Handles duplicate transaction detection
- Supports transaction expiration

### 4. Validation System
- *Block Validation*:
  - Proof of Work difficulty verification
  - Merkle root validation
  - Block size limits
  - Timestamp validation
  - Previous block hash verification

- *Transaction Validation*:
  - Digital signature verification
  - Input/output validation
  - Double-spend prevention
  - Fee verification

## Prerequisites
- Java JDK 17 or higher
- Maven 3.6+
- Protobuf compiler (protoc)
- gRPC Java libraries

## Installation
1. Clone the repository:
   bash
   git clone https://github.com/yourusername/pow-blockchain-java.git
   cd pow-blockchain-java
   

2. Build the project:
   bash
   mvn clean install
   

3. Generate gRPC code:
   bash
   mvn compile
   

## Configuration
Edit config.properties to configure:
- Network port
- Peer connections
- Mining difficulty
- Block time target
- Mempool settings

Example configuration:
properties
node.port=50051
peer.nodes=192.168.1.2:50051,192.168.1.3:50051
mining.difficulty=18
block.target.time=60000
mempool.max.size=10000


## Running the Node
Start a node with:
bash
mvn exec:java -Dexec.mainClass="com.blockchain.NodeMain"


### Command Line Options
- --mine: Enable mining mode
- --port: Specify custom port
- --peers: Override configured peers
- --genesis: Initialize new genesis block

## API Documentation
The node exposes gRPC services for:
- Submitting transactions
- Querying blockchain state
- Peer management
- Mining control

See src/main/proto/blockchain.proto for service definitions.

## Architecture

### Core Components
1. *Blockchain Manager*: Maintains the chain state and handles reorgs
2. *Network Service*: Manages gRPC communication
3. *Consensus Engine*: Implements PoW mining
4. *Mempool*: Handles unconfirmed transactions
5. *Validation Engine*: Validates blocks and transactions

### Data Structures
- Block: Contains header and transaction list
- Transaction: Inputs, outputs, and signatures
- UTXOSet: Unspent transaction outputs
- PeerList: Known network nodes

## Testing
Run unit tests:
bash
mvn test


Integration tests simulate network behavior with multiple nodes.

## Contributing
1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License
MIT License

## Future Work
- Wallet integration
- Smart contract support
- Performance optimizations
- Additional consensus mechanisms

## Contact
For questions or issues, please open a GitHub issue or contact maintainers.
